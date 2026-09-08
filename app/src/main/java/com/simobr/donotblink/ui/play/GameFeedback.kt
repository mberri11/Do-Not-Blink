package com.simobr.donotblink.ui.play

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.simobr.donotblink.game.Twitch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Haptics and sound for the moments the game has. */
interface GameFeedback {
    fun perfect(haptics: Boolean, sound: Boolean)
    fun fail(haptics: Boolean, sound: Boolean)

    /**
     * The reflex test's reward, on the tap that catches the signal.
     *
     * Deliberately NOT played when the signal arrives. A sound or a buzz at that instant would give
     * the player something to react to other than the ring, and reaction to a sound is roughly 40ms
     * faster than to a shape — the reading would stop measuring what it claims to.
     *
     * [reactionMs] picks the pitch: the faster the catch, the brighter the chirp, so the sound says
     * how good the reading was before the number has finished being read.
     */
    fun reflexHit(haptics: Boolean, sound: Boolean, reactionMs: Long)

    /** The reflex test's correction, on a tap that came before the signal. */
    fun reflexTooSoon(haptics: Boolean, sound: Boolean)

    /**
     * The hold hum, at [frequencyHz]. Called once per frame while the ring contracts; the first
     * call starts the tone with its attack ramp and every later one only re-pitches it.
     */
    fun hold(frequencyHz: Float)

    /** Ends the hold hum with its release ramp. Idempotent — a stopped hum stays stopped. */
    fun stopHold()

    fun release()

    companion object {
        /** For tests and previews: silent, still. */
        val None: GameFeedback = object : GameFeedback {
            override fun perfect(haptics: Boolean, sound: Boolean) = Unit
            override fun fail(haptics: Boolean, sound: Boolean) = Unit
            override fun reflexHit(haptics: Boolean, sound: Boolean, reactionMs: Long) = Unit
            override fun reflexTooSoon(haptics: Boolean, sound: Boolean) = Unit
            override fun hold(frequencyHz: Float) = Unit
            override fun stopHold() = Unit
            override fun release() = Unit
        }
    }
}

@Composable
fun rememberGameFeedback(): GameFeedback {
    val context = LocalContext.current.applicationContext
    val feedback = remember(context) { AndroidGameFeedback(context) }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    return feedback
}

/**
 * Zero audio assets ship in the APK. The two verdict tones are synthesised as PCM the first time
 * they are needed and handed to an [AudioTrack] in MODE_STATIC, so a replay is stop / reload / play
 * with no decoding and no streaming. The hold hum cannot be pre-rendered — its pitch is a function
 * of the round in progress — so it gets its own MODE_STREAM track in [HoldHumVoice].
 */
class AndroidGameFeedback(context: Context) : GameFeedback {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    private val perfectTone: AudioTrack? by lazy { buildTrack(perfectPcm()) }
    private val failTone: AudioTrack? by lazy { buildTrack(failPcm()) }

    /**
     * Three chirps rather than one synthesised per tap: an [AudioTrack] built on the tap would
     * allocate and configure a hardware track in the same millisecond the player is being timed,
     * which is exactly the wrong moment to do it on the low-end hardware this game targets.
     */
    private val reflexTones: List<AudioTrack?> by lazy {
        REFLEX_CHIRP_BASE_HZ.map { buildTrack(reflexChirpPcm(it)) }
    }
    private val tooSoonTone: AudioTrack? by lazy { buildTrack(tooSoonPcm()) }
    private val hum = HoldHumVoice()

    override fun perfect(haptics: Boolean, sound: Boolean) {
        if (haptics) vibrate(predefined = VibrationEffect.EFFECT_TICK) {
            VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        if (sound) play(perfectTone)
    }

    override fun fail(haptics: Boolean, sound: Boolean) {
        if (haptics) vibrate(predefined = VibrationEffect.EFFECT_DOUBLE_CLICK) {
            VibrationEffect.createWaveform(longArrayOf(0L, 12L, 55L, 12L), -1)
        }
        if (sound) play(failTone)
    }

    override fun reflexHit(haptics: Boolean, sound: Boolean, reactionMs: Long) {
        // A heavier click than the ring game's tick. Catching the signal is a single decisive
        // event, where a perfect release is the end of a held breath.
        if (haptics) vibrate(predefined = VibrationEffect.EFFECT_HEAVY_CLICK) {
            VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        if (sound) play(reflexTones.getOrNull(Twitch.chirpTier(reactionMs)))
    }

    override fun reflexTooSoon(haptics: Boolean, sound: Boolean) {
        if (haptics) vibrate(predefined = VibrationEffect.EFFECT_DOUBLE_CLICK) {
            VibrationEffect.createWaveform(longArrayOf(0L, 14L, 40L, 14L), -1)
        }
        if (sound) play(tooSoonTone)
    }

    override fun hold(frequencyHz: Float) = hum.hold(frequencyHz)

    override fun stopHold() = hum.stop()

    override fun release() {
        // A leaked AudioTrack is an ANR on some OEMs. The hum owns a thread as well, so it goes
        // first and is waited for.
        hum.release()
        runCatching { perfectTone?.release() }
        runCatching { failTone?.release() }
        reflexTones.forEach { tone -> runCatching { tone?.release() } }
        runCatching { tooSoonTone?.release() }
    }

    private inline fun vibrate(predefined: Int, fallback: () -> VibrationEffect) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(predefined)
        } else {
            fallback()
        }
        runCatching { vibrator.vibrate(effect) }
    }

