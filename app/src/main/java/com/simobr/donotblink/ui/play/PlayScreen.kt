package com.simobr.donotblink.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simobr.donotblink.game.Difficulty
import com.simobr.donotblink.game.GameEvent
import com.simobr.donotblink.game.GameViewModel
import com.simobr.donotblink.game.Phase
import com.simobr.donotblink.game.RoundPlan
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbDim
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.PhosphorPalette
import com.simobr.donotblink.ui.theme.tinted
import com.simobr.donotblink.ui.home.HomeChrome
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

object PlayScreenTags {
    const val SURFACE = "play-surface"
    const val STREAK = "play-streak"
}

/** Outer dim track, and the tick marks that sit just outside it. Read off the mockup. */
private const val OUTER_TRACK_DP = 168f
private const val TICK_INNER_DP = 172f
private const val TICK_OUTER_DP = 180f
private const val BLOOM_DP = 64f
private const val SHOCK_TRAVEL_DP = 32f
private const val STREAK_TOP_DP = 88f
private const val STREAK_GAP_DP = 16f

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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalPalette.current

    val latestRoundStarted = rememberUpdatedState(onRoundStarted)
    val latestFailed = rememberUpdatedState(onFailed)
    val reportDown = remember { { latestRoundStarted.value.invoke() } }

    LaunchedEffect(state.phase, state.roundId) {
        if (state.phase == Phase.Failed || state.phase == Phase.ContinueOffer) {
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
    val animating = state.phase == Phase.Holding || state.phase == Phase.Perfect

    LaunchedEffect(state.roundId, state.phase) {
        do {
            withFrameNanos { frameTimeNanos ->
                // Choreographer frame time and MotionEvent uptimeMillis share one timebase, so the
                // rendered radius tracks the judged one. Rendering may lag; judging never does.
                val now = frameTimeNanos / 1_000_000L
                viewModel.onFrame(now)

                val current = viewModel.state.value
                ringRadiusDp.floatValue = viewModel.ringRadiusDpAt(now)

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
            when (event) {
                is GameEvent.Perfect -> feedback.perfect(current.hapticsEnabled, current.soundEnabled)
                is GameEvent.Fail -> feedback.fail(current.hapticsEnabled, current.soundEnabled)
            }
        }
    }

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
                )
            }
        }

        if (homeChrome) {
            HomeChrome(
                best = state.best,
                onOpenTitles = onOpenTitles,
                onOpenSettings = onOpenSettings,
            )
        } else {
            StreakBlock(
                streak = state.streak,
                phase = state.phase,
                palette = palette,
                digitScale = digitScale,
                modifier = Modifier.align(Alignment.TopCenter),
            )
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
    streak: Int,
    phase: Phase,
    palette: PhosphorPalette,
    digitScale: FloatState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = STREAK_TOP_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(STREAK_GAP_DP.dp),
    ) {
        Text(text = "STREAK", style = DnbType.microLabel.tinted(palette))
        Text(
            text = streak.toString().padStart(2, '0'),
            style = (if (phase == Phase.Perfect) DnbType.streakPerfect else DnbType.streakLive)
                .tinted(palette),
            modifier = Modifier
                .testTag(PlayScreenTags.STREAK)
                .graphicsLayer {
                    val scale = digitScale.floatValue
                    scaleX = scale
                    scaleY = scale
                },
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
) {
    val centre = Offset(size.width / 2f, DnbDim.ringCentreYFromTop.toPx())

    // 1. dim outer track
    drawCircle(
        color = palette.dim,
        radius = OUTER_TRACK_DP.dp.toPx(),
        center = centre,
        style = Stroke(width = 1.dp.toPx()),
    )

    // 2. tick marks at N/E/S/W
    val tickInner = TICK_INNER_DP.dp.toPx()
    val tickOuter = TICK_OUTER_DP.dp.toPx()
    val tickWidth = 1.6.dp.toPx()
    drawLine(palette.dim, Offset(centre.x, centre.y - tickOuter), Offset(centre.x, centre.y - tickInner), tickWidth)
    drawLine(palette.dim, Offset(centre.x, centre.y + tickInner), Offset(centre.x, centre.y + tickOuter), tickWidth)
    drawLine(palette.dim, Offset(centre.x - tickOuter, centre.y), Offset(centre.x - tickInner, centre.y), tickWidth)
    drawLine(palette.dim, Offset(centre.x + tickInner, centre.y), Offset(centre.x + tickOuter, centre.y), tickWidth)

    // 3. target line
    drawCircle(
        color = palette.phosphor.copy(alpha = 0.7f),
        radius = plan.targetRadiusDp.dp.toPx(),
        center = centre,
        style = Stroke(width = 1.2.dp.toPx()),
    )

    // 4. glow stack, then the core. No blur, no RenderEffect, no shadow — concentric strokes.
    if (!ringHidden) {
        val live = ringRadiusDp.dp.toPx()
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
                val ghost = (ringRadiusDp + plan.speedDpPerSec * (ageMs / 1000f)).dp.toPx()
                val ghostAlpha = 0.32f * (1f - (step + 1f) / (TRAIL_STEPS + 1f))
                drawCircle(
                    color = ringColour.copy(alpha = ghostAlpha),
                    radius = ghost,
                    center = centre,
                    style = Stroke(plan.strokeDp.dp.toPx()),
                )
            }
        }

        drawCircle(ringColour.copy(alpha = 0.05f * halo), live, centre, style = Stroke(22.dp.toPx()))
        drawCircle(ringColour.copy(alpha = 0.10f * halo), live, centre, style = Stroke(13.dp.toPx()))
        drawCircle(ringColour.copy(alpha = 0.26f * halo), live, centre, style = Stroke(6.dp.toPx()))
        drawCircle(ringColour, live, centre, style = Stroke(plan.strokeDp.dp.toPx()))

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
            radius = (plan.targetRadiusDp + SHOCK_TRAVEL_DP * shock).dp.toPx(),
            center = centre,
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }

    // 5. finger contact bloom
    if (phase == Phase.Holding) {
        val bloom = BLOOM_DP.dp.toPx()
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
