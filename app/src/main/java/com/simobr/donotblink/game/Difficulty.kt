package com.simobr.donotblink.game

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * The difficulty curve and the per-round plan it produces.
 *
 * Pure Kotlin: no Android, no Compose, no clock reads. Every value here is a function of the
 * streak and, for the unpredictability ladder, of a per-round seed — so any round is exactly
 * reproducible in a unit test.
 *
 * The curve replaces the prototype's, which shrank a pixel tolerance while speed rose: the
 * temporal window collapsed super-linearly and the game went unwinnable near streak 25 while the
 * title ladder runs to 200. Here precision plateaus at a human floor and the *unpredictability*
 * ladder carries the difficulty past that point.
 */
object Difficulty {

    const val START_RADIUS_DP = 168f
    const val TARGET_RADIUS_DP = 96f

    /** Nominal travel: the full 168dp -> 96dp contraction. */
    const val TRAVEL_DP = START_RADIUS_DP - TARGET_RADIUS_DP

    const val BASE_TRAVEL_MS = 1560f
    const val TRAVEL_DECAY = 0.93f
    const val MIN_TRAVEL_MS = 480f

    const val BAND_BASE_DP = 9.5f
    const val BAND_SLOPE_DP = 0.28f
    const val BAND_MIN_DP = 3.5f

    /** The human floor. No streak may be judged on a window tighter than this, either side. */
    const val HUMAN_FLOOR_MS = 52f

    const val STROKE_BASE_DP = 2.6f
    const val STROKE_SLOPE_DP = 0.15f
    const val STROKE_MIN_DP = 0.8f

    /** How far past the line the ring travels before the round is lost without a release. */
    const val OVERSHOOT_MARGIN_DP = 3f

    // ---- unpredictability ladder -------------------------------------------------------------

    const val START_JITTER_FROM_STREAK = 20
    const val START_JITTER_DP = 14f

    const val TARGET_JITTER_FROM_STREAK = 30
    const val TARGET_JITTER_MIN_DP = 88f
    const val TARGET_JITTER_MAX_DP = 108f

    const val BLACKOUT_FROM_STREAK = 45
    const val BLACKOUT_CHANCE = 0.18f
    const val BLACKOUT_MS = 90f

    /** A blackout lives inside the middle 60% of travel; it may never hide the line itself. */
    const val BLACKOUT_EARLIEST_FRACTION = 0.20f
    const val BLACKOUT_LATEST_FRACTION = 0.80f

    // ---- the curve ---------------------------------------------------------------------------

    /** Time for the full nominal 72dp travel, floored so the ring never becomes a flicker. */
    fun travelMs(streak: Int): Float =
        max(MIN_TRAVEL_MS, BASE_TRAVEL_MS * TRAVEL_DECAY.pow(streak))

    fun speedDpPerSec(streak: Int): Float = TRAVEL_DP / (travelMs(streak) / 1000f)

    /** The band before the fairness clamp. Shrinks with streak, floored in dp. */
    fun rawBandDp(streak: Int): Float = max(BAND_MIN_DP, BAND_BASE_DP - BAND_SLOPE_DP * streak)

    /** What the raw band is worth in time at this speed. This is what the clamp inspects. */
    fun rawHalfWindowMs(streak: Int): Float = rawBandDp(streak) / speedDpPerSec(streak) * 1000f

    fun isFairnessClamped(streak: Int): Boolean = rawHalfWindowMs(streak) < HUMAN_FLOOR_MS

    /**
     * The band actually judged against. Once the raw band is worth less than [HUMAN_FLOOR_MS] of
     * reaction time, the band WIDENS to hold the window at the floor rather than shrinking below
     * it — the plateau the ladder is built on.
     */
    fun bandDp(streak: Int): Float =
        if (isFairnessClamped(streak)) {
            speedDpPerSec(streak) * (HUMAN_FLOOR_MS / 1000f)
        } else {
            rawBandDp(streak)
        }

    /** The effective half-window, either side of the line. Never below [HUMAN_FLOOR_MS]. */
    fun halfWindowMs(streak: Int): Float = bandDp(streak) / speedDpPerSec(streak) * 1000f

