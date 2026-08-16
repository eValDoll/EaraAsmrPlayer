package com.asmr.player.ui.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.layout.Box
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
    fun itemTags_keepVisibleTagsInteractiveAndSummarizeTheRest() {
        var clickedTag = ""
        composeRule.setContent {
            AsmrPlayerTheme {
                AlbumItemTagsLightweight(
                    tags = listOf("环境音", "治愈", "睡眠", "耳语"),
                    maxVisibleItems = 2,
                    onTagClick = { clickedTag = it },
                )
            }
        }

        composeRule.onNodeWithText("#环境音").assertExists().performClick()
        composeRule.onNodeWithText("#治愈").assertExists()
        composeRule.onNodeWithText("+2").assertExists()
        composeRule.onNodeWithText("·").assertDoesNotExist()
        composeRule.onNodeWithText("#睡眠").assertDoesNotExist()
        assertEquals("环境音", clickedTag)
    }

    @Test
    fun itemCv_usesSlashAndKeepsHiddenCountUnprefixed() {
        composeRule.setContent {
            AsmrPlayerTheme {
                AlbumItemCvLightweight(
                    cvText = "声优甲 / 声优乙 / 声优丙",
                    maxVisibleItems = 2,
                )
            }
        }

        composeRule.onNodeWithText("声优甲").assertExists()
        composeRule.onNodeWithText("/").assertExists()
        composeRule.onNodeWithText("声优乙").assertExists()
        composeRule.onNodeWithText("+1").assertExists()
        composeRule.onNodeWithText("/+1").assertDoesNotExist()
        composeRule.onNodeWithText("、").assertDoesNotExist()
        composeRule.onNodeWithText("·").assertDoesNotExist()
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
