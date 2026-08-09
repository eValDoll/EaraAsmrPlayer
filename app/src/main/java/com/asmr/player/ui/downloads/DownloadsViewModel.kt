package com.asmr.player.ui.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.work.WorkInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.dao.DownloadDao
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.remote.download.DOWNLOAD_STATE_QUEUED
import com.asmr.player.data.remote.download.DownloadQueueCoordinator
import com.asmr.player.data.remote.download.FinalizeDownloadTaskWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import androidx.work.workDataOf
import javax.inject.Inject
import com.asmr.player.util.MessageManager
import com.asmr.player.subtitle.SubtitleFailureMessages
import com.asmr.player.subtitle.SubtitleItemState
import com.asmr.player.subtitle.SubtitleTaskRepository
import com.asmr.player.subtitle.SubtitleTaskUi
import com.asmr.player.subtitle.SubtitleTranslationTarget
import kotlin.math.max
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

private const val AlbumCoverQueryBatchSize = 900

enum class DownloadItemState {
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    PAUSED,
    CANCELLED
}

data class DownloadItemUi(
    val taskId: Long,
    val workId: String,
    val relativePath: String,
    val fileName: String,
    val targetDir: String,
    val filePath: String,
    val state: DownloadItemState,
    val downloaded: Long,
    val total: Long,
    val speed: Long
)

data class DownloadTaskUi(
    val taskId: Long,
    val taskKey: String,
    val title: String,
    val subtitle: String,
    val rootDir: String,
    val state: DownloadItemState,
    val progressFraction: Float?,
    val hasUnknownTotalRunning: Boolean,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val speed: Long,
    val albumCover: TaskAlbumCoverUi,
    val items: List<DownloadItemUi>
)

data class TaskAlbumCoverUi(
    val coverThumbPath: String = "",
    val coverPath: String = "",
    val coverUrl: String = ""
)

private fun TaskAlbumCoverUi.hasSource(): Boolean =
    coverThumbPath.isNotBlank() || coverPath.isNotBlank() || coverUrl.isNotBlank()

data class TranslationSubtitleUi(
    val trackId: Long,
    val title: String,
    val subtitleCount: Int
)

