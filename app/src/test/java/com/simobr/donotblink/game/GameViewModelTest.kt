package com.simobr.donotblink.game

import com.simobr.donotblink.data.InMemoryGameStore
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The fake clock is the test itself: the view model reads no clock, so every timestamp it sees is
 * one this file chose. A round here is a pure function of those numbers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun streak_increments_on_every_perfect() = runTest(dispatcher) {
        val clock = FakeClock()
        val viewModel = GameViewModel(InMemoryGameStore(), Random(42))
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.streak)
        repeat(3) { index ->
            viewModel.playPerfectRound(clock)
            assertEquals(index + 1, viewModel.state.value.streak)
        }
        assertEquals(Phase.Idle, viewModel.state.value.phase)
    }

    @Test
    fun streak_resets_to_zero_on_a_fail() = runTest(dispatcher) {
        val clock = FakeClock()
        val viewModel = GameViewModel(InMemoryGameStore(), Random(42))
        advanceUntilIdle()

        repeat(4) { viewModel.playPerfectRound(clock) }
        assertEquals(4, viewModel.state.value.streak)

        viewModel.playFailedRound(clock)
        assertEquals(Phase.Failed, viewModel.state.value.phase)
        assertEquals(0, viewModel.state.value.streak)
    }

    @Test
    fun best_updates_only_when_the_streak_passes_it() = runTest(dispatcher) {
        val clock = FakeClock()
        val store = InMemoryGameStore(bestStreak = 2)
        val viewModel = GameViewModel(store, Random(42))
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.best)

        // A streak of 1 and 2 cannot beat a best of 2.
        viewModel.playPerfectRound(clock)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.best)
        assertEquals(2, store.bestStreak.first())

        viewModel.playPerfectRound(clock)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.best)
        assertEquals(2, store.bestStreak.first())

        // The third one does.
        viewModel.playPerfectRound(clock)
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.best)
        assertEquals(3, store.bestStreak.first())
    }

    @Test
    fun cancel_touches_neither_streak_nor_best() = runTest(dispatcher) {
        val clock = FakeClock()
        val store = InMemoryGameStore()
        val viewModel = GameViewModel(store, Random(42))
        advanceUntilIdle()

        repeat(5) { viewModel.playPerfectRound(clock) }
        val streakBefore = viewModel.state.value.streak
        val bestBefore = viewModel.state.value.best
        assertEquals(5, streakBefore)

        // The shade comes down mid-round.
        viewModel.onPointerDown(clock.now)
        clock.advance(300L)
        viewModel.onPointerCancel()
        advanceUntilIdle()

        assertEquals(Phase.Idle, viewModel.state.value.phase)
        assertEquals(streakBefore, viewModel.state.value.streak)
        assertEquals(bestBefore, viewModel.state.value.best)
        assertEquals(bestBefore, store.bestStreak.first())
    }

    @Test
    fun holding_past_the_overshoot_line_fails_the_round() = runTest(dispatcher) {
        val clock = FakeClock()
        val viewModel = GameViewModel(InMemoryGameStore(), Random(42))
        advanceUntilIdle()

        viewModel.playPerfectRound(clock)
        val plan = viewModel.state.value.plan
        viewModel.onPointerDown(clock.now)

        // A frame still inside the round changes nothing.
        viewModel.onFrame(clock.advance(plan.perfectElapsedMs.roundToLong()))
        assertEquals(Phase.Holding, viewModel.state.value.phase)

        // A frame past the overshoot line kills it, with no release at all.
        viewModel.onFrame(clock.advance(plan.overshootFailElapsedMs.roundToLong()))
        assertEquals(Phase.Failed, viewModel.state.value.phase)
        assertEquals(0, viewModel.state.value.streak)
    }

    @Test
    fun a_perfect_arms_the_next_round_only_after_the_flash_and_the_quiet() = runTest(dispatcher) {
        val clock = FakeClock()
        val viewModel = GameViewModel(InMemoryGameStore(), Random(42))
        advanceUntilIdle()

        val plan = viewModel.state.value.plan
        viewModel.onPointerDown(clock.now)
        val releasedAt = clock.advance(plan.perfectElapsedMs.roundToLong())
        viewModel.onPointerUp(releasedAt)
        assertEquals(Phase.Perfect, viewModel.state.value.phase)

        val armAt = releasedAt + GameViewModel.FLASH_MS + GameViewModel.QUIET_MS
        viewModel.onFrame(armAt - 1)
        assertEquals(Phase.Perfect, viewModel.state.value.phase)

        viewModel.onFrame(armAt)
        assertEquals(Phase.Idle, viewModel.state.value.phase)
        assertTrue("the next round must be planned", viewModel.state.value.roundId > 0)
    }

    @Test
    fun a_fail_reports_the_run_streak_and_the_titles_the_run_earned() = runTest(dispatcher) {
        val clock = FakeClock()
        val store = InMemoryGameStore()
        val viewModel = GameViewModel(store, Random(42))
        advanceUntilIdle()

        repeat(3) { viewModel.playPerfectRound(clock) }
        viewModel.playFailedRound(clock)
        advanceUntilIdle()

        assertEquals(3, viewModel.state.value.lastRunStreak)
        assertEquals(
            listOf("EYES OPEN", "TWITCH", "STILL HAND"),
            viewModel.state.value.newlyUnlockedTitles.map { it.name },
        )
        assertEquals(1, store.lifetimeRuns.first())

        // A second run that beats nothing earns nothing.
        viewModel.startNewRun()
        viewModel.playPerfectRound(clock)
        viewModel.playFailedRound(clock)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.lastRunStreak)
        assertEquals(emptyList<String>(), viewModel.state.value.newlyUnlockedTitles.map { it.name })
        assertEquals(2, store.lifetimeRuns.first())
    }

    @Test
    fun resetting_best_clears_it_and_relocks_every_title() = runTest(dispatcher) {
        val clock = FakeClock()
        val store = InMemoryGameStore()
        val viewModel = GameViewModel(store, Random(42))
        advanceUntilIdle()

        repeat(3) { viewModel.playPerfectRound(clock) }
        advanceUntilIdle()
        assertEquals(3, viewModel.state.value.best)
        assertEquals(3, unlockedTitleCount(viewModel.state.value.best))

        viewModel.resetBest()
        advanceUntilIdle()
        assertEquals(0, viewModel.state.value.best)
        assertEquals(0, store.bestStreak.first())
        assertEquals(0, unlockedTitleCount(viewModel.state.value.best))
    }

    @Test
    fun the_first_launch_epoch_is_written_once_and_never_moves() = runTest(dispatcher) {
        val store = InMemoryGameStore()
        GameViewModel(store, Random(42), nowEpochMs = { 1_700_000_000_000L })
        advanceUntilIdle()
        assertEquals(1_700_000_000_000L, store.firstLaunchEpoch.first())

        GameViewModel(store, Random(42), nowEpochMs = { 1_900_000_000_000L })
        advanceUntilIdle()
        assertEquals(1_700_000_000_000L, store.firstLaunchEpoch.first())
    }

    // helpers ------------------------------------------------------------------------------------

    private class FakeClock(var now: Long = 100_000L) {
        fun advance(ms: Long): Long {
            now += ms
            return now
        }
    }

    private fun GameViewModel.playPerfectRound(clock: FakeClock) {
        val plan = state.value.plan
        onPointerDown(clock.now)
        onPointerUp(clock.advance(plan.perfectElapsedMs.roundToLong()))
        onFrame(clock.advance(GameViewModel.FLASH_MS + GameViewModel.QUIET_MS))
    }

    private fun GameViewModel.playFailedRound(clock: FakeClock) {
        val plan = state.value.plan
        onPointerDown(clock.now)
        val tooEarly = (plan.perfectElapsedMs - plan.halfWindowMs - 5f).roundToLong()
        onPointerUp(clock.advance(tooEarly))
    }
}
