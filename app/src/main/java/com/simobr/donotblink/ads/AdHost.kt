package com.simobr.donotblink.ads

/** The five surfaces this app has. There will never be a sixth without a very good reason. */
enum class RewardedSurface { CONTINUE, PHOSPHOR, TITLE_REVEAL }

sealed interface RewardedOutcome {
    /** The player watched it through and the reward is theirs. */
    data object Earned : RewardedOutcome

    /** They closed it early. No scolding, no second prompt. */
    data object Dismissed : RewardedOutcome

    /** No ad, no fill, no consent, no network. Indistinguishable from dismissal to the game. */
    data object Unavailable : RewardedOutcome
}

/**
 * The seam between the game and AdMob.
 *
 * Every method must be safe to call at any time and must always call its callback. The game is
 * never blocked by an ad: if anything at all goes wrong the outcome is [RewardedOutcome.Unavailable]
 * and play continues exactly as if no ad existed.
 */
interface AdHost {
    /** True once consent allows requests and the SDK is up. False everywhere else. */
    val adsEnabled: Boolean get() = false

    /** Full-screen formats need a live Activity; the UI hands one over while it has one. */
    fun attachActivity(activity: android.app.Activity?) = Unit

    /** The splash's startup work: consent, then initialize, then preload. Never throws. */
    suspend fun start(activity: android.app.Activity) = Unit

    fun isRewardedReady(surface: RewardedSurface): Boolean
    fun showRewarded(surface: RewardedSurface, onOutcome: (RewardedOutcome) -> Unit)
    fun isInterstitialReady(): Boolean
    fun showInterstitial(onFinished: () -> Unit)

    /** UMP: whether this user must be offered a way back into the consent form. */
    fun isPrivacyOptionsRequired(): Boolean = false

    /** UMP: reopen the consent form. Always calls back, even when there is no form to show. */
    fun showPrivacyOptions(onDone: () -> Unit = {}) = onDone()

    companion object {
        /** The default everywhere: an app with no ads in it at all. */
        val None: AdHost = object : AdHost {
            override fun isRewardedReady(surface: RewardedSurface) = false
            override fun showRewarded(surface: RewardedSurface, onOutcome: (RewardedOutcome) -> Unit) {
                onOutcome(RewardedOutcome.Unavailable)
            }
            override fun isInterstitialReady() = false
            override fun showInterstitial(onFinished: () -> Unit) = onFinished()
        }
    }
}
