package com.simobr.donotblink

import android.content.Context
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simobr.donotblink.ui.theme.DnbFont
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A missing font resource, or one wired to the wrong weight, fails SILENTLY at render time — the
 * system substitutes a default face and no human reviewing a diff will catch it. This test is the
 * only thing that will.
 */
@RunWith(AndroidJUnit4::class)
class FontResolutionTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun regular_font_resource_resolves() {
        assertNotNull(
            "R.font.jetbrains_mono_regular did not load",
            ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular),
        )
    }

    @Test
    fun medium_font_resource_resolves() {
        assertNotNull(
            "R.font.jetbrains_mono_medium did not load",
            ResourcesCompat.getFont(context, R.font.jetbrains_mono_medium),
        )
    }

    @Test
    fun family_has_exactly_two_entries_at_w400_and_w500() {
        val family = DnbFont.JetBrainsMono as FontListFontFamily
        assertEquals(2, family.fonts.size)
        assertEquals(
            listOf(FontWeight.W400, FontWeight.W500),
            family.fonts.map { it.weight },
        )
    }

    @Test
    fun family_entries_point_at_the_bundled_resources() {
        val family = DnbFont.JetBrainsMono as FontListFontFamily
        assertEquals(
            listOf(R.font.jetbrains_mono_regular, R.font.jetbrains_mono_medium),
            family.fonts.map { (it as ResourceFont).resId },
        )
    }

    /**
     * Guards the case the assertions above cannot see: the right resource names holding the wrong
     * TTF. The weight a font file declares for itself is read straight out of its OS/2 table.
     */
    @Test
    fun bundled_ttf_files_declare_weights_400_and_500() {
        assertEquals(400, usWeightClass(R.font.jetbrains_mono_regular))
        assertEquals(500, usWeightClass(R.font.jetbrains_mono_medium))
    }

    /** usWeightClass out of the sfnt OS/2 table, big-endian, no font library involved. */
    private fun usWeightClass(resId: Int): Int {
        val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
        val buf = ByteBuffer.wrap(bytes)
        val numTables = buf.getShort(4).toInt() and 0xFFFF
        for (i in 0 until numTables) {
            val record = 12 + 16 * i
            val tag = String(bytes, record, 4, Charsets.US_ASCII)
            if (tag == "OS/2") {
                val tableOffset = buf.getInt(record + 8)
                return buf.getShort(tableOffset + 4).toInt() and 0xFFFF
            }
        }
        throw AssertionError("font resource $resId has no OS/2 table")
    }
}
