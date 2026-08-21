package com.simobr.donotblink.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlin.math.abs

/**
 * The app is one colour, so the colour is the thing worth unlocking.
 *
 * Five alternates, named after real CRT phosphors. Each is three constants and a boolean: no
 * content, no progression, no effect whatsoever on the game loop. [hot] and [dim] are DERIVED from
 * the base — five palettes of three hardcoded colours would be five chances to get it wrong.
 */
data class PhosphorPalette(
    val id: String,
    val label: String,
    val phosphor: Color,
    val hot: Color,
    val dim: Color,
    /** P7's slow decay: the ring leaves a trail this long behind it. 0 for every other phosphor. */
    val trailMs: Int = 0,
    val free: Boolean = false,
)

/** Hot is the base lightened by this much, in HSL lightness. */
const val HOT_LIGHTEN = 0.25f

/** Dim is the base's own hue held at this lightness. */
const val DIM_LUMINANCE = 0.18f

private fun derive(
    id: String,
    label: String,
    base: Color,
    trailMs: Int = 0,
): PhosphorPalette {
    val (hue, saturation, lightness) = base.toHsl()
    return PhosphorPalette(
        id = id,
        label = label,
        phosphor = base,
        hot = hslColor(hue, saturation, (lightness + HOT_LIGHTEN).coerceAtMost(1f)),
        dim = hslColor(hue, saturation, DIM_LUMINANCE),
        trailMs = trailMs,
    )
}

/**
 * P3 AMBER is the frozen palette itself, verbatim.
 *
 * The derivation reproduces the frozen Hot almost exactly (#FFD880 against the frozen #FFD97A) but
 * NOT the frozen Dim: this hue at 18% lightness is #5C3F00, where the frozen Dim is #3A2A00, which
 * sits at 11.4%. Rather than repaint every screen the default user sees, the default keeps its
 * frozen values and the formula governs the five alternates. See the stage report.
 */
val PHOSPHORS: List<PhosphorPalette> = listOf(
    PhosphorPalette(
        id = "p3_amber",
        label = "P3 AMBER",
        phosphor = DnbColor.Phosphor,
        hot = DnbColor.Hot,
        dim = DnbColor.Dim,
        free = true,
    ),
    derive("p1_green", "P1 GREEN", Color(0xFF33FF66)),
    derive("p4_white", "P4 WHITE", Color(0xFFFFFFFF)),
    derive("p11_blue", "P11 BLUE", Color(0xFF6699FF)),
    derive("red", "RED", Color(0xFFFF3B30)),
    derive("p7_ghost", "P7 GHOST", Color(0xFFB9FFCB), trailMs = 200),
)

val DEFAULT_PHOSPHOR: PhosphorPalette = PHOSPHORS.first()

fun phosphorById(id: String?): PhosphorPalette =
    PHOSPHORS.firstOrNull { it.id == id } ?: DEFAULT_PHOSPHOR

val LocalPalette = staticCompositionLocalOf { DEFAULT_PHOSPHOR }

/**
 * Re-points a frozen token style at the chosen phosphor, alpha preserved. The tokens themselves are
 * never edited — this maps Phosphor/Hot/Dim onto their equivalents in the active palette.
 */
fun TextStyle.tinted(palette: PhosphorPalette): TextStyle = when {
    palette === DEFAULT_PHOSPHOR -> this
    color sameRgbAs DnbColor.Phosphor -> copy(color = palette.phosphor.copy(alpha = color.alpha))
    color sameRgbAs DnbColor.Hot -> copy(color = palette.hot.copy(alpha = color.alpha))
    color sameRgbAs DnbColor.Dim -> copy(color = palette.dim.copy(alpha = color.alpha))
    else -> this
}

private infix fun Color.sameRgbAs(other: Color): Boolean =
    abs(red - other.red) < 0.002f && abs(green - other.green) < 0.002f && abs(blue - other.blue) < 0.002f

/** HSL, hand-rolled: android.graphics.ColorUtils is not available to a JVM unit test. */
internal fun Color.toHsl(): Triple<Float, Float, Float> {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val lightness = (max + min) / 2f

    if (delta == 0f) return Triple(0f, 0f, lightness)

    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue = when (max) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    return Triple((hue + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness)
}

internal fun hslColor(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = lightness - c / 2f
    val (r, g, b) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = 1f,
    )
}
