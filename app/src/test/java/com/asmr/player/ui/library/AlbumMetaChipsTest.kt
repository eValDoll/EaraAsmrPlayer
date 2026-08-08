package com.asmr.player.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlbumMetaChipsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun headerLabels_doNotEllipsizeWithLargeFontScale() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                AsmrPlayerTheme {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AlbumHeaderCvFlow(cvText = "声优甲")
                        AlbumHeaderTagsFlow(tags = listOf("治愈"))
                    }
                }
            }
        }

        assertTextDoesNotOverflow("声优")
        assertTextDoesNotOverflow("标签")
    }

    @Test
    fun headerTags_expandAndCollapseMoveFollowingContentSmoothly() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AsmrPlayerTheme {
                Column(modifier = Modifier.width(240.dp)) {
                    AlbumHeaderTagsFlow(
                        tags = List(20) { index -> "较长标签${index + 1}" },
                    )
                    Text("后续内容")
                }
            }
        }

        val followingContent = composeRule.onNodeWithText("后续内容")
        val collapsedTop = followingContent.getUnclippedBoundsInRoot().top

        composeRule.onNodeWithText("展开", substring = true).performClick()
        composeRule.mainClock.advanceTimeBy(140)
        val expandingTop = followingContent.getUnclippedBoundsInRoot().top
        composeRule.mainClock.advanceTimeBy(300)
        val expandedTop = followingContent.getUnclippedBoundsInRoot().top

        assertTrue(expandingTop > collapsedTop)
        assertTrue(expandingTop < expandedTop)

        composeRule.onNodeWithText("收起").performClick()
        composeRule.mainClock.advanceTimeBy(140)
        val collapsingTop = followingContent.getUnclippedBoundsInRoot().top
        composeRule.mainClock.advanceTimeBy(300)
        val collapsedAgainTop = followingContent.getUnclippedBoundsInRoot().top

        assertTrue(collapsingTop < expandedTop)
        assertTrue(collapsingTop > collapsedAgainTop)
    }

    private fun assertTextDoesNotOverflow(text: String) {
        val textLayoutResults = mutableListOf<TextLayoutResult>()
        val node = composeRule.onNodeWithText(text)
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(textLayoutResults)
        }
        val layout = textLayoutResults.single()
        assertFalse(
            "$text should not ellipsize: size=${layout.size}, lines=${layout.lineCount}, " +
                "bounds=${node.getUnclippedBoundsInRoot()}",
            layout.isLineEllipsized(0)
        )
    }
}
