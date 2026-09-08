package com.simobr.donotblink.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simobr.donotblink.game.DailyTrial
import com.simobr.donotblink.game.Difficulty
import com.simobr.donotblink.game.GameEvent
import com.simobr.donotblink.game.GameViewModel
import com.simobr.donotblink.game.Mode
import com.simobr.donotblink.game.Phase
import com.simobr.donotblink.game.Readout
import com.simobr.donotblink.game.RoundPlan
import com.simobr.donotblink.game.Teaching
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbDim
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalLayoutScale
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.LocalRingScale
import com.simobr.donotblink.ui.theme.PhosphorPalette
import com.simobr.donotblink.ui.theme.scaled
import com.simobr.donotblink.ui.theme.scaledType
import com.simobr.donotblink.ui.theme.tinted
import com.simobr.donotblink.ui.home.HomeChrome
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

object PlayScreenTags {
    const val SURFACE = "play-surface"
    const val STREAK = "play-streak"
    const val RULE = "play-rule"
    const val PRECISION = "play-precision"
}

/**
 * Mockup styles with no frozen token: derived from the nearest token, never added to DnbType.
 *
 * The STREAK caption carries information, so it sits on [DnbColor.LabelMid] rather than Dim, which
 * is unreadable at low panel brightness and is now decorative only.
 */
internal val StreakCaptionStyle = DnbType.microLabel.copy(color = DnbColor.LabelMid)

/** The rule, stated under the ring while the player is still learning it. */
internal val RuleLineStyle = DnbType.microLabel.copy(
    fontSize = 10.sp,
    fontWeight = FontWeight.W400,
    letterSpacing = 0.28.em,
    color = DnbColor.LabelMid,
)

/**
 * The signed error under a perfect release. It rides [DnbColor.Hot] because it belongs to the flash
 * — it is the flash saying how good the hit actually was, and it leaves with it.
 */
internal val PrecisionStyle = DnbType.microLabel.copy(
    fontSize = 11.sp,
    letterSpacing = 0.22.em,
    color = DnbColor.Hot,
)

/** Outer dim track, and the tick marks that sit just outside it. Read off the mockup. */
private const val OUTER_TRACK_DP = 168f
private const val TICK_INNER_DP = 172f
private const val TICK_OUTER_DP = 180f
private const val BLOOM_DP = 64f
private const val SHOCK_TRAVEL_DP = 32f
private const val STREAK_TOP_DP = 88f
private const val STREAK_GAP_DP = 16f

/** The teaching line's baseline, 120dp below the ring centre, before scaling. */
private const val RULE_BASELINE_FROM_TOP = 430f + 120f

/** How long the dead ring is left standing before the fail screen replaces it. */
const val FAIL_DWELL_MS = 450L

