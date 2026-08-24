package com.simobr.donotblink

import com.simobr.donotblink.ui.licences.OFL_ASSET_PATH
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two bundled `.ttf` files ship inside the APK. That is redistribution under the SIL Open Font
 * License, which requires the copyright notice and the licence text to travel WITH the font. A copy
 * sitting in `licenses/` at the repo root travels nowhere, so the text is an asset and the LICENCES
 * screen reads it at [OFL_ASSET_PATH].
 *
 * If this test fails, the shipped app is redistributing a font without its licence.
 */
class LicenceAssetTest {

    @Test
    fun the_ofl_text_ships_as_an_asset() {
        val asset = oflAsset()
        assertNotNull("no OFL asset at */assets/$OFL_ASSET_PATH", asset)
        assertTrue("the OFL asset is empty", asset!!.length() > 0)
    }

    @Test
    fun the_ofl_text_is_the_whole_licence_and_not_a_stub() {
        val text = oflAsset()!!.readText()
        assertTrue("the OFL asset is only ${text.length} characters", text.length > 1000)
    }

    @Test
    fun the_asset_carries_the_notice_the_licence_requires() {
        val text = oflAsset()!!.readText()
        assertTrue("no copyright notice", text.contains("Copyright"))
        assertTrue("no JetBrains Mono attribution", text.contains("JetBrains Mono"))
        assertTrue("not the OFL", text.contains("SIL OPEN FONT LICENSE") || text.contains("SIL Open Font License"))
    }

    @Test
    fun the_shipped_asset_matches_the_copy_in_the_repo() {
        val repoCopy = firstExisting("licenses/JetBrainsMono-OFL.txt", "../licenses/JetBrainsMono-OFL.txt")
        assertNotNull("could not locate licenses/JetBrainsMono-OFL.txt", repoCopy)
        assertEquals(
            "the shipped licence has drifted from the repo copy",
            repoCopy!!.readText(),
            oflAsset()!!.readText(),
        )
    }

    @Test
    fun the_fonts_the_licence_covers_are_actually_bundled() {
        for (name in listOf("jetbrains_mono_regular.ttf", "jetbrains_mono_medium.ttf")) {
            assertNotNull(
                "$name is not bundled, so the OFL asset covers nothing",
                firstExisting("src/main/res/font/$name", "app/src/main/res/font/$name"),
            )
        }
    }

    private fun oflAsset(): File? =
        firstExisting("src/main/assets/$OFL_ASSET_PATH", "app/src/main/assets/$OFL_ASSET_PATH")

    private fun firstExisting(vararg paths: String): File? =
        paths.map(::File).firstOrNull { it.isFile }
}
