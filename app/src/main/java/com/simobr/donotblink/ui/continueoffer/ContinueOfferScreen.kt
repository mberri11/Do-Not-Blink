package com.simobr.donotblink.ui.continueoffer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simobr.donotblink.ui.common.noRippleClickable
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbDim
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.tinted
import kotlinx.coroutines.isActive

object ContinueTags {
    const val WATCH = "continue-watch"
    const val DECLINE = "continue-decline"
    const val STREAK = "continue-streak"
}

private const val BADGE_TOP_DP = 92f
private const val RING_CENTRE_Y_DP = 370f
private const val RING_RADIUS_DP = 118f
private const val STREAK_TOP_DP = 344f
private const val HEADLINE_TOP_DP = 594f
private const val WATCH_TOP_DP = 644f
private const val DECLINE_TOP_DP = 730f

/** The ring re-knits over this long, linear, no easing. */
private const val REKNIT_MS = 700f

/** The broken ring, straight off the mockup. */
private val REKNIT_DASHES = floatArrayOf(150f, 44f, 96f, 62f)

/** The hot seam: one short lit segment travelling the circumference. */
private val SEAM_DASHES = floatArrayOf(26f, 434f)

@Composable
fun ContinueOfferScreen(
    streak: Int,
    onWatch: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    BackHandler(onBack = onDecline)

    // No ad chrome, no badge, no countdown UI. The machine asks; the player answers.
    val reknit = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (isActive && reknit.floatValue < 1f) {
            withFrameNanos { now ->
                reknit.floatValue = (((now - startNanos) / 1_000_000f) / REKNIT_MS).coerceIn(0f, 1f)
            }
        }
    }

    Box(modifier.fillMaxSize().background(palette.dim.copy(alpha = 0f))) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, RING_CENTRE_Y_DP.dp.toPx())
            val radius = RING_RADIUS_DP.dp.toPx()
            val progress = reknit.floatValue

            // the dead ring the broken one sits on
            drawCircle(palette.dim, radius, centre, style = Stroke(1.4.dp.toPx()))

            // gaps close linearly; the lit dashes take up exactly what the gaps give back
            val closing = floatArrayOf(
                (REKNIT_DASHES[0] + REKNIT_DASHES[1] * progress).dp.toPx(),
                (REKNIT_DASHES[1] * (1f - progress)).dp.toPx().coerceAtLeast(0.01f),
                (REKNIT_DASHES[2] + REKNIT_DASHES[3] * progress).dp.toPx(),
                (REKNIT_DASHES[3] * (1f - progress)).dp.toPx().coerceAtLeast(0.01f),
            )
            listOf(22f to 0.05f, 6f to 0.16f, 2.4f to 0.9f).forEach { (width, alpha) ->
                drawCircle(
                    color = palette.phosphor.copy(alpha = alpha),
                    radius = radius,
                    center = centre,
                    style = Stroke(width.dp.toPx(), pathEffect = PathEffect.dashPathEffect(closing)),
                )
            }

            // the hot seam spark, tracking the closing point
            val circumference = 2f * Math.PI.toFloat() * radius
            drawCircle(
                color = palette.hot,
                radius = radius,
                center = centre,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        SEAM_DASHES.map { it.dp.toPx() }.toFloatArray(),
                        phase = -circumference * progress,
                    ),
                ),
            )
        }

        Text(
            text = "ONCE PER RUN",
            style = DnbType.microLabel.copy(
                fontSize = 8.5.sp,
                letterSpacing = 0.45.em,
                color = DnbColor.LabelMid,
            ),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = BADGE_TOP_DP.dp),
        )

        Text(
            text = streak.toString().padStart(2, '0'),
            style = DnbType.continueNumeral.tinted(palette),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = STREAK_TOP_DP.dp)
                .testTag(ContinueTags.STREAK),
        )

        Text(
            text = "STEADY YOUR EYE",
            style = DnbType.ctaLarge.copy(fontSize = 16.sp, color = palette.phosphor),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = HEADLINE_TOP_DP.dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = WATCH_TOP_DP.dp)
                .size(width = DnbDim.ctaWidth, height = DnbDim.ctaHeight)
                .testTag(ContinueTags.WATCH)
                .noRippleClickable(onClick = onWatch),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(color = palette.phosphor.copy(alpha = 0.55f), style = Stroke(1.dp.toPx()))
            }
            Text(
                text = "WATCH TO CONTINUE",
                style = DnbType.ctaSmall.tinted(palette),
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = "ACCEPT THE BLINK",
            // The decline is a real choice, not a footnote: readable at any panel brightness.
            style = DnbType.microLabel.copy(
                fontSize = 10.sp,
                letterSpacing = 0.3.em,
                color = DnbColor.LabelMid,
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = DECLINE_TOP_DP.dp)
                .testTag(ContinueTags.DECLINE)
                .noRippleClickable(onClick = onDecline),
        )
    }
}
