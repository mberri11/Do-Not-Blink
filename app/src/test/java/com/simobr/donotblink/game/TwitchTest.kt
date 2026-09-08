package com.simobr.donotblink.game

import com.simobr.donotblink.data.InMemoryGameStore
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchTest {

    @Test
    fun `the wait always lands inside the declared bounds`() {
        val random = Random(7)
        repeat(2_000) {
            val wait = Twitch.waitMs(random)
            assertTrue("$wait below floor", wait >= Twitch.MIN_WAIT_MS)
            assertTrue("$wait above ceiling", wait <= Twitch.MAX_WAIT_MS)
        }
    }

    @Test
    fun `the wait actually varies`() {
        // A constant wait would be a rhythm to learn, which is the one thing this test must not
        // reward. Assert spread, not just bounds.
        val random = Random(7)
        val waits = List(200) { Twitch.waitMs(random) }.toSet()
        assertTrue("only ${waits.size} distinct waits", waits.size > 100)
    }

    @Test
    fun `a reaction below the human floor is a guess`() {
        assertTrue(Twitch.isGuess(0L))
        assertTrue(Twitch.isGuess(99L))
        assertFalse(Twitch.isGuess(Twitch.GUESS_FLOOR_MS))
        assertFalse(Twitch.isGuess(240L))
    }

    @Test
    fun `a reading from a player who was not looking is not recordable`() {
        // Found on device: driving the test with a 17-second tap put "BEST 17376MS" on Home,
        // because the stored best is a minimum and a lone absurd reading stands as the record.
        assertTrue(Twitch.isAbandoned(17_376L))
        assertFalse(Twitch.isAbandoned(Twitch.ABANDONED_CEILING_MS))
        assertFalse(Twitch.isRecordable(17_376L))
        assertFalse(Twitch.isRecordable(50L))
        assertTrue(Twitch.isRecordable(240L))
        // The ceiling must sit well clear of any genuine reaction, however tired the player.
        assertTrue(Twitch.isRecordable(900L))
    }

    @Test
    fun `the grade ladder is ordered and total`() {
        assertEquals("INHUMAN", Twitch.grade(150L))
        assertEquals("REFLEX", Twitch.grade(200L))
        assertEquals("SHARP", Twitch.grade(250L))
        assertEquals("AWAKE", Twitch.grade(300L))
        assertEquals("SLOW", Twitch.grade(400L))
        assertEquals("ASLEEP", Twitch.grade(900L))
    }

    @Test
    fun `the chirp tier never disagrees with the grade on screen`() {
        // The sound says how good the reading was before the number is read, so a tier boundary
        // that drifted from a grade boundary would have the app contradicting itself.
        assertEquals(0, Twitch.chirpTier(150L))   // INHUMAN
        assertEquals(0, Twitch.chirpTier(219L))   // REFLEX
        assertEquals(1, Twitch.chirpTier(220L))   // SHARP starts
        assertEquals(1, Twitch.chirpTier(339L))   // AWAKE
        assertEquals(2, Twitch.chirpTier(340L))   // SLOW starts
        assertEquals(2, Twitch.chirpTier(9_999L)) // ASLEEP

        // Every tier must index a real chirp — getOrNull would silently play nothing otherwise.
        listOf(0L, 219L, 220L, 339L, 340L, Long.MAX_VALUE).forEach { ms ->
            val tier = Twitch.chirpTier(ms)
            assertTrue("tier $tier out of range for ${ms}ms", tier in 0 until Twitch.CHIRP_TIERS)
        }
    }

    @Test
    fun `best and mean ignore an empty sitting rather than reporting zero`() {
        // A zero best would show on Home as an unbeatable record nobody set.
        assertNull(Twitch.best(emptyList()))
        assertNull(Twitch.mean(emptyList()))
        assertEquals(210L, Twitch.best(listOf(240L, 210L, 305L)))
        assertEquals(251L, Twitch.mean(listOf(240L, 210L, 305L)))
    }

    @Test
    fun `a faster reading replaces the record and a slower one does not`() = runTest {
        val store = InMemoryGameStore()
        assertEquals(0, store.twitchBestMs.first())

        store.recordTwitchReaction(280)
        assertEquals(280, store.twitchBestMs.first())

        store.recordTwitchReaction(410)
        assertEquals(280, store.twitchBestMs.first())

        store.recordTwitchReaction(199)
        assertEquals(199, store.twitchBestMs.first())
    }

    @Test
    fun `the unset record does not win the comparison`() = runTest {
        // 0 means "never played", and lower-is-better would otherwise make it permanent.
        val store = InMemoryGameStore()
        store.recordTwitchReaction(350)
        assertEquals(350, store.twitchBestMs.first())
    }
}
