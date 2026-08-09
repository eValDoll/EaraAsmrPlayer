package com.asmr.player.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SwipeRevealActionsBoxTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var revealed by mutableStateOf(false)
    private var contentClickCount = 0
    private lateinit var closeController: SwipeRevealCloseController

    @Test
    fun repeatedSwipeAndSmallFingerJitter_closesWithoutOpeningContent() {
        setSwipeContent()

        repeat(2) {
            swipeOpen()
            tapVisibleContentWithSmallJitter()
            composeRule.waitUntil(timeoutMillis = 3_000) { !revealed }
            composeRule.waitForIdle()
        }

        assertEquals(0, contentClickCount)
    }

    @Test
    fun repeatedSwipeAndExternalDismiss_canCloseEveryTime() {
        setSwipeContent()

        repeat(2) {
            swipeOpen()
            composeRule.runOnIdle {
                closeController.requestClose()
                revealed = false
            }
            composeRule.waitForIdle()
            assertFalse(revealed)
        }
    }

    @Test
    fun externalDismissDuringOpeningAnimation_movesRightWithoutBacktrack() {
        composeRule.mainClock.autoAdvance = false
        try {
            setSwipeContent()
            composeRule.runOnIdle { revealed = true }
            repeat(4) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
            }
            val openingPosition = contentLeft()
            assertTrue("测试前置条件：卡片应已开始向左移动", openingPosition < -1f)

            composeRule.runOnIdle {
                closeController.requestClose()
                revealed = false
            }
            val closingPositions = mutableListOf(contentLeft())
            repeat(12) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
                closingPositions += contentLeft()
            }

            assertTrue(
                "空白点击触发收起后，卡片应从第一帧起持续向右移动",
                closingPositions.zipWithNext().all { (previous, next) -> next >= previous - 0.5f }
            )
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

    private fun setSwipeContent() {
        revealed = false
        contentClickCount = 0
        closeController = SwipeRevealCloseController()
        composeRule.setContent {
            AsmrPlayerTheme {
                SwipeRevealActionsBox(
                    modifier = Modifier
                        .width(320.dp)
                        .height(68.dp)
                        .testTag(SWIPE_BOX_TAG),
                    revealed = revealed,
                    enabled = true,
                    closeController = closeController,
                    onRevealedBoundsChanged = {},
                    onRevealedChange = { revealed = it },
                    actionWidth = 112.dp,
                    actions = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Red)
                        )
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .testTag(SWIPE_CONTENT_TAG)
                            .clickable { contentClickCount++ }
                    )
                }
            }
        }
    }

    private fun swipeOpen() {
        composeRule.onNodeWithTag(SWIPE_BOX_TAG).performTouchInput {
            swipe(
                start = Offset(center.x * 1.7f, center.y),
                end = Offset(center.x * 0.3f, center.y),
                durationMillis = 250
            )
        }
        composeRule.waitUntil(timeoutMillis = 3_000) { revealed }
        composeRule.waitForIdle()
        assertTrue(revealed)
    }

    private fun tapVisibleContentWithSmallJitter() {
        composeRule.onNodeWithTag(SWIPE_BOX_TAG).performTouchInput {
            val downPosition = Offset(x = 24f, y = center.y)
            down(downPosition)
            moveTo(downPosition + Offset(x = 2f, y = 1f))
            up()
        }
    }

    private fun contentLeft(): Float {
        return composeRule.onNodeWithTag(SWIPE_CONTENT_TAG)
            .getUnclippedBoundsInRoot()
            .left
            .value
    }

    private companion object {
        const val SWIPE_BOX_TAG = "swipe_reveal_actions_box_test"
        const val SWIPE_CONTENT_TAG = "swipe_reveal_actions_content_test"
    }
}