    private fun play(track: AudioTrack?) {
        val tone = track ?: return
        runCatching {
            if (tone.playState != AudioTrack.PLAYSTATE_STOPPED) tone.stop()
            tone.reloadStaticData()
            tone.play()
        }
    }

    private fun buildTrack(pcm: ShortArray): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
            .also { it.write(pcm, 0, pcm.size) }
    }.getOrNull()

    private companion object {
        const val SAMPLE_RATE_HZ = SampleRateHz

        /**
         * Chirp starting pitches, fastest tier first. Every one glides up an octave, so the whole
         * family reads as the same sound played higher or lower rather than three different sounds.
         */
        val REFLEX_CHIRP_BASE_HZ = listOf(740.0, 587.0, 466.0)

        fun samples(ms: Int): Int = SAMPLE_RATE_HZ * ms / 1000

        /** 40ms of 880Hz sine, 4ms attack, 12ms decay. */
        fun perfectPcm(): ShortArray {
            val total = samples(40)
            val attack = samples(4)
            val decay = samples(12)
            return ShortArray(total) { i ->
                val seconds = i.toDouble() / SAMPLE_RATE_HZ
                val envelope = when {
                    i < attack -> i.toDouble() / attack
                    i > total - decay -> (total - i).toDouble() / decay
                    else -> 1.0
                }
                ((sin(2.0 * PI * 880.0 * seconds) * envelope * 0.6) * Short.MAX_VALUE).toInt().toShort()
            }
        }

        /**
         * 70ms chirp gliding up an octave from [startHz], with a second harmonic for brightness.
         *
         * It has to be recognisably NOT the ring game's perfect tone, which is a steady 880Hz sine.
         * A glide is the cheapest way to be categorically different: the ear hears movement before
         * it hears pitch, so this reads as a distinct event even at the same loudness. Phase is
         * accumulated rather than computed from `sin(2*pi*f*t)` — with a changing f that formula
         * sweeps the phase, not the frequency, and produces a chirp at roughly twice the intended
         * rate.
         */
        fun reflexChirpPcm(startHz: Double): ShortArray {
            val total = samples(70)
            val attack = samples(2)
            val decay = samples(26)
            var phase = 0.0
            return ShortArray(total) { i ->
                val hz = startHz * (1.0 + i.toDouble() / total)
                phase += 2.0 * PI * hz / SAMPLE_RATE_HZ
                if (phase >= 2.0 * PI) phase -= 2.0 * PI
                val envelope = when {
                    i < attack -> i.toDouble() / attack
                    i > total - decay -> (total - i).toDouble() / decay
                    else -> 1.0
                }
                val wave = 0.78 * sin(phase) + 0.22 * sin(2.0 * phase)
                ((wave * envelope * 0.5) * Short.MAX_VALUE).toInt().toShort()
            }
        }

        /**
         * 90ms blip gliding DOWN, the reward chirp's exact opposite.
         *
         * Tapping before the signal is a mistake, not a failure — it costs the attempt nothing — so
         * it gets its own quiet correction rather than the ring game's 110Hz fail thud.
         */
        fun tooSoonPcm(): ShortArray {
            val total = samples(90)
            val attack = samples(2)
            val decay = samples(34)
            var phase = 0.0
            return ShortArray(total) { i ->
                val hz = 470.0 * (1.0 - 0.45 * i.toDouble() / total)
                phase += 2.0 * PI * hz / SAMPLE_RATE_HZ
                if (phase >= 2.0 * PI) phase -= 2.0 * PI
                val envelope = when {
                    i < attack -> i.toDouble() / attack
                    i > total - decay -> (total - i).toDouble() / decay
                    else -> 1.0
                }
                ((sin(phase) * envelope * 0.38) * Short.MAX_VALUE).toInt().toShort()
            }
        }

        /** 120ms of 110Hz square-ish thud: mostly square, softened with its own fundamental. */
        fun failPcm(): ShortArray {
            val total = samples(120)
            val attack = samples(2)
            return ShortArray(total) { i ->
                val seconds = i.toDouble() / SAMPLE_RATE_HZ
                val phase = 2.0 * PI * 110.0 * seconds
                val wave = 0.65 * (if (sin(phase) >= 0.0) 1.0 else -1.0) + 0.35 * sin(phase)
                val envelope = exp(-seconds * 22.0) * if (i < attack) i.toDouble() / attack else 1.0
                ((wave * envelope * 0.55) * Short.MAX_VALUE).toInt().toShort()
            }
        }
    }
}

