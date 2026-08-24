package com.simobr.donotblink

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simobr.donotblink.ui.continueoffer.ContinueOfferScreen
import com.simobr.donotblink.ui.continueoffer.ContinueTags
import com.simobr.donotblink.ui.theme.ScaledLayout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 6, item 2.
 *
 * Every screen in this app was laid out with absolute dp off a 390 x 844 mockup. On the 360 x 640dp
 * budget phone that is most of this game's audience, the rewarded-continue screen's WATCH and
 * DECLINE were both below the bottom edge: the player was offered a continue they could neither
 * accept nor decline except with the back gesture.
 *
 * [ScaledLayout] is the fix, and it is the same call AppRoot makes — this test drives the real
 * path, not a reimplementation of it.
 */
@RunWith(AndroidJUnit4::class)
class SmallWindowLayoutTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setSmallWindow() {
        rule.setContent {
            Box(
                Modifier
                    .size(width = SMALL_WIDTH, height = SMALL_HEIGHT)
                    .testTag(SURFACE)
            ) {
                ScaledLayout {
                    ContinueOfferScreen(streak = 12, onWatch = {}, onDecline = {})
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun the_continue_offer_is_entirely_on_screen_on_a_360_by_640_window() {
        setSmallWindow()

        val surface = rule.onNodeWithTag(SURFACE).getUnclippedBoundsInRoot()

        rule.onNodeWithTag(ContinueTags.WATCH).assertIsDisplayed()
        rule.onNodeWithTag(ContinueTags.DECLINE).assertIsDisplayed()

        assertInside(surface, rule.onNodeWithTag(ContinueTags.WATCH).getUnclippedBoundsInRoot(), "WATCH")
        assertInside(surface, rule.onNodeWithTag(ContinueTags.DECLINE).getUnclippedBoundsInRoot(), "DECLINE")
    }

    @Test
    fun the_offer_s_own_stack_stays_in_order() {
        setSmallWindow()

        val streak = rule.onNodeWithTag(ContinueTags.STREAK).getUnclippedBoundsInRoot()
        val watch = rule.onNodeWithTag(ContinueTags.WATCH).getUnclippedBoundsInRoot()
        val decline = rule.onNodeWithTag(ContinueTags.DECLINE).getUnclippedBoundsInRoot()

        assertTrue("the streak numeral must stay above WATCH", streak.bottom <= watch.top)
        assertTrue("WATCH must stay above DECLINE", watch.bottom <= decline.top)
    }

    private fun assertInside(surface: DpRect, node: DpRect, name: String) {
        assertTrue(
            "$name (${node.top}..${node.bottom}) falls outside the " +
                "${SMALL_WIDTH} x ${SMALL_HEIGHT} window (${surface.top}..${surface.bottom})",
            node.top >= surface.top && node.bottom <= surface.bottom,
        )
        assertTrue(
            "$name (${node.left}..${node.right}) falls outside the window horizontally",
            node.left >= surface.left && node.right <= surface.right,
        )
    }

    private companion object {
        val SMALL_WIDTH = 360.dp
        val SMALL_HEIGHT = 640.dp
        const val SURFACE = "small-window-surface"
    }
}
