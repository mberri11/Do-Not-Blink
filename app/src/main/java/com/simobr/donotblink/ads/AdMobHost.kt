package com.simobr.donotblink.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope

/**
 * The real AdMob wiring. Everything here is written so that failure is indistinguishable from
 * "there are no ads": no exception escapes, every callback fires exactly once, and no method ever
 * makes the caller wait.
 */
class AdMobHost(
    private val context: Context,
    private val scope: CoroutineScope,
) : AdHost {

    /** False until consent says otherwise. Nothing is requested before this is true. */
    override var adsEnabled: Boolean = false
        private set

    private var activity: Activity? = null

    override fun attachActivity(activity: Activity?) {
        this.activity = activity
    }

    private var interstitial: PreloadedAd<InterstitialAd>? = null
    private val rewarded = mutableMapOf<RewardedSurface, PreloadedAd<RewardedAd>>()

    /**
     * The splash's startup work: consent first, initialize second, preload third. If consent is
     * unobtainable this returns having done nothing, and the app runs on with no ads.
     */
    override suspend fun start(activity: Activity) {
        val canRequestAds = runCatching { ConsentGate.gather(activity) }.getOrDefault(false)
        if (!canRequestAds) return

        runCatching { MobileAds.initialize(context) {} }.onFailure { return }
        adsEnabled = true
        preload()
    }

    private fun preload() {
        interstitial = PreloadedAd(scope, AdIds.interstitial) { onLoaded, onFailed ->
            runCatching {
                InterstitialAd.load(
                    context,
                    AdIds.interstitial,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) = onLoaded(ad)
                        override fun onAdFailedToLoad(error: LoadAdError) = onFailed()
                    },
                )
            }.onFailure { onFailed() }
        }.also { it.ensureLoaded() }

        RewardedSurface.entries.forEach { surface ->
            val unitId = AdIds.rewarded(surface)
            rewarded[surface] = PreloadedAd(scope, unitId) { onLoaded, onFailed ->
                runCatching {
                    RewardedAd.load(
                        context,
                        unitId,
                        AdRequest.Builder().build(),
                        object : RewardedAdLoadCallback() {
                            override fun onAdLoaded(ad: RewardedAd) = onLoaded(ad)
                            override fun onAdFailedToLoad(error: LoadAdError) = onFailed()
                        },
                    )
                }.onFailure { onFailed() }
            }.also { it.ensureLoaded() }
        }
    }

    override fun isRewardedReady(surface: RewardedSurface): Boolean =
        adsEnabled && rewarded[surface]?.isReady == true

    override fun showRewarded(surface: RewardedSurface, onOutcome: (RewardedOutcome) -> Unit) {
        val activity = this.activity
        val ad = rewarded[surface]?.consume()
        if (!adsEnabled || activity == null || ad == null) {
            onOutcome(RewardedOutcome.Unavailable)
            return
        }

        var earned = false
        var settled = false
        fun settle(outcome: RewardedOutcome) {
            if (settled) return
            settled = true
            onOutcome(outcome)
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                settle(if (earned) RewardedOutcome.Earned else RewardedOutcome.Dismissed)
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                settle(RewardedOutcome.Unavailable)
            }
        }

        runCatching { ad.show(activity) { earned = true } }
            .onFailure { settle(RewardedOutcome.Unavailable) }
    }

    /**
     * Asked once after startup, from the attached Activity. UMP needs an Activity context and
     * there is nothing to ask before [start] has run its consent round trip.
     */
    override fun isPrivacyOptionsRequired(): Boolean {
        val activity = this.activity ?: return false
        return ConsentGate.privacyOptionsRequired(activity)
    }

    override fun showPrivacyOptions(onDone: () -> Unit) {
        val activity = this.activity
        if (activity == null) {
            onDone()
            return
        }
        ConsentGate.showPrivacyOptions(activity, onDone)
    }

    override fun isInterstitialReady(): Boolean = adsEnabled && interstitial?.isReady == true

    override fun showInterstitial(onFinished: () -> Unit) {
        val activity = this.activity
        val ad = interstitial?.consume()
        if (!adsEnabled || activity == null || ad == null) {
            onFinished()
            return
        }

        var settled = false
        fun settle() {
            if (settled) return
            settled = true
            onFinished()
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = settle()
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) = settle()
        }

        runCatching { ad.show(activity) }.onFailure { settle() }
    }
}
