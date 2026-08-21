package com.simobr.donotblink.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.simobr.donotblink.data.DataStoreGameStore
import com.simobr.donotblink.data.GameStore
import com.simobr.donotblink.ads.AdHost
import com.simobr.donotblink.ads.AdMobHost
import com.simobr.donotblink.ads.AdSession
import com.simobr.donotblink.ads.RewardedOutcome
import com.simobr.donotblink.ads.RewardedSurface
import com.simobr.donotblink.ads.shouldOfferContinue
import com.simobr.donotblink.ads.shouldShowInterstitial
import com.simobr.donotblink.data.InMemoryGameStore
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The whole state machine:
 *
 *   Idle -> Holding -> (Perfect -> 540ms -> Idle, armed) | Failed
 *   Failed -> ContinueOffer (conditional, Stage 5) | Results
 *
 * "Holding-ready" is [Phase.Idle] carrying a non-zero streak: the next round is planned and the
 * ring is sitting at its start radius, waiting for a press.
 *
 * NO GAME CLOCK IS READ HERE. Every entry point takes the timestamp of the thing that happened —
 * the pointer event's own `uptimeMillis` for down/up, the frame time for [onFrame]. That is what
 * makes the judgement exact and the unit test a pure function of its inputs. (The wall clock used
 * for `firstLaunchEpoch` is injected, and is bookkeeping, not timing.)
 */
enum class Phase { Idle, Holding, Perfect, Failed, ContinueOffer, Results }

data class PlayState(
    val phase: Phase,
    val streak: Int,
    val best: Int,
    val roundId: Long,
    /** The round that is armed (Idle) or running (Holding), or the one just finished. */
    val plan: RoundPlan,
    val roundStartUptimeMs: Long,
    val lastRelease: Release?,
    /** Radius the ring died at. Only meaningful in [Phase.Failed] and beyond. */
    val deadRadiusDp: Float,
    /** uptimeMillis of the perfect release the flash is playing from. */
    val perfectAtUptimeMs: Long,
    /** uptimeMillis at which a [Phase.Perfect] hands back to an armed [Phase.Idle]. */
    val armedAtUptimeMs: Long,
    val hapticsEnabled: Boolean,
    val soundEnabled: Boolean,
    /** The streak the run that just ended reached. The fail screen's numeral. */
    val lastRunStreak: Int,
    /** Titles the run that just ended earned. They animate on the fail screen and nowhere else. */
    val newlyUnlockedTitles: List<Title>,
    /** Whether the locked names on the titles screen have been paid for. */
    val titlesRevealed: Boolean,
    /** Rewarded continues this run has spent. Two is the hard ceiling. */
    val continuesUsedThisRun: Int,
    val selectedPhosphorId: String,
    val unlockedPaletteIds: Set<String>,
    val lifetimeRuns: Int,
)

sealed interface GameEvent {
    data class Perfect(val streak: Int) : GameEvent
    data class Fail(val streak: Int) : GameEvent
}

