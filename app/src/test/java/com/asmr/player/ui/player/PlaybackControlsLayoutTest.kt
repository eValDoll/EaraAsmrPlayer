package com.asmr.player.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.createEaraComposeRule
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h600dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaybackControlsLayoutTest {
    @get:Rule val composeRule = createEaraComposeRule()

    @Test
    fun resizingControlPanePreservesAllButtonSizesAndMovesActionsToTheirOwnRow() {
        val width = mutableStateOf(640.dp)
        composeRule.setContent {
            AsmrPlayerTheme {
                Box(Modifier.width(width.value).testTag("pane")) {
                    PlaybackControlsLayout(
                        landscapeControls = true,
                        compactLayout = false,
                        showActionRow = true,
                        actionButtonSize = 48.dp,
                        coreButtonSize = 52.dp,
                        playButtonSize = 76.dp,
                        modifier = Modifier.heightIn(min = 80.dp),
                        actions = {
                            repeat(3) { index ->
                                IconButton(onClick = {}, modifier = Modifier.size(48.dp).testTag("action$index")) {}
                            }
                        },
                        core = {
                            repeat(5) { index ->
                                IconButton(onClick = {}, modifier = Modifier.size(if (index == 2) 76.dp else 52.dp).testTag("core$index")) {}
                            }
                        },
                    )
                }
            }
        }
        // 覆盖大平板、1024dp 全屏右栏、2/3 分屏右栏及最小可用宽度，并返回大窗口。
        for (availableWidth in listOf(640.dp, 506.dp, 480.dp, 311.dp, 284.dp, 640.dp)) {
            composeRule.runOnIdle { width.value = availableWidth }
            val pane = composeRule.onNodeWithTag("pane").assertWidthIsEqualTo(availableWidth).fetchSemanticsNode().boundsInRoot
            for (index in 0..4) {
                val button = composeRule.onNodeWithTag("core$index")
                button.assertIsDisplayed().assertWidthIsEqualTo(if (index == 2) 76.dp else 52.dp)
                val bounds = button.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.left >= pane.left && bounds.right <= pane.right + 1f)
            }
            for (index in 0..2) {
                composeRule.onNodeWithTag("action$index").assertIsDisplayed().assertWidthIsEqualTo(48.dp)
            }
            val actions = composeRule.onNodeWithTag("action0").fetchSemanticsNode().boundsInRoot
            val play = composeRule.onNodeWithTag("core2").fetchSemanticsNode().boundsInRoot
            if (availableWidth < 492.dp) assertTrue("width=$availableWidth actions=$actions play=$play", actions.bottom < play.top)
            else assertTrue("width=$availableWidth pane=$pane actions=$actions play=$play", kotlin.math.abs(actions.center.y - play.center.y) <= 1f)
        }
    }
}