    fun strokeDp(streak: Int): Float = max(STROKE_MIN_DP, STROKE_BASE_DP - STROKE_SLOPE_DP * streak)

    fun overshootFailDp(streak: Int): Float = bandDp(streak) + OVERSHOOT_MARGIN_DP

    // ---- per-round plan ----------------------------------------------------------------------

    /**
     * Everything about one round, decided at round start from [seed] so the round is replayable.
     * Draw order is fixed: start radius, then target radius, then the blackout roll.
     */
    fun planRound(streak: Int, seed: Long): RoundPlan {
        val random = Random(seed)

        val startRadiusDp = if (streak >= START_JITTER_FROM_STREAK) {
            START_RADIUS_DP + (random.nextFloat() * 2f - 1f) * START_JITTER_DP
        } else {
            START_RADIUS_DP
        }

        val targetRadiusDp = if (streak >= TARGET_JITTER_FROM_STREAK) {
            TARGET_JITTER_MIN_DP + random.nextFloat() * (TARGET_JITTER_MAX_DP - TARGET_JITTER_MIN_DP)
        } else {
            TARGET_RADIUS_DP
        }

        val speed = speedDpPerSec(streak)
        val roundTravelMs = (startRadiusDp - targetRadiusDp) / speed * 1000f

        val blackout = if (streak >= BLACKOUT_FROM_STREAK && random.nextFloat() < BLACKOUT_CHANCE) {
            blackoutWithin(roundTravelMs, random)
        } else {
            null
        }

        return RoundPlan(
            streak = streak,
            seed = seed,
            startRadiusDp = startRadiusDp,
            targetRadiusDp = targetRadiusDp,
            speedDpPerSec = speed,
            bandDp = bandDp(streak),
            strokeDp = strokeDp(streak),
            overshootFailDp = overshootFailDp(streak),
            blackout = blackout,
        )
    }

    /** Places the blackout inside the middle 60% of this round's travel, never touching the end. */
    private fun blackoutWithin(roundTravelMs: Float, random: Random): Blackout {
        val earliestStart = BLACKOUT_EARLIEST_FRACTION * roundTravelMs
        val latestEnd = BLACKOUT_LATEST_FRACTION * roundTravelMs
        val durationMs = min(BLACKOUT_MS, latestEnd - earliestStart)
        val latestStart = latestEnd - durationMs
        val startMs = earliestStart + random.nextFloat() * (latestStart - earliestStart)
        return Blackout(startMs = startMs, durationMs = durationMs)
    }
}

/** A stretch of travel where the ring is not drawn. The player holds through it blind. */
data class Blackout(val startMs: Float, val durationMs: Float) {
    val endMs: Float get() = startMs + durationMs
    fun coversMs(elapsedMs: Float): Boolean = elapsedMs >= startMs && elapsedMs < endMs
}

/**
 * One round, fully determined. [startRadiusDp] and [targetRadiusDp] may both be jittered, so a
 * round's own travel time is [perfectElapsedMs] — not [Difficulty.travelMs], which is the nominal
 * curve value for an unjittered round.
 */
data class RoundPlan(
    val streak: Int,
    val seed: Long,
    val startRadiusDp: Float,
    val targetRadiusDp: Float,
    val speedDpPerSec: Float,
    val bandDp: Float,
    val strokeDp: Float,
    val overshootFailDp: Float,
    val blackout: Blackout?,
) {
    /** dp this round actually contracts through before the line. */
    val travelDp: Float = startRadiusDp - targetRadiusDp

    /** Elapsed ms at which the radius sits exactly on the line. */
    val perfectElapsedMs: Float = travelDp / speedDpPerSec * 1000f

    /** Half-window either side of [perfectElapsedMs] that still counts as perfect. */
    val halfWindowMs: Float = bandDp / speedDpPerSec * 1000f

    /** Elapsed ms at which a round still being held is lost. */
    val overshootFailElapsedMs: Float =
        (travelDp + overshootFailDp) / speedDpPerSec * 1000f
}
