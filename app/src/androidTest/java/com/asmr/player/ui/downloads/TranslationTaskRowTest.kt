package com.asmr.player.ui.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.subtitle.SubtitleItemState
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationTaskRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun progressAppearanceAndStateChanges_keepRowHeightAndSinglePercentage() {
        var task by mutableStateOf(
            translationTask(
                state = SubtitleItemState.TRANSCRIBING,
                progress = null,
                progressLabel = "42%",
                stage = "转录中"
            )
        )
        composeRule.setContent {
            AsmrPlayerTheme {
                Column {
                    TranslationTaskRow(
                        task = task,
                        onPause = {},
                        onResume = {},
                        onCancel = {},
                        onRetry = {}
                    )
                }
            }
        }

        val initialHeight = rowHeight()
        composeRule.onAllNodesWithText("42%").assertCountEquals(1)

        composeRule.runOnIdle {
            task = task.copy(progress = 0.42f)
        }
        composeRule.waitForIdle()
        assertEquals(initialHeight, rowHeight(), 1f)

        composeRule.runOnIdle {
            task = task.copy(state = SubtitleItemState.PAUSED, stage = "已暂停")
        }
        composeRule.waitForIdle()
        assertEquals(initialHeight, rowHeight(), 1f)
    }

    private fun rowHeight(): Float {
        val bounds = composeRule
            .onNodeWithTag("translation_task_row_item")
            .getUnclippedBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }

    private fun translationTask(
        state: String,
        progress: Float?,
        progressLabel: String,
        stage: String
    ) = TranslationTaskUi(
        itemId = "item",
        taskId = "task",
        createdAtMillis = 1L,
        trackId = 7L,
        rjCode = "RJ00000001",
        title = "测试音频",
        state = state,
        progress = progress,
        progressLabel = progressLabel,
        completedLines = 0,
        totalLines = 80,
        stage = stage,
        message = ""
    )
}
