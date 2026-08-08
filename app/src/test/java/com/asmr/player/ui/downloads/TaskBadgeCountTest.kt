package com.asmr.player.ui.downloads

import com.asmr.player.subtitle.SubtitleItemState
import com.asmr.player.subtitle.SubtitleTaskItemUi
import com.asmr.player.subtitle.SubtitleTaskUi
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskBadgeCountTest {
    @Test
    fun downloadBadge_countsEveryActiveFileWithinTheSameAlbum() {
        val activeTask = downloadTask(
            id = 1L,
            states = listOf(DownloadItemState.RUNNING, DownloadItemState.ENQUEUED)
        )
        val finishedTask = downloadTask(id = 2L, states = listOf(DownloadItemState.SUCCEEDED))

        assertEquals(2, countActiveDownloadFiles(listOf(activeTask, finishedTask)))
    }

    @Test
    fun translationBadge_countsEveryActiveSubtitleTaskItemWithinTheSameAlbum() {
        val activeTask = subtitleTask(
            id = "active",
            states = listOf(SubtitleItemState.TRANSCRIBING, SubtitleItemState.WAITING_SLOT)
        )
        val suspendedTask = subtitleTask(id = "paused", states = listOf(SubtitleItemState.PAUSED))
        val finishedTask = subtitleTask(id = "finished", states = listOf(SubtitleItemState.SUCCEEDED))

        assertEquals(2, countActiveSubtitleTaskItems(listOf(activeTask, suspendedTask, finishedTask)))
    }

    private fun downloadTask(id: Long, states: List<DownloadItemState>): DownloadTaskUi {
        val items = states.mapIndexed { index, state ->
            DownloadItemUi(
                taskId = id,
                workId = "$id-$index",
                relativePath = "$index.mp3",
                fileName = "$index.mp3",
                targetDir = "",
                filePath = "",
                state = state,
                downloaded = 0L,
                total = 0L,
                speed = 0L
            )
        }
        return DownloadTaskUi(
            taskId = id,
            taskKey = id.toString(),
            title = "RJ$id",
            subtitle = "",
            rootDir = "",
            state = states.firstOrNull() ?: DownloadItemState.CANCELLED,
            progressFraction = null,
            hasUnknownTotalRunning = false,
            downloadedBytes = 0L,
            totalBytes = null,
            speed = 0L,
            albumCover = TaskAlbumCoverUi(),
            items = items
        )
    }

    private fun subtitleTask(id: String, states: List<String>): SubtitleTaskUi {
        return SubtitleTaskUi(
            id = id,
            title = id,
            rjCode = "RJ00000001",
            state = "",
            warning = "",
            createdAt = 0L,
            items = states.mapIndexed { index, state ->
                SubtitleTaskItemUi(
                    id = "$id-$index",
                    taskId = id,
                    trackId = index.toLong(),
                    title = index.toString(),
                    mode = "GENERATED",
                    state = state,
                    transcriptionProgress = 0,
                    transcribedMs = 0L,
                    totalDurationMs = 0L,
                    translationCursor = 0,
                    translationTotal = 0,
                    translationBatchIndex = 0,
                    translationBatchTotal = 0,
                    attempt = 0,
                    nextAttemptAt = 0L,
                    errorMessage = "",
                    createdAt = 0L
                )
            }
        )
    }
}
