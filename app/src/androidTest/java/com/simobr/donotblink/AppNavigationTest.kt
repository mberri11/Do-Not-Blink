package com.simobr.donotblink

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simobr.donotblink.data.InMemoryGameStore
import com.simobr.donotblink.game.GameViewModel
import com.simobr.donotblink.ui.AppRoot
import com.simobr.donotblink.ui.home.HomeTags
import com.simobr.donotblink.ui.settings.SettingsTags
import com.simobr.donotblink.ui.titles.TitlesTags
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke coverage for the five screens and the sealed-class navigation between them. */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val rule = createComposeRule()

    /** Composes the app and waits out the splash, which is where every one of these starts. */
    private fun launchToHome(best: Int = 7): GameViewModel {
        val viewModel = GameViewModel(InMemoryGameStore(bestStreak = best), Random(3))
        rule.setContent { AppRoot(viewModel = viewModel) }
        rule.waitForIdle()
        return viewModel
    }

    @Test
    fun the_splash_hands_over_to_home_showing_the_stored_best() {
        launchToHome(best = 7)
        rule.onNodeWithTag(HomeTags.BEST).assertTextEquals("7")
        rule.onNodeWithTag(HomeTags.HOLD).assertExists()
    }

    @Test
    fun titles_opens_from_home_and_counts_what_the_best_unlocked() {
        launchToHome(best = 7)
        rule.onNodeWithTag(HomeTags.TITLES).performClick()
        rule.waitForIdle()
        // 1, 2, 3 and 5 are at or under 7. 8 is not.
        rule.onNodeWithTag(TitlesTags.COUNT).assertTextEquals("4 / 20")
    }

    @Test
    fun settings_opens_from_home_and_the_switches_are_live() {
        val viewModel = launchToHome()
        rule.onNodeWithTag(HomeTags.SETTINGS).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(SettingsTags.SOUND).performClick()
        rule.waitForIdle()
        assertFalse(viewModel.state.value.soundEnabled)

        rule.onNodeWithTag(SettingsTags.HAPTICS).performClick()
        rule.waitForIdle()
        assertFalse(viewModel.state.value.hapticsEnabled)
    }

    @Test
    fun reset_best_asks_once_on_the_row_before_it_forgets() {
        val viewModel = launchToHome(best = 12)
        rule.onNodeWithTag(HomeTags.SETTINGS).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(SettingsTags.RESET).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SettingsTags.RESET_NO).performClick()
        rule.waitForIdle()
        assertEquals(12, viewModel.state.value.best)

        rule.onNodeWithTag(SettingsTags.RESET).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SettingsTags.RESET_YES).performClick()
        rule.waitForIdle()
        assertEquals(0, viewModel.state.value.best)
    }
}
