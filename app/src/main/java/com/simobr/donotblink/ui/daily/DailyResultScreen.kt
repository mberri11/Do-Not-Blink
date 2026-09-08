package com.simobr.donotblink.ui.daily

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simobr.donotblink.game.DailyResult
import com.simobr.donotblink.game.DailyTrial
import com.simobr.donotblink.ui.common.noRippleClickable
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbDim
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalLayoutScale
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.PhosphorPalette
import com.simobr.donotblink.ui.theme.scaled
import com.simobr.donotblink.ui.theme.scaledType
import com.simobr.donotblink.ui.theme.tinted
import java.time.LocalDate
import kotlinx.coroutines.delay

object DailyTags {
    const val GRID = "daily-grid"
    const val SCORE = "daily-score"
    const val SHARE = "daily-share"
    const val HOME = "daily-home"
    const val COUNTDOWN = "daily-countdown"
}

internal val DailyLabelStyle = DnbType.microLabel.copy(letterSpacing = 0.4.em, color = DnbColor.LabelMid)
internal val DailyMetaStyle = DnbType.microLabel.copy(
    fontSize = 10.sp,
    letterSpacing = 0.3.em,
    color = DnbColor.LabelMid,
)

private const val HEADER_TOP_DP = 96f
private const val DATE_TOP_DP = 126f
private const val GRID_TOP_DP = 186f
private const val GRID_HEIGHT_DP = 172f
private const val SCORE_TOP_DP = 376f
// The 92sp score numeral's box measured 311px tall on a 1080x2400 device, which put its bottom
// 10dp BELOW where the mean-error line started. Line-height padding meant the glyphs themselves
// probably cleared, but "probably" is not a layout: both lines moved down for real clearance.
private const val ERROR_TOP_DP = 498f
private const val STREAK_TOP_DP = 526f

/** Ring geometry inside the grid canvas, before scaling. */
private const val MARK_RADIUS_DP = 22f
private const val MARK_SPACING_DP = 62f
private const val MARK_ROW_GAP_DP = 86f

private const val SHARE_TO_HOME_GAP_DP = 24f
private const val HOME_TOUCH_TARGET_DP = 48f
private val MISS_DASHES = floatArrayOf(9f, 7f)

/**
 * The end of a trial, and the face of a trial already spent today.
 *
 * There is one attempt a day, so this screen is also what a player who comes back later sees: the
 * result they got, and how long until the next transmission. That is the whole retention mechanic
 * and it is why the countdown is here rather than a "PLAY AGAIN" the game would have to refuse.
 */
@Composable
fun DailyResultScreen(
    result: DailyResult,
    dayStreak: Int,
    bestHits: Int,
    onShare: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    /** Seconds until the next local midnight. Injected so a test is not at the mercy of the clock. */
    secondsUntilNextTrial: () -> Long = ::secondsUntilLocalMidnight,
) {
    val palette = LocalPalette.current
    BackHandler(onBack = onHome)

    Box(
        modifier
            .fillMaxSize()
            .background(DnbColor.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Text(
            text = "TODAY'S TRIAL",
            style = DailyLabelStyle.tinted(palette),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = HEADER_TOP_DP.scaled()),
        )
        Text(
            text = LocalDate.ofEpochDay(result.epochDay).toString(),
            style = DailyMetaStyle,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = DATE_TOP_DP.scaled()),
        )

        MarkGrid(
            marks = result.marks,
            palette = palette,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = GRID_TOP_DP.scaled())
                .fillMaxWidth()
                .height(GRID_HEIGHT_DP.scaled())
                .testTag(DailyTags.GRID),
        )

        Text(
            text = "${result.hits}/${DailyTrial.RINGS}",
            style = DnbType.failNumeral.tinted(palette).scaledType(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = SCORE_TOP_DP.scaled())
                .testTag(DailyTags.SCORE),
        )
        Text(
            text = "MEAN ERROR ${result.meanAbsErrorMs}MS",
            style = DailyMetaStyle,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = ERROR_TOP_DP.scaled()),
        )
        Text(
            text = "DAY STREAK $dayStreak · BEST $bestHits/${DailyTrial.RINGS}",
            style = DailyMetaStyle,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = STREAK_TOP_DP.scaled()),
        )

        // One bottom-anchored stack, for the same reason the fail screen has one: absolute Y
        // coordinates from a mockup do not survive a real range of screen heights.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShareButton(palette = palette, onClick = onShare)

            Spacer(Modifier.height(SHARE_TO_HOME_GAP_DP.dp))

            Box(
                modifier = Modifier
                    .height(HOME_TOUCH_TARGET_DP.dp)
                    .testTag(DailyTags.HOME)
                    .noRippleClickable(onClick = onHome),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "HOME",
                    style = DnbType.microLabel.copy(letterSpacing = 0.3.em, color = DnbColor.LabelMid),
                    modifier = Modifier.padding(horizontal = DnbDim.screenPadH),
                )
            }

            Countdown(secondsUntilNextTrial = secondsUntilNextTrial)
        }
    }
}