data class TranslationSubtitleGroupUi(
    val rjCode: String,
    val title: String,
    val albumCover: TaskAlbumCoverUi,
    val subtitles: List<TranslationSubtitleUi>
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val messageManager: MessageManager
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)
    private val subtitleTaskRepository = SubtitleTaskRepository.get(context)

    val tasks: StateFlow<List<DownloadTaskUi>> =
        combine(
            downloadDao.observeTasksWithItems(),
            downloadDao.observeTaskAlbumCovers()
        ) { list, albumCoverRows ->
            val albumCoversByTaskId = albumCoverRows.groupBy { it.taskId }
            list.map { taskWithItems ->
                val task = taskWithItems.task
                val items = taskWithItems.items.map { item ->
                    val state = when (item.state) {
                        "PAUSED" -> DownloadItemState.PAUSED
                        DOWNLOAD_STATE_QUEUED -> DownloadItemState.ENQUEUED
                        else -> when (runCatching { WorkInfo.State.valueOf(item.state) }.getOrDefault(WorkInfo.State.ENQUEUED)) {
                            WorkInfo.State.RUNNING -> DownloadItemState.RUNNING
                            WorkInfo.State.SUCCEEDED -> DownloadItemState.SUCCEEDED
                            WorkInfo.State.FAILED -> DownloadItemState.FAILED
                            WorkInfo.State.CANCELLED -> DownloadItemState.CANCELLED
                            else -> DownloadItemState.ENQUEUED
                        }
                    }
                    DownloadItemUi(
                        taskId = item.taskId,
                        workId = item.workId,
                        relativePath = item.relativePath,
                        fileName = item.fileName,
                        targetDir = item.targetDir,
                        filePath = item.filePath,
                        state = state,
                        downloaded = item.downloaded,
                        total = item.total,
                        speed = item.speed
                    )
                }.sortedBy { it.relativePath }

                val hasRunning = items.any { it.state == DownloadItemState.RUNNING || it.state == DownloadItemState.ENQUEUED }
                val hasFailed = items.any { it.state == DownloadItemState.FAILED }
                val hasPaused = items.any { it.state == DownloadItemState.PAUSED }
                val allCancelled = items.isNotEmpty() && items.all { it.state == DownloadItemState.CANCELLED }
                val allSucceeded = items.isNotEmpty() && items.all { it.state == DownloadItemState.SUCCEEDED }
                val state = when {
                    hasRunning -> DownloadItemState.RUNNING
                    hasFailed -> DownloadItemState.FAILED
                    allSucceeded -> DownloadItemState.SUCCEEDED
                    hasPaused -> DownloadItemState.PAUSED
                    allCancelled -> DownloadItemState.CANCELLED
                    else -> DownloadItemState.ENQUEUED
                }

                val hasUnknownTotalRunning = items.any {
                    (it.state == DownloadItemState.RUNNING || it.state == DownloadItemState.ENQUEUED) && it.total <= 0
                }
                val progressFraction = resolveTaskProgress(
                    items = items,
                    allSucceeded = allSucceeded,
                    hasUnknownTotalRunning = hasUnknownTotalRunning
                )
                val downloadedBytes = items.sumOf { it.downloaded.coerceAtLeast(0L) }
                val totalBytes = resolveTaskTotalBytes(
                    items = items,
                    allSucceeded = allSucceeded,
                    hasUnknownTotalRunning = hasUnknownTotalRunning
                )
                val speed = items.sumOf { item ->
                    if (item.state == DownloadItemState.RUNNING) item.speed.coerceAtLeast(0L) else 0L
                }

                val albumCover = albumCoversByTaskId[task.id]
                    .orEmpty()
                    .asSequence()
                    .map { row ->
                        TaskAlbumCoverUi(
                            coverThumbPath = row.coverThumbPath,
                            coverPath = row.coverPath,
                            coverUrl = row.coverUrl
                        )
                    }
                    .firstOrNull { it.hasSource() }
                    ?: TaskAlbumCoverUi(coverUrl = task.albumCoverUrl)
                DownloadTaskUi(
                    taskId = task.id,
                    taskKey = task.taskKey,
                    title = task.title,
                    subtitle = task.subtitle,
                    rootDir = task.rootDir,
                    state = state,
                    progressFraction = progressFraction,
                    hasUnknownTotalRunning = hasUnknownTotalRunning,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speed = speed,
                    albumCover = albumCover,
                    items = items
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val translationSubtitleGroups: StateFlow<List<TranslationSubtitleGroupUi>> =
        trackDao.observeSubtitleTrackSummaries()
            .map { rows ->
                rows.groupBy { row ->
                    row.rjCode.trim()
                        .ifBlank { row.workId.trim() }
                        .ifBlank { "未知RJ" }
                }.map { (rjCode, groupRows) ->
                    TranslationSubtitleGroupUi(
                        rjCode = rjCode,
                        title = groupRows.firstOrNull()?.albumTitle.orEmpty(),
                        albumCover = groupRows.asSequence()
                            .map { row ->
                                TaskAlbumCoverUi(
                                    coverThumbPath = row.coverThumbPath,
                                    coverPath = row.coverPath,
                                    coverUrl = row.coverUrl
                                )
                            }
                            .firstOrNull { it.hasSource() }
                            ?: TaskAlbumCoverUi(),
                        subtitles = groupRows.map { row ->
                            TranslationSubtitleUi(
                                trackId = row.trackId,
                                title = row.trackTitle.ifBlank { "本地字幕" },
                                subtitleCount = row.subtitleCount
                            )
                        }
                    )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    internal val subtitleTasks: StateFlow<List<SubtitleTaskUi>> = subtitleTaskRepository.tasks
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 正在执行作品级最终润色的作品 rjCode 集合。 */
    internal val polishingRjCodes: StateFlow<Set<String>> = subtitleTaskRepository.polishingRjCodes

    suspend fun loadTranslationAlbumCovers(trackIds: List<Long>): Map<Long, TaskAlbumCoverUi> {
        if (trackIds.isEmpty()) return emptyMap()
        return withContext(Dispatchers.IO) {
            val normalizedTrackIds = trackIds
                .filter { it > 0L }
                .distinct()
            val rows = buildList {
                normalizedTrackIds.chunked(AlbumCoverQueryBatchSize).forEach { ids ->
                    addAll(trackDao.getAlbumCoversForTracks(ids))
                }
            }
            rows.associate { row ->
                row.trackId to TaskAlbumCoverUi(
                    coverThumbPath = row.coverThumbPath,
                    coverPath = row.coverPath,
                    coverUrl = row.coverUrl
                )
            }
        }
    }

    val activeSubtitleTaskCount: StateFlow<Int> = subtitleTaskRepository.tasks
        .map { tasks ->
            tasks.sumOf { task ->
                task.items.count { item ->
                    item.state !in setOf(
                        SubtitleItemState.PAUSED,
                        SubtitleItemState.INTERRUPTED,
                        SubtitleItemState.FAILED
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private fun resolveTaskProgress(
        items: List<DownloadItemUi>,
        allSucceeded: Boolean,
        hasUnknownTotalRunning: Boolean
    ): Float? {
        if (allSucceeded) return 1f
        if (items.isEmpty()) return null

        val hasResolvableTotalsForAllItems = items.all { item ->
            item.total > 0L || item.state == DownloadItemState.SUCCEEDED
        }
        if (hasResolvableTotalsForAllItems) {
            val resolvedTotal = items.sumOf { item ->
                when {
                    item.total > 0L -> item.total
                    item.state == DownloadItemState.SUCCEEDED -> item.downloaded.coerceAtLeast(0L)
                    else -> 0L
                }
            }
            if (resolvedTotal > 0L) {
                val resolvedDownloaded = items.sumOf { item ->
                    when {
                        item.total > 0L -> item.downloaded.coerceIn(0L, item.total)
                        item.state == DownloadItemState.SUCCEEDED -> item.downloaded.coerceAtLeast(0L)
                        else -> 0L
                    }
                }
                return (resolvedDownloaded.toDouble() / resolvedTotal.toDouble()).toFloat().coerceIn(0f, 1f)
            }
        }

        val totalItems = items.size.toDouble()
        val aggregateProgress = items.sumOf { item ->
            when {
                item.state == DownloadItemState.SUCCEEDED -> 1.0
                item.total > 0L -> item.downloaded.coerceIn(0L, item.total).toDouble() / item.total.toDouble()
                else -> 0.0
            }
        } / totalItems

        if (aggregateProgress <= 0.0 && hasUnknownTotalRunning) return null
        return aggregateProgress.toFloat().coerceIn(0f, 1f)
    }

    private fun resolveTaskTotalBytes(
        items: List<DownloadItemUi>,
        allSucceeded: Boolean,
        hasUnknownTotalRunning: Boolean
    ): Long? {
        if (items.isEmpty()) return null

        if (allSucceeded) {
            return items.sumOf { item -> max(item.total, item.downloaded).coerceAtLeast(0L) }
        }

        // A file's size is fixed once the server reports it, so the task total is
        // simply the sum of the known item sizes and must never fluctuate while a
        // download is in progress. If an active item's size is still unknown, we
        // cannot present a trustworthy denominator, so hide it and let the UI show
        // an indeterminate state instead of a changing estimate.
        if (hasUnknownTotalRunning) return null

        return items.sumOf { item ->
            when {
                item.total > 0L -> item.total
                item.state == DownloadItemState.SUCCEEDED -> item.downloaded.coerceAtLeast(0L)
                else -> 0L
            }
        }.takeIf { it > 0L }
    }

    fun cancelItem(workId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { workManager.cancelWorkById(java.util.UUID.fromString(workId)) }
            runCatching { downloadDao.updateItemState(workId, WorkInfo.State.CANCELLED.name, System.currentTimeMillis()) }
            DownloadQueueCoordinator.requestSchedule(context)
        }
    }

    fun pauseItem(workId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { workManager.cancelWorkById(UUID.fromString(workId)) }
            runCatching {
                downloadDao.updateItemState(workId, "PAUSED", System.currentTimeMillis())
            }
            DownloadQueueCoordinator.requestSchedule(context)
        }
    }

    fun resumeItem(workId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = downloadDao.getItemByWorkId(workId) ?: return@launch
            val existingBytes = runCatching {
                File(item.filePath.ifBlank { File(item.targetDir, item.fileName).absolutePath }).length()
            }.getOrDefault(0L)
            val updatedAt = System.currentTimeMillis()
            downloadDao.updateItemProgress(
                workId = workId,
                state = DOWNLOAD_STATE_QUEUED,
                downloaded = existingBytes.coerceAtLeast(0L),
                total = item.total,
                speed = 0L,
                updatedAt = updatedAt
            )
            DownloadQueueCoordinator.requestSchedule(context)
        }
    }

    fun retryItem(workId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = downloadDao.getItemByWorkId(workId) ?: return@launch
            if (item.state != WorkInfo.State.FAILED.name) return@launch
            resumeItem(workId)
        }
    }

    fun retryFailedInTask(taskId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = downloadDao.getItemsForTask(taskId)
            items.filter { it.state == WorkInfo.State.FAILED.name }.forEach { retryItem(it.workId) }
        }
    }

    fun pauseTask(taskId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = downloadDao.getItemsForTask(taskId)
            items.filter { 
                it.state == WorkInfo.State.RUNNING.name || 
                it.state == WorkInfo.State.ENQUEUED.name ||
                it.state == DOWNLOAD_STATE_QUEUED
            }.forEach { item ->
                runCatching { workManager.cancelWorkById(UUID.fromString(item.workId)) }
                runCatching {
                    downloadDao.updateItemState(item.workId, "PAUSED", System.currentTimeMillis())
                }
            }
            DownloadQueueCoordinator.requestSchedule(context)
        }
    }

    fun resumeTask(taskId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = downloadDao.getItemsForTask(taskId)
            items.filter { it.state == "PAUSED" }.forEach { item ->
                resumeItem(item.workId)
            }

        }
    }

    fun pauseAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = downloadDao.getAllActiveOrPausedItems()
            items.filter { 
                it.state == WorkInfo.State.RUNNING.name || 
                it.state == WorkInfo.State.ENQUEUED.name ||
                it.state == DOWNLOAD_STATE_QUEUED
            }.forEach { item ->
                runCatching { workManager.cancelWorkById(UUID.fromString(item.workId)) }
                runCatching {
                    downloadDao.updateItemState(item.workId, "PAUSED", System.currentTimeMillis())
                }
            }
            DownloadQueueCoordinator.requestSchedule(context)
        }
    }

    fun resumeAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = downloadDao.getAllActiveOrPausedItems()
            items.filter { it.state == "PAUSED" }.forEach { item ->
                resumeItem(item.workId)
            }

        }
    }

    fun cancelTask(taskKey: String) {
        if (taskKey.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val task = downloadDao.getTaskByKey(taskKey)
            val now = System.currentTimeMillis()
            task?.let {
                downloadDao.getItemsForTask(it.id).forEach { item ->
                    runCatching { workManager.cancelWorkById(UUID.fromString(item.workId)) }
                    if (item.state != WorkInfo.State.SUCCEEDED.name) {
                        runCatching { downloadDao.updateItemState(item.workId, WorkInfo.State.CANCELLED.name, now) }
                    }
                }
            }
            DownloadQueueCoordinator.requestSchedule(context)
        }
    }

    fun cancelAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            downloadDao.getAllActiveOrPausedItems().forEach { item ->
                runCatching { workManager.cancelWorkById(UUID.fromString(item.workId)) }
                if (item.state != WorkInfo.State.SUCCEEDED.name) {
                    runCatching { downloadDao.updateItemState(item.workId, WorkInfo.State.CANCELLED.name, now) }
                }
            }
            DownloadQueueCoordinator.requestSchedule(context)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = downloadDao.getTaskById(taskId) ?: return@launch
            runCatching { workManager.cancelAllWorkByTag(task.taskKey) }
            val deleted = deletePathSafely(task.rootDir)
            if (!deleted) {
                val items = downloadDao.getItemsForTask(taskId)
                items.forEach { it ->
                    deletePathSafely(it.filePath.ifBlank { File(it.targetDir, it.fileName).absolutePath })
                }
                deletePathSafely(task.rootDir)
            }
            syncLibraryAfterDownloadRootDeleted(task.rootDir)
            downloadDao.deleteItemsForTask(taskId)
            downloadDao.deleteTaskById(taskId)
            DownloadQueueCoordinator.requestSchedule(context)
            messageManager.showInfo("已删除任务")
        }
    }

    fun deleteItem(workId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = downloadDao.getItemByWorkId(workId) ?: return@launch
            val task = downloadDao.getTaskById(item.taskId)
            runCatching { workManager.cancelWorkById(java.util.UUID.fromString(workId)) }
            val primary = item.filePath.ifBlank { File(item.targetDir, item.fileName).absolutePath }
            val deleted = deletePathSafely(primary)
            if (!deleted && item.targetDir.isNotBlank() && item.fileName.isNotBlank()) {
                deletePathSafely(File(item.targetDir, item.fileName).absolutePath)
            }
            downloadDao.deleteItemByWorkId(workId)
            downloadDao.deleteItemsByFilePath(primary)
            syncLibraryAfterDownloadedFileDeleted(primary, task?.rootDir.orEmpty())
            if (task != null && task.taskKey.isNotBlank()) {
                runCatching {
                    val finalizeInput = workDataOf(
                        "taskKey" to task.taskKey,
                        "taskTitle" to task.title,
                        "taskSubtitle" to task.subtitle,
                        "taskRootDir" to task.rootDir
                    )
                    val request = OneTimeWorkRequestBuilder<FinalizeDownloadTaskWorker>()
                        .setInputData(finalizeInput)
                        .addTag("download_finalize")
                        .addTag(task.taskKey)
                        .build()
                    workManager.enqueueUniqueWork(
                        "download_finalize_${task.taskKey.hashCode()}",
                        ExistingWorkPolicy.REPLACE,
                        request
                    )
                }
            }
            val remaining = downloadDao.countItemsForTask(item.taskId)
            if (remaining == 0) {
                downloadDao.deleteTaskById(item.taskId)
            }
            DownloadQueueCoordinator.requestSchedule(context)
            messageManager.showInfo("已删除文件：${item.fileName}")
        }
    }

    fun deleteSubtitleTrack(trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val activeItem = AppDatabaseProvider.get(context).subtitleTaskDao().getItemForTrack(trackId)
                if (activeItem != null) {
                    subtitleTaskRepository.cancelItem(activeItem.id)
                    messageManager.showInfo("已先取消对应字幕任务，清理完成后可删除字幕")
                    return@launch
                }
                trackDao.deleteSubtitlesForTrack(trackId)
            }
                .onFailure {
                    messageManager.showError("删除字幕失败")
                }
        }
    }

    fun deleteSubtitleTracks(trackIds: List<Long>) {
        val distinctTrackIds = trackIds.filter { it > 0L }.distinct()
        if (distinctTrackIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val subtitleTaskDao = AppDatabaseProvider.get(context).subtitleTaskDao()
                val activeItems = distinctTrackIds.mapNotNull { subtitleTaskDao.getItemForTrack(it) }
                if (activeItems.isNotEmpty()) {
                    activeItems.forEach { subtitleTaskRepository.cancelItem(it.id) }
                    messageManager.showInfo("已先取消对应字幕任务，清理完成后可删除字幕")
                    return@launch
                }
                trackDao.deleteSubtitlesForTracks(distinctTrackIds)
                messageManager.showInfo("已删除 ${distinctTrackIds.size} 个字幕")
            }.onFailure {
                messageManager.showError("删除字幕失败")
            }
        }
    }

    fun retrySubtitleTranslation(trackId: Long, title: String) {
        if (trackId <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val handle = subtitleTaskRepository.enqueueTranslation(
                    SubtitleTranslationTarget(
                        trackId = trackId,
                        title = title.ifBlank { "本地字幕" }
                    )
                )
                messageManager.showInfo(
                    if (handle.reusedExisting) "该音频已有可继续的字幕任务" else "已加入全局翻译队列"
                )
            }.onFailure { error ->
                showSubtitleActionFailure(error, fallback = "无法重新翻译字幕")
            }
        }
    }

    /** 手动触发某作品（按 rjCode 定位）的字幕最终润色。 */
    fun polishSubtitleAlbum(rjCode: String) = viewModelScope.launch {
        if (rjCode.isBlank()) return@launch
        val album = withContext(Dispatchers.IO) {
            runCatching { albumDao.getAlbumByWorkIdOnce(rjCode) }.getOrNull()
        }
        if (album == null) {
            messageManager.showWarning("找不到对应作品，无法润色")
            return@launch
        }
        val error = subtitleTaskRepository.requestAlbumPolish(album.id)
        if (error != null) {
            if (SubtitleFailureMessages.isUserActionWarning(error)) {
                messageManager.showWarning(error)
            } else {
                messageManager.showError(error)
            }
        }
    }

    fun pauseSubtitleItem(itemId: String) = viewModelScope.launch { subtitleTaskRepository.pauseItem(itemId) }

    fun resumeSubtitleItem(itemId: String) = viewModelScope.launch { subtitleTaskRepository.resumeItem(itemId) }

    fun cancelSubtitleItem(itemId: String) = viewModelScope.launch { subtitleTaskRepository.cancelItem(itemId) }

    fun retrySubtitleItem(itemId: String) = viewModelScope.launch {
        runCatching { subtitleTaskRepository.retryItem(itemId) }
            .onFailure { error ->
                showSubtitleActionFailure(error, fallback = "无法重试字幕任务")
            }
    }

    fun pauseSubtitleTask(taskId: String) = viewModelScope.launch { subtitleTaskRepository.pauseTask(taskId) }

    fun resumeSubtitleTask(taskId: String) = viewModelScope.launch { subtitleTaskRepository.resumeTask(taskId) }

    fun cancelSubtitleTask(taskId: String) = viewModelScope.launch { subtitleTaskRepository.cancelTask(taskId) }

    private fun showSubtitleActionFailure(error: Throwable, fallback: String) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: fallback
        if (SubtitleFailureMessages.isUserActionWarning(message)) {
            messageManager.showWarning(message)
        } else {
            messageManager.showError(message)
        }
    }

    private suspend fun syncLibraryAfterDownloadedFileDeleted(filePath: String, rootDir: String) {
        if (filePath.isBlank()) return
        val db = AppDatabaseProvider.get(context)
        db.withTransaction {
            val trackDao = db.trackDao()
            val trackTagDao = db.trackTagDao()
            val track = trackDao.getTrackByPathOnce(filePath) ?: return@withTransaction
            runCatching { trackDao.deleteSubtitlesForTrack(track.id) }
            runCatching { trackTagDao.deleteTrackTagsByTrackId(track.id) }
            runCatching { trackDao.deleteTrackById(track.id) }
            reconcileAlbumAfterDownloadChange(db, albumId = track.albumId, deletedRootDir = rootDir)
        }
    }

    private suspend fun syncLibraryAfterDownloadRootDeleted(rootDir: String) {
        if (rootDir.isBlank()) return
        val db = AppDatabaseProvider.get(context)
        db.withTransaction {
            val albumDao = db.albumDao()
            val albumsByDownload = albumDao.getAlbumsByDownloadPathOnce(rootDir).toMutableList()
            val byPath = albumDao.getAlbumByPathOnce(rootDir)
            if (byPath != null && albumsByDownload.none { it.id == byPath.id }) {
                albumsByDownload.add(byPath)
            }
            albumsByDownload.forEach { album ->
                removeDownloadedTracksFromAlbum(db, album.id, rootDir)
                reconcileAlbumAfterDownloadChange(db, albumId = album.id, deletedRootDir = rootDir)
            }
        }
    }

    private suspend fun removeDownloadedTracksFromAlbum(db: com.asmr.player.data.local.db.AppDatabase, albumId: Long, rootDir: String) {
        val trackDao = db.trackDao()
        val trackTagDao = db.trackTagDao()
        val tracks = runCatching { trackDao.getTracksForAlbumOnce(albumId) }.getOrDefault(emptyList())
        val toDelete = tracks.filter { it.path.startsWith(rootDir) }.map { it.id }
        if (toDelete.isEmpty()) return
        runCatching { trackDao.deleteSubtitlesForTracks(toDelete) }
        toDelete.forEach { id -> runCatching { trackTagDao.deleteTrackTagsByTrackId(id) } }
        runCatching { trackDao.deleteTracksByIds(toDelete) }
    }

    private suspend fun reconcileAlbumAfterDownloadChange(
        db: com.asmr.player.data.local.db.AppDatabase,
        albumId: Long,
        deletedRootDir: String
    ) {
        val albumDao = db.albumDao()
        val trackDao = db.trackDao()
        val tagDao = db.tagDao()
        val albumFtsDao = db.albumFtsDao()

        val album = albumDao.getAlbumById(albumId) ?: return
        val remainingTracks = runCatching { trackDao.getTracksForAlbumOnce(albumId) }.getOrDefault(emptyList())
        val localPath = album.localPath.orEmpty()
        val hasLocal = localPath.isNotBlank()
        val clearedCoverPath = if (deletedRootDir.isNotBlank() && album.coverPath.startsWith(deletedRootDir)) "" else album.coverPath

        if (remainingTracks.isEmpty()) {
            if (hasLocal) {
                albumDao.updateAlbum(album.copy(path = localPath, downloadPath = null, coverPath = clearedCoverPath))
            } else {
                runCatching { trackDao.deleteSubtitlesForAlbum(albumId) }
                runCatching { trackDao.deleteTracksForAlbum(albumId) }
                runCatching { tagDao.deleteAlbumTagsByAlbumId(albumId) }
                runCatching { albumFtsDao.deleteByAlbumId(albumId) }
                runCatching { db.onlineSavedResourceDao().deleteByAlbumId(albumId) }
                runCatching { albumDao.deleteAlbum(album) }
            }
            return
        }

        if (album.downloadPath == deletedRootDir) {
            val newPath = if (hasLocal) localPath else album.path
            albumDao.updateAlbum(album.copy(path = newPath, downloadPath = null, coverPath = clearedCoverPath))
        } else if (clearedCoverPath != album.coverPath) {
            albumDao.updateAlbum(album.copy(coverPath = clearedCoverPath))
        }
    }

    private fun deletePathSafely(path: String): Boolean {
        if (path.isBlank()) return false

        val externalBase = context.getExternalFilesDir(null)
        val allowedRoots = listOfNotNull(
            externalBase,
            context.filesDir,
            context.cacheDir
        )
            .mapNotNull { runCatching { it.canonicalFile }.getOrNull() ?: it.absoluteFile }

        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: File(path).absoluteFile
        val isAllowed = allowedRoots.any { root -> target.path.startsWith(root.path) }
        if (!isAllowed) return false
        if (!target.exists()) return false

        return if (target.isDirectory) {
            runCatching { target.deleteRecursively() }.getOrDefault(false)
        } else {
            runCatching { target.delete() }.getOrDefault(false)
        }
    }

}
