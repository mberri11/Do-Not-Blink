package com.simobr.donotblink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.simobr.donotblink.ui.fail.BestLineStyle
import com.simobr.donotblink.ui.fail.HomeLinkStyle
import com.simobr.donotblink.ui.fail.StreakLabelStyle
import com.simobr.donotblink.ui.home.BestLabelStyle
import com.simobr.donotblink.ui.home.RuleLineStyle
import com.simobr.donotblink.ui.home.TitlesLinkStyle
import com.simobr.donotblink.ui.play.StreakCaptionStyle
import com.simobr.donotblink.ui.settings.BuildStyle
import com.simobr.donotblink.ui.settings.OpenStyle
import com.simobr.donotblink.ui.settings.ValueDeclineStyle
import com.simobr.donotblink.ui.settings.ValueOffStyle
import com.simobr.donotblink.ui.settings.ValueQuietStyle
import com.simobr.donotblink.ui.theme.DnbColor
import com.simobr.donotblink.ui.theme.DnbFont
import com.simobr.donotblink.ui.titles.CountStyle
import com.simobr.donotblink.ui.titles.ThresholdLockedStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Stage 5.5, item 6: a micro label that carries information or is tappable is drawn on
 * [DnbColor.LabelMid], never on [DnbColor.Dim]. Dim at #3A2A00 is unreadable at low panel
 * brightness, so it is now decorative only — locked titles, dead ring, off-states, outer track.
 *
 * This is a role table, not a palette change: the frozen tokens themselves are untouched, and
 * TokensResolutionTest still pins DnbType.microLabel at Dim.
 */
class MicroLabelRolesTest {

    @Test
    fun informational_and_tappable_micro_labels_are_label_mid() {
        assertRole("home BEST caption", BestLabelStyle)
        assertRole("home TITLES link", TitlesLinkStyle)
        assertRole("home rule line", RuleLineStyle)
        assertRole("play STREAK caption", StreakCaptionStyle)
        assertRole("fail STREAK caption", StreakLabelStyle)
        assertRole("fail BEST line", BestLineStyle)
        assertRole("fail HOME link", HomeLinkStyle)
        assertRole("settings row value", ValueQuietStyle)
        assertRole("settings OPEN", OpenStyle)
        assertRole("settings reset NO", ValueDeclineStyle)
        assertRole("settings BUILD", BuildStyle)
        assertRole("titles count", CountStyle)
        assertRole("titles locked threshold", ThresholdLockedStyle)
    }

    @Test
    fun dim_is_still_the_off_state() {
        assertEquals(
            "OFF must read as absent — that is what Dim is still for",
            DnbColor.Dim,
            ValueOffStyle.color,
        )
    }

    @Test
    fun label_mid_is_not_a_new_token() {
        // Item 6 reassigns a role; it does not add a colour. LabelMid has existed since Stage 1.
        assertEquals(0xFF8A6A1AL, (DnbColor.LabelMid.value shr 32).toLong())
    }

    @Test
    fun the_two_teaching_lines_share_one_specification() {
        // 10sp / W400 / 0.28em on LabelMid, on Home and under the ring alike.
        for ((name, style) in listOf("home" to RuleLineStyle, "ring" to com.simobr.donotblink.ui.play.RuleLineStyle)) {
            assertSame("$name: bundled JetBrains Mono", DnbFont.JetBrainsMono, style.fontFamily)
            assertEquals("$name: size", 10f, style.fontSize.value, 0f)
            assertEquals("$name: weight", FontWeight.W400, style.fontWeight)
            assertEquals("$name: tracking", 0.28f, style.letterSpacing.value, 0f)
            assertEquals("$name: colour", DnbColor.LabelMid, style.color)
        }
    }

    private fun assertRole(name: String, style: TextStyle) {
        assertEquals("$name must be LabelMid, not ${hex(style.color)}", DnbColor.LabelMid, style.color)
    }

    private fun hex(color: Color): String = "#%08X".format((color.value shr 32).toLong())
}
