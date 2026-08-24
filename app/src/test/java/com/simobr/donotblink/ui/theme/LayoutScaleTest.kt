package com.simobr.donotblink.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 6, item 2. The whole point of one continuous scale factor is that the arithmetic is
 * testable without a device, so every screen size the app can meet is checked here rather than
 * discovered by a player on a 720p phone.
 */
class LayoutScaleTest {

    // ---- layoutScaleFor -----------------------------------------------------------------------

    @Test
    fun the_mockup_height_is_exactly_one() {
        assertEquals(1f, layoutScaleFor(MOCKUP_HEIGHT_DP), 0f)
    }

    @Test
    fun a_shorter_window_scales_down_proportionally() {
        // The 360x640dp budget phone this stage was written for.
        assertEquals(640f / 844f, layoutScaleFor(640f), 0.0001f)
        assertEquals(760f / 844f, layoutScaleFor(760f), 0.0001f)
    }

    @Test
    fun the_lower_clamp_holds_below_the_readable_floor() {
        assertEquals(SCALE_MIN, layoutScaleFor(SCALE_MIN * MOCKUP_HEIGHT_DP - 1f), 0.0001f)
        assertEquals(SCALE_MIN, layoutScaleFor(300f), 0f)
        assertEquals(SCALE_MIN, layoutScaleFor(0f), 0f)
        assertEquals(SCALE_MIN, layoutScaleFor(-40f), 0f)
    }

    @Test
    fun the_upper_clamp_stops_the_composition_floating_in_black() {
        assertEquals(SCALE_MAX, layoutScaleFor(1200f), 0f)
        assertEquals(SCALE_MAX, layoutScaleFor(4000f), 0f)
    }

    @Test
    fun the_factor_never_leaves_its_clamps_at_any_plausible_height() {
        for (heightDp in 0..4000) {
            val scale = layoutScaleFor(heightDp.toFloat())
            assertTrue("height ${heightDp}dp gave $scale", scale in SCALE_MIN..SCALE_MAX)
        }
    }

    @Test
    fun the_factor_is_monotonic_in_height() {
        for (heightDp in 0..3999) {
            assertTrue(
                "scale dropped between ${heightDp}dp and ${heightDp + 1}dp",
                layoutScaleFor((heightDp + 1).toFloat()) >= layoutScaleFor(heightDp.toFloat()),
            )
        }
    }

    // ---- ringScaleFor -------------------------------------------------------------------------

    @Test
    fun a_portrait_phone_scales_the_ring_with_the_layout() {
        // 390x844: the mockup itself. Width is not the binding constraint there.
        assertEquals(1f, ringScaleFor(390f, 844f), 0.0001f)
    }

    @Test
    fun a_tall_narrow_window_is_bounded_by_width_not_height() {
        // 360dp wide, very tall: height alone would say 1.15 and run the ring off both sides.
        val scale = ringScaleFor(360f, 1400f)
        assertTrue("$scale should be width-bound, below the height factor", scale < layoutScaleFor(1400f))
        assertEquals((360f / 2f) / RING_HALF_EXTENT_DP, scale, 0.0001f)
    }

    @Test
    fun the_ring_never_exceeds_its_maximum_outer_radius() {
        for (widthDp in 200..2000 step 20) {
            for (heightDp in 400..2000 step 20) {
                val outerRadius = RING_OUTER_RADIUS_DP * ringScaleFor(widthDp.toFloat(), heightDp.toFloat())
                assertTrue(
                    "${widthDp}x${heightDp} gave an outer radius of ${outerRadius}dp",
                    outerRadius <= RING_MAX_OUTER_RADIUS_DP + 0.001f,
                )
            }
        }
    }

    @Test
    fun the_ring_fits_inside_the_width_on_every_size_it_is_not_floored_at() {
        for (widthDp in 320..1200 step 4) {
            for (heightDp in 500..1600 step 25) {
                val scale = ringScaleFor(widthDp.toFloat(), heightDp.toFloat())
                if (scale <= RING_SCALE_MIN) continue // the floor is allowed to overflow, see below
                val halfExtent = RING_HALF_EXTENT_DP * scale
                assertTrue(
                    "${widthDp}x${heightDp}: half-extent ${halfExtent}dp exceeds half the width",
                    halfExtent <= widthDp / 2f + 0.001f,
                )
            }
        }
    }

    @Test
    fun the_ring_is_never_squeezed_into_a_dot() {
        assertEquals(RING_SCALE_MIN, ringScaleFor(10f, 640f), 0.0001f)
        assertTrue(ringScaleFor(0f, 0f) >= RING_SCALE_MIN)
    }

    // ---- the constants themselves --------------------------------------------------------------

    @Test
    fun the_mockup_and_the_clamps_are_what_the_brief_named() {
        assertEquals(844f, MOCKUP_HEIGHT_DP, 0f)
        assertEquals(0.68f, SCALE_MIN, 0f)
        assertEquals(1.15f, SCALE_MAX, 0f)
        assertEquals(0.6f, TYPE_SCALE_FLOOR, 0f)
        assertEquals(600f, WIDE_WINDOW_THRESHOLD.value, 0f)
        assertEquals(420f, WIDE_CONTENT_MAX_WIDTH.value, 0f)
        assertEquals(210f, RING_MAX_OUTER_RADIUS_DP, 0f)
    }

    // ---- the defect this item exists for -------------------------------------------------------

    /**
     * The continue offer's DECLINE sat at an absolute 730dp with a ~14dp line under it. On the
     * 360x640dp phone that is most of this game's audience it was 104dp below the bottom edge:
     * the player was offered a continue they could neither accept nor decline.
     */
    @Test
    fun the_continue_offer_s_decline_line_fits_on_a_640dp_window() {
        val declineTopDp = 730f
        val lineHeightDp = 14f
        val scaled = declineTopDp * layoutScaleFor(640f) + lineHeightDp
        assertTrue("DECLINE would sit at ${scaled}dp on a 640dp window", scaled <= 640f)

        // And at the lower clamp, which is the worst case the app will render at.
        val worstCase = declineTopDp * SCALE_MIN + lineHeightDp
        assertTrue("DECLINE at the clamp sits at ${worstCase}dp", worstCase <= SCALE_MIN * MOCKUP_HEIGHT_DP)
    }
}
