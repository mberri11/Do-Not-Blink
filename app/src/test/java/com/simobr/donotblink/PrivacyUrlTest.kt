package com.simobr.donotblink

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The privacy policy URL is shipped: SETTINGS -> PRIVACY POLICY hands it to a browser, and the same
 * string has to appear in the Play listing and in AdMob's app settings.
 *
 * The previous value (`mberri11.github.io/Do-Not-Blink/privacy.html`) returned HTTP 404. A live app
 * whose policy link opens a dead page is a Play policy violation and a rejection, so the value is
 * pinned here rather than trusted to a gradle property nobody re-reads.
 */
class PrivacyUrlTest {

    @Test
    fun the_policy_url_is_the_published_simobr_studio_page() {
        assertEquals(PUBLISHED_POLICY_URL, BuildConfig.PRIVACY_POLICY_URL)
    }

    @Test
    fun the_policy_url_is_https() {
        assertTrue(
            "the policy URL must be https, not '${BuildConfig.PRIVACY_POLICY_URL}'",
            BuildConfig.PRIVACY_POLICY_URL.startsWith("https://"),
        )
    }

    /**
     * Nothing shipped may still name a personal account or the sibling app. This walks the source
     * tree rather than shelling out to grep so the test is the same on any machine and reports the
     * offending path itself.
     */
    @Test
    fun no_shipped_source_names_a_stale_host_or_the_wrong_app() {
        val main = mainSourceRoot()
        assertNotNull("could not locate app/src/main from ${File(".").absolutePath}", main)

        val offenders = main!!.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                // Bytes, not text: ISO-8859-1 maps every byte to a char, so a binary asset is
                // searched safely and an ASCII substring is still found if it is in there.
                val content = file.readBytes().toString(Charsets.ISO_8859_1)
                val hits = FORBIDDEN.filter { it in content }
                if (hits.isEmpty()) null else "${file.relativeTo(main)} -> $hits"
            }
            .toList()

        assertEquals("shipped source still names: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun the_walk_actually_walked_something() {
        // A silent zero-file walk would make the test above pass for the wrong reason.
        val main = mainSourceRoot()
        assertNotNull(main)
        val files = main!!.walkTopDown().count { it.isFile }
        assertTrue("only $files files under app/src/main — the walk found nothing", files > 20)
    }

    private fun mainSourceRoot(): File? =
        listOf("src/main", "app/src/main").map(::File).firstOrNull { it.isDirectory }

    private companion object {
        const val PUBLISHED_POLICY_URL = "https://simobr-studio.github.io/Do-Not-Blink_Legal/"
        val FORBIDDEN = listOf("mberri11", "aurafyapp")
    }
}
