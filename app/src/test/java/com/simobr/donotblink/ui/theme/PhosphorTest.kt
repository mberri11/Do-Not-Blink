package com.simobr.donotblink.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Five palettes of three hardcoded colours would be five chances to get it wrong. */
class PhosphorTest {

    private fun hex(color: Color): String =
        "#%06X".format((color.value shr 32).toLong() and 0xFFFFFF)

    @Test
    fun there_are_six_phosphors_and_only_the_default_is_free() {
        assertEquals(6, PHOSPHORS.size)
        assertEquals(listOf("p3_amber"), PHOSPHORS.filter { it.free }.map { it.id })
        assertEquals(
            listOf("P3 AMBER", "P1 GREEN", "P4 WHITE", "P11 BLUE", "RED", "P7 GHOST"),
            PHOSPHORS.map { it.label },
        )
    }

    @Test
    fun the_bases_are_the_hexes_they_were_specified_as() {
        assertEquals(
            listOf("#FFB000", "#33FF66", "#FFFFFF", "#6699FF", "#FF3B30", "#B9FFCB"),
            PHOSPHORS.map { hex(it.phosphor) },
        )
    }

    @Test
    fun the_default_is_the_frozen_palette_untouched() {
        val amber = PHOSPHORS.first()
        assertEquals(DnbColor.Phosphor, amber.phosphor)
        assertEquals(DnbColor.Hot, amber.hot)
        assertEquals(DnbColor.Dim, amber.dim)
    }

    @Test
    fun hot_is_the_base_lightened_and_dim_is_the_base_darkened() {
        PHOSPHORS.drop(1).forEach { palette ->
            val baseLightness = palette.phosphor.toHsl().third
            val hotLightness = palette.hot.toHsl().third
            val dimLightness = palette.dim.toHsl().third
            assertEquals("${palette.label} hot", (baseLightness + HOT_LIGHTEN).coerceAtMost(1f), hotLightness, 0.01f)
            assertEquals("${palette.label} dim", DIM_LUMINANCE, dimLightness, 0.01f)
        }
    }

    @Test
    fun the_derivation_holds_the_hue_it_was_given() {
        PHOSPHORS.drop(1).forEach { palette ->
            val (baseHue, saturation, _) = palette.phosphor.toHsl()
            if (saturation < 0.01f) return@forEach // white has no hue to hold

            assertTrue("${palette.label} dim hue", kotlin.math.abs(palette.dim.toHsl().first - baseHue) < 1.5f)

            // Lightening by 25 points of lightness can clip to white, and white has no hue left to
            // hold. P7 GHOST starts at 86% lightness and does exactly that: its flash is white.
            if (palette.hot.toHsl().third < 0.999f) {
                assertTrue("${palette.label} hot hue", kotlin.math.abs(palette.hot.toHsl().first - baseHue) < 1.5f)
            } else {
                assertEquals("${palette.label} hot", Color.White, palette.hot)
            }
        }
    }

    @Test
    fun only_the_two_palest_phosphors_lighten_all_the_way_to_white() {
        assertEquals(
            listOf("P4 WHITE", "P7 GHOST"),
            PHOSPHORS.filter { it.hot == Color.White }.map { it.label },
        )
    }

    @Test
    fun only_p7_leaves_a_trail() {
        assertEquals(
            mapOf("p7_ghost" to 200),
            PHOSPHORS.filter { it.trailMs > 0 }.associate { it.id to it.trailMs },
        )
    }
}
