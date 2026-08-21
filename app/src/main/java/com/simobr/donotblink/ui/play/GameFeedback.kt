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
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Haptics and sound for the two moments the game has. */
interface GameFeedback {
    fun perfect(haptics: Boolean, sound: Boolean)
    fun fail(haptics: Boolean, sound: Boolean)
    fun release()

    companion object {
        /** For tests and previews: silent, still. */
        val None: GameFeedback = object : GameFeedback {
            override fun perfect(haptics: Boolean, sound: Boolean) = Unit
            override fun fail(haptics: Boolean, sound: Boolean) = Unit
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
 * Zero audio assets ship in the APK. Both tones are synthesised as PCM the first time they are
 * needed and handed to an [AudioTrack] in MODE_STATIC, so a replay is stop / reload / play with no
 * decoding and no streaming.
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

    override fun release() {
        runCatching { perfectTone?.release() }
        runCatching { failTone?.release() }
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
        const val SAMPLE_RATE_HZ = 44_100

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
