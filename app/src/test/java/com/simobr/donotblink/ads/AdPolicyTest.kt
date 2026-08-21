package com.simobr.donotblink.ads

import com.simobr.donotblink.data.InMemoryGameStore
import com.simobr.donotblink.game.GameViewModel
import com.simobr.donotblink.game.Phase
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The interstitial gate is the highest-risk logic in this app. "Every 3 runs" on a game whose runs
 * can end in eight seconds is an interstitial every thirty seconds — a Disruptive Ads policy
 * problem and a retention catastrophe. Every clause is tested in isolation: the passing case is
 * broken one clause at a time, so no clause can quietly stop mattering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdPolicyTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    /** A gate where every clause passes. Each test below breaks exactly one of them. */
    private val allowed = InterstitialGate(
        runsSinceLastInterstitial = 3,
        secondsSinceLastInterstitial = 90,
        runsThisSession = 4,
        previousRunWasPersonalBest = false,
        previousRunUsedRewardedContinue = false,
        interstitialsThisSession = 0,
    )

    @Test
    fun the_baseline_gate_allows_an_interstitial() {
        assertTrue(shouldShowInterstitial(allowed))
    }

    @Test
    fun clause_runs_since_last_interstitial() {
        assertFalse(shouldShowInterstitial(allowed.copy(runsSinceLastInterstitial = 2)))
        assertTrue(shouldShowInterstitial(allowed.copy(runsSinceLastInterstitial = 3)))
    }

    @Test
    fun clause_seconds_since_last_interstitial() {
        assertFalse(shouldShowInterstitial(allowed.copy(secondsSinceLastInterstitial = 89)))
        assertTrue(shouldShowInterstitial(allowed.copy(secondsSinceLastInterstitial = 90)))
    }

    @Test
    fun clause_first_four_runs_of_a_session_are_always_clean() {
        assertFalse(shouldShowInterstitial(allowed.copy(runsThisSession = 3)))
        assertTrue(shouldShowInterstitial(allowed.copy(runsThisSession = 4)))
    }

    @Test
    fun clause_never_tax_a_personal_best() {
        assertFalse(shouldShowInterstitial(allowed.copy(previousRunWasPersonalBest = true)))
    }

    @Test
    fun clause_never_follow_a_rewarded_continue() {
        assertFalse(shouldShowInterstitial(allowed.copy(previousRunUsedRewardedContinue = true)))
    }

    @Test
    fun clause_session_ceiling() {
        assertFalse(shouldShowInterstitial(allowed.copy(interstitialsThisSession = 6)))
        assertTrue(shouldShowInterstitial(allowed.copy(interstitialsThisSession = 5)))
    }

    @Test
    fun the_session_counters_feed_the_gate() {
        var now = 1_000_000L
        val session = AdSession { now }

        repeat(4) { session.onRunEnded(wasPersonalBest = false, usedRewardedContinue = false) }
        assertTrue("four clean runs and no interstitial yet", shouldShowInterstitial(session.gate()))

        session.onInterstitialShown()
        assertFalse("straight after one, everything is blocked", shouldShowInterstitial(session.gate()))

        repeat(3) { session.onRunEnded(wasPersonalBest = false, usedRewardedContinue = false) }
        assertFalse("three runs is not enough without the ninety seconds", shouldShowInterstitial(session.gate()))

        now += 90_000L
        assertTrue("three runs and ninety seconds", shouldShowInterstitial(session.gate()))
    }

    // ---- the continue offer ---------------------------------------------------------------------

    @Test
    fun continue_is_offered_when_the_run_was_close_to_the_record() {
        assertTrue(
            "streak 8 against a best of 9 is a run worth thirty seconds",
            shouldOfferContinue(streak = 8, bestStreak = 9, continuesUsedThisRun = 0, bestAtRunStart = 9),
        )
    }

    @Test
    fun continue_is_not_offered_when_the_run_was_nowhere_near_the_record() {
        assertFalse(
            "streak 8 against a best of 30 is not worth an ad",
            shouldOfferContinue(streak = 8, bestStreak = 30, continuesUsedThisRun = 0, bestAtRunStart = 30),
        )
    }

    @Test
    fun continue_needs_the_streak_floor_as_well() {
        assertFalse(shouldOfferContinue(streak = 7, bestStreak = 7, continuesUsedThisRun = 0, bestAtRunStart = 7))
        assertTrue(shouldOfferContinue(streak = 8, bestStreak = 8, continuesUsedThisRun = 0, bestAtRunStart = 8))
    }

    @Test
    fun the_second_continue_needs_the_old_record_beaten() {
        // Used one already, and the run has passed the record it started with.
        assertTrue(shouldOfferContinue(streak = 12, bestStreak = 12, continuesUsedThisRun = 1, bestAtRunStart = 10))
        // Used one already, but the run has not passed it.
        assertFalse(shouldOfferContinue(streak = 9, bestStreak = 10, continuesUsedThisRun = 1, bestAtRunStart = 10))
        // Equalling the old record is not passing it.
        assertFalse(shouldOfferContinue(streak = 10, bestStreak = 10, continuesUsedThisRun = 1, bestAtRunStart = 10))
    }

    @Test
    fun there_is_never_a_third_continue() {
        assertFalse(shouldOfferContinue(streak = 99, bestStreak = 99, continuesUsedThisRun = 2, bestAtRunStart = 1))
    }

    // ---- an ad that fails must never cost the player anything -----------------------------------

    @Test
    fun a_rewarded_ad_that_fails_to_show_goes_straight_to_the_fail_screen() = runTest(dispatcher) {
        val host = FakeAdHost(rewardedReady = true, outcome = RewardedOutcome.Unavailable)
        val viewModel = GameViewModel(InMemoryGameStore(), Random(42), { 0L }, host)
        advanceUntilIdle()

        val clock = FakeClock()
        repeat(8) { viewModel.playPerfectRound(clock) }
        viewModel.playFailedRound(clock)
        assertEquals("a run this close to the record earns the offer", Phase.ContinueOffer, viewModel.state.value.phase)

        viewModel.acceptContinue()
        advanceUntilIdle()
        assertEquals("the ad died; the game did not", Phase.Failed, viewModel.state.value.phase)
        assertEquals(8, viewModel.state.value.lastRunStreak)
    }

    @Test
    fun no_rewarded_ad_in_hand_means_no_offer_at_all() = runTest(dispatcher) {
        val host = FakeAdHost(rewardedReady = false, outcome = RewardedOutcome.Unavailable)
        val viewModel = GameViewModel(InMemoryGameStore(), Random(42), { 0L }, host)
        advanceUntilIdle()

        val clock = FakeClock()
        repeat(8) { viewModel.playPerfectRound(clock) }
        viewModel.playFailedRound(clock)
        assertEquals(Phase.Failed, viewModel.state.value.phase)
    }

    @Test
    fun a_watched_rewarded_ad_resumes_the_run_at_the_same_streak() = runTest(dispatcher) {
        val host = FakeAdHost(rewardedReady = true, outcome = RewardedOutcome.Earned)
        val viewModel = GameViewModel(InMemoryGameStore(), Random(42), { 0L }, host)
        advanceUntilIdle()

        val clock = FakeClock()
        repeat(8) { viewModel.playPerfectRound(clock) }
        viewModel.playFailedRound(clock)
        viewModel.acceptContinue()
        advanceUntilIdle()

        assertEquals(Phase.Idle, viewModel.state.value.phase)
        assertEquals("resumed at the same streak, on a fresh round", 8, viewModel.state.value.streak)
        assertEquals(1, viewModel.state.value.continuesUsedThisRun)
    }

    @Test
    fun an_interstitial_that_cannot_show_still_releases_the_round() {
        var released = false
        AdHost.None.showInterstitial { released = true }
        assertTrue("the round card must never strand the player", released)
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private class FakeAdHost(
        private val rewardedReady: Boolean,
        private val outcome: RewardedOutcome,
    ) : AdHost {
        override fun isRewardedReady(surface: RewardedSurface) = rewardedReady
        override fun showRewarded(surface: RewardedSurface, onOutcome: (RewardedOutcome) -> Unit) {
            onOutcome(outcome)
        }
        override fun isInterstitialReady() = false
        override fun showInterstitial(onFinished: () -> Unit) = onFinished()
    }

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
        onPointerUp(clock.advance((plan.perfectElapsedMs - plan.halfWindowMs - 5f).roundToLong()))
    }
}
