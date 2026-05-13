package com.asmr.player.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntil
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.util.MessageManager
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ImagePreviewDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun singleImage_hidesNavigationAndClosesFromButtonsAndOverlay() {
        var dismissCount = 0

        composeRule.setContent {
            AsmrPlayerTheme {
                ImagePreviewDialog(
                    request = ImagePreviewRequest(
                        items = listOf(sampleItem("a"))
                    ),
                    messageManager = MessageManager(),
                    onDismiss = { dismissCount++ },
                    pageContent = { _, _, _ ->
                        Box(modifier = androidx.compose.ui.Modifier.fillMaxSize())
                    }
                )
            }
        }

        composeRule.onAllNodesWithTag(IMAGE_PREVIEW_COUNT_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(IMAGE_PREVIEW_PREV_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(IMAGE_PREVIEW_NEXT_TAG).assertCountEquals(0)

        composeRule.onNodeWithTag(IMAGE_PREVIEW_CLOSE_TAG).performClick()
        composeRule.onNodeWithTag(IMAGE_PREVIEW_OUTSIDE_TAG).performClick()

        assertEquals(2, dismissCount)
    }

    @Test
    fun multiImage_showsCountAndNavigationButtons() {
        composeRule.setContent {
            AsmrPlayerTheme {
                ImagePreviewDialog(
                    request = ImagePreviewRequest(
                        items = listOf(sampleItem("a"), sampleItem("b")),
                        initialIndex = 0
                    ),
                    messageManager = MessageManager(),
                    onDismiss = {},
                    pageContent = { _, _, _ ->
                        Box(modifier = androidx.compose.ui.Modifier.fillMaxSize())
                    }
                )
            }
        }

        composeRule.onNodeWithTag(IMAGE_PREVIEW_COUNT_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, IMAGE_PREVIEW_COUNT_TAG)
        )
        composeRule.onNodeWithTag(IMAGE_PREVIEW_PREV_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, IMAGE_PREVIEW_PREV_TAG)
        )
        composeRule.onNodeWithTag(IMAGE_PREVIEW_NEXT_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, IMAGE_PREVIEW_NEXT_TAG)
        )
    }

    @Test
    fun layoutSpec_matchesPlannedBounds() {
        assertEquals(0.92f, DefaultImagePreviewLayoutSpec.widthFraction)
        assertEquals(760, DefaultImagePreviewLayoutSpec.maxWidthDp)
        assertEquals(0.66f, DefaultImagePreviewLayoutSpec.heightFraction)
        assertEquals(260, DefaultImagePreviewLayoutSpec.minHeightDp)
        assertEquals(680, DefaultImagePreviewLayoutSpec.maxHeightDp)
    }

    @Test
    fun zoomedState_hidesNavigationButtons() {
        composeRule.setContent {
            AsmrPlayerTheme {
                ImagePreviewDialog(
                    request = ImagePreviewRequest(
                        items = listOf(sampleItem("a"), sampleItem("b")),
                        initialIndex = 0
                    ),
                    messageManager = MessageManager(),
                    onDismiss = {},
                    pageContent = { item, _, onStateChange ->
                        LaunchedEffect(item.key) {
                            onStateChange(ImagePreviewTransformState(scale = 2f, offset = Offset(24f, 0f)))
                        }
                        Box(modifier = androidx.compose.ui.Modifier.fillMaxSize())
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(IMAGE_PREVIEW_PREV_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(IMAGE_PREVIEW_NEXT_TAG).assertCountEquals(0)
    }

    @Test
    fun lazyPrepareImage_onlyResolvesVisitedPages() {
        var prepareCount by mutableIntStateOf(0)

        composeRule.setContent {
            AsmrPlayerTheme {
                ImagePreviewDialog(
                    request = ImagePreviewRequest(
                        items = listOf(
                            sampleLazyItem("a") { prepareCount++ },
                            sampleLazyItem("b") { prepareCount++ }
                        ),
                        initialIndex = 0
                    ),
                    messageManager = MessageManager(),
                    onDismiss = {},
                    pageContent = { _, _, _ ->
                        Box(modifier = androidx.compose.ui.Modifier.fillMaxSize())
                    }
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { prepareCount == 1 }
        composeRule.onNodeWithTag(IMAGE_PREVIEW_NEXT_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { prepareCount == 2 }
    }

    private fun sampleItem(key: String): ImagePreviewItem {
        return ImagePreviewItem(
            key = key,
            title = "Image $key",
            imageModel = "mock://$key",
            openPathOrUrl = "https://example.com/$key.jpg"
        )
    }

    private fun sampleLazyItem(key: String, onPrepare: () -> Unit): ImagePreviewItem {
        return ImagePreviewItem(
            key = key,
            title = "Image $key",
            openPathOrUrl = "https://example.com/$key.jpg",
            prepareImage = {
                onPrepare()
                ImagePreviewPreparedItem(
                    imageModel = "mock://$key",
                    openPathOrUrl = "https://example.com/$key.jpg"
                )
            }
        )
    }
}
