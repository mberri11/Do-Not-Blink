package com.simobr.donotblink.game

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the game says about a release, in milliseconds.
 *
 * [RoundClock.judgeRelease] has always computed [Release.errorMs] and the game has always thrown it
 * away: a player who released 4ms late and one who released 400ms early saw the identical fail
 * screen. A precision game that will not say which SIDE you missed on cannot be learned, so it reads
 * as luck. These two lines are the whole fix.
 *
 * Pure Kotlin, no Android: the strings are asserted in a JVM unit test rather than eyeballed.
 */
object Readout {

    /**
     * The fail screen's verdict line.
     *
     * A null [release] is the overshoot fail — the ring travelled past the band and the finger was
     * never lifted, so there is no release to measure and no number to print.
     */
    fun missLine(release: Release?): String = when {
        release == null -> "HELD TOO LONG"
        release.verdict == Verdict.EARLY -> "EARLY BY ${millis(release.errorMs)}"
        release.verdict == Verdict.LATE -> "LATE BY ${millis(release.errorMs)}"
        // A PERFECT release never reaches the fail screen, but the fail screen must not be able to
        // crash or print a lie if the state machine ever hands it one.
        else -> "ON THE LINE"
    }

    /**
     * The signed error shown for the beat after a perfect release. Positive is late.
     *
     * The sign is the point: it is the only thing that tells a player which way to correct, so a
     * release that rounds to zero prints an explicit `+0MS` rather than a bare `0MS`.
     */
    fun precisionLine(release: Release?): String {
        if (release == null) return ""
        // The sign comes from the raw error, NOT from the rounded integer: a release 0.2ms early
        // rounds to 0, and taking the sign from that would print "+0MS" — the game claiming the
        // player was late when they were early. Direction is the one thing this line exists to say.
        val sign = if (release.errorMs < 0f) "-" else "+"
        return "$sign${abs(release.errorMs).roundToInt()}MS"
    }

    /** Whole milliseconds, unsigned — [missLine] carries the direction in a word. */
    private fun millis(errorMs: Float): String = "${abs(errorMs).roundToInt()}MS"
}