/**
 * Ticks once a second, which is all a countdown to midnight needs. It re-reads the supplied clock
 * every tick rather than decrementing a remembered value, so it stays honest across a process that
 * was backgrounded for an hour.
 */
@Composable
private fun Countdown(secondsUntilNextTrial: () -> Long) {
    var remaining by remember { mutableLongStateOf(secondsUntilNextTrial()) }
    LaunchedEffect(Unit) {
        while (true) {
            remaining = secondsUntilNextTrial()
            delay(1_000L)
        }
    }

    Text(
        text = "NEXT TRIAL IN ${formatCountdown(remaining)}",
        style = DailyMetaStyle,
        modifier = Modifier.padding(top = 4.dp).testTag(DailyTags.COUNTDOWN),
    )
}

@Composable
private fun MarkGrid(marks: List<Boolean>, palette: PhosphorPalette, modifier: Modifier = Modifier) {
    val scale = LocalLayoutScale.current
    Canvas(modifier) {
        val perRow = DailyTrial.RINGS / 2
        val radius = (MARK_RADIUS_DP * scale).dp.toPx()
        val spacing = (MARK_SPACING_DP * scale).dp.toPx()
        val rowGap = (MARK_ROW_GAP_DP * scale).dp.toPx()
        val firstX = size.width / 2f - spacing * (perRow - 1) / 2f
        val firstY = radius + 4.dp.toPx()

        marks.forEachIndexed { index, hit ->
            val centre = Offset(
                firstX + spacing * (index % perRow),
                firstY + rowGap * (index / perRow),
            )
            if (hit) {
                drawCircle(palette.phosphor.copy(alpha = 0.10f), radius, centre, style = Stroke(13.dp.toPx()))
                drawCircle(palette.phosphor.copy(alpha = 0.26f), radius, centre, style = Stroke(6.dp.toPx()))
                drawCircle(palette.phosphor, radius, centre, style = Stroke(2.2.dp.toPx()))
            } else {
                drawCircle(
                    color = palette.dim,
                    radius = radius,
                    center = centre,
                    style = Stroke(
                        width = 1.6.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            MISS_DASHES.map { it.dp.toPx() }.toFloatArray(),
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun ShareButton(palette: PhosphorPalette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = DnbDim.ctaWidth, height = DnbDim.ctaHeight)
            .testTag(DailyTags.SHARE)
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
            drawRect(color = palette.phosphor.copy(alpha = 0.55f), style = Stroke(width = 1.dp.toPx()))
        }
        Text(text = "SHARE", style = DnbType.ctaLarge.tinted(palette), textAlign = TextAlign.Center)
    }
}

/** `HH:MM:SS`, clamped at zero — a negative countdown is a clock that moved, not a thing to show. */
internal fun formatCountdown(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

/** Seconds from now to the next local midnight, which is when the next trial exists. */
internal fun secondsUntilLocalMidnight(): Long {
    val now = java.time.LocalDateTime.now()
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
    return java.time.Duration.between(now, midnight).seconds
}