@Composable
fun PlayScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier,
    feedback: GameFeedback = rememberGameFeedback(),
    homeChrome: Boolean = false,
    onRoundStarted: () -> Unit = {},
    onFailed: () -> Unit = {},
    onOpenTitles: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDaily: () -> Unit = {},
    onOpenTwitch: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalPalette.current

    val latestRoundStarted = rememberUpdatedState(onRoundStarted)
    val latestFailed = rememberUpdatedState(onFailed)
    val reportDown = remember { { latestRoundStarted.value.invoke() } }

    // Only endless mode leaves for the fail screen. In a trial a miss is scored and the next ring
    // arms itself, so this must not fire — it would tear the player out of ring three of ten.
    LaunchedEffect(state.phase, state.roundId, state.mode) {
        if (state.mode == Mode.Endless &&
            (state.phase == Phase.Failed || state.phase == Phase.ContinueOffer)
        ) {
            delay(FAIL_DWELL_MS)
            latestFailed.value.invoke()
        }
    }

    // Per-frame values. NEVER read from the composable body — only from inside a draw or
    // graphicsLayer lambda, so a frame invalidates the draw phase and nothing above it. Reading
    // these in composition would re-run the whole composable 60-120 times a second, which on the
    // Redmi/Infinix hardware this game is aimed at is the entire frame budget.
    val ringRadiusDp = remember { mutableFloatStateOf(Difficulty.START_RADIUS_DP) }
    val flash = remember { mutableFloatStateOf(0f) }
    val shock = remember { mutableFloatStateOf(INACTIVE) }
    val digitScale = remember { mutableFloatStateOf(1f) }
    val ringHidden = remember { mutableStateOf(false) }

    // Frames are only asked for while something actually moves. Idle, Failed and the states past
    // it are static: they take one frame to paint and then stop, which keeps a still screen off the
    // CPU on low-end hardware (and lets a Compose test reach idle).
    //
    // A missed TRIAL ring is the exception: its dwell is driven by onFrame, so frames must keep
    // coming or the trial would stop dead on the first ring anyone misses.
    val animating = state.phase == Phase.Holding ||
        state.phase == Phase.Perfect ||
        (state.mode == Mode.Daily && state.phase == Phase.Failed)

    // state.mode is a KEY here, not just an input to `animating`: every current caller happens to
    // flip mode in the same breath as phase or roundId, which is what makes this safe today, not
    // what makes it correct. Without the key, a future mode change alone would leave this loop
    // running on a stale `animating` captured from the composition that launched it.
    LaunchedEffect(state.roundId, state.phase, state.mode) {
        do {
            withFrameNanos { frameTimeNanos ->
                // Choreographer frame time and MotionEvent uptimeMillis share one timebase, so the
                // rendered radius tracks the judged one. Rendering may lag; judging never does.
                val now = frameTimeNanos / 1_000_000L
                viewModel.onFrame(now)

                val current = viewModel.state.value
                val radiusDp = viewModel.ringRadiusDpAt(now)
                ringRadiusDp.floatValue = radiusDp

                // The hum is the ring in audio. It rides this same frame — no second clock, no
                // second sampling of the round.
                if (current.phase == Phase.Holding && current.soundEnabled) {
                    feedback.hold(
                        HoldHum.frequencyHz(
                            radiusDp = radiusDp,
                            startRadiusDp = current.plan.startRadiusDp,
                            targetRadiusDp = current.plan.targetRadiusDp,
                        )
                    )
                }

                val elapsedMs = (now - current.roundStartUptimeMs).toFloat()
                ringHidden.value = current.phase == Phase.Holding &&
                    current.plan.blackout?.coversMs(elapsedMs) == true

                if (current.phase == Phase.Perfect) {
                    val t = ((now - current.perfectAtUptimeMs).toFloat() / GameViewModel.FLASH_MS)
                        .coerceIn(0f, 1f)
                    flash.floatValue = 1f - t
                    shock.floatValue = t
                    digitScale.floatValue =
                        if (t <= 0.5f) 1f + 0.36f * t else 1.18f - 0.36f * (t - 0.5f)
                } else {
                    flash.floatValue = 0f
                    shock.floatValue = INACTIVE
                    digitScale.floatValue = 1f
                }
            }
        } while (animating && isActive)
    }

    LaunchedEffect(viewModel, feedback) {
        viewModel.events.collect { event ->
            val current = viewModel.state.value
            // The hum dies on the frame of judgement, before the verdict's own tone starts.
            feedback.stopHold()
            when (event) {
                is GameEvent.Perfect -> feedback.perfect(current.hapticsEnabled, current.soundEnabled)
                is GameEvent.Fail -> feedback.fail(current.hapticsEnabled, current.soundEnabled)
            }
        }
    }

    // A cancelled gesture never reaches the event flow, and SOUND can be switched off mid-hold.
    // Either way the hum stops with its release ramp rather than being left running.
    LaunchedEffect(state.phase, state.soundEnabled) {
        if (state.phase != Phase.Holding || !state.soundEnabled) feedback.stopHold()
    }

    val scale = LocalLayoutScale.current
    val ringScale = LocalRingScale.current

    // The play surface is the ONE screen that must keep filling the whole window: the press can
    // land anywhere, including under the status bar. So the Canvas draws edge to edge and takes no
    // insets, and the ring is offset by the top inset by hand so that it shares a coordinate space
    // with the chrome, which IS inset-padded.
    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()

    Box(modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag(PlayScreenTags.SURFACE)
                .roundInput(viewModel, reportDown)
        ) {
            // Home has no ring at all: the ring appears with the press that starts the round.
            if (!homeChrome) {
                drawRound(
                    palette = palette,
                    plan = state.plan,
                    phase = state.phase,
                    ringRadiusDp = ringRadiusDp.floatValue,
                    flash = flash.floatValue,
                    shock = shock.floatValue,
                    ringHidden = ringHidden.value,
                    scale = scale,
                    ringScale = ringScale,
                    topInset = topInset,
                )
            }
        }

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (homeChrome) {
            HomeChrome(
                best = state.best,
                onOpenTitles = onOpenTitles,
                onOpenSettings = onOpenSettings,
                dailyPlayedToday = state.dailyPlayedToday,
                dailyHitsToday = state.dailyResult?.hits ?: 0,
                onOpenDaily = onOpenDaily,
                twitchBestMs = state.twitchBestMs,
                onOpenTwitch = onOpenTwitch,
            )
        } else {
            StreakBlock(
                // In a trial the numeral is hits-so-far and the caption is which ring you are on:
                // "STREAK" would be a lie, because a trial ring survives a miss.
                caption = if (state.mode == Mode.Daily) {
                    "RING ${(state.dailyRingIndex + 1).coerceAtMost(DailyTrial.RINGS)}/${DailyTrial.RINGS}"
                } else {
                    "STREAK"
                },
                streak = if (state.mode == Mode.Daily) state.dailyHits else state.streak,
                phase = state.phase,
                palette = palette,
                digitScale = digitScale,
                // The hit's own error, for the 540ms the flash and its quiet last. Reading
                // lastRelease in composition is safe: it changes at judgement, never per frame.
                precision = if (state.phase == Phase.Perfect) {
                    Readout.precisionLine(state.lastRelease)
                } else {
                    ""
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // The rule, for as long as the player is still learning it, and then never again.
            if (Teaching.showsRingLine(state.lifetimeRuns, state.streak)) {
                Text(
                    text = "release when the ring meets the line",
                    style = RuleLineStyle,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .paddingFromBaseline(top = RULE_BASELINE_FROM_TOP.scaled())
                        .testTag(PlayScreenTags.RULE),
                )
            }
        }
        }
    }
}

