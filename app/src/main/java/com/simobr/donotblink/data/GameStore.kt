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

    suspend fun setBestStreak(value: Int)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setSoundEnabled(enabled: Boolean)
    suspend fun setUnlockedPaletteIds(ids: Set<String>)
    suspend fun setTitlesRevealed(revealed: Boolean)
    suspend fun setSelectedPhosphorId(id: String)
    suspend fun unlockPalette(id: String)
    suspend fun incrementLifetimeRuns()

    /** Writes [epochMs] only if this is genuinely the first launch. */
    suspend fun recordFirstLaunch(epochMs: Long)
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
) : GameStore {

    private val best = MutableStateFlow(bestStreak)
    private val haptics = MutableStateFlow(hapticsEnabled)
    private val sound = MutableStateFlow(soundEnabled)
    private val palettes = MutableStateFlow(unlockedPaletteIds)
    private val revealed = MutableStateFlow(titlesRevealed)
    private val phosphor = MutableStateFlow(selectedPhosphorId)
    private val runs = MutableStateFlow(lifetimeRuns)
    private val firstLaunch = MutableStateFlow(firstLaunchEpoch)

    override val bestStreak: Flow<Int> = best.asStateFlow()
    override val hapticsEnabled: Flow<Boolean> = haptics.asStateFlow()
    override val soundEnabled: Flow<Boolean> = sound.asStateFlow()
    override val unlockedPaletteIds: Flow<Set<String>> = palettes.asStateFlow()
    override val titlesRevealed: Flow<Boolean> = revealed.asStateFlow()
    override val selectedPhosphorId: Flow<String> = phosphor.asStateFlow()
    override val lifetimeRuns: Flow<Int> = runs.asStateFlow()
    override val firstLaunchEpoch: Flow<Long> = firstLaunch.asStateFlow()

    override suspend fun setBestStreak(value: Int) { best.value = value }
    override suspend fun setHapticsEnabled(enabled: Boolean) { haptics.value = enabled }
    override suspend fun setSoundEnabled(enabled: Boolean) { sound.value = enabled }
    override suspend fun setUnlockedPaletteIds(ids: Set<String>) { palettes.value = ids }
    override suspend fun setTitlesRevealed(revealed: Boolean) { this.revealed.value = revealed }
    override suspend fun setSelectedPhosphorId(id: String) { phosphor.value = id }
    override suspend fun unlockPalette(id: String) { palettes.value = palettes.value + id }
    override suspend fun incrementLifetimeRuns() { runs.value += 1 }
    override suspend fun recordFirstLaunch(epochMs: Long) {
        if (firstLaunch.value == 0L) firstLaunch.value = epochMs
    }
}
