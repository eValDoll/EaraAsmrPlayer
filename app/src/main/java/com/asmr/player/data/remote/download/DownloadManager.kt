package com.asmr.player.data.remote.download

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.buildDlsiteCookieHeader
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumFtsEntity
import com.asmr.player.data.local.db.dao.DownloadDao
import com.asmr.player.data.local.db.entities.DownloadItemEntity
import com.asmr.player.data.local.db.entities.DownloadTaskEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.util.SubtitleParser
import com.asmr.player.util.TrackKeyNormalizer
import com.asmr.player.work.AlbumCoverThumbWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueueDownload(
        url: String,
        fileName: String,
        targetDir: String,
        taskRootDir: String = targetDir,
        relativePath: String = fileName,
        taskSubtitle: String = "",
        tags: List<String> = emptyList(),
        albumTitle: String = "",
        albumCircle: String = "",
        albumCv: String = "",
        albumTagsCsv: String = "",
        albumCoverUrl: String = "",
        albumDescription: String = "",
        albumWorkId: String = "",
        albumRjCode: String = ""
    ) {
        scope.launch {
            val now = System.currentTimeMillis()

            val taskKey = tags.firstOrNull { it.startsWith("album:") } ?: "dir:$taskRootDir"
            val taskTitle = taskKey.removePrefix("album:").ifBlank { File(taskRootDir).name.ifBlank { "download" } }
            val safeTaskSubtitle = taskSubtitle.trim()
            val safeRelativePath = relativePath.ifBlank { fileName }.replace('\\', '/')
            val filePath = File(targetDir, fileName).absolutePath
            val safeAlbumTitle = albumTitle.trim().take(200)
            val safeAlbumCircle = albumCircle.trim().take(200)
            val safeAlbumCv = albumCv.trim().take(400)
            val safeAlbumTagsCsv = albumTagsCsv.trim().take(1200)
            val safeAlbumCoverUrl = albumCoverUrl.trim().take(800)
            val safeAlbumDescription = albumDescription.trim().take(0)
            val safeAlbumWorkId = albumWorkId.trim().take(40)
            val safeAlbumRjCode = albumRjCode.trim().take(40)
            val taskId = ensureTask(
                taskKey = taskKey,
                title = taskTitle,
                subtitle = safeTaskSubtitle,
                rootDir = taskRootDir,
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

            val existingFile = File(filePath)
            if (existingFile.exists() && existingFile.isFile) {
                val size = existingFile.length().coerceAtLeast(0L)
                val existingItem = downloadDao.getItemByFilePath(filePath)
                if (existingItem != null) {
                    downloadDao.updateItemProgress(
                        workId = existingItem.workId,
                        state = WorkInfo.State.SUCCEEDED.name,
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
                            state = WorkInfo.State.SUCCEEDED.name,
                            downloaded = size,
                            total = size,
                            speed = 0L,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
                runCatching {
                    val task = downloadDao.getTaskByKey(taskKey)
                    if (task != null) {
                        val items = downloadDao.getItemsForTask(task.id)
                        val done = items.isNotEmpty() && items.all { it.state == WorkInfo.State.SUCCEEDED.name }
                        if (done) {
                            val db = AppDatabaseProvider.get(context)
                            db.withTransaction {
                                upsertDownloadedAlbumToLibrary(
                                    db = db,
                                    appContext = context,
                                    rootDir = taskRootDir,
                                    taskTitle = taskTitle,
                                    taskSubtitle = safeTaskSubtitle,
                                    albumTitle = safeAlbumTitle,
                                    albumCircle = safeAlbumCircle,
                                    albumCv = safeAlbumCv,
                                    albumTagsCsv = safeAlbumTagsCsv,
                                    albumCoverUrl = safeAlbumCoverUrl,
                                    albumDescription = safeAlbumDescription,
                                    albumWorkId = safeAlbumWorkId,
                                    albumRjCode = safeAlbumRjCode
                                )
                            }
                            runCatching {
                                val marker = File(taskRootDir, ".download_complete")
                                if (!marker.exists()) marker.createNewFile()
                            }
                        }
                    }
                }
                return@launch
            }

            val existingItem = downloadDao.getItemByFilePath(filePath)
            val existingBytes = runCatching { File(filePath).length() }.getOrDefault(0L).coerceAtLeast(0L)
            if (existingItem != null) {
                val file = File(existingItem.filePath.ifBlank { filePath })
                if (existingItem.state == WorkInfo.State.SUCCEEDED.name && file.exists() && file.isFile) {
                    return@launch
                }
                if (
                    existingItem.state == WorkInfo.State.RUNNING.name ||
                    existingItem.state == WorkInfo.State.ENQUEUED.name ||
                    existingItem.state == WorkInfo.State.BLOCKED.name ||
                    existingItem.state == DOWNLOAD_STATE_QUEUED
                ) {
                    return@launch
                }
                downloadDao.updateItemProgress(
                    workId = existingItem.workId,
                    state = DOWNLOAD_STATE_QUEUED,
                    downloaded = existingBytes,
                    total = existingItem.total,
                    speed = 0L,
                    updatedAt = now
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
                        updatedAt = now
                    )
                )
            }
            DownloadQueueCoordinator.requestSchedule(context)
        }
    }

    private suspend fun ensureTask(
        taskKey: String,
        title: String,
        subtitle: String,
        rootDir: String,
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
                title = title,
                subtitle = subtitle,
                rootDir = rootDir,
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

object DownloadQueueCoordinator {
    private const val ACTIVE_WORK_RECONCILE_GRACE_MS = 30_000L
    private const val MEMORY_RETRY_DELAY_MS = 15_000L
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
                runCatching { schedulePendingDownloads(appContext) }
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
        val wm = WorkManager.getInstance(appContext)
        runCatching { wm.cancelAllWorkByTag("download") }
        val dao = AppDatabaseProvider.get(appContext).downloadDao()
        val now = System.currentTimeMillis()
        dao.getAllActiveOrPausedItems()
            .forEach { item ->
                val resolvedBytes = resolveExistingBytes(item)
                val resolvedState = when (item.state) {
                    WorkInfo.State.SUCCEEDED.name -> WorkInfo.State.SUCCEEDED.name
                    "PAUSED" -> "PAUSED"
                    else -> "PAUSED"
                }
                runCatching {
                    dao.updateItemProgress(
                        workId = item.workId,
                        state = resolvedState,
                        downloaded = resolvedBytes,
                        total = item.total,
                        speed = 0L,
                        updatedAt = now
                    )
                }
            }
    }

    fun onTrimMemory(context: Context, level: Int) {
        val now = System.currentTimeMillis()
        val backoffMs = when {
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> TRIM_MEMORY_RUNNING_CRITICAL_BACKOFF_MS
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> TRIM_MEMORY_RUNNING_LOW_BACKOFF_MS
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
            reconcileActiveItems(wm, dao)

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
                            "albumRjCode" to task.albumRjCode
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

                wm.enqueue(request)

                val existingBytes = runCatching {
                    File(item.filePath.ifBlank { File(item.targetDir, item.fileName).absolutePath }).length()
                }.getOrDefault(item.downloaded).coerceAtLeast(0L)

                dao.replaceWorkIdForResume(
                    oldWorkId = item.workId,
                    newWorkId = request.id.toString(),
                    state = WorkInfo.State.ENQUEUED.name,
                    downloaded = existingBytes,
                    updatedAt = System.currentTimeMillis()
                )
            }
        }
    }

    private suspend fun reconcileActiveItems(workManager: WorkManager, dao: DownloadDao) {
        val now = System.currentTimeMillis()
        dao.getActiveItems().forEach { item ->
            val workId = runCatching { UUID.fromString(item.workId) }.getOrNull()
            if (workId == null) {
                dao.updateItemProgress(
                    workId = item.workId,
                    state = DOWNLOAD_STATE_QUEUED,
                    downloaded = resolveExistingBytes(item),
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
                        dao.updateItemProgress(
                            workId = item.workId,
                            state = DOWNLOAD_STATE_QUEUED,
                            downloaded = resolveExistingBytes(item),
                            total = item.total,
                            speed = 0L,
                            updatedAt = now
                        )
                    }
                }
                WorkInfo.State.SUCCEEDED -> {
                    val size = resolveExistingBytes(item)
                    dao.updateItemProgress(
                        workId = item.workId,
                        state = WorkInfo.State.SUCCEEDED.name,
                        downloaded = size,
                        total = size.coerceAtLeast(item.total),
                        speed = 0L,
                        updatedAt = now
                    )
                }
                WorkInfo.State.FAILED -> dao.updateItemState(item.workId, WorkInfo.State.FAILED.name, now)
                WorkInfo.State.CANCELLED -> dao.updateItemState(item.workId, WorkInfo.State.CANCELLED.name, now)
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

    private fun resolveExistingBytes(item: DownloadItemEntity): Long {
        return runCatching {
            File(item.filePath.ifBlank { File(item.targetDir, item.fileName).absolutePath }).length()
        }.getOrDefault(item.downloaded).coerceAtLeast(0L)
    }
}

class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadWorkerEntryPoint {
        fun okHttpClient(): OkHttpClient
    }

    override suspend fun doWork(): ListenableWorker.Result = withContext(downloadDispatcher(applicationContext)) {
        executeDownloadWork()
    }

    private suspend fun executeDownloadWork(): ListenableWorker.Result {
        val url = inputData.getString("url") ?: return ListenableWorker.Result.failure()
        val fileName = inputData.getString("fileName") ?: return ListenableWorker.Result.failure()
        val targetDir = inputData.getString("targetDir") ?: return ListenableWorker.Result.failure()
        val taskKey = inputData.getString("taskKey").orEmpty()
        val taskTitle = inputData.getString("taskTitle").orEmpty()
        val taskSubtitle = inputData.getString("taskSubtitle").orEmpty()
        val taskRootDir = inputData.getString("taskRootDir").orEmpty()
        val relativePath = inputData.getString("relativePath").orEmpty().ifBlank { fileName }.replace('\\', '/')
        val albumTitle = inputData.getString("albumTitle").orEmpty()
        val albumCircle = inputData.getString("albumCircle").orEmpty()
        val albumCv = inputData.getString("albumCv").orEmpty()
        val albumTagsCsv = inputData.getString("albumTagsCsv").orEmpty()
        val albumCoverUrl = inputData.getString("albumCoverUrl").orEmpty()
        val albumDescription = inputData.getString("albumDescription").orEmpty()
        val albumWorkId = inputData.getString("albumWorkId").orEmpty()
        val albumRjCode = inputData.getString("albumRjCode").orEmpty()

        return try {
            val workId = id.toString()
            val appDb = AppDatabaseProvider.get(applicationContext)
            val dao = appDb.downloadDao()
            val dailyStatDao = appDb.dailyStatDao()
            val now0 = System.currentTimeMillis()
            val resolvedTaskKey = taskKey.ifBlank { "dir:${taskRootDir.ifBlank { targetDir }}" }
            val resolvedRootDir = taskRootDir.ifBlank { targetDir }
            val resolvedTitle = taskTitle.ifBlank { resolvedTaskKey.removePrefix("album:").ifBlank { File(resolvedRootDir).name.ifBlank { "download" } } }
            val resolvedSubtitle = taskSubtitle.trim()
            val taskId = run {
                val existing = dao.getTaskByKey(resolvedTaskKey)
                if (existing != null) {
                    val mergedSubtitle = existing.subtitle.ifBlank { resolvedSubtitle }
                    val mergedAlbumTitle = existing.albumTitle.ifBlank { albumTitle }
                    val mergedAlbumCircle = existing.albumCircle.ifBlank { albumCircle }
                    val mergedAlbumCv = existing.albumCv.ifBlank { albumCv }
                    val mergedAlbumTagsCsv = existing.albumTagsCsv.ifBlank { albumTagsCsv }
                    val mergedAlbumCoverUrl = existing.albumCoverUrl.ifBlank { albumCoverUrl }
                    val mergedAlbumDescription = existing.albumDescription.ifBlank { albumDescription }
                    val mergedAlbumWorkId = existing.albumWorkId.ifBlank { albumWorkId }
                    val mergedAlbumRjCode = existing.albumRjCode.ifBlank { albumRjCode }
                    if (
                        mergedSubtitle != existing.subtitle ||
                        mergedAlbumTitle != existing.albumTitle ||
                        mergedAlbumCircle != existing.albumCircle ||
                        mergedAlbumCv != existing.albumCv ||
                        mergedAlbumTagsCsv != existing.albumTagsCsv ||
                        mergedAlbumCoverUrl != existing.albumCoverUrl ||
                        mergedAlbumDescription != existing.albumDescription ||
                        mergedAlbumWorkId != existing.albumWorkId ||
                        mergedAlbumRjCode != existing.albumRjCode
                    ) {
                        runCatching {
                            dao.updateTaskMetadata(
                                taskId = existing.id,
                                subtitle = mergedSubtitle,
                                albumTitle = mergedAlbumTitle,
                                albumCircle = mergedAlbumCircle,
                                albumCv = mergedAlbumCv,
                                albumTagsCsv = mergedAlbumTagsCsv,
                                albumCoverUrl = mergedAlbumCoverUrl,
                                albumDescription = mergedAlbumDescription,
                                albumWorkId = mergedAlbumWorkId,
                                albumRjCode = mergedAlbumRjCode,
                                updatedAt = now0
                            )
                        }
                    }
                    existing.id
                } else {
                    val inserted = dao.insertTask(
                        DownloadTaskEntity(
                            taskKey = resolvedTaskKey,
                            title = resolvedTitle,
                            subtitle = resolvedSubtitle,
                            rootDir = resolvedRootDir,
                            albumTitle = albumTitle,
                            albumCircle = albumCircle,
                            albumCv = albumCv,
                            albumTagsCsv = albumTagsCsv,
                            albumCoverUrl = albumCoverUrl,
                            albumDescription = albumDescription,
                            albumWorkId = albumWorkId,
                            albumRjCode = albumRjCode,
                            createdAt = now0,
                            updatedAt = now0
                        )
                    )
                    if (inserted > 0) inserted else (dao.getTaskByKey(resolvedTaskKey)?.id ?: 0L)
                }
            }

            val targetFolder = File(targetDir)
            val file = File(targetFolder, fileName)
            if (!targetFolder.exists()) targetFolder.mkdirs()
            runCatching {
                val albumsRoot = File(applicationContext.getExternalFilesDir(null), "albums")
                if (!albumsRoot.exists()) albumsRoot.mkdirs()
                val rootMarker = File(albumsRoot, ".nomedia")
                if (!rootMarker.exists()) rootMarker.createNewFile()
                val marker = File(targetFolder, ".nomedia")
                if (!marker.exists()) marker.createNewFile()
            }

            val entryPoint = EntryPointAccessors.fromApplication(applicationContext, DownloadWorkerEntryPoint::class.java)
            val client = entryPoint.okHttpClient()
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", NetworkHeaders.USER_AGENT)
            
            var existingBytes = if (file.exists()) file.length().coerceAtLeast(0L) else 0L
            val lowerUrl = url.lowercase()
            if (lowerUrl.contains("play.dlsite.com")) {
                val cookie = buildDlsiteCookieHeader(DlsiteAuthStore(applicationContext).getPlayCookie())
                requestBuilder
                    .addHeader("Referer", "https://play.dlsite.com/library")
                    .addHeader("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
                if (cookie.isNotBlank()) {
                    requestBuilder.addHeader("Cookie", cookie)
                }
            } else if (lowerUrl.contains("dlsite")) {
                val cookie = buildDlsiteCookieHeader(DlsiteAuthStore(applicationContext).getDlsiteCookie())
                requestBuilder
                    .addHeader("Referer", NetworkHeaders.REFERER_DLSITE)
                    .addHeader("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
                if (cookie.isNotBlank()) {
                    requestBuilder.addHeader("Cookie", cookie)
                }
            }
            if (existingBytes > 0L) {
                requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            }

            var total = -1L
            var downloaded = existingBytes
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now0))
            var pendingTrafficBytes = 0L

            suspend fun flushTrafficStats() {
                if (pendingTrafficBytes <= 0L) return
                dailyStatDao.addTraffic(today, pendingTrafficBytes)
                pendingTrafficBytes = 0L
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return ListenableWorker.Result.failure()
                val body = response.body ?: return ListenableWorker.Result.failure()
                val supportsRange = response.code == 206 && existingBytes > 0L
                if (!supportsRange && existingBytes > 0L) {
                    existingBytes = 0L
                    downloaded = 0L
                }
                val contentLen = body.contentLength().takeIf { it > 0 } ?: -1L
                total = when {
                    supportsRange && contentLen > 0 -> existingBytes + contentLen
                    contentLen > 0 -> contentLen
                    else -> -1L
                }
                val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                var lastProgressAt = 0L
                var lastBytes = 0L
                var lastTs = System.currentTimeMillis()

                dao.upsertItem(
                    DownloadItemEntity(
                        taskId = taskId,
                        workId = workId,
                        url = url,
                        relativePath = relativePath,
                        fileName = fileName,
                        targetDir = targetDir,
                        filePath = file.absolutePath,
                        state = WorkInfo.State.RUNNING.name,
                        downloaded = downloaded,
                        total = total,
                        speed = 0L,
                        createdAt = now0,
                        updatedAt = now0
                    )
                )

                body.byteStream().use { input ->
                    FileOutputStream(file, existingBytes > 0L).use { output ->
                        while (true) {
                            if (isStopped) {
                                val now = System.currentTimeMillis()
                                flushTrafficStats()
                                dao.updateItemState(workId, "PAUSED", now)
                                DownloadQueueCoordinator.requestSchedule(applicationContext)
                                return ListenableWorker.Result.success(
                                    workDataOf(
                                        "fileName" to fileName,
                                        "targetDir" to targetDir,
                                        "filePath" to file.absolutePath,
                                        "relativePath" to relativePath,
                                        "taskKey" to resolvedTaskKey
                                    )
                                )
                            }
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            pendingTrafficBytes += read.toLong()

                            val now = System.currentTimeMillis()
                            val shouldUpdate = downloaded - lastProgressAt >= PROGRESS_UPDATE_BYTES || now - lastTs >= PROGRESS_UPDATE_INTERVAL_MS
                            if (shouldUpdate) {
                                flushTrafficStats()
                                val dt = (now - lastTs).coerceAtLeast(1)
                                val speed = ((downloaded - lastBytes) * 1000 / dt).coerceAtLeast(0)
                                dao.updateItemProgress(
                                    workId = workId,
                                    state = WorkInfo.State.RUNNING.name,
                                    downloaded = downloaded,
                                    total = total,
                                    speed = speed,
                                    updatedAt = now
                                )
                                lastProgressAt = downloaded
                                lastBytes = downloaded
                                lastTs = now
                            }
                        }
                    }
                }
            }
            val now = System.currentTimeMillis()
            try {
                flushTrafficStats()
            } catch (_: Exception) {
            }
            val finalTotal = if (total > 0) total else downloaded
            dao.updateItemProgress(
                workId = workId,
                state = WorkInfo.State.SUCCEEDED.name,
                downloaded = downloaded,
                total = finalTotal,
                speed = 0L,
                updatedAt = now
            )
            runCatching {
                val finalizeInput = workDataOf(
                    "taskKey" to resolvedTaskKey,
                    "taskTitle" to resolvedTitle,
                    "taskSubtitle" to resolvedSubtitle,
                    "taskRootDir" to resolvedRootDir,
                    "albumTitle" to albumTitle,
                    "albumCircle" to albumCircle,
                    "albumCv" to albumCv,
                    "albumTagsCsv" to albumTagsCsv,
                    "albumCoverUrl" to albumCoverUrl,
                    "albumDescription" to albumDescription,
                    "albumWorkId" to albumWorkId,
                    "albumRjCode" to albumRjCode
                )
                val request = OneTimeWorkRequestBuilder<FinalizeDownloadTaskWorker>()
                    .setInputData(finalizeInput)
                    .addTag("download_finalize")
                    .addTag(resolvedTaskKey)
                    .build()
                val unique = "download_finalize_${resolvedTaskKey.hashCode()}"
                WorkManager.getInstance(applicationContext)
                    .enqueueUniqueWork(unique, ExistingWorkPolicy.REPLACE, request)
            }
            DownloadQueueCoordinator.requestSchedule(applicationContext)
            ListenableWorker.Result.success(
                workDataOf(
                    "fileName" to fileName,
                    "targetDir" to targetDir,
                    "filePath" to File(targetDir, fileName).absolutePath,
                    "relativePath" to relativePath,
                    "taskKey" to resolvedTaskKey
                )
            )
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            runCatching {
                val workId = id.toString()
                val dao = AppDatabaseProvider.get(applicationContext).downloadDao()
                dao.updateItemState(workId, WorkInfo.State.FAILED.name, now)
            }
            DownloadQueueCoordinator.requestSchedule(applicationContext)
            ListenableWorker.Result.failure(
                workDataOf(
                    "fileName" to fileName,
                    "targetDir" to targetDir,
                    "filePath" to File(targetDir, fileName).absolutePath,
                    "relativePath" to relativePath,
                    "taskKey" to taskKey
                )
            )
        }
    }

    companion object {
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_UPDATE_BYTES = 256 * 1024L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 1_000L

        @Volatile
        private var lowRamDispatcher: ExecutorCoroutineDispatcher? = null

        @Volatile
        private var defaultDispatcher: ExecutorCoroutineDispatcher? = null

        private val dispatcherLock = Any()

        private fun downloadDispatcher(context: Context): CoroutineDispatcher {
            val lowRamDevice = DownloadRuntimeConfig.isLowRamDevice(context)
            val existing = if (lowRamDevice) lowRamDispatcher else defaultDispatcher
            if (existing != null) return existing

            return synchronized(dispatcherLock) {
                val cached = if (lowRamDevice) lowRamDispatcher else defaultDispatcher
                if (cached != null) {
                    cached
                } else {
                    // Each file maps to its own worker, so we cap download execution here to
                    // avoid dozens of concurrent network streams overwhelming low-memory phones.
                    val created = Executors
                        .newFixedThreadPool(DownloadRuntimeConfig.maxConcurrentDownloads(context))
                        .asCoroutineDispatcher()
                    if (lowRamDevice) {
                        lowRamDispatcher = created
                    } else {
                        defaultDispatcher = created
                    }
                    created
                }
            }
        }
    }
}

class FinalizeDownloadTaskWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): ListenableWorker.Result {
        val taskKey = inputData.getString("taskKey").orEmpty()
        val taskTitle = inputData.getString("taskTitle").orEmpty()
        val taskSubtitle = inputData.getString("taskSubtitle").orEmpty()
        val taskRootDir = inputData.getString("taskRootDir").orEmpty()
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
            db.withTransaction {
                val dao = db.downloadDao()
                val task = if (taskKey.isNotBlank()) {
                    dao.getTaskByKey(taskKey)
                } else {
                    dao.getTaskByRootDir(taskRootDir)
                } ?: return@withTransaction

                val items = dao.getItemsForTask(task.id)
                val done = items.isNotEmpty() && items.all { it.state == WorkInfo.State.SUCCEEDED.name }
                if (!done) return@withTransaction

                upsertDownloadedAlbumToLibrary(
                    db = db,
                    appContext = applicationContext,
                    rootDir = task.rootDir.ifBlank { taskRootDir },
                    taskTitle = task.title.ifBlank { taskTitle },
                    taskSubtitle = task.subtitle.ifBlank { taskSubtitle },
                    albumTitle = albumTitle,
                    albumCircle = albumCircle,
                    albumCv = albumCv,
                    albumTagsCsv = albumTagsCsv,
                    albumCoverUrl = albumCoverUrl,
                    albumDescription = albumDescription,
                    albumWorkId = albumWorkId,
                    albumRjCode = albumRjCode
                )

                runCatching {
                    val marker = File(task.rootDir.ifBlank { taskRootDir }, ".download_complete")
                    if (!marker.exists()) marker.createNewFile()
                }
            }
            ListenableWorker.Result.success()
        } catch (_: Exception) {
            ListenableWorker.Result.success()
        }
    }
}

