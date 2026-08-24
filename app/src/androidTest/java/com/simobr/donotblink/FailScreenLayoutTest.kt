package com.simobr.donotblink

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simobr.donotblink.game.TITLES
import com.simobr.donotblink.game.Title
import com.simobr.donotblink.ui.fail.FailScreen
import com.simobr.donotblink.ui.fail.FailTags
import com.simobr.donotblink.ui.theme.ScaledLayout
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

    private fun setContent(
        surfaceHeight: Dp,
        bannerSlot: Dp,
        unlocked: List<Title> = emptyList(),
    ) {
        rule.setContent {
            Box(Modifier.size(width = 360.dp, height = surfaceHeight)) {
                ScaledLayout {
                    FailScreen(
                        streak = 12,
                        best = 40,
                        newlyUnlocked = unlocked,
                        onAgain = {},
                        onHome = {},
                        bannerEnabled = false,
                        bannerSlotHeight = bannerSlot,
                    )
                }
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

    // ---- Stage 6, item 1 -------------------------------------------------------------------

    /**
     * The unlocked-title list used to be top-anchored at an absolute 520dp and to grow DOWNWARD,
     * while AGAIN and HOME grew UPWARD from the bottom edge. Two stacks moving toward each other
     * in the same space meet: AGAIN was drawn on top of the title rows, and on a six-title run it
     * swallowed HOME as well. One bottom-anchored stack cannot do that at any size.
     *
     * This fails on the pre-fix code.
     */
    @Test
    fun the_unlocked_title_list_never_collides_with_again_or_home() {
        setContent(surfaceHeight = 640.dp, bannerSlot = 60.dp, unlocked = TITLES.take(4))

        val again = rule.onNodeWithTag(FailTags.AGAIN).getUnclippedBoundsInRoot()
        val home = rule.onNodeWithTag(FailTags.HOME).getUnclippedBoundsInRoot()
        val titles = rule.onNodeWithTag(FailTags.UNLOCKED).getUnclippedBoundsInRoot()
        val banner = rule.onNodeWithTag(FailTags.BANNER).getUnclippedBoundsInRoot()

        assertTrue(
            "AGAIN (${again.top}..${again.bottom}) overlaps the title list " +
                "(${titles.top}..${titles.bottom})",
            titles.bottom <= again.top,
        )
        assertTrue("AGAIN overlaps HOME", again.bottom <= home.top)
        assertTrue("HOME overlaps the banner slot", home.bottom <= banner.top)
    }

    /** Six titles in one run is possible early on. The list is capped; nothing is drawn over. */
    @Test
    fun an_overflowing_run_shows_four_rows_and_a_count_not_six_rows() {
        setContent(surfaceHeight = 640.dp, bannerSlot = 60.dp, unlocked = TITLES.take(6))

        val again = rule.onNodeWithTag(FailTags.AGAIN).getUnclippedBoundsInRoot()
        val titles = rule.onNodeWithTag(FailTags.UNLOCKED).getUnclippedBoundsInRoot()
        assertTrue("AGAIN overlaps the capped title list", titles.bottom <= again.top)

        rule.onNodeWithText("+2 MORE").assertExists()
        // The fifth and sixth names are not drawn; the TITLES screen lists them instead.
        rule.onNodeWithText(TITLES[4].name).assertDoesNotExist()
    }
}
