package com.simobr.donotblink.game

/**
 * When the game states its own rule.
 *
 * The rule — release while the ring crosses the line — was written nowhere, so a new player held
 * still and waited, which is an overshoot fail every single time. Two lines of type fix that:
 * a permanent one under the HOLD affordance on Home, and a temporary one under the ring while the
 * player is still learning. There is no tutorial screen, no overlay and no modal.
 *
 * Pure Kotlin: a function of the two counters and nothing else, so it is a unit test's problem.
 */
object Teaching {

    /** Lifetime runs after which the ring line is never drawn again. */
    const val TAUGHT_AFTER_RUNS = 3

    /** Streak within a run after which the ring line is not drawn for the rest of that run. */
    const val TAUGHT_AFTER_STREAK = 3

    /**
     * Whether the line under the ring is drawn: the first [TAUGHT_AFTER_STREAK] rounds of the
     * first [TAUGHT_AFTER_RUNS] lifetime runs. Three clean releases prove the rule landed, so the
     * line goes away inside the run that proved it, not just at the end of it.
     */
    fun showsRingLine(lifetimeRuns: Int, streak: Int): Boolean =
        lifetimeRuns < TAUGHT_AFTER_RUNS && streak < TAUGHT_AFTER_STREAK
}
