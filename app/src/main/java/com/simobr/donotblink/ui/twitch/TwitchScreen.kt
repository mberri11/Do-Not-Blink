package com.simobr.donotblink.ui.twitch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simobr.donotblink.game.Twitch
import com.simobr.donotblink.ui.common.noRippleClickable
import com.simobr.donotblink.ui.play.GameFeedback
import com.simobr.donotblink.ui.play.rememberGameFeedback
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbDim
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalLayoutScale
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.LocalRingScale
import com.simobr.donotblink.ui.theme.scaled
import com.simobr.donotblink.ui.theme.scaledType
import com.simobr.donotblink.ui.theme.tinted
import kotlin.random.Random
import kotlinx.coroutines.delay

object TwitchTags {
    const val SURFACE = "twitch-surface"
    const val PROMPT = "twitch-prompt"
    const val READING = "twitch-reading"
    const val HOME = "twitch-home"
}

/** Where the test is in one attempt. Local to this screen: it shares nothing with the ring game. */
private enum class Step { Ready, Waiting, Signal, Scored, TooSoon, Done }

internal val TwitchLabelStyle = DnbType.microLabel.copy(letterSpacing = 0.4.em, color = DnbColor.LabelMid)
internal val TwitchGradeStyle = DnbType.microLabel.copy(
    fontSize = 11.sp,
    letterSpacing = 0.3.em,
    color = DnbColor.LabelMid,
)

private const val HEADER_TOP_DP = 96f
private const val ATTEMPT_TOP_DP = 126f
private const val RING_CENTRE_Y_DP = 360f
private const val READING_TOP_DP = 320f
private const val GRADE_TOP_DP = 424f
private const val PROMPT_TOP_DP = 470f
private const val SIGNAL_RADIUS_DP = 120f

/** How long a scored attempt is left on screen before the next wait begins. */
private const val SCORE_DWELL_MS = 950L

/** A too-soon tap is shown for slightly longer — it is a correction, and it needs to land. */
private const val TOO_SOON_DWELL_MS = 1_150L

/**
 * The reflex test. Five attempts: the screen waits an unpredictable stretch, the ring arrives, and
 * the gap between the frame it was drawn on and the finger going down is the score.
 *
 * The measurement is the same one the main game makes — a frame time and a pointer's own
 * `uptimeMillis`, on one clock — so a number here means the same thing as a number there.
 */
