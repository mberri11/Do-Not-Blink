package com.simobr.donotblink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbDim
import com.simobr.donotblink.ui.theme.DnbFont
import com.simobr.donotblink.ui.theme.DnbType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tokens are frozen. Every literal below is duplicated from the brief on purpose: if a token
 * is edited, rounded, or "balanced", this test fails and names the token.
 */
class TokensResolutionTest {

    // ---- colours ----------------------------------------------------------------------------

    @Test
    fun colours_resolve_to_their_frozen_argb_longs() {
        assertEquals(0xFF000000L, argb(DnbColor.Black))
        assertEquals(0xFFFFB000L, argb(DnbColor.Phosphor))
        assertEquals(0xFFFFD97AL, argb(DnbColor.Hot))
        assertEquals(0xFF3A2A00L, argb(DnbColor.Dim))
        assertEquals(0xFFFFFFFFL, argb(DnbColor.Glint))
        assertEquals(0xFF2A1F00L, argb(DnbColor.Hairline))
        assertEquals(0xFF241A00L, argb(DnbColor.Ink))
        assertEquals(0xFF1A1300L, argb(DnbColor.Rule))
        assertEquals(0xFF8A6A1AL, argb(DnbColor.LabelMid))
    }

    // ---- dimensions -------------------------------------------------------------------------

    @Test
    fun dimensions_resolve_to_their_frozen_dp_values() {
        assertEquals(168f, DnbDim.ringStartRadius.value, 0f)
        assertEquals(96f, DnbDim.ringTargetRadius.value, 0f)
        assertEquals(430f, DnbDim.ringCentreYFromTop.value, 0f)
        assertEquals(300f, DnbDim.ctaWidth.value, 0f)
        assertEquals(58f, DnbDim.ctaHeight.value, 0f)
        assertEquals(45f, DnbDim.ctaInsetX.value, 0f)
        assertEquals(26f, DnbDim.screenPadH.value, 0f)
        assertEquals(62f, DnbDim.settingsRowHeight.value, 0f)
        assertEquals(30f, DnbDim.titleRowHeight.value, 0f)
        assertEquals(108f, DnbDim.homeIndicatorW.value, 0f)
        assertEquals(3f, DnbDim.homeIndicatorH.value, 0f)
    }

    // ---- type: size / weight / tracking / colour ---------------------------------------------

    @Test
    fun wordmark_resolves() =
        assertStyle(DnbType.wordmark, 19f, FontWeight.W500, 0.34f, DnbColor.Phosphor)

    @Test
    fun bestNumeral_resolves() =
        assertStyle(DnbType.bestNumeral, 132f, FontWeight.W500, -0.01f, DnbColor.Glint)

    @Test
    fun failNumeral_resolves() =
        assertStyle(DnbType.failNumeral, 92f, FontWeight.W500, 0f, DnbColor.Glint)

    @Test
    fun continueNumeral_resolves() =
        assertStyle(DnbType.continueNumeral, 46f, FontWeight.W500, 0f, DnbColor.Glint)

    @Test
    fun roundNumeral_resolves() =
        assertStyle(DnbType.roundNumeral, 96f, FontWeight.W500, 0f, DnbColor.Phosphor)

    @Test
    fun streakLive_resolves() =
        assertStyle(DnbType.streakLive, 26f, FontWeight.W500, 0.10f, DnbColor.Phosphor)

    @Test
    fun streakPerfect_resolves() =
        assertStyle(DnbType.streakPerfect, 32f, FontWeight.W500, 0.10f, DnbColor.Glint)

    @Test
    fun ctaLarge_resolves() =
        assertStyle(DnbType.ctaLarge, 14f, FontWeight.W500, 0.34f, DnbColor.Hot)

    @Test
    fun ctaSmall_resolves() =
        assertStyle(DnbType.ctaSmall, 12.5f, FontWeight.W500, 0.32f, DnbColor.Hot)

    @Test
    fun sectionTitle_resolves() =
        assertStyle(DnbType.sectionTitle, 13f, FontWeight.W500, 0.40f, DnbColor.Phosphor)

    @Test
    fun settingsRow_resolves() =
        assertStyle(DnbType.settingsRow, 12f, FontWeight.W400, 0.24f, DnbColor.Phosphor)

