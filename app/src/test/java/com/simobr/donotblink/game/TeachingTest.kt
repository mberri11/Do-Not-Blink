package com.simobr.donotblink.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The teaching line's visibility is a pure function of two counters, so it is decided here and the
 * screen only reads the answer.
 */
class TeachingTest {

    @Test
    fun the_line_is_drawn_for_the_first_three_rounds_of_the_first_three_runs() {
        for (run in 0..2) {
            for (streak in 0..2) {
                assertTrue(
                    "run $run, streak $streak should still be taught",
                    Teaching.showsRingLine(lifetimeRuns = run, streak = streak),
                )
            }
        }
    }

    @Test
    fun three_clean_releases_end_the_lesson_inside_the_run_that_earned_them() {
        for (run in 0..2) {
            assertFalse(
                "run $run, streak 3 has already proved the rule landed",
                Teaching.showsRingLine(lifetimeRuns = run, streak = 3),
            )
            assertFalse(Teaching.showsRingLine(lifetimeRuns = run, streak = 40))
        }
    }

    @Test
    fun the_line_never_returns_after_the_third_lifetime_run() {
        for (run in 3..50) {
            for (streak in 0..5) {
                assertFalse(
                    "run $run, streak $streak must never be taught again",
                    Teaching.showsRingLine(lifetimeRuns = run, streak = streak),
                )
            }
        }
    }

    @Test
    fun the_thresholds_are_the_ones_the_brief_named() {
        assertTrue(Teaching.TAUGHT_AFTER_RUNS == 3)
        assertTrue(Teaching.TAUGHT_AFTER_STREAK == 3)
    }
}