@Composable
fun TwitchScreen(
    bestMs: Int,
    onReaction: (Long) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    random: Random = Random.Default,
    hapticsEnabled: Boolean = true,
    soundEnabled: Boolean = true,
    feedback: GameFeedback = rememberGameFeedback(),
    /**
     * Fired once when the fifth attempt has been scored and the summary is up. The screen reports
     * the fact; whether it is worth an interstitial is not its decision to make.
     */
    onSittingCompleted: () -> Unit = {},
) {
    val palette = LocalPalette.current
    BackHandler(onBack = onHome)

    var step by remember { mutableStateOf(Step.Ready) }
    var attempt by remember { mutableIntStateOf(0) }
    var reactions by remember { mutableStateOf(emptyList<Long>()) }
    var lastReactionMs by remember { mutableStateOf<Long?>(null) }

    // The frame the signal was actually drawn on. Not a coroutine's idea of "now": the delay above
    // it can overshoot by a frame or more, and that error would be charged to the player.
    var signalAtUptimeMs by remember { mutableLongStateOf(0L) }

    // The gesture block below is keyed on Unit and so is captured exactly once. Everything it reads
    // must therefore be either a state delegate (live by construction) or wrapped here — a plain
    // parameter would freeze at whatever it was on first composition. Both flags arrive from
    // DataStore asynchronously, so "it cannot have changed yet" is an assumption, not a guarantee.
    val report = rememberUpdatedState(onReaction)
    val haptics = rememberUpdatedState(hapticsEnabled)
    val sound = rememberUpdatedState(soundEnabled)
    val sittingCompleted = rememberUpdatedState(onSittingCompleted)

    // One attempt: wait an unpredictable stretch, then light the ring on a frame we timestamp.
    LaunchedEffect(step, attempt) {
        when (step) {
            Step.Waiting -> {
                delay(Twitch.waitMs(random))
                signalAtUptimeMs = withFrameNanos { it / 1_000_000L }
                step = Step.Signal
            }
            Step.Scored -> {
                delay(SCORE_DWELL_MS)
                if (attempt >= Twitch.ATTEMPTS) {
                    step = Step.Done
                    // Exactly once per sitting: (Scored, attempt=ATTEMPTS) is a key combination
                    // this effect reaches once, and Done is only ever entered from here.
                    sittingCompleted.value.invoke()
                } else {
                    step = Step.Waiting
                }
            }
            Step.TooSoon -> {
                delay(TOO_SOON_DWELL_MS)
                step = Step.Waiting
            }
            else -> Unit
        }
    }

    val scale = LocalLayoutScale.current
    val ringScale = LocalRingScale.current

    Box(
        modifier
            .fillMaxSize()
            .background(DnbColor.Black)
            .testTag(TwitchTags.SURFACE)
            // Keyed on Unit, NOT on step: pointerInput cancels and restarts its coroutine whenever
            // its key changes, and across that restart there is a window in which this node is
            // listening to nothing. `step` turns over four times per attempt, so keying on it put
            // such a window either side of every transition — including Waiting -> Signal, where a
            // fast tap is the whole point. A tap landing in one was not mistimed, it was DROPPED.
            // Keying on Unit lets the loop run uninterrupted for the screen's whole life; `step` is
            // read fresh each iteration anyway, exactly as PlayScreen's roundInput does it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        when (step) {
                            Step.Ready -> {
                                attempt = 0
                                reactions = emptyList()
                                lastReactionMs = null
                                step = Step.Waiting
                            }
                            // Tapping before the signal voids the attempt. It is NOT scored as a
                            // slow reaction — guessing must cost, not merely rank badly.
                            Step.Waiting -> {
                                feedback.reflexTooSoon(haptics.value, sound.value)
                                step = Step.TooSoon
                            }
                            Step.Signal -> {
                                val reactionMs = down.uptimeMillis - signalAtUptimeMs
                                lastReactionMs = reactionMs
                                attempt += 1
                                if (Twitch.isRecordable(reactionMs)) {
                                    reactions = reactions + reactionMs
                                    report.value.invoke(reactionMs)
                                    // Only a reading that counts gets the reward. A guess or a tap
                                    // from someone who wandered off is told so, not congratulated.
                                    feedback.reflexHit(haptics.value, sound.value, reactionMs)
                                } else {
                                    feedback.reflexTooSoon(haptics.value, sound.value)
                                }
                                step = Step.Scored
                            }
                            Step.Done -> {
                                step = Step.Ready
                                attempt = 0
                                reactions = emptyList()
                                lastReactionMs = null
                            }
                            else -> Unit
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, (RING_CENTRE_Y_DP * scale).dp.toPx())
            val radius = (SIGNAL_RADIUS_DP * ringScale).dp.toPx()

            // Dim outer track always, so the eye has somewhere to rest and the signal has somewhere
            // to arrive. Anything more during the wait would be something to anticipate.
            drawCircle(palette.dim, radius, centre, style = Stroke(1.dp.toPx()))

            if (step == Step.Signal) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to palette.hot.copy(alpha = 0.30f),
                        1f to Color.Transparent,
                        center = centre,
                        radius = radius * 1.6f,
                    ),
                    radius = radius * 1.6f,
                    center = centre,
                )
                drawCircle(palette.hot.copy(alpha = 0.12f), radius, centre, style = Stroke(26.dp.toPx()))
                drawCircle(palette.hot.copy(alpha = 0.30f), radius, centre, style = Stroke(12.dp.toPx()))
                drawCircle(palette.hot, radius, centre, style = Stroke(3.dp.toPx()))
            }
        }

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Text(
                text = "REFLEX TEST",
                style = TwitchLabelStyle.tinted(palette),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = HEADER_TOP_DP.scaled()),
            )
            Text(
                text = when {
                    step == Step.Ready -> if (bestMs > 0) "BEST ${bestMs}MS" else "NO READING YET"
                    step == Step.Done -> "BEST ${bestMs}MS"
                    else -> "ATTEMPT ${(attempt + 1).coerceAtMost(Twitch.ATTEMPTS)}/${Twitch.ATTEMPTS}"
                },
                style = TwitchGradeStyle,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = ATTEMPT_TOP_DP.scaled()),
            )

            // The reading. Only a scored attempt or the summary has one.
            val reading = when (step) {
                Step.Scored -> lastReactionMs?.let { "${it}MS" }
                Step.Done -> Twitch.best(reactions)?.let { "${it}MS" } ?: "—"
                else -> null
            }
            if (reading != null) {
                Text(
                    text = reading,
                    style = DnbType.failNumeral.tinted(palette).scaledType(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = READING_TOP_DP.scaled())
                        .testTag(TwitchTags.READING),
                )
            }

            val grade = when {
                step == Step.Scored -> lastReactionMs?.let {
                    when {
                        Twitch.isGuess(it) -> "TOO FAST TO BE REAL — NOT COUNTED"
                        Twitch.isAbandoned(it) -> "WERE YOU LOOKING? — NOT COUNTED"
                        else -> Twitch.grade(it)
                    }
                }
                step == Step.Done -> Twitch.mean(reactions)?.let { "MEAN ${it}MS" } ?: "NOTHING RECORDED"
                else -> null
            }
            if (grade != null) {
                Text(
                    text = grade,
                    style = TwitchGradeStyle.tinted(palette),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = GRADE_TOP_DP.scaled()),
                )
            }

            Text(
                text = when (step) {
                    Step.Ready -> "TAP TO ARM"
                    Step.Waiting -> "WAIT"
                    Step.Signal -> "NOW"
                    Step.TooSoon -> "TOO SOON"
                    Step.Scored -> ""
                    Step.Done -> "TAP TO RUN AGAIN"
                },
                style = DnbType.ctaSmall.tinted(palette).let {
                    if (step == Step.TooSoon) it.copy(color = DnbColor.LabelMid) else it
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = PROMPT_TOP_DP.scaled())
                    .testTag(TwitchTags.PROMPT),
            )

            if (step == Step.Done) {
                AttemptStrip(
                    reactions = reactions,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = (PROMPT_TOP_DP + 40f).scaled()),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(48.dp)
                    .testTag(TwitchTags.HOME)
                    .noRippleClickable(onClick = onHome),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "HOME",
                    style = DnbType.microLabel.copy(letterSpacing = 0.3.em, color = DnbColor.LabelMid),
                    modifier = Modifier.padding(horizontal = DnbDim.screenPadH),
                )
            }
        }
    }
}

/** Every reading from the sitting, in order, so the summary shows consistency and not just a best. */
@Composable
private fun AttemptStrip(reactions: List<Long>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (reactions.isEmpty()) "" else reactions.joinToString("  ") { "$it" },
            style = TwitchGradeStyle,
        )
    }
}
