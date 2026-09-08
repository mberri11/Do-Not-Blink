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

    /**
     * A completed activity that is not an endless run — today, a finished reflex sitting.
     *
     * It feeds the same two counters, so one cadence paces the whole app rather than each mode
     * inventing its own quota. It deliberately does NOT touch [previousRunWasPersonalBest] or
     * [previousRunUsedRewardedContinue]: those describe the last RUN, and a reflex sitting neither
     * earns nor spends them. Routing this through [onRunEnded] with `false, false` would have
     * silently cancelled the protection a personal best had just bought — play a blinder, wander
     * into the reflex test, come back and get taxed anyway.
     */
    fun onSideActivityEnded() {
        runsThisSession += 1
        runsSinceLastInterstitial += 1
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
