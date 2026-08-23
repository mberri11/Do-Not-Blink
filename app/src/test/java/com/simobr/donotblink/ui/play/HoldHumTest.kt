package com.simobr.donotblink.ui.play

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hum's pitch curve. The synthesiser itself is an AudioTrack and is verified by ear on a
 * device; this is the part that can be a number.
 */
class HoldHumTest {

    @Test
    fun the_start_radius_hums_at_220_and_the_line_at_440() {
        assertEquals(220f, HoldHum.frequencyHz(START, START, TARGET), 0f)
        assertEquals(440f, HoldHum.frequencyHz(TARGET, START, TARGET), 0f)
    }

    @Test
    fun the_midpoint_of_the_travel_is_the_midpoint_of_the_interval() {
        val halfway = (START + TARGET) / 2f
        assertEquals(330f, HoldHum.frequencyHz(halfway, START, TARGET), 0.001f)
    }

    @Test
    fun the_mapping_is_linear_in_contraction_progress() {
        for (step in 0..100) {
            val progress = step / 100f
            val radius = START - (START - TARGET) * progress
            assertEquals(
                "progress $progress",
                220f + 220f * progress,
                HoldHum.frequencyHz(radius, START, TARGET),
                0.01f,
            )
        }
    }

    @Test
    fun a_ring_past_the_line_holds_the_line_s_pitch_instead_of_climbing_past_it() {
        // Overshoot: the ring keeps contracting for OVERSHOOT_MARGIN_DP past the band, and the
        // pitch must not run away above 440Hz while it does.
        assertEquals(440f, HoldHum.frequencyHz(TARGET - 12f, START, TARGET), 0f)
        assertEquals(440f, HoldHum.frequencyHz(-500f, START, TARGET), 0f)
    }

    @Test
    fun a_radius_wider_than_the_start_holds_the_start_pitch() {
        assertEquals(220f, HoldHum.frequencyHz(START + 40f, START, TARGET), 0f)
    }

    @Test
    fun a_degenerate_round_with_no_travel_does_not_divide_by_zero() {
        assertEquals(440f, HoldHum.frequencyHz(96f, 96f, 96f), 0f)
        assertEquals(440f, HoldHum.frequencyHz(96f, 90f, 96f), 0f)
    }

    @Test
    fun the_tone_is_quiet_and_ramped() {
        assertEquals(0.18f, HoldHum.AMPLITUDE, 0f)
        assertTrue("a shorter ramp clicks", HoldHum.RAMP_MS >= 12)
    }

    private companion object {
        const val START = 168f
        const val TARGET = 96f
    }
}