/**
 * The first pointer down owns the round. Every other pointer is ignored completely — a second
 * finger cannot start, restart or end anything.
 *
 * A cancelled gesture (shade pulled down, call arriving, system gesture) unwinds this coroutine,
 * and the `finally` reports a cancel rather than a lift, so the round is aborted without a fail
 * and the streak survives.
 */
private fun Modifier.roundInput(
    viewModel: GameViewModel,
    onDown: () -> Unit,
): Modifier = pointerInput(viewModel, onDown) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val pointerId = down.id
            viewModel.onPointerDown(down.uptimeMillis)
            onDown()

            var settled = false
            try {
                while (!settled) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId }
                    if (change == null) {
                        // Our pointer left the stream without a lift.
                        viewModel.onPointerCancel()
                        settled = true
                    } else if (!change.pressed) {
                        change.consume()
                        viewModel.onPointerUp(change.uptimeMillis)
                        settled = true
                    }
                }
            } finally {
                if (!settled) viewModel.onPointerCancel()
            }
        }
    }
}

@Composable
private fun StreakBlock(
    caption: String,
    streak: Int,
    phase: Phase,
    palette: PhosphorPalette,
    digitScale: FloatState,
    precision: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = STREAK_TOP_DP.scaled()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(STREAK_GAP_DP.dp),
    ) {
        Text(text = caption, style = StreakCaptionStyle)
        Text(
            text = streak.toString().padStart(2, '0'),
            style = (if (phase == Phase.Perfect) DnbType.streakPerfect else DnbType.streakLive)
                .tinted(palette)
                .scaledType(),
            modifier = Modifier
                .testTag(PlayScreenTags.STREAK)
                .graphicsLayer {
                    val scale = digitScale.floatValue
                    scaleX = scale
                    scaleY = scale
                },
        )
        // Always laid out, so the streak numeral above it does not jump when a hit lands. The
        // string is empty outside Phase.Perfect and the row simply measures to nothing visible.
        Text(
            text = precision,
            style = PrecisionStyle.tinted(palette),
            modifier = Modifier.testTag(PlayScreenTags.PRECISION),
        )
    }
}

