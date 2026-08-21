package com.simobr.donotblink

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simobr.donotblink.data.InMemoryGameStore
import com.simobr.donotblink.game.GameViewModel
import com.simobr.donotblink.game.Phase
import com.simobr.donotblink.ui.play.GameFeedback
import com.simobr.donotblink.ui.play.PlayScreen
import com.simobr.donotblink.ui.play.PlayScreenTags
import kotlin.math.roundToLong
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The clock does not move on its own here: `autoAdvance = false`, and the release timestamp is
 * chosen by [androidx.compose.ui.test.TouchInjectionScope.advanceEventTime], which is the same
 * `uptimeMillis` the view model judges against.
 */
@RunWith(AndroidJUnit4::class)
class PlayScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun newViewModel() = GameViewModel(InMemoryGameStore(), Random(7))

    private fun setContent(viewModel: GameViewModel) {
        // The content is composed with the clock still running: setContent waits for idle, and an
        // idle state is only reachable once the first frame has been painted. The clock is frozen
        // straight afterwards, before a single pointer event is injected.
        rule.setContent { PlayScreen(viewModel = viewModel, feedback = GameFeedback.None) }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    @Test
    fun down_then_up_on_the_line_is_a_perfect() {
        val viewModel = newViewModel()
        setContent(viewModel)

        val travelMs = viewModel.state.value.plan.perfectElapsedMs.roundToLong()
        rule.onNodeWithTag(PlayScreenTags.SURFACE).performTouchInput {
            down(center)
            advanceEventTime(travelMs)
            up()
        }

        assertEquals(Phase.Perfect, viewModel.state.value.phase)
        assertEquals(1, viewModel.state.value.streak)
    }

    @Test
    fun down_then_up_far_from_the_line_is_a_fail() {
        val viewModel = newViewModel()
        setContent(viewModel)

        val travelMs = viewModel.state.value.plan.perfectElapsedMs.roundToLong()
        rule.onNodeWithTag(PlayScreenTags.SURFACE).performTouchInput {
            down(center)
            advanceEventTime(travelMs / 4)
            up()
        }

        assertEquals(Phase.Failed, viewModel.state.value.phase)
        assertEquals(0, viewModel.state.value.streak)
    }

    @Test
    fun a_second_finger_can_neither_end_nor_restart_the_round() {
        val viewModel = newViewModel()
        setContent(viewModel)

        val travelMs = viewModel.state.value.plan.perfectElapsedMs.roundToLong()
        rule.onNodeWithTag(PlayScreenTags.SURFACE).performTouchInput {
            down(0, center)
            advanceEventTime(travelMs / 2)
            // A second finger arrives and leaves. The round must not notice.
            down(1, center + Offset(120f, 0f))
            advanceEventTime(40)
            up(1)
        }
        assertEquals(Phase.Holding, viewModel.state.value.phase)

        rule.onNodeWithTag(PlayScreenTags.SURFACE).performTouchInput {
            advanceEventTime(travelMs - travelMs / 2 - 40)
            up(0)
        }
        assertEquals(Phase.Perfect, viewModel.state.value.phase)
        assertEquals(1, viewModel.state.value.streak)
    }

    @Test
    fun the_next_round_arms_after_the_flash_and_the_quiet() {
        val viewModel = newViewModel()
        setContent(viewModel)

        val travelMs = viewModel.state.value.plan.perfectElapsedMs.roundToLong()
        rule.onNodeWithTag(PlayScreenTags.SURFACE).performTouchInput {
            down(center)
            advanceEventTime(travelMs)
            up()
        }
        assertEquals(Phase.Perfect, viewModel.state.value.phase)

        rule.mainClock.advanceTimeBy(travelMs + GameViewModel.FLASH_MS + GameViewModel.QUIET_MS + 32L)

        assertEquals(Phase.Idle, viewModel.state.value.phase)
        assertEquals(1, viewModel.state.value.streak)
    }
}