/** Mono 16-bit 44.1kHz, for every tone this app makes. */
private const val SampleRateHz = 44_100

/**
 * The rising phosphor hum: a continuously generated sine whose pitch tracks the ring.
 *
 * MODE_STREAM rather than MODE_STATIC, because the pitch is not known until the round is running.
 * A single daemon thread generates blocks and hands them to a blocking [AudioTrack.write], which is
 * what paces it — there is no timer here and no second clock. Phase is carried across blocks, so
 * re-pitching mid-tone never produces a discontinuity, and the gain ramps over
 * [HoldHum.RAMP_MS] at both ends so neither the start nor the stop clicks.
 */
private class HoldHumVoice {

    private val lock = Any()
    private var track: AudioTrack? = null
    private var writer: Thread? = null

    /** Read by the writer thread every sample. */
    @Volatile private var frequencyHz: Float = HoldHum.START_HZ

    /** Set by the UI thread; the writer ramps to silence and then finishes. */
    @Volatile private var stopping: Boolean = true

    @Volatile private var closed: Boolean = false

    /** Starts the tone if it is not already running, and re-pitches it either way. */
    fun hold(hz: Float) {
        frequencyHz = hz
        synchronized(lock) {
            if (closed) return
            stopping = false
            if (writer != null) return
            val minBytes = AudioTrack.getMinBufferSize(SampleRateHz, CHANNEL_MASK, ENCODING)
            if (minBytes <= 0) return
            val tone = runCatching { buildStreamTrack(minBytes) }.getOrNull() ?: return
            if (runCatching { tone.play() }.isFailure) {
                runCatching { tone.release() }
                return
            }
            track = tone
            writer = Thread({ pump(tone) }, "dnb-hold-hum").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY + 1
                start()
            }
        }
    }

    /** Ramps to silence. The writer tears the track down once the ramp has actually been heard. */
    fun stop() {
        stopping = true
    }

    /**
     * Signals and returns. The writer owns the track and releases it in its own `finally` within
     * one block plus the release ramp — about 25ms — so nothing is leaked and the main thread does
     * not wait on an audio thread to notice.
     */
    fun release() {
        synchronized(lock) {
            closed = true
            stopping = true
        }
    }

    private fun pump(tone: AudioTrack) {
        val block = ShortArray(BLOCK_SAMPLES)
        val rampStep = 1f / max(1, SampleRateHz * HoldHum.RAMP_MS / 1000)
        var phase = 0.0
        var gain = 0f
        try {
            while (true) {
                for (i in block.indices) {
                    val target = if (stopping) 0f else 1f
                    gain = if (gain < target) min(target, gain + rampStep) else max(target, gain - rampStep)
                    phase += TWO_PI * frequencyHz / SampleRateHz
                    if (phase >= TWO_PI) phase -= TWO_PI
                    block[i] = (sin(phase) * gain * HoldHum.AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
                }
                val written = runCatching { tone.write(block, 0, block.size) }.getOrDefault(-1)
                if (written < 0) break
                if (stopping && gain <= 0f) break
            }
        } finally {
            runCatching { tone.stop() }
            runCatching { tone.release() }
            synchronized(lock) {
                if (track === tone) {
                    track = null
                    writer = null
                }
            }
        }
    }

    private fun buildStreamTrack(minBytes: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SampleRateHz)
                .setChannelMask(CHANNEL_MASK)
                .build()
        )
        .setBufferSizeInBytes(max(minBytes, BLOCK_SAMPLES * 2 * Short.SIZE_BYTES))
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    private companion object {
        val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO

        /** ~11.6ms per block: one ramp fits inside a block, and latency stays under a frame. */
        const val BLOCK_SAMPLES = 512

        const val TWO_PI = 2.0 * PI
    }
}
