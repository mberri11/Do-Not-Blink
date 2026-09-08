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
import java.time.LocalDate
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
enum class Phase { Idle, Holding, Perfect, Failed, ContinueOffer, Results, DailyDone }

/**
 * Which game is being played on the one play surface.
 *
 * [Daily] reuses every part of [Endless] that matters — the gesture, the frame loop, the hum, the
 * judging — and changes only what a miss means. Duplicating the real-time half of this class to get
 * a second mode would have been the riskiest possible way to add one.
 */
enum class Mode { Endless, Daily }

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
    /** UMP: this user must be offered a way back into the consent form. EEA/UK only. */
    val privacyOptionsRequired: Boolean,
    /**
     * Whether an ad may be requested. False until the consent+init round trip finishes — on a
     * real device that took seven seconds, so a player who fails fast can reach the fail screen
     * before this flips. It lives in [PlayState], not a bare property read once at composition,
     * so the banner appears the instant readiness changes rather than being stuck at whatever it
     * was the moment Screen.Fail first composed.
     */
    val adsEnabled: Boolean,

    // ---- daily trial -------------------------------------------------------------------------
    val mode: Mode,
    /** Which of [DailyTrial.RINGS] is armed or running. Meaningless outside [Mode.Daily]. */
    val dailyRingIndex: Int,
    /** One entry per finished ring of the trial in progress, in ring order. */
    val dailyMarks: List<Boolean>,
    /** Signed errors of the trial's rings that actually produced a release. */
    val dailyErrorsMs: List<Float>,
    /** The last trial this device finished, on any day. */
    val dailyResult: DailyResult?,
    /** True once today's trial is spent. One attempt per day is the whole point. */
    val dailyPlayedToday: Boolean,
    val dailyDayStreak: Int,
    val dailyBestHits: Int,

    /** Fastest reflex-test reaction ever recorded, in ms. 0 means never played. */
    val twitchBestMs: Int,
) {
    /** Hits so far in the trial in progress. The numeral on the play surface in [Mode.Daily]. */
    val dailyHits: Int get() = dailyMarks.count { it }
}

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
    /**
     * Today's date as a day number. Injected for the same reason [nowEpochMs] is: it is calendar
     * bookkeeping, never timing, and a test must be able to cross midnight on demand.
     */
    private val todayEpochDay: () -> Long = { LocalDate.now().toEpochDay() },
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
            privacyOptionsRequired = false,
            adsEnabled = false,
            mode = Mode.Endless,
            dailyRingIndex = 0,
            dailyMarks = emptyList(),
            dailyErrorsMs = emptyList(),
            dailyResult = null,
            dailyPlayedToday = false,
            dailyDayStreak = 0,
            dailyBestHits = 0,
            twitchBestMs = 0,
        )
    )
    val state: StateFlow<PlayState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private var roundClock: RoundClock? = null
    private var runInProgress = false
    private var bestAtRunStart = 0

    /**
     * The ten rounds of the trial in progress, and the day they belong to.
     *
     * They live here rather than in [PlayState] because they are fixed for the whole trial: putting
     * ten [RoundPlan]s in a state object that is copied on every judgement would copy them all every
     * time for nothing.
     */
    private var dailyPlans: List<RoundPlan> = emptyList()
    private var dailyEpochDay = 0L

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
        viewModelScope.launch {
            store.lastDailyResult.collect { result ->
                _state.update {
                    it.copy(
                        dailyResult = result,
                        dailyPlayedToday = result != null && result.epochDay == todayEpochDay(),
                    )
                }
            }
        }
        viewModelScope.launch {
            store.dailyDayStreak.collect { days -> _state.update { it.copy(dailyDayStreak = days) } }
        }
        viewModelScope.launch {
            store.dailyBestHits.collect { hits -> _state.update { it.copy(dailyBestHits = hits) } }
        }
        viewModelScope.launch {
            store.twitchBestMs.collect { ms -> _state.update { it.copy(twitchBestMs = ms) } }
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
            // A daily hit does not touch the best streak or the title ladder: the trial is a
            // separate contest against a fixed set of ten rings, and letting it feed the endless
            // ladder would make the ladder mean two different things.
            if (current.mode == Mode.Daily) {
                _state.value = current.copy(
                    phase = Phase.Perfect,
                    lastRelease = release,
                    deadRadiusDp = release.radiusAtReleaseDp,
                    perfectAtUptimeMs = upUptimeMs,
                    armedAtUptimeMs = upUptimeMs + FLASH_MS + QUIET_MS,
                    dailyMarks = current.dailyMarks + true,
                    dailyErrorsMs = current.dailyErrorsMs + release.errorMs,
                )
                _events.tryEmit(GameEvent.Perfect(current.dailyHits + 1))
                return
            }

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
            fail(current, release.radiusAtReleaseDp, release, upUptimeMs)
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
                    fail(current, clock.radiusDpAt(nowUptimeMs), release = null, atUptimeMs = nowUptimeMs)
                }
            }
            Phase.Perfect -> if (nowUptimeMs >= current.armedAtUptimeMs) {
                if (current.mode == Mode.Daily) advanceDaily(current) else armNextRound(current.streak)
            }
            // A missed ring in a trial is a beat, not an ending: the dead ring stands for
            // DAILY_MISS_DWELL_MS and then the next ring arms itself. Endless mode never advances
            // from Failed — there the fail screen is the destination.
            Phase.Failed -> if (current.mode == Mode.Daily && nowUptimeMs >= current.armedAtUptimeMs) {
                advanceDaily(current)
            }
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
                // Endless is the surface's resting state. Anything that starts a fresh run is
                // leaving the trial by definition, so this can never strand the surface in Daily.
                mode = Mode.Endless,
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
            Phase.Perfect, Phase.Failed, Phase.ContinueOffer, Phase.Results, Phase.DailyDone ->
                current.deadRadiusDp
        }
    }

    private fun fail(current: PlayState, deadRadiusDp: Float, release: Release?, atUptimeMs: Long) {
        // A trial ring is scored and moved past. No continue offer — a rewarded second chance would
        // make one player's ten rings a different contest from everyone else's, which is the one
        // thing a shared daily seed cannot survive.
        if (current.mode == Mode.Daily) {
            _state.value = current.copy(
                phase = Phase.Failed,
                lastRelease = release,
                deadRadiusDp = deadRadiusDp,
                dailyMarks = current.dailyMarks + false,
                dailyErrorsMs = if (release == null) {
                    current.dailyErrorsMs
                } else {
                    current.dailyErrorsMs + release.errorMs
                },
                armedAtUptimeMs = atUptimeMs + DAILY_MISS_DWELL_MS,
            )
            _events.tryEmit(GameEvent.Fail(current.dailyHits))
            return
        }

        val offerContinue = adHost.isRewardedReady(RewardedSurface.CONTINUE) &&
            shouldOfferContinue(
                streak = current.streak,
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
            // Asked once, here: UMP has nothing to say before the consent round trip has run, and
            // the answer cannot change again inside a session.
            val required = runCatching { adHost.isPrivacyOptionsRequired() }.getOrDefault(false)
            _state.update { it.copy(privacyOptionsRequired = required, adsEnabled = adHost.adsEnabled) }
            _adStartupDone.value = true
        }
    }

    suspend fun awaitAdStartup() {
        _adStartupDone.first { it }
    }

    /** SETTINGS -> PRIVACY OPTIONS. Reopens the UMP form; nothing about the game changes. */
    fun showPrivacyOptions() = adHost.showPrivacyOptions()

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

    // ---- daily trial -----------------------------------------------------------------------

    /**
     * Arms today's trial at ring one. Refuses if today's attempt is already spent — one attempt per
     * day is not a soft rule, it is what makes a shared result worth sharing.
     *
     * Returns whether the trial actually started, so the caller does not navigate into a screen the
     * player is not allowed to be on.
     */
    fun startDailyTrial(): Boolean {
        val today = todayEpochDay()
        if (_state.value.dailyResult?.epochDay == today) return false

        dailyEpochDay = today
        dailyPlans = DailyTrial.plansFor(today)
        roundClock = null
        runInProgress = false
        _state.update {
            it.copy(
                phase = Phase.Idle,
                mode = Mode.Daily,
                streak = 0,
                roundId = it.roundId + 1,
                plan = dailyPlans.first(),
                roundStartUptimeMs = 0L,
                lastRelease = null,
                deadRadiusDp = Difficulty.START_RADIUS_DP,
                perfectAtUptimeMs = 0L,
                armedAtUptimeMs = 0L,
                newlyUnlockedTitles = emptyList(),
                continuesUsedThisRun = 0,
                dailyRingIndex = 0,
                dailyMarks = emptyList(),
                dailyErrorsMs = emptyList(),
            )
        }
        return true
    }

    /** Leaves the trial and puts the surface back in endless mode, armed and waiting. */
    fun leaveDailyTrial() {
        _state.update { it.copy(mode = Mode.Endless) }
        startNewRun()
    }

    /**
     * Walking out of a trial part-way forfeits the rings that were not played — they are recorded as
     * misses and today's attempt is spent.
     *
     * The alternative is a back press that silently costs nothing, which turns one attempt a day
     * into unlimited attempts for anyone who notices. A trial abandoned before its first ring is
     * simply not started, so this cannot manufacture a zero out of an accidental tap.
     */
    fun abandonDailyTrial() {
        val current = _state.value
        if (current.mode != Mode.Daily || current.phase == Phase.DailyDone) return

        if (current.dailyMarks.isNotEmpty()) {
            val forfeited = DailyResult(
                epochDay = dailyEpochDay,
                marks = current.dailyMarks + List(DailyTrial.RINGS - current.dailyMarks.size) { false },
                meanAbsErrorMs = DailyTrial.meanAbsErrorMs(current.dailyErrorsMs),
            )
            // Into state first, for the same reason advanceDaily does: Home reads dailyPlayedToday
            // the moment this returns, and must not offer a run that startDailyTrial would refuse.
            _state.update { it.copy(dailyResult = forfeited, dailyPlayedToday = true) }
            viewModelScope.launch { store.recordDailyResult(forfeited) }
        }
        leaveDailyTrial()
    }

    /**
     * The ring just scored is behind us. Either arm the next one, or the trial is over and the
     * result is written — once, here, which is the only place a daily result is ever persisted.
     */
    private fun advanceDaily(current: PlayState) {
        val nextIndex = current.dailyRingIndex + 1
        roundClock = null

        if (nextIndex >= DailyTrial.RINGS) {
            val result = DailyResult(
                epochDay = dailyEpochDay,
                marks = current.dailyMarks,
                meanAbsErrorMs = DailyTrial.meanAbsErrorMs(current.dailyErrorsMs),
            )
            // The result goes into state in the SAME breath as the phase, not when the store echoes
            // it back. The result screen composes the instant the phase flips, and on a first-ever
            // trial the stored value is still null at that moment — it would have read "no result"
            // and bounced the player home a frame after they finished.
            _state.value = current.copy(
                phase = Phase.DailyDone,
                dailyRingIndex = nextIndex,
                dailyResult = result,
                dailyPlayedToday = true,
            )
            viewModelScope.launch { store.recordDailyResult(result) }
            return
        }

        _state.value = current.copy(
            phase = Phase.Idle,
            roundId = current.roundId + 1,
            dailyRingIndex = nextIndex,
            plan = dailyPlans[nextIndex],
            roundStartUptimeMs = 0L,
            lastRelease = null,
            deadRadiusDp = Difficulty.START_RADIUS_DP,
            perfectAtUptimeMs = 0L,
            armedAtUptimeMs = 0L,
        )
    }

    /**
     * One reflex-test reaction. A guess below the human floor is discarded rather than recorded as a
     * record no one could ever beat honestly.
     */
    fun recordTwitchReaction(reactionMs: Long) {
        if (!Twitch.isRecordable(reactionMs)) return
        viewModelScope.launch { store.recordTwitchReaction(reactionMs.toInt()) }
    }

    /**
     * A finished reflex sitting — five attempts, summary on screen. The second interstitial site in
     * the app, and a natural break by construction: the test is over and nothing is in flight.
     *
     * It answers to the SAME gate the round card uses, so the whole app shares one cadence and a
     * player bouncing between the two modes cannot be shown more ads than either alone would allow.
     * The decision lives here rather than in the screen: what the screen knows is that a sitting
     * ended, not whether that is worth an ad.
     */
    fun onReflexSittingCompleted() {
        adSession.onSideActivityEnded()
        // Nothing about the reflex screen depends on the ad, so the callback is empty by design —
        // the summary is already behind it and is still there when it is dismissed.
        if (shouldShowInterstitialNow()) showInterstitial {}
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

        /**
         * How long a missed trial ring is left standing before the next one arms. Longer than the
         * perfect flash: a miss needs a beat to register as a miss, or ten rings blur into one.
         */
        const val DAILY_MISS_DWELL_MS = 700L

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
