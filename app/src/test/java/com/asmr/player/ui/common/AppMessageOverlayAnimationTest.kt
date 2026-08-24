package com.asmr.player.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.util.MessageType
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppMessageOverlayAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exitingMessage_repositionsRemainingMessageGradually() {
        composeRule.mainClock.autoAdvance = false
        val messages = mutableStateListOf(
            visibleMessage(id = 2L, message = "保留消息"),
            visibleMessage(id = 1L, message = "消失消息")
        )

        composeRule.setContent {
            AsmrPlayerTheme {
                Box(
                    modifier = Modifier.size(width = 360.dp, height = 800.dp),
                    contentAlignment = AppMessageOverlayAlignment
                ) {
                    AppMessageOverlay(messages = messages)
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(300L)

        val startTop = retainedMessageTop()
        composeRule.runOnIdle {
            messages[1] = messages[1].copy(isVisible = false)
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(90L)
        val middleTop = retainedMessageTop()

        composeRule.mainClock.advanceTimeBy(160L)
        val endTop = retainedMessageTop()

        assertTrue(middleTop < startTop)
        assertTrue(middleTop > endTop)
        assertTrue(endTop < startTop)
    }

    private fun retainedMessageTop(): Float {
        composeRule.waitForIdle()
        return composeRule.onNodeWithText("保留消息")
            .getUnclippedBoundsInRoot()
            .top
            .value
    }

    private fun visibleMessage(id: Long, message: String) = VisibleAppMessage(
        id = id,
        key = id.toString(),
        message = message,
        type = MessageType.Info,
        durationMs = 10_000L
    )
}
