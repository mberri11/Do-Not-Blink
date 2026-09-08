package com.simobr.donotblink.game

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The readout is the only place the game tells a player which way they missed, so the wording and
 * the sign are asserted rather than eyeballed. Nothing here touches Android.
 */
class ReadoutTest {

    private fun release(verdict: Verdict, errorMs: Float) = Release(
        verdict = verdict,
        elapsedMs = 0L,
        radiusAtReleaseDp = 0f,
        errorDp = 0f,
        errorMs = errorMs,
    )

    @Test
    fun `an early release names the direction and drops the sign`() {
        // Early means the ring was still wide, which is a NEGATIVE errorMs. The word carries the
        // direction, so the number must not repeat it with a minus.
        assertEquals("EARLY BY 128MS", Readout.missLine(release(Verdict.EARLY, -128.4f)))
    }

    @Test
    fun `a late release names the direction and drops the sign`() {
        assertEquals("LATE BY 41MS", Readout.missLine(release(Verdict.LATE, 41.2f)))
    }

    @Test
    fun `no release at all is the overshoot fail and prints no number`() {
        // Phase.Failed reached from onFrame: the finger never lifted, so there is nothing to measure.
        assertEquals("HELD TOO LONG", Readout.missLine(null))
    }

    @Test
    fun `the precision line keeps its sign because the sign is the whole point`() {
        assertEquals("+7MS", Readout.precisionLine(release(Verdict.PERFECT, 7.4f)))
        assertEquals("-12MS", Readout.precisionLine(release(Verdict.PERFECT, -11.6f)))
    }

    @Test
    fun `a release that rounds to zero still shows a sign`() {
        assertEquals("+0MS", Readout.precisionLine(release(Verdict.PERFECT, 0.2f)))
        assertEquals("-0MS", Readout.precisionLine(release(Verdict.PERFECT, -0.2f)))
    }

    @Test
    fun `the precision line is empty when there is no release to report`() {
        assertEquals("", Readout.precisionLine(null))
    }

    @Test
    fun `rounding is to the nearest millisecond, not truncation`() {
        assertEquals("LATE BY 42MS", Readout.missLine(release(Verdict.LATE, 41.6f)))
    }
}
