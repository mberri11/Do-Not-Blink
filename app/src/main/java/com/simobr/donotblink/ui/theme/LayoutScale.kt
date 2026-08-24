package com.simobr.donotblink.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ONE scale factor for the whole app.
 *
 * Every screen in this game positions its content with absolute dp measured off a 390 x 844
 * mockup. That is fine on the device the mockup was drawn for and nowhere else: on a 360 x 640dp
 * budget phone the continue offer's WATCH and DECLINE both fell off the bottom edge, so the player
 * was offered a continue they could neither accept nor decline. At the other end, target API 36
 * ignores orientation and resizability restrictions on `smallestScreenWidthDp >= 600`, so landscape
 * and tablet windows WILL happen.
 *
 * The answer is not per-size layouts and it is not a window size class — a bucket cannot tell a
 * 640dp phone from an 844dp one, and those need different numbers. It is one continuous factor,
 * derived from the height the layouts are actually constrained by, multiplied into every mockup
 * offset at its use site.
 *
 * Everything below the composition locals is pure Kotlin, so the arithmetic is a unit test's
 * problem rather than a device's.
 */

/** The mockup every absolute offset in this app was measured off. */
const val MOCKUP_HEIGHT_DP = 844f

/** Below this the type stops being readable; above it the composition just floats in black. */
const val SCALE_MIN = 0.68f
const val SCALE_MAX = 1.15f

/** Type never shrinks below this fraction of its frozen token size, whatever the scale says. */
const val TYPE_SCALE_FLOOR = 0.6f

/** A window at least this wide is a tablet or a landscape phone, not a portrait phone. */
val WIDE_WINDOW_THRESHOLD = 600.dp

/** The game is centred in a column of at most this width. It is not stretched across a tablet. */
val WIDE_CONTENT_MAX_WIDTH = 420.dp

/** The ring composition's half-extent at scale 1: the tick marks reach 180dp, plus a hair. */
const val RING_HALF_EXTENT_DP = 186f

/** The outer track's radius at scale 1. */
const val RING_OUTER_RADIUS_DP = 168f

/** However tall the window, the ring stops growing here. */
const val RING_MAX_OUTER_RADIUS_DP = 210f

/** The ring may be squeezed harder than the rest of the layout, but not into a dot. */
const val RING_SCALE_MIN = 0.5f

/**
 * The factor every absolute mockup offset is multiplied by, from the SAFE height available —
 * window height minus the top and bottom insets, because that is the space a layout actually gets.
 */
fun layoutScaleFor(availableHeightDp: Float): Float =
    (availableHeightDp / MOCKUP_HEIGHT_DP).coerceIn(SCALE_MIN, SCALE_MAX)

/**
 * The ring is a circle, so it is bounded by the SMALLER axis and never by height alone: a tall
 * narrow window would otherwise scale a 336dp-wide ring up until it ran off both sides.
 */
fun ringScaleFor(availableWidthDp: Float, availableHeightDp: Float): Float {
    val byHeight = layoutScaleFor(availableHeightDp)
    val byWidth = (availableWidthDp / 2f) / RING_HALF_EXTENT_DP
    val cap = RING_MAX_OUTER_RADIUS_DP / RING_OUTER_RADIUS_DP
    return minOf(byHeight, byWidth, cap).coerceAtLeast(RING_SCALE_MIN)
}

/** The factor in force for everything drawn inside the nearest [ScaledLayout]. */
val LocalLayoutScale = staticCompositionLocalOf { 1f }

/** The ring's own factor. Equal to [LocalLayoutScale] on a portrait phone, smaller when squeezed. */
val LocalRingScale = staticCompositionLocalOf { 1f }

/** A mockup offset, scaled. One call, so the multiplication is never scattered by hand. */
@Composable
fun Float.scaled(): Dp = (this * LocalLayoutScale.current).dp

/** A frozen [DnbDim] value, scaled. */
@Composable
fun Dp.scaled(): Dp = value.scaled()

/**
 * A numeral scaled with the layout. The frozen token stays frozen — this re-points the rendered
 * size only, and never below [TYPE_SCALE_FLOOR] of it, so a small window shrinks the streak
 * digits instead of clipping them.
 */
@Composable
fun TextStyle.scaledType(floor: Float = TYPE_SCALE_FLOOR): TextStyle {
    val factor = LocalLayoutScale.current.coerceAtLeast(floor)
    return if (factor == 1f) this else copy(fontSize = fontSize * factor)
}

/**
 * Measures the window once, provides both factors, and caps the composition's width on a wide
 * one. Every screen is composed inside exactly one of these.
 */
@Composable
fun ScaledLayout(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val direction = LocalLayoutDirection.current
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val safeHeightDp =
            (maxHeight - insets.calculateTopPadding() - insets.calculateBottomPadding()).value
        val safeWidthDp = (
            maxWidth - insets.calculateStartPadding(direction) - insets.calculateEndPadding(direction)
            ).value

        CompositionLocalProvider(
            LocalLayoutScale provides layoutScaleFor(safeHeightDp),
            LocalRingScale provides ringScaleFor(safeWidthDp, safeHeightDp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .then(
                        if (maxWidth >= WIDE_WINDOW_THRESHOLD) {
                            Modifier.width(WIDE_CONTENT_MAX_WIDTH)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    ),
                content = content,
            )
        }
    }
}
