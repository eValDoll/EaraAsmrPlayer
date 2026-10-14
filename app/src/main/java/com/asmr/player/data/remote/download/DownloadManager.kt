package com.asmr.player.data.remote.download

import com.asmr.player.util.LocalFileScope

import android.content.Context
import android.util.Log
import androidx.work.*
import com.asmr.player.data.local.db.dao.DownloadDao
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private data class PendingDownloadItemRequest(
    val url: String,
    val fileName: String,
    val targetDir: String,
    val taskRootDir: String,
    val relativePath: String,
    val taskSubtitle: String,
    val tags: List<String>,
    val albumTitle: String,
    val albumCircle: String,
    val albumCv: String,
    val albumTagsCsv: String,
    val albumCoverUrl: String,
    val albumDescription: String,
    val albumWorkId: String,
    val albumRjCode: String,
    val dlsitePlayImageSeed: Int?,
    val dlsitePlayImageWidth: Int?,
    val dlsitePlayImageHeight: Int?,
)

/** Validates download requests and persists queued tasks; scheduling is owned by DownloadQueueCoordinator. */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val directoryCoordinator: DownloadDirectoryCoordinator,
    private val storage: DownloadStorageGateway,
) {
    suspend fun enqueueBatch(request: DownloadBatchRequest): EnqueueDownloadBatchResult {
        if (request.items.isEmpty()) return EnqueueDownloadBatchResult.Accepted(0)
        val albumDirectory = request.albumDirectoryName.replace('\\', '/').trim('/').ifBlank { "download" }
        if (albumDirectory.contains('/') || albumDirectory == "." || albumDirectory == "..") {
            return EnqueueDownloadBatchResult.DirectoryUnavailable
        }
        val defaultRoot = DownloadDestinationStore(context).defaultDestination().root
        val taskRoot = File(defaultRoot, albumDirectory)
        val pendingRequests = request.items.mapNotNull { item ->
            val relativePath = item.relativePath.replace('\\', '/').trim('/').ifBlank { "download.bin" }
            val segments = relativePath.split('/').filter { it.isNotBlank() }
            if (segments.isEmpty() || segments.any { it == "." || it == ".." }) return@mapNotNull null
            val fileName = relativePath.substringAfterLast('/').ifBlank { "download.bin" }
            val relativeDirectory = relativePath.substringBeforeLast('/', "")
            PendingDownloadItemRequest(
                url = item.url,
                fileName = fileName,
                targetDir = if (relativeDirectory.isBlank()) taskRoot.absolutePath else File(taskRoot, relativeDirectory).absolutePath,
                taskRootDir = taskRoot.absolutePath,
                relativePath = relativePath,
                taskSubtitle = request.taskSubtitle,
                tags = listOf(request.logicalTaskKey),
                albumTitle = request.albumTitle,
                albumCircle = request.albumCircle,
                albumCv = request.albumCv,
                albumTagsCsv = request.albumTagsCsv,
                albumCoverUrl = request.albumCoverUrl,
                albumDescription = request.albumDescription,
                albumWorkId = request.albumWorkId,
                albumRjCode = request.albumRjCode,
                dlsitePlayImageSeed = item.dlsitePlayImageSeed,
                dlsitePlayImageWidth = item.dlsitePlayImageWidth,
                dlsitePlayImageHeight = item.dlsitePlayImageHeight,
            )
        }
        if (pendingRequests.size != request.items.size) return EnqueueDownloadBatchResult.DirectoryUnavailable
        return enqueueRequests(pendingRequests)
    }

    private suspend fun enqueueRequests(requests: List<PendingDownloadItemRequest>): EnqueueDownloadBatchResult {
        return directoryCoordinator.withDirectoryLock(scopeFactory = { destination ->
            requests.fold(LocalFileScope()) { scope, request ->
                scope + directoryCoordinator.plannedAlbumScope(
                    destination, request.taskRootDir,
                    listOf(request.albumRjCode, request.albumWorkId, request.albumTitle) + request.tags,
                )
            }
        }) {
            try {
                val destination = directoryCoordinator.currentDestination()
                if (destination is DownloadDestination.DocumentTree && !storage.hasPersistedWritePermission(destination.root)) {
                    return@withDirectoryLock EnqueueDownloadBatchResult.DirectoryUnavailable
                }
                val destinationKey = storage.stableIdentity(destination.root).hashCode().toUInt().toString(16)
                val newRequests = requests.distinctBy { it.taskRootDir to it.relativePath }.filterNot { request ->
                    val logicalTaskKey = request.tags.firstOrNull { it.startsWith("album:") }
                        ?: "dir:${request.taskRootDir}"
                    val task = downloadDao.getTaskByKey("$logicalTaskKey@$destinationKey")
                        ?: return@filterNot false
                    val item = downloadDao.getItemByTaskAndRelativePath(task.id, request.relativePath)
                        ?: return@filterNot false
                    item.state in setOf(
                        WorkInfo.State.RUNNING.name,
                        WorkInfo.State.ENQUEUED.name,
                        WorkInfo.State.BLOCKED.name,
                        DOWNLOAD_STATE_QUEUED,
                        DOWNLOAD_STATE_FINALIZING,
                    )
                }
                if (newRequests.isEmpty()) return@withDirectoryLock EnqueueDownloadBatchResult.TaskBlocked
                newRequests.forEach { request -> enqueueDownloadLocked(request) }
                EnqueueDownloadBatchResult.Accepted(newRequests.size)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: DownloadTaskBlockedException) {
                EnqueueDownloadBatchResult.TaskBlocked
            } catch (failure: Exception) {
                Log.w("DownloadManager", "Unable to enqueue ${requests.size} download items", failure)
                EnqueueDownloadBatchResult.DirectoryUnavailable
            }
        }
    }

    private suspend fun enqueueDownloadLocked(request: PendingDownloadItemRequest) {
            val resolvedPaths = directoryCoordinator.resolveLegacyPaths(request.targetDir, request.taskRootDir)
            val url = request.url
            val fileName = request.fileName
            val targetDir = resolvedPaths.targetDir
            val taskRootDir = resolvedPaths.taskRootDir
            val relativePath = request.relativePath
            val taskSubtitle = request.taskSubtitle
            val tags = request.tags
            val albumTitle = request.albumTitle
            val albumCircle = request.albumCircle
            val albumCv = request.albumCv
            val albumTagsCsv = request.albumTagsCsv
            val albumCoverUrl = request.albumCoverUrl
            val albumDescription = request.albumDescription
            val albumWorkId = request.albumWorkId
            val albumRjCode = request.albumRjCode
            val dlsitePlayImageSeed = request.dlsitePlayImageSeed
            val dlsitePlayImageWidth = request.dlsitePlayImageWidth
            val dlsitePlayImageHeight = request.dlsitePlayImageHeight
            val now = System.currentTimeMillis()

            val logicalTaskKey = tags.firstOrNull { it.startsWith("album:") } ?: "dir:$taskRootDir"
            val destinationKey = storage.stableIdentity(resolvedPaths.destinationRoot).hashCode().toUInt().toString(16)
            val taskKey = "$logicalTaskKey@$destinationKey"
            val taskTitle = logicalTaskKey.removePrefix("album:").ifBlank { File(taskRootDir).name.ifBlank { "download" } }
            val safeTaskSubtitle = taskSubtitle.trim()
            val safeRelativePath = relativePath.ifBlank { fileName }.replace('\\', '/')
            val existingDestinationFile = storage.findFile(targetDir, fileName)
            val filePath = existingDestinationFile.orEmpty().ifBlank {
                if (storage.isDocumentReference(targetDir)) "" else File(targetDir, fileName).absolutePath
            }
            val safeAlbumTitle = albumTitle.trim().take(200)
            val safeAlbumCircle = albumCircle.trim().take(200)
            val safeAlbumCv = albumCv.trim().take(400)
            val safeAlbumTagsCsv = albumTagsCsv.trim().take(1200)
            val safeAlbumCoverUrl = albumCoverUrl.trim().take(800)
            val safeAlbumDescription = albumDescription.trim().take(0)
            val safeAlbumWorkId = albumWorkId.trim().take(40)
            val safeAlbumRjCode = albumRjCode.trim().take(40)
            val hasDlsitePlayImageTransform = dlsitePlayImageSeed != null &&
                (dlsitePlayImageWidth ?: 0) > 0 &&
                (dlsitePlayImageHeight ?: 0) > 0
            val safeDlsitePlayImageSeed = dlsitePlayImageSeed.takeIf { hasDlsitePlayImageTransform }
            val safeDlsitePlayImageWidth = dlsitePlayImageWidth.takeIf { hasDlsitePlayImageTransform }
            val safeDlsitePlayImageHeight = dlsitePlayImageHeight.takeIf { hasDlsitePlayImageTransform }
            val taskId = ensureTask(
                taskKey = taskKey,
                logicalTaskKey = logicalTaskKey,
                title = taskTitle,
                subtitle = safeTaskSubtitle,
                rootDir = taskRootDir,
                destinationRoot = resolvedPaths.destinationRoot,
                albumRootDir = resolvedPaths.albumRootDir,
                albumTitle = safeAlbumTitle,
                albumCircle = safeAlbumCircle,
                albumCv = safeAlbumCv,
                albumTagsCsv = safeAlbumTagsCsv,
                albumCoverUrl = safeAlbumCoverUrl,
                albumDescription = safeAlbumDescription,
                albumWorkId = safeAlbumWorkId,
                albumRjCode = safeAlbumRjCode,
                now = now
            )

            val existingItem = downloadDao.getItemByTaskAndRelativePath(taskId, safeRelativePath)
            if (existingDestinationFile != null && storage.exists(existingDestinationFile) &&
                (existingItem == null || existingItem.state == WorkInfo.State.SUCCEEDED.name) &&
                storage.size(existingDestinationFile) > 0L &&
                (existingItem == null || existingItem.total <= 0L || hasDlsitePlayImageTransform ||
                    storage.size(existingDestinationFile) == existingItem.total)
            ) {
                val size = storage.size(existingDestinationFile)
                if (existingItem != null) {
                    downloadDao.updateItemProgress(
                        workId = existingItem.workId,
                        state = DOWNLOAD_STATE_FINALIZING,
                        downloaded = size,
                        total = size,
                        speed = 0L,
                        updatedAt = now
                    )
                } else {
                    val localWorkId = "local_${UUID.randomUUID()}"
                    downloadDao.upsertItem(
                        DownloadItemEntity(
                            taskId = taskId,
                            workId = localWorkId,
                            url = url,
                            relativePath = safeRelativePath,
                            fileName = fileName,
                            targetDir = targetDir,
                            filePath = filePath,
                            state = DOWNLOAD_STATE_FINALIZING,
                            downloaded = size,
                            total = size,
                            speed = 0L,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
                downloadDao.getTaskById(taskId)?.let { enqueueDownloadFinalization(context, it) }
                return
            }

            val partialFile = existingItem?.let { downloadStagingFile(context, it) }
            val existingBytes = runCatching {
                when {
                    partialFile != null && partialFile.exists() -> partialFile.length()
                    filePath.isNotBlank() -> storage.size(filePath)
                    else -> 0L
                }
            }.getOrDefault(0L).coerceAtLeast(0L)
            if (existingItem != null) {
                if (
                    existingItem.state == WorkInfo.State.RUNNING.name ||
                    existingItem.state == WorkInfo.State.ENQUEUED.name ||
                    existingItem.state == WorkInfo.State.BLOCKED.name ||
                    existingItem.state == DOWNLOAD_STATE_QUEUED || existingItem.state == DOWNLOAD_STATE_FINALIZING
                ) {
                    throw DownloadTaskBlockedException()
                }
                downloadDao.upsertItem(
                    existingItem.copy(
                        state = DOWNLOAD_STATE_QUEUED,
                        downloaded = existingBytes,
                        speed = 0L,
                        updatedAt = now,
                        dlsitePlayImageSeed = safeDlsitePlayImageSeed,
                        dlsitePlayImageWidth = safeDlsitePlayImageWidth,
                        dlsitePlayImageHeight = safeDlsitePlayImageHeight
                    )
                )
            } else {
                downloadDao.upsertItem(
                    DownloadItemEntity(
                        taskId = taskId,
                        workId = "queued_${UUID.randomUUID()}",
                        url = url,
                        relativePath = safeRelativePath,
                        fileName = fileName,
                        targetDir = targetDir,
                        filePath = filePath,
                        state = DOWNLOAD_STATE_QUEUED,
                        downloaded = existingBytes,
                        total = -1L,
                        speed = 0L,
                        createdAt = now,
                        updatedAt = now,
                        dlsitePlayImageSeed = safeDlsitePlayImageSeed,
                        dlsitePlayImageWidth = safeDlsitePlayImageWidth,
                        dlsitePlayImageHeight = safeDlsitePlayImageHeight
                    )
                )
            }
            DownloadQueueCoordinator.requestSchedule(context)
    }

    private class DownloadTaskBlockedException : IllegalStateException()

    private suspend fun ensureTask(
        taskKey: String,
        logicalTaskKey: String,
        title: String,
        subtitle: String,
        rootDir: String,
        destinationRoot: String,
        albumRootDir: String,
        albumTitle: String,
        albumCircle: String,
        albumCv: String,
        albumTagsCsv: String,
        albumCoverUrl: String,
        albumDescription: String,
        albumWorkId: String,
        albumRjCode: String,
        now: Long
    ): Long {
        val existing = downloadDao.getTaskByKey(taskKey)
        if (existing != null) {
            val resolvedSubtitle = existing.subtitle.ifBlank { subtitle }
            val resolvedAlbumTitle = existing.albumTitle.ifBlank { albumTitle }
            val resolvedAlbumCircle = existing.albumCircle.ifBlank { albumCircle }
            val resolvedAlbumCv = existing.albumCv.ifBlank { albumCv }
            val resolvedAlbumTagsCsv = existing.albumTagsCsv.ifBlank { albumTagsCsv }
            val resolvedAlbumCoverUrl = existing.albumCoverUrl.ifBlank { albumCoverUrl }
            val resolvedAlbumDescription = existing.albumDescription.ifBlank { albumDescription }
            val resolvedAlbumWorkId = existing.albumWorkId.ifBlank { albumWorkId }
            val resolvedAlbumRjCode = existing.albumRjCode.ifBlank { albumRjCode }
            if (
                resolvedSubtitle != existing.subtitle ||
                resolvedAlbumTitle != existing.albumTitle ||
                resolvedAlbumCircle != existing.albumCircle ||
                resolvedAlbumCv != existing.albumCv ||
                resolvedAlbumTagsCsv != existing.albumTagsCsv ||
                resolvedAlbumCoverUrl != existing.albumCoverUrl ||
                resolvedAlbumDescription != existing.albumDescription ||
                resolvedAlbumWorkId != existing.albumWorkId ||
                resolvedAlbumRjCode != existing.albumRjCode
            ) {
                runCatching {
                    downloadDao.updateTaskMetadata(
                        taskId = existing.id,
                        subtitle = resolvedSubtitle,
                        albumTitle = resolvedAlbumTitle,
                        albumCircle = resolvedAlbumCircle,
                        albumCv = resolvedAlbumCv,
                        albumTagsCsv = resolvedAlbumTagsCsv,
                        albumCoverUrl = resolvedAlbumCoverUrl,
                        albumDescription = resolvedAlbumDescription,
                        albumWorkId = resolvedAlbumWorkId,
                        albumRjCode = resolvedAlbumRjCode,
                        updatedAt = now
                    )
                }
            }
            return existing.id
        }
        val created = downloadDao.insertTask(
            DownloadTaskEntity(
                taskKey = taskKey,
                logicalTaskKey = logicalTaskKey,
                title = title,
                subtitle = subtitle,
                rootDir = rootDir,
                destinationRoot = destinationRoot,
                albumRootDir = albumRootDir,
                albumTitle = albumTitle,
                albumCircle = albumCircle,
                albumCv = albumCv,
                albumTagsCsv = albumTagsCsv,
                albumCoverUrl = albumCoverUrl,
                albumDescription = albumDescription,
                albumWorkId = albumWorkId,
                albumRjCode = albumRjCode,
                createdAt = now,
                updatedAt = now
            )
        )
        if (created > 0) return created
        return downloadDao.getTaskByKey(taskKey)?.id ?: 0L
    }
}
