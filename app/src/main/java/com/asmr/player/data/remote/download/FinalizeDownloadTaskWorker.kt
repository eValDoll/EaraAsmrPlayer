package com.asmr.player.data.remote.download

import com.asmr.player.nas.LibraryWorkspace
import com.asmr.player.nas.libraryWorkRequest
import com.asmr.player.util.LocalFileOperation
import com.asmr.player.util.LocalFileScopes
import com.asmr.player.util.LocalFileOperationCoordinator
import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import androidx.work.*
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import com.asmr.player.util.LocalFileSnapshot
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun enqueueDownloadFinalization(context: Context, task: DownloadTaskEntity) {
    if (!AppDatabaseProvider.get(context).downloadDao().isReadyForFinalization(task.id)) return
    val request = libraryWorkRequest<FinalizeDownloadTaskWorker>()
        .setInputData(workDataOf("taskKey" to task.taskKey, "taskRootDir" to task.rootDir))
        .addTag("download_finalize")
        .addTag(LibraryWorkspace.name(task.taskKey))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        LibraryWorkspace.name("download_finalize_${task.taskKey.hashCode()}"), ExistingWorkPolicy.APPEND_OR_REPLACE, request
    )
}

/** Validates completed files and imports the album in a transaction before marking the task complete. */
class FinalizeDownloadTaskWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val database = AppDatabaseProvider.get(applicationContext)
        val taskKey = inputData.getString("taskKey").orEmpty()
        val task = if (taskKey.isNotBlank()) database.downloadDao().getTaskByKey(taskKey)
            else database.downloadDao().getTaskByRootDir(inputData.getString("taskRootDir").orEmpty())
        if (task == null || !database.downloadDao().isReadyForFinalization(task.id)) {
            return@withContext ListenableWorker.Result.success()
        }
        val scope = LocalFileScopes(database, DownloadStorageGateway(applicationContext)).download(task)
        LocalFileOperationCoordinator.shared.withOperation(LocalFileOperation.FINALIZE_DOWNLOAD, scope = scope) {
            finalizeDownload()
        }
    }

    private suspend fun finalizeDownload(): ListenableWorker.Result {
        val taskKey = inputData.getString("taskKey").orEmpty()
        val taskTitle = inputData.getString("taskTitle").orEmpty()
        val taskSubtitle = inputData.getString("taskSubtitle").orEmpty()
        val taskRootDir = inputData.getString("taskRootDir").orEmpty()
        val albumRootDir = inputData.getString("albumRootDir").orEmpty()
        val albumTitle = inputData.getString("albumTitle").orEmpty()
        val albumCircle = inputData.getString("albumCircle").orEmpty()
        val albumCv = inputData.getString("albumCv").orEmpty()
        val albumTagsCsv = inputData.getString("albumTagsCsv").orEmpty()
        val albumCoverUrl = inputData.getString("albumCoverUrl").orEmpty()
        val albumDescription = inputData.getString("albumDescription").orEmpty()
        val albumWorkId = inputData.getString("albumWorkId").orEmpty()
        val albumRjCode = inputData.getString("albumRjCode").orEmpty()

        if (taskKey.isBlank() && taskRootDir.isBlank()) return ListenableWorker.Result.success()

        return try {
            val db = AppDatabaseProvider.get(applicationContext)
            val dao = db.downloadDao()
            val task = if (taskKey.isNotBlank()) {
                dao.getTaskByKey(taskKey)
            } else {
                dao.getTaskByRootDir(taskRootDir)
            } ?: return ListenableWorker.Result.success()

            val items = dao.getItemsForTask(task.id)
            val done = items.isNotEmpty() && items.all { it.state == WorkInfo.State.SUCCEEDED.name || it.state == DOWNLOAD_STATE_FINALIZING }
            if (!done || items.none { it.state == DOWNLOAD_STATE_FINALIZING }) return ListenableWorker.Result.success()

            val storage = DownloadStorageGateway(applicationContext)
            val resolvedAlbumRoot = task.albumRootDir
                .ifBlank { albumRootDir }
                .ifBlank { task.rootDir.ifBlank { taskRootDir } }
            val nas = LibraryWorkspace.owns(android.net.Uri.parse(resolvedAlbumRoot))
            if (nas) foregroundNasTransfer(task.title)
            fun nasSnapshot() = storage.walk(resolvedAlbumRoot).associateBy { storage.stableIdentity(it.reference) }
            var nasEntries = if (nas) nasSnapshot() else null
            val completedMarker = if (nas) nasEntries?.values?.firstOrNull { it.relativePath == ".download_complete" }?.reference
                else storage.findFile(resolvedAlbumRoot, ".download_complete")
            val invalidItems = items.filter { item ->
                val extractedArchive = item.fileName.equals("dlsite_lossless_archive.zip", true) && completedMarker != null
                if (extractedArchive) return@filter false
                val length = if (nas) nasEntries?.get(storage.stableIdentity(item.filePath))?.takeUnless { it.isDirectory }?.sizeBytes
                    else if (storage.exists(item.filePath)) storage.size(item.filePath) else null
                length == null || (item.total > 0L && !item.hasDlsitePlayImageTransform() && length != item.total)
            }
            if (invalidItems.isNotEmpty()) {
                invalidItems.forEach { dao.updateItemState(it.workId, WorkInfo.State.FAILED.name, System.currentTimeMillis()) }
                completedMarker?.let(storage::delete)
                return ListenableWorker.Result.success()
            }
            val archive = if (storage.isDocumentReference(resolvedAlbumRoot)) {
                finalizeDlsiteLosslessArchiveInStorageIfNeeded(
                    context = applicationContext,
                    rootDir = resolvedAlbumRoot,
                    items = items,
                    storage = storage,
                    nasEntries = nasEntries?.values,
                )
            } else {
                finalizeDlsiteLosslessArchiveIfNeeded(File(resolvedAlbumRoot), items)
                null
            }
            if (nas && archive != null) nasEntries = nasSnapshot()

            val sourceSnapshots = if (nas) emptyMap() else (listOf(resolvedAlbumRoot) + storage.walk(resolvedAlbumRoot).map { it.reference })
                .associateWith { LocalFileSnapshot.read(applicationContext, it) }
            db.withTransaction {
                upsertDownloadedAlbumToLibrary(
                    db = db,
                    appContext = applicationContext,
                    rootDir = resolvedAlbumRoot,
                    taskTitle = task.title.ifBlank { taskTitle },
                    taskSubtitle = task.subtitle.ifBlank { taskSubtitle },
                    albumTitle = task.albumTitle.ifBlank { albumTitle },
                    albumCircle = task.albumCircle.ifBlank { albumCircle },
                    albumCv = task.albumCv.ifBlank { albumCv },
                    albumTagsCsv = task.albumTagsCsv.ifBlank { albumTagsCsv },
                    albumTagDetailsJson = task.albumTagDetailsJson,
                    albumCoverUrl = task.albumCoverUrl.ifBlank { albumCoverUrl },
                    albumDescription = task.albumDescription.ifBlank { albumDescription },
                    albumWorkId = task.albumWorkId.ifBlank { albumWorkId },
                    albumRjCode = task.albumRjCode.ifBlank { albumRjCode },
                    documentEntries = nasEntries?.values?.filterNot { it.reference == archive?.filePath },
                )

                if (nasEntries != null) {
                    check(nasEntries == nasSnapshot()) {
                        com.asmr.player.i18n.appString(com.asmr.player.R.string.ui_the_local_file_changed_during_this_task_writing)
                    }
                }
                sourceSnapshots.forEach { (reference, snapshot) -> snapshot.requireUnchanged(applicationContext, reference) }
                runCatching {
                    if (storage.isDocumentReference(resolvedAlbumRoot)) {
                        storage.ensureFile(resolvedAlbumRoot, ".download_complete", "application/octet-stream")
                    } else {
                        val marker = File(resolvedAlbumRoot, ".download_complete")
                        if (!marker.exists()) marker.createNewFile()
                    }
                    Unit
                }.onFailure { if (LibraryWorkspace.nas) throw it }
                dao.finishFinalizingItems(task.id, System.currentTimeMillis())
            }
            archive?.let { item ->
                storage.delete(item.filePath)
                downloadStagingFile(applicationContext, item).delete()
            }
            ListenableWorker.Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (LibraryWorkspace.nas && failure.isTransientNasTransferFailure() && runAttemptCount < 3) {
                return ListenableWorker.Result.retry()
            }
            Log.w("DownloadFinalization", "Unable to finalize work $id, task $taskKey", failure)
            AppDatabaseProvider.get(applicationContext).downloadDao()
                .failFinalizingItems(taskKey, System.currentTimeMillis(), if (LibraryWorkspace.nas) DOWNLOAD_STATE_IMPORT_FAILED else WorkInfo.State.FAILED.name)
            ListenableWorker.Result.failure()
        }
    }
}
