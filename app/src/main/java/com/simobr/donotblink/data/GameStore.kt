package com.simobr.donotblink.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simobr.donotblink.game.DailyResult
import com.simobr.donotblink.game.DailyTrial
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Everything the game remembers. DataStore Preferences is the only persistence in this app —
 * there is nothing relational here.
 *
 * An interface so [com.simobr.donotblink.game.GameViewModel] stays a JVM unit test away from
 * Android.
 */
interface GameStore {
    val bestStreak: Flow<Int>
    val hapticsEnabled: Flow<Boolean>
    val soundEnabled: Flow<Boolean>
    val unlockedPaletteIds: Flow<Set<String>>
    val titlesRevealed: Flow<Boolean>
    /** Which phosphor is in use. Not in the original key list; a palette you cannot pick is useless. */
    val selectedPhosphorId: Flow<String>
    val lifetimeRuns: Flow<Int>
    val firstLaunchEpoch: Flow<Long>

    /** The most recently finished daily trial, or null if none has ever been played. */
    val lastDailyResult: Flow<DailyResult?>

    /** Consecutive days finished, ending with [lastDailyResult]'s day. */
    val dailyDayStreak: Flow<Int>

    /** Most rings ever hit in one trial. */
    val dailyBestHits: Flow<Int>

    /** Fastest reflex-test reaction ever recorded, in ms. 0 means never played. */
    val twitchBestMs: Flow<Int>

    suspend fun setBestStreak(value: Int)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setUnlockedPaletteIds(ids: Set<String>)
    suspend fun setTitlesRevealed(revealed: Boolean)
    suspend fun setSelectedPhosphorId(id: String)
    suspend fun unlockPalette(id: String)
    suspend fun incrementLifetimeRuns()

    /**
     * Stores a finished trial and rolls the day-streak and the best-hits record forward with it.
     *
     * One write, because the three values are one fact: a result whose streak did not move with it
     * would be a lie the next launch would read back.
     */
    suspend fun recordDailyResult(result: DailyResult)

    /** Keeps [reactionMs] only if it beats what is stored — lower is better, and 0 means unset. */
    suspend fun recordTwitchReaction(reactionMs: Int)

    /** Writes [epochMs] only if this is genuinely the first launch. */
    suspend fun recordFirstLaunch(epochMs: Long)
}

/**
 * A [DailyResult] as one preference string: `epochDay|marks|meanErrorMs`, marks being one character
 * per ring. DataStore Preferences stores primitives, and this app has no Room and no serialization
 * dependency — CLAUDE.md pins the dependency list exactly — so the encoding is explicit and tested
 * rather than reflective.
 *
 * Anything unparseable decodes to null and the player simply gets today's trial back. A corrupt
 * preference must never be able to crash the app on launch.
 */
internal object DailyCodec {
    private const val HIT = '1'
    private const val MISS = '0'

    fun encode(result: DailyResult): String = buildString {
        append(result.epochDay)
        append('|')
        result.marks.forEach { append(if (it) HIT else MISS) }
        append('|')
        append(result.meanAbsErrorMs)
    }

    fun decode(raw: String?): DailyResult? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split('|')
        if (parts.size != 3) return null
        val epochDay = parts[0].toLongOrNull() ?: return null
        val marksText = parts[1]
        if (marksText.length != DailyTrial.RINGS) return null
        if (marksText.any { it != HIT && it != MISS }) return null
        val meanError = parts[2].toIntOrNull() ?: return null
        return DailyResult(
            epochDay = epochDay,
            marks = marksText.map { it == HIT },
            meanAbsErrorMs = meanError,
        )
    }
}

private val Context.gameDataStore: DataStore<Preferences> by preferencesDataStore(name = "do_not_blink")

class DataStoreGameStore(private val dataStore: DataStore<Preferences>) : GameStore {

    constructor(context: Context) : this(context.applicationContext.gameDataStore)

