package com.simobr.donotblink.game

/**
 * Judges one round from input timestamps alone.
 *
 * Every method takes an uptime in milliseconds — the value carried by the pointer event itself
 * (`PointerInputChange.uptimeMillis`, which sits on the SystemClock.uptimeMillis clock and is
 * stamped by the input subsystem when the finger actually moved). This class NEVER reads a clock,
 * never sees a frame callback, and never inspects an animation's internal value.
 *
 * That is the mechanic, not an optimisation. Judging against the last rendered frame quantises
 * the player's input to the frame boundary — up to 16.7ms on a 60Hz panel. At the plateau the
 * whole window is 104ms wide, so frame quantisation would be ~16% of it and releases that felt
 * identical would score differently.
 *
 * Pure Kotlin: no Android, no Compose.
 */
class RoundClock(
    val plan: RoundPlan,
    /** uptimeMillis of the DOWN event that started the round. */
    val roundStartUptimeMs: Long,
) {

    /** Elapsed ms at [uptimeMs], measured from the DOWN event. */
    fun elapsedMsAt(uptimeMs: Long): Long = uptimeMs - roundStartUptimeMs

    /** The ring's radius at [uptimeMs]. Linear contraction, no easing anywhere. */
    fun radiusDpAt(uptimeMs: Long): Float =
        plan.startRadiusDp - plan.speedDpPerSec * (elapsedMsAt(uptimeMs) / 1000f)

    /** True while the ring is hidden by this round's blackout. */
    fun isBlackedOutAt(uptimeMs: Long): Boolean =
        plan.blackout?.coversMs(elapsedMsAt(uptimeMs).toFloat()) == true

    /** True once a still-held round has travelled past the line far enough to be lost. */
    fun hasOvershotAt(uptimeMs: Long): Boolean =
        elapsedMsAt(uptimeMs) > plan.overshootFailElapsedMs

    /**
     * Judges the release stamped [releaseUptimeMs]. The radius is derived from that timestamp and
     * compared against the round's target radius, tolerance [RoundPlan.bandDp] either side.
     */
    fun judgeRelease(releaseUptimeMs: Long): Release {
        val elapsedMs = elapsedMsAt(releaseUptimeMs)
        val radiusDp = radiusDpAt(releaseUptimeMs)
        val errorDp = radiusDp - plan.targetRadiusDp
        val verdict = when {
            errorDp > plan.bandDp -> Verdict.EARLY
            errorDp < -plan.bandDp -> Verdict.LATE
            else -> Verdict.PERFECT
        }
        return Release(
            verdict = verdict,
            elapsedMs = elapsedMs,
            radiusAtReleaseDp = radiusDp,
            errorDp = errorDp,
            errorMs = elapsedMs - plan.perfectElapsedMs,
        )
    }

    companion object {
        /** Starts a round: plans it from [seed], anchored to the DOWN event's uptime. */
        fun start(streak: Int, seed: Long, downUptimeMs: Long): RoundClock =
            RoundClock(Difficulty.planRound(streak, seed), downUptimeMs)
    }
}

enum class Verdict {
    /** Released on the line, inside the band. */
    PERFECT,

    /** Released while the ring was still outside the band. */
    EARLY,

    /** Released after the ring had passed through the band. */
    LATE;

    val isFail: Boolean get() = this != PERFECT
}

/** The judged result of one release. */
data class Release(
    val verdict: Verdict,
    val elapsedMs: Long,
    val radiusAtReleaseDp: Float,
    /** Positive: released early, ring still wide. Negative: released late, ring already inside. */
    val errorDp: Float,
    /** Positive: late by this many ms. Negative: early by this many ms. */
    val errorMs: Float,
)
