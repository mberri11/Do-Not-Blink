package com.simobr.donotblink.ads

/**
 * The session counters the interstitial gate reads. One instance per process lifetime; nothing
 * here is persisted, which is the point — "this session" means this session.
 */
class AdSession(private val nowEpochMs: () -> Long) {

    var runsThisSession: Int = 0
        private set
    var interstitialsThisSession: Int = 0
        private set
    var runsSinceLastInterstitial: Int = 0
        private set

    private var lastInterstitialAtMs: Long? = null
    private var previousRunWasPersonalBest = false
    private var previousRunUsedRewardedContinue = false

    fun onRunEnded(wasPersonalBest: Boolean, usedRewardedContinue: Boolean) {
        runsThisSession += 1
        runsSinceLastInterstitial += 1
        previousRunWasPersonalBest = wasPersonalBest
        previousRunUsedRewardedContinue = usedRewardedContinue
    }

    fun onInterstitialShown() {
        interstitialsThisSession += 1
        runsSinceLastInterstitial = 0
        lastInterstitialAtMs = nowEpochMs()
    }

    fun gate(): InterstitialGate = InterstitialGate(
        runsSinceLastInterstitial = runsSinceLastInterstitial,
        secondsSinceLastInterstitial = lastInterstitialAtMs
            ?.let { (nowEpochMs() - it) / 1000L }
            ?: Long.MAX_VALUE,
        runsThisSession = runsThisSession,
        previousRunWasPersonalBest = previousRunWasPersonalBest,
        previousRunUsedRewardedContinue = previousRunUsedRewardedContinue,
        interstitialsThisSession = interstitialsThisSession,
    )
}
