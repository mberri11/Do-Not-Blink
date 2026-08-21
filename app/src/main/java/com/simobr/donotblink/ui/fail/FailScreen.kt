package com.simobr.donotblink.ui.fail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simobr.donotblink.ads.AdaptiveAnchoredBanner
import com.simobr.donotblink.game.Title
import com.simobr.donotblink.ui.common.noRippleClickable
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbDim
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.PhosphorPalette
import com.simobr.donotblink.ui.theme.tinted
import kotlinx.coroutines.isActive

object FailTags {
    const val AGAIN = "fail-again"
    const val HOME = "fail-home"
    const val STREAK = "fail-streak"
    const val BANNER = "fail-banner"
}

/** Mockup styles with no frozen token: derived from the nearest token, never added to DnbType. */
private val StreakLabelStyle = DnbType.microLabel
private val BestLineStyle = DnbType.microLabel.copy(
    fontSize = 10.sp,
    letterSpacing = 0.34.em,
    color = DnbColor.Phosphor.copy(alpha = 0.42f),
)
private val HomeLinkStyle = DnbType.microLabel.copy(letterSpacing = 0.3.em)
private val UnlockedThresholdStyle = DnbType.microLabel.copy(fontSize = 10.sp, letterSpacing = 0.16.em)

private const val BLINKED_TOP_DP = 104f
private const val RING_CENTRE_Y_DP = 360f
private const val STREAK_LABEL_TOP_DP = 300f
private const val STREAK_NUMERAL_TOP_DP = 322f
private const val BEST_LINE_TOP_DP = 444f
private const val UNLOCKED_TOP_DP = 520f
private const val AGAIN_TOP_DP = 604f
private const val HOME_TOP_DP = 690f
private const val BANNER_HEIGHT_DP = 60f
private const val REVEAL_FADE_MS = 400f
private const val REVEAL_MS_PER_CHAR = 24f

/** The dashes of the broken ring, straight off the mockup. */
private val BROKEN_RING_DASHES = floatArrayOf(34f, 26f, 12f, 40f, 58f, 18f)

@Composable
fun FailScreen(
    streak: Int,
    best: Int,
    newlyUnlocked: List<Title>,
    onAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    bannerEnabled: Boolean = false,
) {
    val palette = LocalPalette.current
    BackHandler(onBack = onHome)

    Box(modifier.fillMaxSize().background(DnbColor.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(size.width / 2f, RING_CENTRE_Y_DP.dp.toPx())
            // the dead outer track
            drawCircle(
                color = DnbColor.Rule,
                radius = 168.dp.toPx(),
                center = centre,
                style = Stroke(width = 1.dp.toPx()),
            )
            // the ring, broken where it died
            drawCircle(
                color = palette.dim,
                radius = 96.dp.toPx(),
                center = centre,
                style = Stroke(
                    width = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        BROKEN_RING_DASHES.map { it.dp.toPx() }.toFloatArray(),
                    ),
                ),
            )
        }

        Text(
            text = "blinked.",
            style = DnbType.failLine.tinted(palette),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = BLINKED_TOP_DP.dp),
        )

        Text(
            text = "STREAK",
            style = StreakLabelStyle.tinted(palette),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = STREAK_LABEL_TOP_DP.dp),
        )
        Text(
            text = streak.toString().padStart(2, '0'),
            style = DnbType.failNumeral.tinted(palette),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = STREAK_NUMERAL_TOP_DP.dp)
                .testTag(FailTags.STREAK),
        )
        Text(
            text = "BEST $best",
            style = BestLineStyle.tinted(palette),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = BEST_LINE_TOP_DP.dp),
        )

        if (newlyUnlocked.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = UNLOCKED_TOP_DP.dp, start = DnbDim.screenPadH, end = DnbDim.screenPadH),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                newlyUnlocked.forEach { title -> UnlockedTitleRow(title, palette) }
            }
        }

        AgainButton(
            palette = palette,
            onClick = onAgain,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = AGAIN_TOP_DP.dp),
        )

        Text(
            text = "HOME",
            style = HomeLinkStyle.tinted(palette),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = HOME_TOP_DP.dp)
                .testTag(FailTags.HOME)
                .noRippleClickable(onClick = onHome),
        )

        // The app's ONLY banner surface. It is created when this screen enters and destroyed the
        // moment the player leaves it, and it never appears during a run.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(BANNER_HEIGHT_DP.dp)
                .testTag(FailTags.BANNER),
            contentAlignment = Alignment.Center,
        ) {
            AdaptiveAnchoredBanner(enabled = bannerEnabled)
        }
    }
}

@Composable
private fun AgainButton(palette: PhosphorPalette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = DnbDim.ctaWidth, height = DnbDim.ctaHeight)
            .testTag(FailTags.AGAIN)
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    0f to palette.phosphor.copy(alpha = 0.12f),
                    1f to Color.Transparent,
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.width / 2f,
                ),
            )
            drawRect(
                color = palette.phosphor.copy(alpha = 0.55f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        Text(text = "AGAIN", style = DnbType.ctaLarge.tinted(palette), textAlign = TextAlign.Center)
    }
}

/**
 * A title earned during the run arrives here and nowhere else: 400ms from Dim to lit, with the name
 * typing on at one character per 24ms. No dialog, no confetti, no sound.
 */
@Composable
private fun UnlockedTitleRow(title: Title, palette: PhosphorPalette) {
    var elapsedMs by remember(title) { mutableIntStateOf(0) }

    LaunchedEffect(title) {
        val startNanos = withFrameNanos { it }
        val until = REVEAL_FADE_MS.coerceAtLeast(title.name.length * REVEAL_MS_PER_CHAR)
        while (isActive && elapsedMs < until) {
            withFrameNanos { now -> elapsedMs = ((now - startNanos) / 1_000_000L).toInt() }
        }
        elapsedMs = until.toInt()
    }

    val fade = (elapsedMs / REVEAL_FADE_MS).coerceIn(0f, 1f)
    val typed = (elapsedMs / REVEAL_MS_PER_CHAR).toInt().coerceIn(0, title.name.length)

    Box(Modifier.fillMaxWidth().height(DnbDim.titleRowHeight)) {
        Text(
            text = title.name.take(typed),
            style = DnbType.titleRowName.copy(color = lerp(palette.dim, palette.phosphor, fade)),
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = title.threshold.toString().padStart(3, '0'),
            style = UnlockedThresholdStyle.copy(color = lerp(palette.dim, palette.hot, fade)),
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}