class GameViewModel(
    private val store: GameStore = InMemoryGameStore(),
    private val random: Random = Random.Default,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val adHost: AdHost = AdHost.None,
    private val adSession: AdSession = AdSession(nowEpochMs),
) : ViewModel() {

    private val _state = MutableStateFlow(
        PlayState(
            phase = Phase.Idle,
            streak = 0,
            best = 0,
            roundId = 0L,
            plan = Difficulty.planRound(streak = 0, seed = random.nextLong()),
            roundStartUptimeMs = 0L,
            lastRelease = null,
            deadRadiusDp = Difficulty.START_RADIUS_DP,
            perfectAtUptimeMs = 0L,
            armedAtUptimeMs = 0L,
            hapticsEnabled = true,
            soundEnabled = true,
            lastRunStreak = 0,
            newlyUnlockedTitles = emptyList(),
            titlesRevealed = false,
            continuesUsedThisRun = 0,
            selectedPhosphorId = "",
            unlockedPaletteIds = emptySet(),
            lifetimeRuns = 0,
        )
    )
    val state: StateFlow<PlayState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var roundClock: RoundClock? = null
    private var runInProgress = false
    private var bestAtRunStart = 0

    init {
        viewModelScope.launch {
            store.bestStreak.collect { stored -> _state.update { it.copy(best = stored) } }
        }
        viewModelScope.launch {
            store.hapticsEnabled.collect { enabled -> _state.update { it.copy(hapticsEnabled = enabled) } }
        }
        viewModelScope.launch {
            store.soundEnabled.collect { enabled -> _state.update { it.copy(soundEnabled = enabled) } }
        }
        viewModelScope.launch {
            store.titlesRevealed.collect { revealed -> _state.update { it.copy(titlesRevealed = revealed) } }
        }
        viewModelScope.launch {
            store.selectedPhosphorId.collect { id -> _state.update { it.copy(selectedPhosphorId = id) } }
        }
        viewModelScope.launch {
            store.unlockedPaletteIds.collect { ids -> _state.update { it.copy(unlockedPaletteIds = ids) } }
        }
        viewModelScope.launch {
            store.lifetimeRuns.collect { runs -> _state.update { it.copy(lifetimeRuns = runs) } }
        }
        viewModelScope.launch { store.recordFirstLaunch(nowEpochMs()) }
    }

    /** DOWN. The ring is contracting on this very frame — no start animation, no delay. */
    fun onPointerDown(downUptimeMs: Long) {
        val current = _state.value
        if (current.phase != Phase.Idle) return
        if (!runInProgress) {
            runInProgress = true
            bestAtRunStart = current.best
        }
        roundClock = RoundClock(current.plan, downUptimeMs)
        _state.value = current.copy(phase = Phase.Holding, roundStartUptimeMs = downUptimeMs)
    }

    /** UP. Judged from this pointer's own timestamp, never from a frame. */
    fun onPointerUp(upUptimeMs: Long) {
        val current = _state.value
        val clock = roundClock
        if (current.phase != Phase.Holding || clock == null) return

        val release = clock.judgeRelease(upUptimeMs)
        roundClock = null

        if (release.verdict == Verdict.PERFECT) {
            val streak = current.streak + 1
            _state.value = current.copy(
                phase = Phase.Perfect,
                streak = streak,
                best = maxOf(current.best, streak),
                lastRelease = release,
                deadRadiusDp = release.radiusAtReleaseDp,
                perfectAtUptimeMs = upUptimeMs,
                armedAtUptimeMs = upUptimeMs + FLASH_MS + QUIET_MS,
            )
            if (streak > current.best) {
                viewModelScope.launch { store.setBestStreak(streak) }
            }
            _events.tryEmit(GameEvent.Perfect(streak))
        } else {
            fail(current, release.radiusAtReleaseDp, release)
        }
    }

    /**
     * CANCEL: the shade came down, a call arrived, a system gesture stole the touch. Abort the
     * round, do NOT count a fail, keep the streak. Losing a 40-streak to a notification banner is
     * the fastest way to earn a one-star review on a game like this.
     */
    fun onPointerCancel() {
        val current = _state.value
        if (current.phase != Phase.Holding) return
        roundClock = null
        armNextRound(streak = current.streak)
    }

    /**
     * One frame. Drives only the coarse transitions — the overshoot fail and the end of the
     * perfect flash. Nothing precise is decided here.
     */
    fun onFrame(nowUptimeMs: Long) {
        val current = _state.value
        when (current.phase) {
            Phase.Holding -> {
                val clock = roundClock ?: return
                if (clock.hasOvershotAt(nowUptimeMs)) {
                    roundClock = null
                    fail(current, clock.radiusDpAt(nowUptimeMs), release = null)
                }
            }
            Phase.Perfect -> if (nowUptimeMs >= current.armedAtUptimeMs) armNextRound(current.streak)
            else -> Unit
        }
    }

    /** Failed -> Results. Stage 5 owns the ContinueOffer branch and its condition. */
    fun onFailAcknowledged() {
        val current = _state.value
        if (current.phase == Phase.Failed) _state.value = current.copy(phase = Phase.Results)
    }

    /** AGAIN, or arriving at Home: a fresh run, armed and waiting for a press. */
    fun startNewRun() {
        runInProgress = false
        _state.update {
            it.copy(
                phase = Phase.Idle,
                streak = 0,
                roundId = it.roundId + 1,
                plan = Difficulty.planRound(streak = 0, seed = random.nextLong()),
                roundStartUptimeMs = 0L,
                lastRelease = null,
                deadRadiusDp = Difficulty.START_RADIUS_DP,
                perfectAtUptimeMs = 0L,
                armedAtUptimeMs = 0L,
                newlyUnlockedTitles = emptyList(),
                continuesUsedThisRun = 0,
            )
        }
        roundClock = null
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch { store.setHapticsEnabled(enabled) }
    }

    fun setSound(enabled: Boolean) {
        viewModelScope.launch { store.setSoundEnabled(enabled) }
    }

    /** Settings: RESET BEST. The best streak is the entire unlock state, so this relocks titles. */
    fun resetBest() {
        _state.update { it.copy(best = 0) }
        viewModelScope.launch { store.setBestStreak(0) }
    }

    /** The ring's radius at [nowUptimeMs], for rendering only. Judging never uses this. */
    fun ringRadiusDpAt(nowUptimeMs: Long): Float {
        val current = _state.value
        return when (current.phase) {
            Phase.Idle -> current.plan.startRadiusDp
            Phase.Holding -> {
                val clock = roundClock ?: return current.plan.startRadiusDp
                val floor = current.plan.targetRadiusDp - current.plan.overshootFailDp
                clock.radiusDpAt(nowUptimeMs).coerceAtLeast(floor)
            }
            Phase.Perfect, Phase.Failed, Phase.ContinueOffer, Phase.Results -> current.deadRadiusDp
        }
    }

    private fun fail(current: PlayState, deadRadiusDp: Float, release: Release?) {
        val offerContinue = adHost.isRewardedReady(RewardedSurface.CONTINUE) &&
            shouldOfferContinue(
                streak = current.streak,
                bestStreak = current.best,
                continuesUsedThisRun = current.continuesUsedThisRun,
                bestAtRunStart = bestAtRunStart,
            )

        _state.value = current.copy(
            phase = if (offerContinue) Phase.ContinueOffer else Phase.Failed,
            streak = 0,
            lastRelease = release,
            deadRadiusDp = deadRadiusDp,
            lastRunStreak = current.streak,
        )
        _events.tryEmit(GameEvent.Fail(current.streak))

        if (!offerContinue) endRun()
    }

    /**
     * The run is genuinely over. Everything that counts a run — the ladder, the lifetime tally, the
     * interstitial cadence — happens here and only here, so a rewarded continue does not bank one.
     */
    private fun endRun() {
        val current = _state.value
        val wasPersonalBest = current.best > bestAtRunStart
        runInProgress = false
        _state.update {
            it.copy(
                phase = Phase.Failed,
                newlyUnlockedTitles = titlesUnlockedBetween(bestAtRunStart, it.best),
            )
        }
        adSession.onRunEnded(
            wasPersonalBest = wasPersonalBest,
            usedRewardedContinue = current.continuesUsedThisRun > 0,
        )
        viewModelScope.launch { store.incrementLifetimeRuns() }
    }

    /** WATCH TO CONTINUE. Any outcome other than a completed view goes straight to the fail screen. */
    fun acceptContinue() {
        if (_state.value.phase != Phase.ContinueOffer) return
        adHost.showRewarded(RewardedSurface.CONTINUE) { outcome ->
            if (outcome == RewardedOutcome.Earned) resumeAfterContinue() else endRun()
        }
    }

    /** ACCEPT THE BLINK. No scolding, no second prompt. */
    fun declineContinue() {
        if (_state.value.phase != Phase.ContinueOffer) return
        endRun()
    }

    private fun resumeAfterContinue() {
        val current = _state.value
        _state.value = current.copy(
            phase = Phase.Idle,
            streak = current.lastRunStreak,
            roundId = current.roundId + 1,
            plan = Difficulty.planRound(current.lastRunStreak, random.nextLong()),
            roundStartUptimeMs = 0L,
            lastRelease = null,
            deadRadiusDp = Difficulty.START_RADIUS_DP,
            perfectAtUptimeMs = 0L,
            armedAtUptimeMs = 0L,
            continuesUsedThisRun = current.continuesUsedThisRun + 1,
        )
        roundClock = null
        runInProgress = true
    }

    /** The ROUND card asks this. Both halves must agree: policy first, then an ad actually in hand. */
    fun shouldShowInterstitialNow(): Boolean =
        adHost.isInterstitialReady() && shouldShowInterstitial(adSession.gate())

    fun showInterstitial(onFinished: () -> Unit) {
        adSession.onInterstitialShown()
        adHost.showInterstitial(onFinished)
    }

    /** Whether an ad may be requested at all. The banner asks before it exists. */
    val adsEnabled: Boolean get() = adHost.adsEnabled

    fun attachAdActivity(activity: android.app.Activity?) = adHost.attachActivity(activity)

    private val _adStartupDone = MutableStateFlow(false)
    private var adStartupBegun = false

    /**
     * Consent, initialize, preload — owned by the view model, NOT by the splash.
     *
     * The splash waits on [awaitAdStartup] and gives up at its ceiling, but this job keeps running:
     * on a real device the UMP round trip took seven seconds, and cancelling it there would have
     * left the session with no ads at all.
     */
    fun beginAdStartup(activity: android.app.Activity) {
        if (adStartupBegun) return
        adStartupBegun = true
        viewModelScope.launch {
            runCatching { adHost.start(activity) }
            _adStartupDone.value = true
        }
    }

    suspend fun awaitAdStartup() {
        _adStartupDone.first { it }
    }

    fun selectPhosphor(id: String) {
        viewModelScope.launch { store.setSelectedPhosphorId(id) }
    }

    /** One rewarded ad, one permanent unlock. Nothing about the run changes, ever. */
    fun purchasePhosphor(id: String, onResult: (Boolean) -> Unit = {}) {
        adHost.showRewarded(RewardedSurface.PHOSPHOR) { outcome ->
            val earned = outcome == RewardedOutcome.Earned
            if (earned) {
                viewModelScope.launch {
                    store.unlockPalette(id)
                    store.setSelectedPhosphorId(id)
                }
            }
            onResult(earned)
        }
    }

    fun purchaseTitleReveal(onResult: (Boolean) -> Unit = {}) {
        adHost.showRewarded(RewardedSurface.TITLE_REVEAL) { outcome ->
            val earned = outcome == RewardedOutcome.Earned
            if (earned) viewModelScope.launch { store.setTitlesRevealed(true) }
            onResult(earned)
        }
    }

    private fun armNextRound(streak: Int) {
        val current = _state.value
        _state.value = current.copy(
            phase = Phase.Idle,
            streak = streak,
            roundId = current.roundId + 1,
            plan = Difficulty.planRound(streak, random.nextLong()),
            roundStartUptimeMs = 0L,
            lastRelease = null,
            perfectAtUptimeMs = 0L,
            armedAtUptimeMs = 0L,
        )
    }

    companion object {
        /** The perfect flash. */
        const val FLASH_MS = 120L

        /** Nothing at all, after the flash, before the next round is armed. */
        const val QUIET_MS = 420L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("GameViewModel needs an Application in CreationExtras")
                // The host outlives the Activity so a rotation does not throw away a preloaded ad.
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                GameViewModel(
                    store = DataStoreGameStore(application),
                    adHost = AdMobHost(application, scope),
                )
            }
        }
    }
}
