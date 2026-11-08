package com.asmr.player.data.remote.download

import android.content.Context
import android.util.Log
import androidx.work.*
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.dao.DownloadDao
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Process-wide scheduler; WorkManager owns transfer execution and persisted tasks survive process death. */
object DownloadQueueCoordinator {
    private const val ACTIVE_WORK_RECONCILE_GRACE_MS = 30_000L
    private const val MEMORY_RETRY_DELAY_MS = 15_000L
    // Android 15 起不再发送运行时内存级别，保留数值以兼容旧系统回调。
    private const val TRIM_MEMORY_RUNNING_LOW_COMPAT = 10
    private const val TRIM_MEMORY_RUNNING_CRITICAL_COMPAT = 15
    private const val TRIM_MEMORY_RUNNING_LOW_BACKOFF_MS = 20_000L
    private const val TRIM_MEMORY_RUNNING_CRITICAL_BACKOFF_MS = 45_000L

    private val scheduleMutex = Mutex()
    private val requestMutex = Any()
    @Volatile
    private var scheduleRequested = false
    @Volatile
    private var scheduleLoopRunning = false
    @Volatile
    private var memoryRetryScheduled = false
    @Volatile
    private var memoryPauseUntilMs = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun requestSchedule(context: Context) {
        val appContext = context.applicationContext
        synchronized(requestMutex) {
            scheduleRequested = true
            if (scheduleLoopRunning) return
            scheduleLoopRunning = true
        }
        scope.launch {
            while (true) {
                synchronized(requestMutex) {
                    scheduleRequested = false
                }
                try {
                    schedulePendingDownloads(appContext)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    synchronized(requestMutex) { scheduleLoopRunning = false }
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w("DownloadQueue", "Unable to schedule pending downloads", failure)
                }
                val shouldContinue = synchronized(requestMutex) {
                    if (scheduleRequested) {
                        true
                    } else {
                        scheduleLoopRunning = false
                        false
                    }
                }
                if (!shouldContinue) break
            }
        }
    }

    suspend fun recoverDownloadsOnAppLaunch(context: Context) {
        val appContext = context.applicationContext
        val dao = AppDatabaseProvider.get(appContext).downloadDao()
        dao.getPendingFinalizationTasks().forEach { enqueueDownloadFinalization(appContext, it) }
        val recoverableItems = dao.getAllActiveOrPausedItems()
        if (recoverableItems.isEmpty()) return
        val wm = WorkManager.getInstance(appContext)
        runCatching { wm.cancelAllWorkByTag("download") }
        val now = System.currentTimeMillis()
        recoverableItems.forEach { item ->
            dao.stopInterruptibleItem(item.workId, "PAUSED", now)
        }
    }

    fun onTrimMemory(context: Context, level: Int) {
        val now = System.currentTimeMillis()
        val backoffMs = when {
            level >= TRIM_MEMORY_RUNNING_CRITICAL_COMPAT -> TRIM_MEMORY_RUNNING_CRITICAL_BACKOFF_MS
            level >= TRIM_MEMORY_RUNNING_LOW_COMPAT -> TRIM_MEMORY_RUNNING_LOW_BACKOFF_MS
            else -> 0L
        }
        if (backoffMs > 0L) {
            memoryPauseUntilMs = max(memoryPauseUntilMs, now + backoffMs)
            scheduleMemoryRetry(context, backoffMs)
        }
    }

