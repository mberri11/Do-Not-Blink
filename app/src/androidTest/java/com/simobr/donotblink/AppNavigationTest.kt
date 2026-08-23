package com.simobr.donotblink

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simobr.donotblink.data.InMemoryGameStore
import com.simobr.donotblink.game.GameViewModel
import com.simobr.donotblink.game.Phase
import com.simobr.donotblink.ui.AppRoot
import com.simobr.donotblink.ui.home.HomeTags
import com.simobr.donotblink.ui.play.PlayScreenTags
import com.simobr.donotblink.ui.settings.SettingsTags
import com.simobr.donotblink.ui.titles.TitlesTags
import kotlin.math.roundToLong
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

    /**
     * The Stage 5.5 regression, item 1.
     *
     * Home and Play used to be two `when` branches, so `onRoundStarted` swapped one PlayScreen for
     * another WHILE the finger was still down: the gesture coroutine was disposed, the round was
     * cancelled, and the first hold of every session was unwinnable. One call site means the press
     * that starts the round is the press that gets judged.
     *
     * On the pre-fix code this fails at the roundId assertion — the cancel arms a new round.
     */
    @Test
    fun the_first_hold_from_home_survives_the_move_into_play_and_is_judged() {
        val viewModel = launchToHome()
        rule.mainClock.autoAdvance = false

        val roundIdAtPress = viewModel.state.value.roundId
        val travelMs = viewModel.state.value.plan.perfectElapsedMs.roundToLong()

        rule.onNodeWithTag(PlayScreenTags.SURFACE).performTouchInput {
            down(center)
            advanceEventTime(HOLD_MS)
        }
        // Frames run: this is where the old code disposed the node under the finger.
        rule.mainClock.advanceTimeBy(HOLD_MS)

        assertEquals(Phase.Holding, viewModel.state.value.phase)
        assertEquals("the round was restarted mid-hold", roundIdAtPress, viewModel.state.value.roundId)

        rule.onNodeWithTag(PlayScreenTags.SURFACE).performTouchInput {
            advanceEventTime(travelMs - HOLD_MS)
            up()
        }

        assertEquals(Phase.Perfect, viewModel.state.value.phase)
        assertEquals(1, viewModel.state.value.streak)
        assertEquals(roundIdAtPress, viewModel.state.value.roundId)
    }

    /** Home is the idle face of the play surface, so its chrome must not eat the press. */
    @Test
    fun the_home_chrome_leaves_the_press_to_the_surface() {
        val viewModel = launchToHome()
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag(HomeTags.HOLD).assertExists()
        rule.onNodeWithTag(HomeTags.RULE).assertExists()

        rule.onNodeWithTag(PlayScreenTags.SURFACE).performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(32L)

        assertEquals(Phase.Holding, viewModel.state.value.phase)
        // The chrome is gone and the streak block has taken its place: this is one screen.
        rule.onNodeWithTag(HomeTags.HOLD).assertDoesNotExist()
        rule.onNodeWithTag(PlayScreenTags.STREAK).assertExists()
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

    private companion object {
        /** Long enough that the old code had disposed and cancelled well before the release. */
        const val HOLD_MS = 120L
    }
}