private suspend fun upsertDownloadedAlbumToLibrary(
    db: com.asmr.player.data.local.db.AppDatabase,
    appContext: Context,
    rootDir: String,
    taskTitle: String,
    taskSubtitle: String,
    albumTitle: String = "",
    albumCircle: String = "",
    albumCv: String = "",
    albumTagsCsv: String = "",
    albumCoverUrl: String = "",
    albumDescription: String = "",
    albumWorkId: String = "",
    albumRjCode: String = ""
) {
    val dir = File(rootDir)
    if (!dir.exists() || !dir.isDirectory) return

    val titleTrimmed = taskTitle.trim()
    val subtitleTrimmed = taskSubtitle.trim()
    val normalizedWorkId = albumRjCode.trim().ifBlank { albumWorkId.trim() }
    val rj = extractRjCode(normalizedWorkId.ifBlank { titleTrimmed.ifBlank { dir.name } })

    val albumDao = db.albumDao()
    val trackDao = db.trackDao()
    val albumFtsDao = db.albumFtsDao()

    val existing = try {
        albumDao.getAlbumByPathOnce(dir.absolutePath)
    } catch (_: Exception) {
        null
    } ?: try {
        if (rj.isNotBlank()) albumDao.getAlbumByWorkIdOnce(rj) else null
    } catch (_: Exception) {
        null
    }

    val cover = pickCoverFileFromAlbumDir(dir)
    val entity = AlbumEntity(
        id = existing?.id ?: 0L,
        title = subtitleTrimmed
            .ifBlank { albumTitle.trim() }
            .ifBlank { existing?.title?.takeIf { it.isNotBlank() } ?: titleTrimmed.ifBlank { dir.name } },
        path = existing?.path?.takeIf { it.isNotBlank() } ?: dir.absolutePath,
        localPath = existing?.localPath,
        downloadPath = dir.absolutePath,
        circle = existing?.circle?.takeIf { it.isNotBlank() } ?: albumCircle.trim(),
        cv = existing?.cv?.takeIf { it.isNotBlank() } ?: albumCv.trim(),
        tags = existing?.tags?.takeIf { it.isNotBlank() } ?: albumTagsCsv.trim(),
        coverUrl = existing?.coverUrl?.takeIf { it.isNotBlank() } ?: albumCoverUrl.trim(),
        coverPath = cover?.absolutePath ?: existing?.coverPath.orEmpty(),
        coverThumbPath = existing?.coverThumbPath.orEmpty(),
        workId = existing?.workId?.takeIf { it.isNotBlank() }
            ?: albumWorkId.trim().ifBlank { rj },
        rjCode = existing?.rjCode?.takeIf { it.isNotBlank() }
            ?: albumRjCode.trim().ifBlank { rj },
        description = existing?.description?.takeIf { it.isNotBlank() } ?: albumDescription.trim()
    )

    val albumId = try {
        albumDao.insertAlbum(entity)
    } catch (_: Exception) {
        0L
    }
    if (albumId <= 0L) return

    val coverThumbWork = OneTimeWorkRequestBuilder<AlbumCoverThumbWorker>()
        .setInputData(workDataOf(AlbumCoverThumbWorker.KEY_ALBUM_ID to albumId))
        .addTag("album_cover_thumb")
        .build()
    WorkManager.getInstance(appContext)
        .enqueueUniqueWork("album_cover_thumb_$albumId", ExistingWorkPolicy.REPLACE, coverThumbWork)

    val fts = AlbumFtsEntity(
        albumId = albumId,
        title = entity.title,
        circle = entity.circle,
        cv = entity.cv,
        rjCode = entity.rjCode,
        workId = entity.workId,
        tagsToken = entity.tags.replace(',', ' ').trim()
    )
    try {
        albumFtsDao.upsert(listOf(fts))
    } catch (_: Exception) {
    }

    val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
    val subtitleExtensions = setOf("lrc", "srt", "vtt")
    val audioFiles = dir.walkTopDown()
        .filter { it.isFile && audioExtensions.contains(it.extension.lowercase()) }
        .toList()
        .sortedBy { it.absolutePath.lowercase() }

    val existingTracks = try {
        trackDao.getTracksForAlbumOnce(albumId)
    } catch (_: Exception) {
        emptyList()
    }
    val prefix = dir.absolutePath.trimEnd('\\', '/') + File.separator
    val existingLocalKeys = LinkedHashSet<String>()
    val existingLocalKeysNoGroup = LinkedHashSet<String>()
    existingTracks
        .filter { it.path.isNotBlank() && !it.path.startsWith(prefix) && !it.path.trim().startsWith("http", ignoreCase = true) }
        .forEach { t ->
            existingLocalKeys.add(TrackKeyNormalizer.buildKey(t.title, t.group, null))
            existingLocalKeysNoGroup.add(TrackKeyNormalizer.buildKey(t.title, "", null))
        }
    val toDelete = existingTracks.filter { it.path.startsWith(dir.absolutePath) }.map { it.id }
    if (toDelete.isNotEmpty()) {
        runCatching { trackDao.deleteSubtitlesForTracks(toDelete) }
        runCatching { trackDao.deleteTracksByIds(toDelete) }
    }

    val filteredAudioFiles = audioFiles.filter { f ->
        val group = if (f.parentFile != null && f.parentFile?.absolutePath != dir.absolutePath) f.parentFile?.name.orEmpty() else ""
        val key = TrackKeyNormalizer.buildKey(f.nameWithoutExtension.ifBlank { "track" }, group, null)
        val keyNoGroup = TrackKeyNormalizer.buildKey(f.nameWithoutExtension.ifBlank { "track" }, "", null)
        !(existingLocalKeys.contains(key) || existingLocalKeysNoGroup.contains(keyNoGroup))
    }

    val newTracks = filteredAudioFiles.map { f ->
        val group = if (f.parentFile != null && f.parentFile?.absolutePath != dir.absolutePath) f.parentFile?.name.orEmpty() else ""
        TrackEntity(
            albumId = albumId,
            title = f.nameWithoutExtension.ifBlank { "track" },
            path = f.absolutePath,
            duration = 0.0,
            group = group
        )
    }
    if (newTracks.isNotEmpty()) {
        val insertedTrackIds = runCatching { trackDao.insertTracks(newTracks) }.getOrDefault(emptyList())
        if (insertedTrackIds.isNotEmpty()) {
            val subtitlesToInsert = ArrayList<SubtitleEntity>()
            val prefer = listOf("default", "zh", "cn", "chs", "ja", "jp", "jpn", "en")

            fun findBestSidecarSubtitle(audio: File): File? {
                val parent = audio.parentFile ?: return null
                val base = audio.nameWithoutExtension
                val siblings = parent.listFiles()
                    ?.filter { it.isFile && subtitleExtensions.contains(it.extension.lowercase()) }
                    .orEmpty()

                val matched = siblings.mapNotNull { f ->
                    val nameWithoutExt = f.nameWithoutExtension
                    val isMatch: Boolean
                    val language: String
                    if (nameWithoutExt.equals(base, ignoreCase = true)) {
                        isMatch = true
                        language = "default"
                    } else if (nameWithoutExt.startsWith("$base.", ignoreCase = true)) {
                        isMatch = true
                        val suffix = nameWithoutExt.substring(base.length + 1)
                        val parts = suffix.split('.').filter { it.isNotBlank() }
                        val valid = parts.filter { !audioExtensions.contains(it.lowercase()) }
                        language = when {
                            valid.isEmpty() -> "zh"
                            valid.size == 1 -> valid[0]
                            else -> valid.last()
                        }
                    } else {
                        isMatch = false
                        language = "default"
                    }
                    if (isMatch) f to language else null
                }

                val ordered = matched.sortedWith(
                    compareBy<Pair<File, String>> { (_, lang) ->
                        val idx = prefer.indexOf(lang.lowercase())
                        if (idx >= 0) idx else Int.MAX_VALUE
                    }.thenBy { it.first.name.lowercase() }
                )
                return ordered.firstOrNull()?.first
            }

            insertedTrackIds.zip(filteredAudioFiles).forEach { (trackId, audio) ->
                val subtitleFile = findBestSidecarSubtitle(audio) ?: return@forEach
                val entries = runCatching { SubtitleParser.parse(subtitleFile.absolutePath) }.getOrDefault(emptyList())
                entries.forEach { e ->
                    subtitlesToInsert.add(
                        SubtitleEntity(
                            trackId = trackId,
                            startMs = e.startMs,
                            endMs = e.endMs,
                            text = e.text
                        )
                    )
                }
            }

            if (subtitlesToInsert.isNotEmpty()) {
                runCatching { trackDao.insertSubtitles(subtitlesToInsert) }
            }
        }
    }

    val allAfterInsert = runCatching { trackDao.getTracksForAlbumOnce(albumId) }.getOrDefault(emptyList())
    val localAfterInsert = allAfterInsert.filter { !it.path.trim().startsWith("http", ignoreCase = true) }
    val localKeyToId = LinkedHashMap<String, Long>()
    val localKeyToIdNoGroup = LinkedHashMap<String, Long>()
    localAfterInsert
        .sortedWith(compareByDescending<TrackEntity> { it.path.startsWith(prefix) }.thenBy { it.id })
        .forEach { t ->
            localKeyToId.putIfAbsent(TrackKeyNormalizer.buildKey(t.title, t.group, null), t.id)
            localKeyToIdNoGroup.putIfAbsent(TrackKeyNormalizer.buildKey(t.title, "", null), t.id)
        }

    allAfterInsert
        .filter { it.path.trim().startsWith("http", ignoreCase = true) }
        .forEach { online ->
            val key = TrackKeyNormalizer.buildKey(online.title, online.group, null)
            val keyNoGroup = TrackKeyNormalizer.buildKey(online.title, "", null)
            val targetId = localKeyToId[key] ?: localKeyToIdNoGroup[keyNoGroup]
            if (targetId != null) {
                val sourceSubs = runCatching { trackDao.getSubtitlesForTrack(online.id) }.getOrDefault(emptyList())
                if (sourceSubs.isNotEmpty()) {
                    val targetHasSubs = runCatching { trackDao.getSubtitlesForTrack(targetId) }.getOrDefault(emptyList()).isNotEmpty()
                    if (!targetHasSubs) {
                        runCatching {
                            trackDao.insertSubtitles(
                                sourceSubs.map { s ->
                                    SubtitleEntity(
                                        trackId = targetId,
                                        startMs = s.startMs,
                                        endMs = s.endMs,
                                        text = s.text
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
}

private fun extractRjCode(text: String): String {
    val raw = text.trim()
    val m = Regex("""RJ\s*([0-9]{3,})""", RegexOption.IGNORE_CASE).find(raw) ?: return raw.takeIf { it.startsWith("RJ", true) } ?: ""
    return "RJ" + m.groupValues[1]
}

private fun pickCoverFileFromAlbumDir(dir: File): File? {
    val exts = setOf("jpg", "jpeg", "png", "webp")
    val direct = dir.listFiles()?.firstOrNull { f ->
        f.isFile && f.nameWithoutExtension.equals("cover", ignoreCase = true) && exts.contains(f.extension.lowercase())
    }
    if (direct != null) return direct
    return dir.walkTopDown()
        .firstOrNull { f -> f.isFile && exts.contains(f.extension.lowercase()) }
}
