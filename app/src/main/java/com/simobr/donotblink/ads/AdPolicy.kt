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

const val MIN_RUNS_BETWEEN_INTERSTITIALS = 3
const val MIN_SECONDS_BETWEEN_INTERSTITIALS = 90L
const val CLEAN_RUNS_AT_SESSION_START = 4
const val MAX_INTERSTITIALS_PER_SESSION = 6

/**
 * "Every 3 runs" would mean an interstitial every 30 seconds for a good player — a retention
 * catastrophe and a policy problem. Every clause below has to hold.
 */
fun shouldShowInterstitial(gate: InterstitialGate): Boolean =
    gate.runsSinceLastInterstitial >= MIN_RUNS_BETWEEN_INTERSTITIALS &&
        gate.secondsSinceLastInterstitial >= MIN_SECONDS_BETWEEN_INTERSTITIALS &&
        gate.runsThisSession >= CLEAN_RUNS_AT_SESSION_START &&
        !gate.previousRunWasPersonalBest &&
        !gate.previousRunUsedRewardedContinue &&
        gate.interstitialsThisSession < MAX_INTERSTITIALS_PER_SESSION

const val CONTINUE_MIN_STREAK = 8
const val CONTINUE_BEST_MARGIN = 3
const val MAX_CONTINUES_PER_RUN = 2

/**
 * The offer only lands when the run was actually worth something. A flat "streak >= 10" offers the
 * ad while the run is still cheap, and opt-in stays low.
 *
 * @param streak the streak the run reached.
 * @param bestStreak the personal best as it stands now.
 * @param continuesUsedThisRun how many rewarded continues this run has already spent.
 * @param bestAtRunStart the personal best the run began with — the record a second continue has
 *   to have beaten.
 */
fun shouldOfferContinue(
    streak: Int,
    bestStreak: Int,
    continuesUsedThisRun: Int,
    bestAtRunStart: Int,
): Boolean = when {
    continuesUsedThisRun >= MAX_CONTINUES_PER_RUN -> false

    // The second continue is rare on purpose: only once the run has passed the old record.
    continuesUsedThisRun == 1 -> streak > bestAtRunStart

    else -> streak >= CONTINUE_MIN_STREAK && streak >= bestStreak - CONTINUE_BEST_MARGIN
}