    @Test
    fun titleRowName_resolves() =
        assertStyle(DnbType.titleRowName, 11.5f, FontWeight.W500, 0.22f, DnbColor.Phosphor)

    @Test
    fun microLabel_resolves() =
        assertStyle(DnbType.microLabel, 9.5f, FontWeight.W400, 0.45f, DnbColor.Dim)

    @Test
    fun failLine_resolves() =
        assertStyle(DnbType.failLine, 17f, FontWeight.W400, 0.14f, DnbColor.Phosphor)

    // ---- tracking, asserted again on its own -------------------------------------------------

    @Test
    fun every_tracking_value_is_em_and_literal() {
        assertTracking(DnbType.wordmark, 0.34f)
        assertTracking(DnbType.bestNumeral, -0.01f)
        assertTracking(DnbType.failNumeral, 0f)
        assertTracking(DnbType.continueNumeral, 0f)
        assertTracking(DnbType.roundNumeral, 0f)
        assertTracking(DnbType.streakLive, 0.10f)
        assertTracking(DnbType.streakPerfect, 0.10f)
        assertTracking(DnbType.ctaLarge, 0.34f)
        assertTracking(DnbType.ctaSmall, 0.32f)
        assertTracking(DnbType.sectionTitle, 0.40f)
        assertTracking(DnbType.settingsRow, 0.24f)
        assertTracking(DnbType.titleRowName, 0.22f)
        assertTracking(DnbType.microLabel, 0.45f)
        assertTracking(DnbType.failLine, 0.14f)
    }

    // ---- the set of tokens is frozen too -----------------------------------------------------

    @Test
    fun no_colour_was_added_or_removed() {
        assertEquals(
            setOf(
                "Black", "Phosphor", "Hot", "Dim", "Glint", "Hairline", "Ink", "Rule", "LabelMid",
            ),
            tokenNames(DnbColor::class.java),
        )
    }

    @Test
    fun no_dimension_was_added_or_removed() {
        assertEquals(
            setOf(
                "ringStartRadius", "ringTargetRadius", "ringCentreYFromTop",
                "ctaWidth", "ctaHeight", "ctaInsetX",
                "screenPadH", "settingsRowHeight", "titleRowHeight",
                "homeIndicatorW", "homeIndicatorH",
            ),
            tokenNames(DnbDim::class.java),
        )
    }

    @Test
    fun no_text_style_was_added_or_removed() {
        assertEquals(
            setOf(
                "wordmark", "bestNumeral", "failNumeral", "continueNumeral", "roundNumeral",
                "streakLive", "streakPerfect", "ctaLarge", "ctaSmall", "sectionTitle",
                "settingsRow", "titleRowName", "microLabel", "failLine",
            ),
            tokenNames(DnbType::class.java),
        )
    }

    // ---- helpers -----------------------------------------------------------------------------

    private fun argb(color: Color): Long = (color.value shr 32).toLong()

    private fun assertStyle(
        style: TextStyle,
        sizeSp: Float,
        weight: FontWeight,
        trackingEm: Float,
        color: Color,
    ) {
        assertSame("must use the bundled JetBrains Mono family", DnbFont.JetBrainsMono, style.fontFamily)
        assertTrue("fontSize must be declared in sp", style.fontSize.isSp)
        assertEquals(sizeSp, style.fontSize.value, 0f)
        assertEquals(weight, style.fontWeight)
        assertTracking(style, trackingEm)
        assertEquals(color, style.color)
    }

    private fun assertTracking(style: TextStyle, trackingEm: Float) {
        assertTrue("letterSpacing must be declared in em", style.letterSpacing.isEm)
        assertEquals(trackingEm, style.letterSpacing.value, 0f)
    }

    /** Property names on a token object, with Kotlin value-class name mangling stripped. */
    private fun tokenNames(cls: Class<*>): Set<String> =
        cls.declaredMethods
            .map { it.name }
            .filter { it.startsWith("get") }
            .map { it.substringBefore('-').removePrefix("get").replaceFirstChar { c -> c.lowercase() } }
            .map { if (cls == DnbColor::class.java) it.replaceFirstChar { c -> c.uppercase() } else it }
            .toSet()
}
