package com.asmr.player.ui.downloads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FinalPolishBottomProgressTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun progressLine_isOneDpHighAndFlushWithItemBottom() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Box(
                    modifier = Modifier
                        .width(320.dp)
                        .height(68.dp)
                        .testTag(ITEM_TAG)
                ) {
                    FinalPolishBottomProgress(
                        trackColor = Color.LightGray,
                        progressColor = Color.Blue
                    )
                }
            }
        }

        val itemBounds = composeRule.onNodeWithTag(ITEM_TAG).getUnclippedBoundsInRoot()
        val progressBounds = composeRule.onNodeWithTag(FINAL_POLISH_PROGRESS_TAG)
            .getUnclippedBoundsInRoot()

        assertEquals(1.dp, progressBounds.bottom - progressBounds.top)
        assertEquals(itemBounds.bottom, progressBounds.bottom)
        assertEquals(itemBounds.left, progressBounds.left)
        assertEquals(itemBounds.right, progressBounds.right)
    }

    private companion object {
        const val ITEM_TAG = "final_polish_progress_item"
    }
}
