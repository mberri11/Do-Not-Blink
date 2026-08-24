package com.simobr.donotblink

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simobr.donotblink.ui.settings.SettingsScreen
import com.simobr.donotblink.ui.settings.SettingsTags
import com.simobr.donotblink.ui.theme.ScaledLayout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 6, item 3.
 *
 * Every screen called `navigationBarsPadding()` and nothing called anything for the status bar or
 * the display cutout. It survived only because the smallest top offset in the app is 60dp, which
 * happens to clear the test device's status bar. On a device with a taller cutout the SETTINGS,
 * TITLES and PHOSPHOR headers slide under the clock.
 *
 * The insets here are dispatched onto the Compose view rather than waited for from the system, so
 * the assertion holds on any device this runs on.
 */
@RunWith(AndroidJUnit4::class)
class TopInsetTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun the_settings_header_clears_a_48dp_top_inset() {
        rule.setContent {
            val view = LocalView.current
            val density = LocalDensity.current
            LaunchedEffect(view) {
                val topPx = with(density) { SIMULATED_TOP_INSET.roundToPx() }
                ViewCompat.dispatchApplyWindowInsets(
                    view,
                    WindowInsetsCompat.Builder()
                        .setInsets(
                            WindowInsetsCompat.Type.systemBars(),
                            Insets.of(0, topPx, 0, 0),
                        )
                        .build(),
                )
            }

            ScaledLayout {
                SettingsScreen(
                    hapticsEnabled = true,
                    soundEnabled = true,
                    bestStreak = 12,
                    phosphorLabel = "P3 AMBER",
                    onSetHaptics = {},
                    onSetSound = {},
                    onResetBest = {},
                    onOpenPhosphor = {},
                    onOpenPrivacyPolicy = {},
                    onBack = {},
                )
            }
        }
        rule.waitForIdle()

        val header = rule.onNodeWithTag(SettingsTags.HEADER).getUnclippedBoundsInRoot()
        assertTrue(
            "the SETTINGS header's top is ${header.top}, inside a ${SIMULATED_TOP_INSET} inset",
            header.top >= SIMULATED_TOP_INSET,
        )
    }

    /** The row exists only where UMP says it is required. See `showsPrivacyOptionsRow`. */
    @Test
    fun the_privacy_options_row_is_absent_unless_ump_requires_it() {
        rule.setContent {
            ScaledLayout {
                SettingsScreen(
                    hapticsEnabled = true,
                    soundEnabled = true,
                    bestStreak = 0,
                    phosphorLabel = "P3 AMBER",
                    onSetHaptics = {},
                    onSetSound = {},
                    onResetBest = {},
                    onOpenPhosphor = {},
                    onOpenPrivacyPolicy = {},
                    onBack = {},
                    showPrivacyOptions = false,
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(SettingsTags.PRIVACY_OPTIONS).assertDoesNotExist()
        rule.onNodeWithTag(SettingsTags.PRIVACY).assertExists()
    }

    @Test
    fun the_privacy_options_row_sits_between_phosphor_and_privacy_policy_when_required() {
        rule.setContent {
            ScaledLayout {
                SettingsScreen(
                    hapticsEnabled = true,
                    soundEnabled = true,
                    bestStreak = 0,
                    phosphorLabel = "P3 AMBER",
                    onSetHaptics = {},
                    onSetSound = {},
                    onResetBest = {},
                    onOpenPhosphor = {},
                    onOpenPrivacyPolicy = {},
                    onBack = {},
                    showPrivacyOptions = true,
                )
            }
        }
        rule.waitForIdle()

        val phosphor = rule.onNodeWithTag(SettingsTags.PHOSPHOR).getUnclippedBoundsInRoot()
        val options = rule.onNodeWithTag(SettingsTags.PRIVACY_OPTIONS).getUnclippedBoundsInRoot()
        val policy = rule.onNodeWithTag(SettingsTags.PRIVACY).getUnclippedBoundsInRoot()

        assertTrue("PRIVACY OPTIONS must sit under PHOSPHOR", phosphor.bottom <= options.top)
        assertTrue("PRIVACY OPTIONS must sit above PRIVACY POLICY", options.bottom <= policy.top)
    }

    private companion object {
        val SIMULATED_TOP_INSET = 48.dp
    }
}
