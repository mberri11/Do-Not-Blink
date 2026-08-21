package com.simobr.donotblink.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ladder is content, and content is what typos live in. Every pair is asserted literally.
 */
class TitlesTest {

    @Test
    fun the_ladder_is_exactly_the_twenty_pairs() {
        assertEquals(
            listOf(
                1 to "EYES OPEN",
                2 to "TWITCH",
                3 to "STILL HAND",
                5 to "SLOW BLINKER",
                8 to "STEADY",
                12 to "COLD NERVE",
                16 to "LIDLESS",
                20 to "HAIRLINE",
                25 to "UNFLINCHING",
                30 to "QUIET PULSE",
                36 to "GLASS EYE",
                42 to "DEAD CALM",
                50 to "THE LONG LOOK",
                60 to "NO TREMOR",
                70 to "SIGHTLINE",
                85 to "HELD BREATH",
                100 to "CENTURY STARE",
                120 to "STONE LENS",
                150 to "SLEEPLESS",
                200 to "DO NOT BLINK",
            ),
            TITLES.map { it.threshold to it.name },
        )
    }

    @Test
    fun thresholds_strictly_ascend() {
        TITLES.zipWithNext { lower, higher ->
            assertTrue("$lower then $higher", higher.threshold > lower.threshold)
        }
    }

    @Test
    fun a_title_unlocks_exactly_at_its_threshold() {
        val steady = TITLES.single { it.name == "STEADY" }
        assertFalse(isTitleUnlocked(steady, 7))
        assertTrue(isTitleUnlocked(steady, 8))
        assertTrue(isTitleUnlocked(steady, 9))
    }

    @Test
    fun unlocked_count_follows_the_best_streak() {
        assertEquals(0, unlockedTitleCount(0))
        assertEquals(1, unlockedTitleCount(1))
        assertEquals(3, unlockedTitleCount(4))
        assertEquals(10, unlockedTitleCount(35))
        assertEquals(20, unlockedTitleCount(200))
        assertEquals(20, unlockedTitleCount(10_000))
    }

    @Test
    fun a_run_earns_only_the_titles_its_predecessor_had_not() {
        assertEquals(
            listOf("EYES OPEN", "TWITCH", "STILL HAND"),
            titlesUnlockedBetween(previousBest = 0, newBest = 3).map { it.name },
        )
        assertEquals(
            listOf("SLOW BLINKER", "STEADY"),
            titlesUnlockedBetween(previousBest = 3, newBest = 8).map { it.name },
        )
        assertEquals(emptyList<String>(), titlesUnlockedBetween(previousBest = 8, newBest = 8).map { it.name })
        assertEquals(emptyList<String>(), titlesUnlockedBetween(previousBest = 30, newBest = 12).map { it.name })
    }
}
