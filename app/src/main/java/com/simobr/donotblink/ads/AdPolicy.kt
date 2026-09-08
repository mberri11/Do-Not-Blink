package com.simobr.donotblink.ads

/**
 * When an ad may be shown. Pure functions over plain data — no Android, no SDK, no clock.
 *
 * This is the highest-risk logic in the app: an interstitial cadence that feels punitive gets the
 * app buried under a Disruptive Ads policy strike, and no amount of polish elsewhere survives that.
 */

/** Everything the interstitial gate is allowed to look at. */
data class InterstitialGate(
    val runsSinceLastInterstitial: Int,
    val secondsSinceLastInterstitial: Long,
    val runsThisSession: Int,
    val previousRunWasPersonalBest: Boolean,
    val previousRunUsedRewardedContinue: Boolean,
    val interstitialsThisSession: Int,
)

/**
 * Tightened from 3/90s/4/6 (Stage 6) at Simo's request for the v1.2.0 production build: more
 * impressions, same shape of gate. Every exclusion below is untouched — a personal best and a
 * rewarded continue are still never taxed, because THOSE are what a Disruptive Ads strike is
 * actually about, not the raw numbers here.
 */
const val MIN_RUNS_BETWEEN_INTERSTITIALS = 2
const val MIN_SECONDS_BETWEEN_INTERSTITIALS = 60L
const val CLEAN_RUNS_AT_SESSION_START = 3
const val MAX_INTERSTITIALS_PER_SESSION = 8

/**
 * Every clause has to hold. The run count alone is not a cadence: runs here can end in eight
 * seconds, so "every 2 runs" unaccompanied would be an interstitial every twenty seconds — a
 * retention catastrophe and a policy problem. The seconds clause is what actually paces this;
 * the run clause only stops an ad landing on two consecutive fails.
 */
fun shouldShowInterstitial(gate: InterstitialGate): Boolean =
    gate.runsSinceLastInterstitial >= MIN_RUNS_BETWEEN_INTERSTITIALS &&
        gate.secondsSinceLastInterstitial >= MIN_SECONDS_BETWEEN_INTERSTITIALS &&
        gate.runsThisSession >= CLEAN_RUNS_AT_SESSION_START &&
        !gate.previousRunWasPersonalBest &&
        !gate.previousRunUsedRewardedContinue &&
        gate.interstitialsThisSession < MAX_INTERSTITIALS_PER_SESSION

/**
 * Was 8, gated further by `streak >= bestStreak - 3` — the offer only ever appeared on a run
 * already near the personal best. Simo's ask for v1.2.0: offer it in every case, not just the good
 * ones. Dropped to the smallest floor that still means something: at streak 0 there is nothing yet
 * to protect — a continue would resume at streak 0, identical to AGAIN, for the price of an ad
 * watched for no benefit. One perfect release is the line past which continuing is worth anything.
 */
const val CONTINUE_MIN_STREAK = 1
const val MAX_CONTINUES_PER_RUN = 2

/**
 * The offer no longer looks at how the run compares to the personal best — see
 * [CONTINUE_MIN_STREAK].
 *
 * This answers only "is this run worth offering for". Whether a rewarded ad actually exists to show
 * is a separate question, asked alongside this one at the call site, so a player with no fill never
 * sees the offer however good the run was.
 *
 * @param streak the streak the run reached.
 * @param continuesUsedThisRun how many rewarded continues this run has already spent.
 * @param bestAtRunStart the personal best the run began with — the record a second continue has
 *   to have beaten.
 */
fun shouldOfferContinue(
    streak: Int,
    continuesUsedThisRun: Int,
    bestAtRunStart: Int,
): Boolean = when {
    continuesUsedThisRun >= MAX_CONTINUES_PER_RUN -> false

    // The second continue stays rare on purpose: only once the run has passed the old record.
    // This is a different, deliberately-tighter rule from the first continue's floor above, not
    // an oversight — a run already extended once needs to have earned the second extension.
    continuesUsedThisRun == 1 -> streak > bestAtRunStart

    else -> streak >= CONTINUE_MIN_STREAK
}

/**
 * Whether SETTINGS shows the PRIVACY OPTIONS row.
 *
 * UMP reports REQUIRED for every user who has been through a consent form — in practice the EEA
 * and the UK. Outside that, there is no form to reopen and the row must not exist at all: a row
 * labelled OPEN that opens nothing is worse than no row.
 */
fun showsPrivacyOptionsRow(privacyOptionsRequired: Boolean): Boolean = privacyOptionsRequired
