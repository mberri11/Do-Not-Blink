package com.simobr.donotblink.game

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * One seeded trial per calendar day: the same ten rings, in the same order, for everyone who opens
 * the app on that date.
 *
 * This costs nothing to host because there is nothing to host. [Difficulty.planRound] was written
 * pure and seed-driven from the start — it reads no clock and holds no state — so a date is a seed
 * and a seed is a trial. No backend, no accounts, no network call.
 *
 * A miss does NOT end the trial. All ten rings are always played, so every player's result is the
 * same shape and can be compared, and a bad first ring does not cost someone their whole day.
 */
object DailyTrial {

    /** Rings in a trial. Ten is the whole design: it fits one share grid and one screen. */
    const val RINGS = 10

    /**
     * The difficulty ramp across the ten rings, expressed as the streak each ring is planned at.
     *
     * Three per ring walks the curve from the streak-0 half-window (~162ms) to the plateau's 52ms
     * floor by ring ten, and crosses [Difficulty.START_JITTER_FROM_STREAK] at ring eight so the last
     * three rings also stop starting from the same radius. It never reaches
     * [Difficulty.BLACKOUT_FROM_STREAK] — going blind on a one-attempt-per-day run would be cruel.
     */
    fun streakForRing(index: Int): Int = index * 3

    /**
     * The ten rounds for [epochDay]. Deterministic: same day in, same ten rounds out, on every
     * device, forever. The seeds are drawn in order from one generator, so ring N's plan depends on
     * every ring before it and the sequence cannot be reordered without changing the day.
     */
    fun plansFor(epochDay: Long): List<RoundPlan> {
        val random = Random(epochDay)
        return List(RINGS) { index ->
            Difficulty.planRound(streak = streakForRing(index), seed = random.nextLong())
        }
    }

    /**
     * The day-streak after finishing the trial for [todayEpochDay].
     *
     * Only a genuinely consecutive day extends it. A gap of two or more days starts again at one,
     * and replaying the same day cannot inflate it — the view model refuses a second attempt, but
     * the rule is stated here so it is testable rather than incidental.
     */
    fun nextDayStreak(previousEpochDay: Long?, previousStreak: Int, todayEpochDay: Long): Int = when {
        previousEpochDay == null -> 1
        todayEpochDay == previousEpochDay -> previousStreak.coerceAtLeast(1)
        todayEpochDay == previousEpochDay + 1 -> previousStreak + 1
        else -> 1
    }

    /**
     * Mean absolute error over the rings that produced a release.
     *
     * An overshoot miss never released at all, so it contributes no measurement — averaging a
     * number that was never taken would quietly punish the same mistake twice. A trial where every
     * ring overshot has no error to report and returns 0.
     */
    fun meanAbsErrorMs(errorsMs: List<Float>): Int =
        if (errorsMs.isEmpty()) 0 else (errorsMs.sumOf { abs(it).toDouble() } / errorsMs.size).roundToInt()
}

/**
 * A finished trial. [marks] is always [DailyTrial.RINGS] long and in ring order — it is both the
 * score and the share card's grid.
 */
data class DailyResult(
    val epochDay: Long,
    val marks: List<Boolean>,
    val meanAbsErrorMs: Int,
) {
    val hits: Int get() = marks.count { it }

    /** The share grid, and the same glyphs the result screen draws. */
    fun grid(): String = marks.joinToString("") { if (it) "▮" else "▯" }
}