    override val bestStreak: Flow<Int> = dataStore.data.map { it[KEY_BEST] ?: 0 }
    override val hapticsEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_HAPTICS] ?: true }
    override val soundEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_SOUND] ?: true }
    override val unlockedPaletteIds: Flow<Set<String>> = dataStore.data.map { it[KEY_PALETTES] ?: emptySet() }
    override val titlesRevealed: Flow<Boolean> = dataStore.data.map { it[KEY_TITLES_REVEALED] ?: false }
    override val selectedPhosphorId: Flow<String> = dataStore.data.map { it[KEY_PHOSPHOR] ?: "" }
    override val lifetimeRuns: Flow<Int> = dataStore.data.map { it[KEY_LIFETIME_RUNS] ?: 0 }
    override val firstLaunchEpoch: Flow<Long> = dataStore.data.map { it[KEY_FIRST_LAUNCH] ?: 0L }
    override val lastDailyResult: Flow<DailyResult?> =
        dataStore.data.map { DailyCodec.decode(it[KEY_DAILY_RESULT]) }
    override val dailyDayStreak: Flow<Int> = dataStore.data.map { it[KEY_DAILY_DAY_STREAK] ?: 0 }
    override val dailyBestHits: Flow<Int> = dataStore.data.map { it[KEY_DAILY_BEST_HITS] ?: 0 }
    override val twitchBestMs: Flow<Int> = dataStore.data.map { it[KEY_TWITCH_BEST_MS] ?: 0 }

    override suspend fun setBestStreak(value: Int) {
        dataStore.edit { it[KEY_BEST] = value }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_HAPTICS] = enabled }
    }

    override suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SOUND] = enabled }
    }

    override suspend fun setUnlockedPaletteIds(ids: Set<String>) {
        dataStore.edit { it[KEY_PALETTES] = ids }
    }

    override suspend fun setTitlesRevealed(revealed: Boolean) {
        dataStore.edit { it[KEY_TITLES_REVEALED] = revealed }
    }

    override suspend fun setSelectedPhosphorId(id: String) {
        dataStore.edit { it[KEY_PHOSPHOR] = id }
    }

    override suspend fun unlockPalette(id: String) {
        dataStore.edit { prefs -> prefs[KEY_PALETTES] = (prefs[KEY_PALETTES] ?: emptySet()) + id }
    }

    override suspend fun incrementLifetimeRuns() {
        dataStore.edit { it[KEY_LIFETIME_RUNS] = (it[KEY_LIFETIME_RUNS] ?: 0) + 1 }
    }

    override suspend fun recordDailyResult(result: DailyResult) {
        dataStore.edit { prefs ->
            // Read the PREVIOUS result inside the same edit block: computing the streak from a
            // flow collected elsewhere would race a second write and could double-count a day.
            val previous = DailyCodec.decode(prefs[KEY_DAILY_RESULT])
            prefs[KEY_DAILY_DAY_STREAK] = DailyTrial.nextDayStreak(
                previousEpochDay = previous?.epochDay,
                previousStreak = prefs[KEY_DAILY_DAY_STREAK] ?: 0,
                todayEpochDay = result.epochDay,
            )
            prefs[KEY_DAILY_RESULT] = DailyCodec.encode(result)
            prefs[KEY_DAILY_BEST_HITS] = maxOf(prefs[KEY_DAILY_BEST_HITS] ?: 0, result.hits)
        }
    }

    override suspend fun recordTwitchReaction(reactionMs: Int) {
        dataStore.edit { prefs ->
            // Lower is better, so an unset 0 must not win the comparison.
            val stored = prefs[KEY_TWITCH_BEST_MS] ?: 0
            if (stored == 0 || reactionMs < stored) prefs[KEY_TWITCH_BEST_MS] = reactionMs
        }
    }

    override suspend fun recordFirstLaunch(epochMs: Long) {
        dataStore.edit { if (it[KEY_FIRST_LAUNCH] == null) it[KEY_FIRST_LAUNCH] = epochMs }
    }

    private companion object {
        val KEY_BEST = intPreferencesKey("best_streak")
        val KEY_HAPTICS = booleanPreferencesKey("haptics_enabled")
        val KEY_SOUND = booleanPreferencesKey("sound_enabled")
        val KEY_PALETTES = stringSetPreferencesKey("unlocked_palette_ids")
        val KEY_TITLES_REVEALED = booleanPreferencesKey("titles_revealed")
        val KEY_LIFETIME_RUNS = intPreferencesKey("lifetime_runs")
        val KEY_FIRST_LAUNCH = longPreferencesKey("first_launch_epoch")
        val KEY_PHOSPHOR = stringPreferencesKey("selected_phosphor_id")
        val KEY_DAILY_RESULT = stringPreferencesKey("daily_last_result")
        val KEY_DAILY_DAY_STREAK = intPreferencesKey("daily_day_streak")
        val KEY_DAILY_BEST_HITS = intPreferencesKey("daily_best_hits")
        val KEY_TWITCH_BEST_MS = intPreferencesKey("twitch_best_ms")
    }
}

