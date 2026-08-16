package com.asmr.player.ui.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumMetaLightweightTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scrollableMeta_keepsEveryValueAvailableWithoutOverflowSummary() {
        var clickedTag = ""
        composeRule.setContent {
            AsmrPlayerTheme {
                Column(modifier = Modifier.width(140.dp)) {
                    AlbumItemCvLightweight(
                        cvText = "声优甲 / 声优乙 / 声优丙",
                    )
                    AlbumItemTagsLightweight(
                        tags = listOf("环境音", "治愈", "睡眠", "耳语"),
                        onTagClick = { clickedTag = it },
                    )
                }
            }
        }

        composeRule.onNodeWithText("#环境音").assertExists().performClick()
        composeRule.onNodeWithText("#治愈").assertExists()
        composeRule.onNodeWithText("#睡眠").assertExists()
        composeRule.onNodeWithText("#耳语").assertExists()
        composeRule.onNodeWithText("声优丙").assertExists()
        composeRule.onNodeWithText("/").assertDoesNotExist()
        composeRule.onNodeWithText("+1").assertDoesNotExist()
        composeRule.onNodeWithText("+2").assertDoesNotExist()
        assertEquals("环境音", clickedTag)
    }

    @Test
    fun flowMeta_wrapsAndShowsEveryValueWithoutOverflowSummary() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Column(modifier = Modifier.width(140.dp)) {
                    AlbumItemCvLightweight(
                        cvText = "声优丁 / 声优戊 / 声优己",
                        layout = AlbumInlineValuesLayout.Flow,
                    )
                    AlbumItemTagsLightweight(
                        tags = listOf("白噪音", "角色扮演", "助眠", "掏耳"),
                        layout = AlbumInlineValuesLayout.Flow,
                    )
                }
            }
        }

        composeRule.onNodeWithText("声优丁").assertExists()
        composeRule.onNodeWithText("声优戊").assertExists()
        composeRule.onNodeWithText("声优己").assertExists()
        composeRule.onNodeWithText("#白噪音").assertExists()
        composeRule.onNodeWithText("#角色扮演").assertExists()
        composeRule.onNodeWithText("#助眠").assertExists()
        composeRule.onNodeWithText("#掏耳").assertExists()
        composeRule.onNodeWithText("/").assertDoesNotExist()
        composeRule.onNodeWithText("+1").assertDoesNotExist()
        composeRule.onNodeWithText("+2").assertDoesNotExist()
    }

    @Test
    fun primaryMeta_pinsRjToTheRightWithoutDotSeparator() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    AlbumItemPrimaryMetaLightweight(
                        rjCode = "RJ123456",
                        circle = "测试社团",
                    )
                }
            }
        }

        val circleBounds = composeRule.onNodeWithText("测试社团").getUnclippedBoundsInRoot()
        val rjBounds = composeRule.onNodeWithText("RJ123456").getUnclippedBoundsInRoot()

        composeRule.onNodeWithText("·").assertDoesNotExist()
        assertTrue(rjBounds.left > circleBounds.right)
        assertTrue(rjBounds.right >= 318.dp)
    }
}
