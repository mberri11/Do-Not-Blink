package com.simobr.donotblink.game

import com.simobr.donotblink.data.InMemoryGameStore
import kotlin.math.abs
import kotlin.math.roundToInt
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The trial's state machine. Everything here rides the same fake clock the endless-mode tests use —
 * the view model still reads no clock of its own, and the trial's dwell is driven by frames whose
 * timestamps this file chooses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyTrialViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = 20_338L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        store: InMemoryGameStore = InMemoryGameStore(),
        day: Long = today,
    ) = GameViewModel(store, Random(42), todayEpochDay = { day })

    @Test
    fun `a trial plays all ten rings even when every one is missed`() = runTest(dispatcher) {
        val store = InMemoryGameStore()
        val model = viewModel(store)
        advanceUntilIdle()

        assertTrue(model.startDailyTrial())
        assertEquals(Mode.Daily, model.state.value.mode)

        val clock = FakeClock()
        repeat(DailyTrial.RINGS) { model.missRing(clock) }
        advanceUntilIdle()

        // Ten rings played, all missed, and the trial is over rather than the run being over.
        assertEquals(Phase.DailyDone, model.state.value.phase)
        val stored = store.lastDailyResult.first()
        assertEquals(DailyTrial.RINGS, stored?.marks?.size)
        assertEquals(0, stored?.hits)
    }

    @Test
    fun `a miss does not end the trial and does not reach the fail screen`() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.startDailyTrial()

        val clock = FakeClock()
        model.missRing(clock)
        advanceUntilIdle()

        // Endless mode would be sitting in Failed or ContinueOffer here. The trial has armed ring 2.
        assertEquals(Phase.Idle, model.state.value.phase)
        assertEquals(1, model.state.value.dailyRingIndex)
        assertEquals(listOf(false), model.state.value.dailyMarks)
    }

    @Test
    fun `a mixed trial records its marks in ring order`() = runTest(dispatcher) {
        val store = InMemoryGameStore()
        val model = viewModel(store)
        advanceUntilIdle()
        model.startDailyTrial()

        val clock = FakeClock()
        val plannedHits = listOf(true, false, true, true, false, true, true, true, false, true)
        plannedHits.forEach { hit -> if (hit) model.hitRing(clock) else model.missRing(clock) }
        advanceUntilIdle()

        val stored = store.lastDailyResult.first()
        assertEquals(plannedHits, stored?.marks)
        assertEquals(7, stored?.hits)
        assertEquals(today, stored?.epochDay)
    }

    @Test
    fun `a trial never touches the endless best streak or the title ladder`() = runTest(dispatcher) {
        val store = InMemoryGameStore(bestStreak = 3)
        val model = viewModel(store)
        advanceUntilIdle()
        model.startDailyTrial()

        val clock = FakeClock()
        repeat(DailyTrial.RINGS) { model.hitRing(clock) }
        advanceUntilIdle()

        // Ten perfect trial rings would be a streak of 10 in endless mode. The ladder must not move.
        assertEquals(3, store.bestStreak.first())
        assertEquals(3, model.state.value.best)
        assertEquals(0, model.state.value.streak)
        assertTrue(model.state.value.newlyUnlockedTitles.isEmpty())
    }

    @Test
    fun `a trial does not count as a run for the interstitial cadence`() = runTest(dispatcher) {
        val store = InMemoryGameStore()
        val model = viewModel(store)
        advanceUntilIdle()
        model.startDailyTrial()

        val clock = FakeClock()
        repeat(DailyTrial.RINGS) { model.missRing(clock) }
        advanceUntilIdle()

        // endRun() is what counts a run, and the trial must never reach it.
        assertEquals(0, store.lifetimeRuns.first())
    }

    @Test
    fun `today's trial can only be played once`() = runTest(dispatcher) {
        val store = InMemoryGameStore()
        val model = viewModel(store)
        advanceUntilIdle()

        assertTrue(model.startDailyTrial())
        val clock = FakeClock()
        repeat(DailyTrial.RINGS) { model.hitRing(clock) }
        advanceUntilIdle()

        assertTrue(model.state.value.dailyPlayedToday)
        assertFalse(model.startDailyTrial())
    }

    @Test
    fun `tomorrow is a new trial`() = runTest(dispatcher) {
        val store = InMemoryGameStore()
        val model = viewModel(store)
        advanceUntilIdle()
        model.startDailyTrial()
        val clock = FakeClock()
        repeat(DailyTrial.RINGS) { model.hitRing(clock) }
        advanceUntilIdle()

        // A second view model on the following day, reading the same store.
        val tomorrow = viewModel(store, day = today + 1)
        advanceUntilIdle()
        assertFalse(tomorrow.state.value.dailyPlayedToday)
        assertTrue(tomorrow.startDailyTrial())
    }

    @Test
    fun `walking out part-way forfeits the remaining rings`() = runTest(dispatcher) {
        val store = InMemoryGameStore()
        val model = viewModel(store)
        advanceUntilIdle()
        model.startDailyTrial()

        val clock = FakeClock()
        model.hitRing(clock)
        model.hitRing(clock)
        model.abandonDailyTrial()
        advanceUntilIdle()

        val stored = store.lastDailyResult.first()
        assertEquals(DailyTrial.RINGS, stored?.marks?.size)
        assertEquals(2, stored?.hits)
        // And the attempt is spent, so backing out cannot buy a second try.
        assertFalse(model.startDailyTrial())
        assertEquals(Mode.Endless, model.state.value.mode)
    }

    @Test
    fun `abandoning before the first ring does not spend the day`() = runTest(dispatcher) {
        val store = InMemoryGameStore()
        val model = viewModel(store)
        advanceUntilIdle()
        model.startDailyTrial()

        model.abandonDailyTrial()
        advanceUntilIdle()

        // An accidental tap into the trial and straight back out costs nothing.
        assertNull(store.lastDailyResult.first())
        assertTrue(model.startDailyTrial())
    }

    @Test
    fun `finishing a trial rolls the day streak`() = runTest(dispatcher) {
        val store = InMemoryGameStore()
        val clock = FakeClock()

        val first = viewModel(store, day = today)
        advanceUntilIdle()
        first.startDailyTrial()
        repeat(DailyTrial.RINGS) { first.hitRing(clock) }
        advanceUntilIdle()
        assertEquals(1, store.dailyDayStreak.first())

        val second = viewModel(store, day = today + 1)
        advanceUntilIdle()
        second.startDailyTrial()
        repeat(DailyTrial.RINGS) { second.hitRing(clock) }
        advanceUntilIdle()
        assertEquals(2, store.dailyDayStreak.first())
    }

    @Test
    fun `the result is in state on the very frame the trial ends`() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.startDailyTrial()

        val clock = FakeClock()
        repeat(DailyTrial.RINGS) { model.hitRing(clock) }

        // NOTE: no advanceUntilIdle(). The result screen composes the instant the phase flips, long
        // before the store write lands — if the result only arrived with the store's echo, the
        // screen would read "no result" and send the player home a frame after they finished.
        assertEquals(Phase.DailyDone, model.state.value.phase)
        assertEquals(DailyTrial.RINGS, model.state.value.dailyResult?.hits)
        assertTrue(model.state.value.dailyPlayedToday)
    }

    @Test
    fun `leaving the trial puts the surface back in endless mode`() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.startDailyTrial()
        assertEquals(Mode.Daily, model.state.value.mode)

        model.leaveDailyTrial()

        assertEquals(Mode.Endless, model.state.value.mode)
        assertEquals(Phase.Idle, model.state.value.phase)
        assertEquals(0, model.state.value.streak)
    }

    @Test
    fun `the trial plays the day's planned rings, in order`() = runTest(dispatcher) {
        val model = viewModel()
        advanceUntilIdle()
        model.startDailyTrial()

        val expected = DailyTrial.plansFor(today)
        val clock = FakeClock()
        expected.forEachIndexed { index, plan ->
            // The armed plan is the day's plan for this ring, not a randomly seeded one.
            assertEquals("ring $index", plan, model.state.value.plan)
            model.hitRing(clock)
        }
        advanceUntilIdle()
    }

    @Test
    fun `an overshoot contributes a miss but no error measurement`() = runTest(dispatcher) {
        val store = InMemoryGameStore()
        val model = viewModel(store)
        advanceUntilIdle()
        model.startDailyTrial()

        val clock = FakeClock()
        // One ring released outside the band, then nine held straight through to overshoot.
        model.missRing(clock)
        val measured = model.state.value.dailyErrorsMs.single()
        repeat(DailyTrial.RINGS - 1) { model.overshootRing(clock) }
        advanceUntilIdle()

        val stored = store.lastDailyResult.first()
        assertEquals(0, stored?.hits)
        // Nine overshoots produced no measurement at all, so the mean is the ONE release that did.
        // Averaging them in as zero would have dragged this toward a tenth of the real value.
        assertNotEquals(0, stored?.meanAbsErrorMs)
        assertEquals(abs(measured).roundToInt(), stored?.meanAbsErrorMs)
        assertEquals(1, model.state.value.dailyErrorsMs.size)
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Plays the armed ring perfectly and lets the flash run out so the next ring arms. */
    private fun GameViewModel.hitRing(clock: FakeClock) {
        val plan = state.value.plan
        onPointerDown(clock.now)
        onPointerUp(clock.advance(plan.perfectElapsedMs.roundToLong()))
        onFrame(clock.advance(GameViewModel.FLASH_MS + GameViewModel.QUIET_MS))
    }

    /** Releases just outside the band — late, so the ring is never held to an overshoot. */
    private fun GameViewModel.missRing(clock: FakeClock) {
        val plan = state.value.plan
        onPointerDown(clock.now)
        onPointerUp(clock.advance((plan.perfectElapsedMs + plan.halfWindowMs + 5f).roundToLong()))
        onFrame(clock.advance(GameViewModel.DAILY_MISS_DWELL_MS))
    }

    /** Holds straight through the band until the round is lost with no release at all. */
    private fun GameViewModel.overshootRing(clock: FakeClock) {
        val plan = state.value.plan
        onPointerDown(clock.now)
        onFrame(clock.advance(plan.overshootFailElapsedMs.roundToLong() + 20L))
        onFrame(clock.advance(GameViewModel.DAILY_MISS_DWELL_MS))
    }

    private class FakeClock(var now: Long = 100_000L) {
        fun advance(ms: Long): Long {
            now += ms
            return now
        }
    }
}
