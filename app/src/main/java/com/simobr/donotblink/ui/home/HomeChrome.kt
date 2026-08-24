package com.simobr.donotblink.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.simobr.donotblink.ui.common.noRippleClickable
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbType
import com.simobr.donotblink.ui.theme.LocalLayoutScale
import com.simobr.donotblink.ui.theme.LocalPalette
import com.simobr.donotblink.ui.theme.LocalRingScale
import com.simobr.donotblink.ui.theme.PhosphorPalette
import com.simobr.donotblink.ui.theme.scaled
import com.simobr.donotblink.ui.theme.scaledType
import com.simobr.donotblink.ui.theme.tinted
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.isActive

object HomeTags {
    const val TITLES = "home-titles"
    const val SETTINGS = "home-settings"
    const val BEST = "home-best"
    const val HOLD = "home-hold"
    const val RULE = "home-rule"
}

/**
 * Mockup styles with no frozen token: derived from the nearest token, never added to DnbType.
 *
 * BEST and TITLES carry information and TITLES is tappable, so both sit on [DnbColor.LabelMid]
 * rather than Dim — Dim is unreadable at low panel brightness and is now decorative only.
 */
internal val BestLabelStyle = DnbType.microLabel.copy(letterSpacing = 0.5.em, color = DnbColor.LabelMid)
private val HoldToBeginStyle = DnbType.ctaSmall.copy(letterSpacing = 0.36.em, color = DnbColor.Phosphor)
internal val TitlesLinkStyle = DnbType.microLabel.copy(letterSpacing = 0.28.em, color = DnbColor.LabelMid)

/** The rule, stated. 10sp / W400 / 0.28em on LabelMid — the same line the ring draws under itself. */
internal val RuleLineStyle = DnbType.microLabel.copy(
    fontSize = 10.sp,
    fontWeight = FontWeight.W400,
    letterSpacing = 0.28.em,
    color = DnbColor.LabelMid,
)

private const val BEST_LABEL_TOP_DP = 250f
private const val BEST_NUMERAL_TOP_DP = 280f
private const val GLOW_CENTRE_Y_DP = 355f
private const val GLOW_RADIUS_DP = 195f
private const val HOLD_TOP_DP = 566f

/** HOLD's own box is ~18dp tall at 12.5sp; the rule line sits 14dp under it. */
private const val RULE_TOP_DP = 598f
private const val BREATHE_PERIOD_MS = 2400L
private const val GEAR_SIZE_DP = 18f
private const val GEAR_STROKE_DP = 1.4f

/**
 * Home is the idle face of the game surface, not a separate surface: the press that leaves this
 * screen is the same pointer-down that starts the round, so the ring is contracting on frame one.
 */
@Composable
fun HomeChrome(
    best: Int,
    onOpenTitles: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The breathe. An infinite frame clock rather than a plain frame loop, so a Compose test can
    // still reach idle. The value is read inside graphicsLayer: draw phase only.
    val palette = LocalPalette.current
    val breathe = remember { mutableFloatStateOf(1f) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withInfiniteAnimationFrameNanos { frameTimeNanos ->
                val phase = (frameTimeNanos / 1_000_000L % BREATHE_PERIOD_MS).toFloat() /
                    BREATHE_PERIOD_MS * (2f * PI.toFloat())
                breathe.floatValue = 0.8f + 0.2f * sin(phase)
            }
        }
    }

    val scale = LocalLayoutScale.current
    val ringScale = LocalRingScale.current

    // No insets here: PlayScreen already consumed safeDrawing for the whole chrome layer, and
    // consuming an inset twice double-pads it.
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = (GLOW_RADIUS_DP * ringScale).dp.toPx()
            val centre = Offset(size.width / 2f, (GLOW_CENTRE_Y_DP * scale).dp.toPx())
            drawCircle(
                brush = Brush.radialGradient(
                    0f to palette.phosphor.copy(alpha = 0.13f),
                    1f to Color.Transparent,
                    center = centre,
                    radius = radius,
                ),
                radius = radius,
                center = centre,
            )
        }

        Text(
            text = "BEST",
            style = BestLabelStyle.tinted(palette),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = BEST_LABEL_TOP_DP.scaled()),
        )
        Text(
            text = best.toString(),
            style = DnbType.bestNumeral.tinted(palette).scaledType(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = BEST_NUMERAL_TOP_DP.scaled())
                .testTag(HomeTags.BEST),
        )

        Text(
            text = "HOLD TO BEGIN",
            style = HoldToBeginStyle.tinted(palette),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = HOLD_TOP_DP.scaled())
                .testTag(HomeTags.HOLD)
                .graphicsLayer { alpha = breathe.floatValue },
        )

        // The rule. It does not breathe and it never leaves: this is the one place the game says
        // what it wants the player to do.
        Text(
            text = "RELEASE ON THE RING",
            style = RuleLineStyle,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = RULE_TOP_DP.scaled())
                .testTag(HomeTags.RULE),
        )

        Text(
            text = "TITLES",
            style = TitlesLinkStyle.tinted(palette),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp)
                .testTag(HomeTags.TITLES)
                .noRippleClickable(onClick = onOpenTitles),
        )

        SettingsGlyph(
            palette = palette,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 18.dp)
                .testTag(HomeTags.SETTINGS)
                .noRippleClickable(onClick = onOpenSettings),
        )
    }
}

/**
 * The settings glyph, drawn by hand — the mockup's mark is the game's own ring-and-ticks motif, not
 * a cog. Pulling material-icons-extended in for one glyph would be absurd.
 */
@Composable
private fun SettingsGlyph(palette: PhosphorPalette, modifier: Modifier = Modifier) {
    Canvas(modifier.size(GEAR_SIZE_DP.dp)) {
        val stroke = GEAR_STROKE_DP.dp.toPx()
        val centre = Offset(size.width / 2f, size.height / 2f)
        val unit = size.minDimension / 24f
        drawCircle(palette.dim, radius = 3.6f * unit, center = centre, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawCircle(palette.dim, radius = 8.4f * unit, center = centre, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        val inner = 3.6f * unit
        val outer = 1.2f * unit
        drawLine(palette.dim, Offset(centre.x, outer), Offset(centre.x, inner), stroke)
        drawLine(palette.dim, Offset(centre.x, size.height - inner), Offset(centre.x, size.height - outer), stroke)
        drawLine(palette.dim, Offset(outer, centre.y), Offset(inner, centre.y), stroke)
        drawLine(palette.dim, Offset(size.width - inner, centre.y), Offset(size.width - outer, centre.y), stroke)
    }
}