/** For tests and previews. */
class InMemoryGameStore(
    bestStreak: Int = 0,
    hapticsEnabled: Boolean = true,
    soundEnabled: Boolean = true,
    unlockedPaletteIds: Set<String> = emptySet(),
    titlesRevealed: Boolean = false,
    selectedPhosphorId: String = "",
    lifetimeRuns: Int = 0,
    firstLaunchEpoch: Long = 0L,
    lastDailyResult: DailyResult? = null,
    dailyDayStreak: Int = 0,
    dailyBestHits: Int = 0,
    twitchBestMs: Int = 0,
) : GameStore {

    private val best = MutableStateFlow(bestStreak)
    private val haptics = MutableStateFlow(hapticsEnabled)
    private val sound = MutableStateFlow(soundEnabled)
    private val palettes = MutableStateFlow(unlockedPaletteIds)
    private val revealed = MutableStateFlow(titlesRevealed)
    private val phosphor = MutableStateFlow(selectedPhosphorId)
    private val runs = MutableStateFlow(lifetimeRuns)
    private val firstLaunch = MutableStateFlow(firstLaunchEpoch)
    private val daily = MutableStateFlow(lastDailyResult)
    private val dayStreak = MutableStateFlow(dailyDayStreak)
    private val bestHits = MutableStateFlow(dailyBestHits)
    private val twitchBest = MutableStateFlow(twitchBestMs)

    override val bestStreak: Flow<Int> = best.asStateFlow()
    override val hapticsEnabled: Flow<Boolean> = haptics.asStateFlow()
    override val soundEnabled: Flow<Boolean> = sound.asStateFlow()
    override val unlockedPaletteIds: Flow<Set<String>> = palettes.asStateFlow()
    override val titlesRevealed: Flow<Boolean> = revealed.asStateFlow()
    override val selectedPhosphorId: Flow<String> = phosphor.asStateFlow()
    override val lifetimeRuns: Flow<Int> = runs.asStateFlow()
    override val firstLaunchEpoch: Flow<Long> = firstLaunch.asStateFlow()
    override val lastDailyResult: Flow<DailyResult?> = daily.asStateFlow()
    override val dailyDayStreak: Flow<Int> = dayStreak.asStateFlow()
    override val dailyBestHits: Flow<Int> = bestHits.asStateFlow()
    override val twitchBestMs: Flow<Int> = twitchBest.asStateFlow()

    override suspend fun setBestStreak(value: Int) { best.value = value }
    override suspend fun setHapticsEnabled(enabled: Boolean) { haptics.value = enabled }
    override suspend fun setSoundEnabled(enabled: Boolean) { sound.value = enabled }
    override suspend fun setUnlockedPaletteIds(ids: Set<String>) { palettes.value = ids }
    override suspend fun setTitlesRevealed(revealed: Boolean) { this.revealed.value = revealed }
    override suspend fun setSelectedPhosphorId(id: String) { phosphor.value = id }
    override suspend fun unlockPalette(id: String) { palettes.value = palettes.value + id }
    override suspend fun incrementLifetimeRuns() { runs.value += 1 }
    override suspend fun recordDailyResult(result: DailyResult) {
        dayStreak.value = DailyTrial.nextDayStreak(
            previousEpochDay = daily.value?.epochDay,
            previousStreak = dayStreak.value,
            todayEpochDay = result.epochDay,
        )
        daily.value = result
        bestHits.value = maxOf(bestHits.value, result.hits)
    }
    override suspend fun recordTwitchReaction(reactionMs: Int) {
        if (twitchBest.value == 0 || reactionMs < twitchBest.value) twitchBest.value = reactionMs
    }
    override suspend fun recordFirstLaunch(epochMs: Long) {
        if (firstLaunch.value == 0L) firstLaunch.value = epochMs
    }
}