    suspend fun schedulePendingDownloads(context: Context) {
        val appContext = context.applicationContext
        scheduleMutex.withLock {
            val wm = WorkManager.getInstance(appContext)
            val dao = AppDatabaseProvider.get(appContext).downloadDao()
            reconcileActiveItems(appContext, wm, dao)

            val availableSlots = DownloadRuntimeConfig.maxConcurrentDownloads(appContext) - dao.countActiveItems()
            if (availableSlots <= 0) return

            val hasQueuedItems = dao.getQueuedItems(limit = 1).isNotEmpty()
            if (!hasQueuedItems) return

            val now = System.currentTimeMillis()
            val memoryPaused = now < memoryPauseUntilMs
            val memoryConstrained = DownloadRuntimeConfig.isMemoryConstrained(appContext)
            if (memoryPaused || memoryConstrained) {
                scheduleMemoryRetry(appContext, MEMORY_RETRY_DELAY_MS)
                return
            }

            dao.getQueuedItems(availableSlots).forEach { item ->
                val task = dao.getTaskById(item.taskId) ?: run {
                    dao.updateItemState(item.workId, WorkInfo.State.FAILED.name, System.currentTimeMillis())
                    return@forEach
                }
                val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        workDataOf(
                            "url" to item.url,
                            "fileName" to item.fileName,
                            "targetDir" to item.targetDir,
                            "taskRootDir" to task.rootDir,
                            "albumRootDir" to task.albumRootDir,
                            "relativePath" to item.relativePath,
                            "taskKey" to task.taskKey,
                            "taskTitle" to task.title,
                            "taskSubtitle" to task.subtitle,
                            "albumTitle" to task.albumTitle,
                            "albumCircle" to task.albumCircle,
                            "albumCv" to task.albumCv,
                            "albumTagsCsv" to task.albumTagsCsv,
                            "albumCoverUrl" to task.albumCoverUrl,
                            "albumDescription" to task.albumDescription,
                            "albumWorkId" to task.albumWorkId,
                            "albumRjCode" to task.albumRjCode,
                            "dlsitePlayImageSeed" to (item.dlsitePlayImageSeed ?: -1),
                            "dlsitePlayImageWidth" to (item.dlsitePlayImageWidth ?: -1),
                            "dlsitePlayImageHeight" to (item.dlsitePlayImageHeight ?: -1)
                        )
                    )
                    .addTag("download")
                    .addTag(task.taskKey)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

                val existingBytes = runCatching {
                    resolveExistingBytes(appContext, item)
                }.getOrDefault(item.downloaded).coerceAtLeast(0L)

                val scheduled = dao.replaceWorkIdForResume(
                    oldWorkId = item.workId,
                    newWorkId = request.id.toString(),
                    state = WorkInfo.State.ENQUEUED.name,
                    downloaded = existingBytes,
                    updatedAt = System.currentTimeMillis()
                )
                if (scheduled > 0) wm.enqueue(request)
            }
        }
    }

    private suspend fun reconcileActiveItems(context: Context, workManager: WorkManager, dao: DownloadDao) {
        val now = System.currentTimeMillis()
        dao.getActiveItems().forEach { item ->
            val workId = runCatching { UUID.fromString(item.workId) }.getOrNull()
            if (workId == null) {
                dao.updateRunningItemProgress(
                    workId = item.workId,
                    state = DOWNLOAD_STATE_QUEUED,
                    downloaded = resolveExistingBytes(context, item),
                    total = item.total,
                    speed = 0L,
                    updatedAt = now
                )
                return@forEach
            }

            val info = runCatching { workManager.getWorkInfoById(workId).get() }.getOrNull()
            when (info?.state) {
                null -> {
                    val recentlyScheduled = now - item.updatedAt <= ACTIVE_WORK_RECONCILE_GRACE_MS
                    if (!recentlyScheduled) {
                        dao.updateRunningItemProgress(
                            workId = item.workId,
                            state = DOWNLOAD_STATE_QUEUED,
                            downloaded = resolveExistingBytes(context, item),
                            total = item.total,
                            speed = 0L,
                            updatedAt = now
                        )
                    }
                }
                WorkInfo.State.SUCCEEDED -> {
                    val size = resolveExistingBytes(context, item)
                    dao.updateRunningItemProgress(
                        workId = item.workId,
                        state = DOWNLOAD_STATE_FINALIZING,
                        downloaded = size,
                        total = size.coerceAtLeast(item.total),
                        speed = 0L,
                        updatedAt = now
                    )
                    dao.getTaskById(item.taskId)?.let { enqueueDownloadFinalization(context, it) }
                }
                WorkInfo.State.FAILED -> dao.failRunningItem(item.workId, now)
                WorkInfo.State.CANCELLED -> dao.updateRunningItemProgress(item.workId, WorkInfo.State.CANCELLED.name, item.downloaded, item.total, 0, now)
                else -> Unit
            }
        }
    }

    private fun scheduleMemoryRetry(context: Context, delayMs: Long) {
        val appContext = context.applicationContext
        synchronized(requestMutex) {
            if (memoryRetryScheduled) return
            memoryRetryScheduled = true
        }
        scope.launch {
            delay(delayMs.coerceAtLeast(1_000L))
            synchronized(requestMutex) {
                memoryRetryScheduled = false
            }
            requestSchedule(appContext)
        }
    }

    private fun resolveExistingBytes(context: Context, item: DownloadItemEntity): Long {
        return runCatching {
            if (item.targetDir.startsWith("content://")) {
                val staging = downloadStagingFile(context, item)
                val scrambled = dlsitePlayImagePartFile(staging)
                return@runCatching if (item.hasDlsitePlayImageTransform() && scrambled.exists()) {
                    scrambled.length()
                } else if (staging.exists()) {
                    staging.length()
                } else {
                    DownloadStorageGateway(context).size(item.filePath)
                }
            }
            val outputFile = File(item.filePath.ifBlank { File(item.targetDir, item.fileName).absolutePath })
            val partialFile = dlsitePlayImagePartFile(outputFile)
            if (item.hasDlsitePlayImageTransform() && partialFile.exists()) partialFile.length() else outputFile.length()
        }.getOrDefault(item.downloaded).coerceAtLeast(0L)
    }
}
