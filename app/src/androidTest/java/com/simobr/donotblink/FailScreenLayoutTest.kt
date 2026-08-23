package com.simobr.donotblink

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simobr.donotblink.ui.fail.FailScreen
import com.simobr.donotblink.ui.fail.FailTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 5.5, item 3.
 *
 * HOME used to be placed at an absolute 690dp from the top, straight off the 390x844 mockup, while
 * the banner was bottom-anchored in a hardcoded 60dp slot. On a real ~800dp screen those two land
 * on each other and HOME is unreachable. The bottom block is now anchored to the bottom edge above
 * the reserved slot, so the only way this can regress is if someone reintroduces a mockup Y.
 */
@RunWith(AndroidJUnit4::class)
class FailScreenLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setContent(surfaceHeight: Dp, bannerSlot: Dp) {
        rule.setContent {
            Box(Modifier.size(width = 360.dp, height = surfaceHeight)) {
                FailScreen(
                    streak = 12,
                    best = 40,
                    newlyUnlocked = emptyList(),
                    onAgain = {},
                    onHome = {},
                    bannerEnabled = false,
                    bannerSlotHeight = bannerSlot,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun home_clears_the_banner_slot_on_a_short_screen() {
        setContent(surfaceHeight = 640.dp, bannerSlot = 60.dp)

        val home = rule.onNodeWithTag(FailTags.HOME).getUnclippedBoundsInRoot()
        val banner = rule.onNodeWithTag(FailTags.BANNER).getUnclippedBoundsInRoot()

        assertTrue(
            "HOME (${home.top}..${home.bottom}) overlaps the banner slot (${banner.top}..${banner.bottom})",
            home.bottom <= banner.top,
        )
        assertTrue(
            "AGAIN must stay above HOME",
            rule.onNodeWithTag(FailTags.AGAIN).getUnclippedBoundsInRoot().bottom <= home.top,
        )
    }

    @Test
    fun home_is_a_forty_eight_dp_touch_target_despite_its_ten_sp_type() {
        setContent(surfaceHeight = 640.dp, bannerSlot = 60.dp)

        val home = rule.onNodeWithTag(FailTags.HOME).getUnclippedBoundsInRoot()
        val height = home.bottom - home.top
        assertTrue("HOME's clickable height is $height", height >= 48.dp)
    }

    @Test
    fun the_slot_is_reserved_at_exactly_the_height_it_is_told() {
        setContent(surfaceHeight = 640.dp, bannerSlot = 60.dp)

        val banner = rule.onNodeWithTag(FailTags.BANNER).getUnclippedBoundsInRoot()
        val height = banner.bottom - banner.top
        assertTrue("banner slot is $height, not 60dp", height == 60.dp)
    }

    @Test
    fun a_taller_banner_pushes_the_block_up_rather_than_under_it() {
        setContent(surfaceHeight = 640.dp, bannerSlot = 100.dp)

        val home = rule.onNodeWithTag(FailTags.HOME).getUnclippedBoundsInRoot()
        val banner = rule.onNodeWithTag(FailTags.BANNER).getUnclippedBoundsInRoot()
        assertTrue("HOME still collides at a 100dp slot", home.bottom <= banner.top)
    }
}
