package com.asmr.player.ui.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.work.WorkInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkManager
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.dao.DownloadDao
import com.asmr.player.data.remote.download.DownloadWorker
import com.asmr.player.data.remote.download.FinalizeDownloadTaskWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import java.util.UUID
import androidx.work.workDataOf
import com.asmr.player.util.MessageManager

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
    val items: List<DownloadItemUi>
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val messageManager: MessageManager
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)

    val tasks: StateFlow<List<DownloadTaskUi>> =
        downloadDao.observeTasksWithItems()
            .map { list ->
                list.map { taskWithItems ->
                    val task = taskWithItems.task
                    val items = taskWithItems.items.map { item ->
                        val state = when (item.state) {
                            "PAUSED" -> DownloadItemState.PAUSED
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
                    val allSucceeded = items.isNotEmpty() && items.all { it.state == DownloadItemState.SUCCEEDED }
                    val state = when {
                        hasRunning -> DownloadItemState.RUNNING
                        hasFailed -> DownloadItemState.FAILED
                        allSucceeded -> DownloadItemState.SUCCEEDED
                        else -> DownloadItemState.ENQUEUED
                    }

                    val knownTotal = items.filter { it.total > 0 }.sumOf { it.total }
                    val knownDownloaded = items.filter { it.total > 0 }.sumOf { it.downloaded.coerceAtMost(it.total) }
                    val progressFraction = when {
                        allSucceeded -> 1f
                        knownTotal > 0 -> (knownDownloaded.toDouble() / knownTotal.toDouble()).toFloat().coerceIn(0f, 1f)
                        else -> null
                    }
                    val hasUnknownTotalRunning = items.any {
                        (it.state == DownloadItemState.RUNNING || it.state == DownloadItemState.ENQUEUED) && it.total <= 0
                    }

                    DownloadTaskUi(
                        taskId = task.id,
                        taskKey = task.taskKey,
                        title = task.title,
                        subtitle = task.subtitle,
                        rootDir = task.rootDir,
                        state = state,
                        progressFraction = progressFraction,
                        hasUnknownTotalRunning = hasUnknownTotalRunning,
                        items = items
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cancelItem(workId: String) {
        runCatching { 
            workManager.cancelWorkById(java.util.UUID.fromString(workId))
            messageManager.showInfo("已取消下载任务")
        }
    }

    fun pauseItem(workId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { workManager.cancelWorkById(UUID.fromString(workId)) }
            runCatching { 
                downloadDao.updateItemState(workId, "PAUSED", System.currentTimeMillis())
                messageManager.showInfo("下载已暂停")
            }
        }
    }

    fun resumeItem(workId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = downloadDao.getItemByWorkId(workId) ?: return@launch
            val task = downloadDao.getTaskById(item.taskId) ?: return@launch
            val targetDir = item.targetDir
            val fileName = item.fileName
            val inputData = workDataOf(
                "url" to item.url,
                "fileName" to fileName,
                "targetDir" to targetDir,
                "taskRootDir" to task.rootDir,
                "relativePath" to item.relativePath,
                "taskKey" to task.taskKey,
                "taskTitle" to task.title,
                "taskSubtitle" to task.subtitle
            )
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .addTag("download")
                .addTag(task.taskKey)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            val uniqueName = "${targetDir.trimEnd('/', '\\')}\\$fileName"
            workManager.enqueueUniqueWork(uniqueName.hashCode().toString(), ExistingWorkPolicy.REPLACE, request)

            val existingBytes = runCatching { File(item.filePath.ifBlank { File(targetDir, fileName).absolutePath }).length() }.getOrDefault(0L)
            val updatedAt = System.currentTimeMillis()
            downloadDao.replaceWorkIdForResume(
                oldWorkId = workId,
                newWorkId = request.id.toString(),
                state = WorkInfo.State.ENQUEUED.name,
                downloaded = existingBytes.coerceAtLeast(0L),
                updatedAt = updatedAt
            )
            messageManager.showInfo("下载已继续")
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

    fun cancelTask(taskKey: String) {
        if (taskKey.isBlank()) return
        runCatching { 
            workManager.cancelAllWorkByTag(taskKey)
            messageManager.showInfo("已取消所有任务")
        }
    }

    fun cancelAll() {
        runCatching { 
            workManager.cancelAllWorkByTag("download")
            messageManager.showInfo("已取消所有下载")
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
            messageManager.showInfo("已删除任务：${task.title}")
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
            messageManager.showInfo("已删除文件：${item.fileName}")
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