private fun DrawScope.drawRound(
    palette: PhosphorPalette,
    plan: RoundPlan,
    phase: Phase,
    ringRadiusDp: Float,
    flash: Float,
    shock: Float,
    ringHidden: Boolean,
    scale: Float,
    ringScale: Float,
    topInset: Dp,
) {
    // The ring's radii ride ringScale, which is bounded by the SMALLER axis: a circle that scaled
    // with height alone would run off the sides of a tall narrow window. Its centre rides the
    // layout scale plus the top inset, so it agrees with the inset-padded chrome above it.
    val centre = Offset(
        size.width / 2f,
        topInset.toPx() + (DnbDim.ringCentreYFromTop.value * scale).dp.toPx(),
    )

    // 1. dim outer track
    drawCircle(
        color = palette.dim,
        radius = (OUTER_TRACK_DP * ringScale).dp.toPx(),
        center = centre,
        style = Stroke(width = 1.dp.toPx()),
    )

    // 2. tick marks at N/E/S/W
    val tickInner = (TICK_INNER_DP * ringScale).dp.toPx()
    val tickOuter = (TICK_OUTER_DP * ringScale).dp.toPx()
    val tickWidth = 1.6.dp.toPx()
    drawLine(palette.dim, Offset(centre.x, centre.y - tickOuter), Offset(centre.x, centre.y - tickInner), tickWidth)
    drawLine(palette.dim, Offset(centre.x, centre.y + tickInner), Offset(centre.x, centre.y + tickOuter), tickWidth)
    drawLine(palette.dim, Offset(centre.x - tickOuter, centre.y), Offset(centre.x - tickInner, centre.y), tickWidth)
    drawLine(palette.dim, Offset(centre.x + tickInner, centre.y), Offset(centre.x + tickOuter, centre.y), tickWidth)

    // 3. target line
    drawCircle(
        color = palette.phosphor.copy(alpha = 0.7f),
        radius = (plan.targetRadiusDp * ringScale).dp.toPx(),
        center = centre,
        style = Stroke(width = 1.2.dp.toPx()),
    )

    // 4. glow stack, then the core. No blur, no RenderEffect, no shadow — concentric strokes.
    if (!ringHidden) {
        // Flash snap. A release inside the band can still sit visibly off the line, which makes a
        // clean hit look sloppy. The Perfect frame is drawn ON the line; Release.errorDp still
        // carries the true value and the judgement never sees this.
        val liveDp = if (phase == Phase.Perfect) plan.targetRadiusDp else ringRadiusDp
        val live = (liveDp * ringScale).dp.toPx()
        val ringColour = if (phase == Phase.Idle || phase == Phase.Holding || phase == Phase.Perfect) {
            lerp(palette.phosphor, palette.hot, flash)
        } else {
            palette.dim
        }
        val halo = 1f + 1.5f * flash

        // P7's slow decay: the ring drags a short trail of where it just was.
        if (palette.trailMs > 0 && phase == Phase.Holding) {
            repeat(TRAIL_STEPS) { step ->
                val ageMs = palette.trailMs * (step + 1f) / TRAIL_STEPS
                val ghost = ((liveDp + plan.speedDpPerSec * (ageMs / 1000f)) * ringScale).dp.toPx()
                val ghostAlpha = 0.32f * (1f - (step + 1f) / (TRAIL_STEPS + 1f))
                drawCircle(
                    color = ringColour.copy(alpha = ghostAlpha),
                    radius = ghost,
                    center = centre,
                    style = Stroke((plan.strokeDp * ringScale).dp.toPx()),
                )
            }
        }

        drawCircle(ringColour.copy(alpha = 0.05f * halo), live, centre, style = Stroke(22.dp.toPx()))
        drawCircle(ringColour.copy(alpha = 0.10f * halo), live, centre, style = Stroke(13.dp.toPx()))
        drawCircle(ringColour.copy(alpha = 0.26f * halo), live, centre, style = Stroke(6.dp.toPx()))
        drawCircle(ringColour, live, centre, style = Stroke((plan.strokeDp * ringScale).dp.toPx()))

        if (flash > 0f) {
            drawCircle(
                color = DnbColor.Glint.copy(alpha = flash),
                radius = live,
                center = centre,
                style = Stroke(width = 1.3.dp.toPx()),
            )
        }
    }

    // the shock ring, expanding out of the line and fading
    if (shock >= 0f) {
        drawCircle(
            color = palette.hot.copy(alpha = 1f - shock),
            radius = ((plan.targetRadiusDp + SHOCK_TRAVEL_DP * shock) * ringScale).dp.toPx(),
            center = centre,
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }

    // 5. finger contact bloom
    if (phase == Phase.Holding) {
        val bloom = (BLOOM_DP * ringScale).dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                0f to palette.hot.copy(alpha = 0.5f),
                0.62f to Color.Transparent,
                1f to Color.Transparent,
                center = centre,
                radius = bloom,
            ),
            radius = bloom,
            center = centre,
        )
    }
}

private const val INACTIVE = -1f
private const val TRAIL_STEPS = 4
