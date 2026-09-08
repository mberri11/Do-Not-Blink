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
        runsSinceLastInterstitial = 2,
        secondsSinceLastInterstitial = 60,
        runsThisSession = 3,
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
        assertFalse(shouldShowInterstitial(allowed.copy(runsSinceLastInterstitial = 1)))
        assertTrue(shouldShowInterstitial(allowed.copy(runsSinceLastInterstitial = 2)))
    }

    @Test
    fun clause_seconds_since_last_interstitial() {
        assertFalse(shouldShowInterstitial(allowed.copy(secondsSinceLastInterstitial = 59)))
        assertTrue(shouldShowInterstitial(allowed.copy(secondsSinceLastInterstitial = 60)))
    }

    @Test
    fun clause_first_runs_of_a_session_are_always_clean() {
        assertFalse(shouldShowInterstitial(allowed.copy(runsThisSession = 2)))
        assertTrue(shouldShowInterstitial(allowed.copy(runsThisSession = 3)))
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
        assertFalse(shouldShowInterstitial(allowed.copy(interstitialsThisSession = 8)))
        assertTrue(shouldShowInterstitial(allowed.copy(interstitialsThisSession = 7)))
    }

    @Test
    fun the_session_counters_feed_the_gate() {
        var now = 1_000_000L
        val session = AdSession { now }

        repeat(3) { session.onRunEnded(wasPersonalBest = false, usedRewardedContinue = false) }
        assertTrue("three clean runs and no interstitial yet", shouldShowInterstitial(session.gate()))

        session.onInterstitialShown()
        assertFalse("straight after one, everything is blocked", shouldShowInterstitial(session.gate()))

        repeat(2) { session.onRunEnded(wasPersonalBest = false, usedRewardedContinue = false) }
        assertFalse("two runs is not enough without the sixty seconds", shouldShowInterstitial(session.gate()))

        now += 60_000L
        assertTrue("two runs and sixty seconds", shouldShowInterstitial(session.gate()))
    }

    // ---- the second interstitial site: a finished reflex sitting ---------------------------------

    @Test
    fun a_reflex_sitting_feeds_the_same_cadence_as_a_run() {
        var now = 1_000_000L
        val session = AdSession { now }

        // Three sittings and nothing else is enough to open the gate, exactly as three runs are.
        repeat(3) { session.onSideActivityEnded() }
        assertTrue(shouldShowInterstitial(session.gate()))
    }

    @Test
    fun a_reflex_sitting_does_not_cancel_a_personal_best_s_protection() {
        var now = 1_000_000L
        val session = AdSession { now }

        repeat(3) { session.onRunEnded(wasPersonalBest = false, usedRewardedContinue = false) }
        session.onRunEnded(wasPersonalBest = true, usedRewardedContinue = false)
        assertFalse("the best itself is protected", shouldShowInterstitial(session.gate()))

        // Wandering into the reflex test and finishing a sitting must not clear that protection.
        // Routing this through onRunEnded(false, false) would have done exactly that.
        session.onSideActivityEnded()
        assertFalse(
            "a personal best still protects the run after it, whatever happened in between",
            shouldShowInterstitial(session.gate()),
        )
    }

    @Test
    fun a_reflex_sitting_does_not_cancel_a_rewarded_continue_s_protection() {
        var now = 1_000_000L
        val session = AdSession { now }

        repeat(3) { session.onRunEnded(wasPersonalBest = false, usedRewardedContinue = false) }
        session.onRunEnded(wasPersonalBest = false, usedRewardedContinue = true)
        session.onSideActivityEnded()
        assertFalse(
            "a player who just watched a rewarded ad is not shown an interstitial next",
            shouldShowInterstitial(session.gate()),
        )
    }

    // ---- the continue offer ---------------------------------------------------------------------
    // v1.2.0: the offer no longer looks at proximity to the personal best — Simo's ask was for it
    // to appear "in all cases", not just on a run that was already good. The one floor kept is
    // streak >= 1: at streak 0 there is nothing to protect, and continuing would be identical to
    // AGAIN for the price of a watched ad the player gets nothing for.

    @Test
    fun continue_is_offered_the_moment_there_is_something_to_protect() {
        assertTrue(shouldOfferContinue(streak = 1, continuesUsedThisRun = 0, bestAtRunStart = 50))
    }

    @Test
    fun continue_is_offered_regardless_of_distance_from_the_personal_best() {
        // Streak 1 against a personal best of 500 used to fail the old margin check outright.
        // It must be offered now — that check is gone, not merely widened.
        assertTrue(shouldOfferContinue(streak = 1, continuesUsedThisRun = 0, bestAtRunStart = 500))
    }

    @Test
    fun continue_is_not_offered_at_streak_zero() {
        // Nothing was ever landed, so a continue would resume at streak 0 — identical to AGAIN.
        assertFalse(shouldOfferContinue(streak = 0, continuesUsedThisRun = 0, bestAtRunStart = 0))
    }

    @Test
    fun the_second_continue_needs_the_old_record_beaten() {
        // Used one already, and the run has passed the record it started with.
        assertTrue(shouldOfferContinue(streak = 12, continuesUsedThisRun = 1, bestAtRunStart = 10))
        // Used one already, but the run has not passed it.
        assertFalse(shouldOfferContinue(streak = 9, continuesUsedThisRun = 1, bestAtRunStart = 10))
        // Equalling the old record is not passing it.
        assertFalse(shouldOfferContinue(streak = 10, continuesUsedThisRun = 1, bestAtRunStart = 10))
    }

    @Test
    fun there_is_never_a_third_continue() {
        assertFalse(shouldOfferContinue(streak = 99, continuesUsedThisRun = 2, bestAtRunStart = 1))
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
        assertEquals("a failed run with a streak on it earns the offer", Phase.ContinueOffer, viewModel.state.value.phase)

        viewModel.acceptContinue()
        advanceUntilIdle()
        assertEquals("the ad died; the game did not", Phase.Failed, viewModel.state.value.phase)
        assertEquals(8, viewModel.state.value.lastRunStreak)
    }

    /**
     * The v1.2.0 behaviour change, end to end rather than as a pure function: one perfect release
     * and then a fail is now enough. Under the old policy this run — streak 1, against a stored
     * best of 40 — failed both the streak floor of 8 and the `best - 3` proximity check, and the
     * player was sent straight to the fail screen with no offer at all.
     */
    @Test
    fun a_single_perfect_release_is_now_enough_to_earn_the_offer() = runTest(dispatcher) {
        val host = FakeAdHost(rewardedReady = true, outcome = RewardedOutcome.Earned)
        val viewModel = GameViewModel(InMemoryGameStore(bestStreak = 40), Random(42), { 0L }, host)
        advanceUntilIdle()

        val clock = FakeClock()
        viewModel.playPerfectRound(clock)
        viewModel.playFailedRound(clock)

        assertEquals(Phase.ContinueOffer, viewModel.state.value.phase)
        assertEquals(1, viewModel.state.value.lastRunStreak)
    }

    /** The one floor kept: nothing landed yet means nothing to protect. */
    @Test
    fun a_run_that_never_landed_a_release_gets_no_offer() = runTest(dispatcher) {
        val host = FakeAdHost(rewardedReady = true, outcome = RewardedOutcome.Earned)
        val viewModel = GameViewModel(InMemoryGameStore(), Random(42), { 0L }, host)
        advanceUntilIdle()

        viewModel.playFailedRound(FakeClock())

        assertEquals("continuing at streak 0 is just AGAIN", Phase.Failed, viewModel.state.value.phase)
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

    // ---- the UMP privacy-options entry point (Stage 6, item 4) --------------------------------

    /**
     * `privacyOptionsRequired()` existed since Stage 5 and was called from nowhere, and
     * `showPrivacyOptionsForm` was never called at all. An app that showed a consent form and
     * offers no way back into it is out of compliance with Google's EU user consent policy — a
     * live enforcement surface for the whole ads account, not a missing nicety.
     */
    @Test
    fun the_privacy_options_row_exists_exactly_where_ump_says_it_is_required() {
        assertTrue(showsPrivacyOptionsRow(privacyOptionsRequired = true))
        assertFalse(showsPrivacyOptionsRow(privacyOptionsRequired = false))
    }

}
