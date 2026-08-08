package com.asmr.player.ui.downloads

import com.asmr.player.subtitle.SubtitleItemState
import com.asmr.player.subtitle.SubtitleTaskItemUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TranslationTaskStateTest {
    @Test
    fun transcribedItem_neverDisplaysAsTranslationComplete() {
        val stage = subtitleItemStage(item(SubtitleItemState.QUEUED_TRANSLATION))

        assertEquals("日文已生成，等待翻译", stage)
        assertFalse(stage.contains("完成"))
    }

    @Test
    fun waitingSlot_isNotDisplayedAsWaitingNetwork() {
        assertEquals("等待翻译槽位", subtitleItemStage(item(SubtitleItemState.WAITING_SLOT)))
        assertEquals("等待网络", subtitleItemStage(item(SubtitleItemState.WAITING_NETWORK)))
    }

    @Test
    fun translatingStage_describesTheSingleFullRequest() {
        val mapped = item(SubtitleItemState.TRANSLATING).copy(translationCursor = 24).toTranslationTaskUi(task())

        assertEquals("AI 正在确认字幕", mapped.stage)
        assertEquals("已确认 24/80", mapped.progressLabel)
        assertEquals(0.3f, mapped.progress)
    }

    @Test
    fun transcribingStage_doesNotRepeatPercentageAndKeepsZeroProgress() {
        val item = item(SubtitleItemState.TRANSCRIBING).copy(
            transcriptionProgress = 0,
            translationTotal = 0
        )
        val mapped = item.toTranslationTaskUi(task())

        assertEquals("转录中", mapped.stage)
        assertFalse(mapped.stage.contains("%"))
        assertEquals("0%", mapped.progressLabel)
        assertEquals(0f, mapped.progress)
    }

    @Test
    fun pausedTranscription_keepsLastTranscriptionProgress() {
        val item = item(SubtitleItemState.PAUSED).copy(
            transcriptionProgress = 42,
            translationTotal = 0
        )
        val mapped = item.toTranslationTaskUi(task())

        assertEquals("42%", mapped.progressLabel)
        assertEquals(0.42f, mapped.progress)
    }

    private fun item(state: String) = SubtitleTaskItemUi(
        id = "item",
        taskId = "task",
        trackId = 7L,
        title = "测试音频",
        mode = "GENERATED",
        state = state,
        transcriptionProgress = 100,
        transcribedMs = 30_000L,
        totalDurationMs = 30_000L,
        translationCursor = 0,
        translationTotal = 80,
        translationBatchIndex = 0,
        translationBatchTotal = 1,
        attempt = 0,
        nextAttemptAt = 0L,
        errorMessage = "",
        createdAt = 1L
    )

    private fun task() = com.asmr.player.subtitle.SubtitleTaskUi(
        id = "task",
        title = "测试作品",
        rjCode = "RJ00000001",
        state = "ACTIVE",
        warning = "",
        createdAt = 1L,
        items = emptyList()
    )
}
