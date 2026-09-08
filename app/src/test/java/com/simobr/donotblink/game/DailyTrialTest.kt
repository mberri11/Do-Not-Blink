package com.simobr.donotblink.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The daily trial's entire promise is that a date determines the rings. If this file goes red, two
 * players on the same day are playing different games and the share card is meaningless.
 */
class DailyTrialTest {

    @Test
    fun `the same day always plans the same ten rings`() {
        val first = DailyTrial.plansFor(20_338L)
        val second = DailyTrial.plansFor(20_338L)

        assertEquals(DailyTrial.RINGS, first.size)
        assertEquals(first, second)
    }

    @Test
    fun `a different day plans a different trial`() {
        val today = DailyTrial.plansFor(20_338L)
        val tomorrow = DailyTrial.plansFor(20_339L)

        // Seeds are what actually differ; the derived fields can collide at the same streak.
        assertNotEquals(today.map { it.seed }, tomorrow.map { it.seed })
    }

    @Test
    fun `adjacent days do not share a single ring seed`() {
        // A weak date mix would leave neighbouring days visibly related. They must be independent.
        val today = DailyTrial.plansFor(20_338L).map { it.seed }.toSet()
        val tomorrow = DailyTrial.plansFor(20_339L).map { it.seed }.toSet()

        assertTrue((today intersect tomorrow).isEmpty())
    }

    @Test
    fun `the ramp runs from the opening window to the plateau`() {
        assertEquals(0, DailyTrial.streakForRing(0))
        assertEquals(27, DailyTrial.streakForRing(DailyTrial.RINGS - 1))

        // Ring one is the full opening window; ring ten is judged on the human floor.
        assertEquals(162, Difficulty.halfWindowMs(0).toInt())
        assertEquals(Difficulty.HUMAN_FLOOR_MS, Difficulty.halfWindowMs(27))
    }

    @Test
    fun `the ramp never reaches a blackout`() {
        // Going blind on a one-attempt-per-day run would be cruel, and it is the ramp that
        // guarantees it rather than luck: assert the ceiling, not a sampled roll.
        val hardest = DailyTrial.streakForRing(DailyTrial.RINGS - 1)
        assertTrue(hardest < Difficulty.BLACKOUT_FROM_STREAK)
        assertTrue(DailyTrial.plansFor(20_338L).all { it.blackout == null })
    }

    @Test
    fun `the last rings jitter their start radius`() {
        val plans = DailyTrial.plansFor(20_338L)
        // Ring eight is the first at or past START_JITTER_FROM_STREAK (21 >= 20).
        assertEquals(Difficulty.START_RADIUS_DP, plans[0].startRadiusDp)
        assertNotEquals(Difficulty.START_RADIUS_DP, plans[DailyTrial.RINGS - 1].startRadiusDp)
    }

    @Test
    fun `a consecutive day extends the streak`() {
        assertEquals(5, DailyTrial.nextDayStreak(previousEpochDay = 20_337L, previousStreak = 4, todayEpochDay = 20_338L))
    }

    @Test
    fun `a missed day starts again at one`() {
        assertEquals(1, DailyTrial.nextDayStreak(previousEpochDay = 20_330L, previousStreak = 9, todayEpochDay = 20_338L))
    }

    @Test
    fun `the first trial ever is a streak of one`() {
        assertEquals(1, DailyTrial.nextDayStreak(previousEpochDay = null, previousStreak = 0, todayEpochDay = 20_338L))
    }

    @Test
    fun `replaying the same day cannot inflate the streak`() {
        assertEquals(4, DailyTrial.nextDayStreak(previousEpochDay = 20_338L, previousStreak = 4, todayEpochDay = 20_338L))
    }

    @Test
    fun `mean error ignores rings that never released`() {
        // Three releases, one overshoot that contributes nothing at all.
        assertEquals(20, DailyTrial.meanAbsErrorMs(listOf(-10f, 20f, 30f)))
        assertEquals(0, DailyTrial.meanAbsErrorMs(emptyList()))
    }

    @Test
    fun `the grid is one glyph per ring in ring order`() {
        val result = DailyResult(
            epochDay = 20_338L,
            marks = listOf(true, true, false, true, true, true, true, false, true, true),
            meanAbsErrorMs = 23,
        )

        assertEquals("▮▮▯▮▮▮▮▯▮▮", result.grid())
        assertEquals(8, result.hits)
    }
}
