package com.asmr.player.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.util.AudioQuality
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioMetadataLineTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun longCvNamesKeepSourceBadgeDurationAndFileSizeVisibleAcrossThemes() {
        composeRule.setContent {
            Column {
                ThemeMode.values().forEach { mode ->
                    AsmrPlayerTheme(mode = mode) {
                        Column(Modifier.width(240.dp).testTag(mode.name)) {
                            AudioMetadataLine(
                                text = "声优甲、声优乙、声优丙、声优丁、声优戊、声优己",
                                trailingText = audioTrailingText("01:23:45", "123.4 MB"),
                                source = AudioSource.Online,
                                quality = AudioQuality.SQ,
                            )
                        }
                    }
                }
            }
        }
        composeRule.onAllNodesWithContentDescription("在线").assertCountEquals(3)
        composeRule.onNodeWithText("在线").assertDoesNotExist()
        ThemeMode.values().forEach { mode ->
            val parent = hasAnyAncestor(hasTestTag(mode.name))
            val badge = composeRule.onNode(hasText("SQ") and parent)
            val duration = composeRule.onNode(hasText("01:23:45 · 123.4 MB") and parent)
            badge.assertIsDisplayed()
            duration.assertIsDisplayed()
            val container = composeRule.onNodeWithTag(mode.name).getUnclippedBoundsInRoot()
            val bounds = duration.getUnclippedBoundsInRoot()
            assertEquals(container.right.value, bounds.right.value, 0.5f)
            assertTrue(bounds.left >= badge.getUnclippedBoundsInRoot().right)
        }
    }

    @Test
    fun shortAndMissingCvKeepDurationAndSizeAtTheSameRightEdge() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Column(Modifier.width(240.dp).testTag("rows")) {
                    AudioMetadataLine(text = "声优甲", trailingText = audioTrailingText("01:05", "26.3 MB"), source = AudioSource.Local, quality = AudioQuality.HQ)
                    AudioMetadataLine(trailingText = audioTrailingText("", "1.2 GB"), source = AudioSource.Online, quality = AudioQuality.SQ)
                }
            }
        }
        val right = composeRule.onNodeWithTag("rows").getUnclippedBoundsInRoot().right.value
        listOf("01:05 · 26.3 MB", "1.2 GB").forEach { duration ->
            assertEquals(right, composeRule.onNodeWithText(duration).getUnclippedBoundsInRoot().right.value, 0.5f)
        }
    }

    @Test
    fun unknownQualityDoesNotInventABadge() {
        composeRule.setContent {
            AsmrPlayerTheme {
                AudioMetadataLine(source = AudioSource.Local, trailingText = audioTrailingText("01:05", null))
            }
        }
        composeRule.onAllNodesWithContentDescription("本地").assertCountEquals(1)
        composeRule.onNodeWithText("HQ").assertDoesNotExist()
        composeRule.onNodeWithText("SQ").assertDoesNotExist()
        composeRule.onNodeWithText("01:05").assertIsDisplayed()
    }
}
