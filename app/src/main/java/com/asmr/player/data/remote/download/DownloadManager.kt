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
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
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
        val uniqueName = "${targetDir.trimEnd('/', '\\')}\\$fileName"
        val uniqueWorkName = uniqueName.hashCode().toString()

        scope.launch {
            val wm = WorkManager.getInstance(context)
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

            val existingFile = File(filePath)
            if (existingFile.exists() && existingFile.isFile) {
                val taskId = ensureTask(taskKey = taskKey, title = taskTitle, subtitle = safeTaskSubtitle, rootDir = taskRootDir, now = now)
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
                    val localWorkId = "local_$uniqueWorkName"
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
            val workInfos = runCatching { wm.getWorkInfosForUniqueWork(uniqueWorkName).get() }.getOrDefault(emptyList())
            val hasActiveWork = workInfos.any { info ->
                info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.BLOCKED
            }
            val hasSucceededWork = workInfos.any { it.state == WorkInfo.State.SUCCEEDED }

            if (hasActiveWork) return@launch
            if (hasSucceededWork && existingItem != null) {
                val file = File(existingItem.filePath.ifBlank { filePath })
                if (file.exists() && file.isFile) {
                    val size = file.length().coerceAtLeast(0L)
                    downloadDao.updateItemProgress(
                        workId = existingItem.workId,
                        state = WorkInfo.State.SUCCEEDED.name,
                        downloaded = size,
                        total = size,
                        speed = 0L,
                        updatedAt = now
                    )
                    return@launch
                }
            }
            if (existingItem != null && existingItem.state == WorkInfo.State.SUCCEEDED.name) {
                val file = File(existingItem.filePath.ifBlank { filePath })
                if (file.exists() && file.isFile) return@launch
            }

            val inputData = workDataOf(
                "url" to url,
                "fileName" to fileName,
                "targetDir" to targetDir,
                "taskRootDir" to taskRootDir,
                "relativePath" to relativePath,
                "taskKey" to taskKey,
                "taskTitle" to taskTitle,
                "taskSubtitle" to safeTaskSubtitle,
                "albumTitle" to safeAlbumTitle,
                "albumCircle" to safeAlbumCircle,
                "albumCv" to safeAlbumCv,
                "albumTagsCsv" to safeAlbumTagsCsv,
                "albumCoverUrl" to safeAlbumCoverUrl,
                "albumDescription" to safeAlbumDescription,
                "albumWorkId" to safeAlbumWorkId,
                "albumRjCode" to safeAlbumRjCode
            )

            val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .addTag("download")
                .apply {
                    addTag(taskKey)
                    tags.asSequence().filter { it != taskKey }.distinct().forEach { addTag(it) }
                }
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            val policy = if (workInfos.isNotEmpty()) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            wm.enqueueUniqueWork(uniqueWorkName, policy, downloadRequest)

            val taskId = ensureTask(taskKey = taskKey, title = taskTitle, subtitle = safeTaskSubtitle, rootDir = taskRootDir, now = now)
            val workId = downloadRequest.id.toString()
            val existingBytes = runCatching { File(filePath).length() }.getOrDefault(0L).coerceAtLeast(0L)

            if (existingItem != null) {
                downloadDao.replaceWorkIdForResume(
                    oldWorkId = existingItem.workId,
                    newWorkId = workId,
                    state = WorkInfo.State.ENQUEUED.name,
                    downloaded = existingBytes,
                    updatedAt = now
                )
            } else {
                downloadDao.upsertItem(
                    DownloadItemEntity(
                        taskId = taskId,
                        workId = workId,
                        url = url,
                        relativePath = safeRelativePath,
                        fileName = fileName,
                        targetDir = targetDir,
                        filePath = filePath,
                        state = WorkInfo.State.ENQUEUED.name,
                        downloaded = existingBytes,
                        total = -1L,
                        speed = 0L,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    private suspend fun ensureTask(taskKey: String, title: String, subtitle: String, rootDir: String, now: Long): Long {
        val existing = downloadDao.getTaskByKey(taskKey)
        if (existing != null) {
            if (existing.subtitle.isBlank() && subtitle.isNotBlank()) {
                runCatching { downloadDao.updateTaskSubtitle(existing.id, subtitle, now) }
            }
            return existing.id
        }
        val created = downloadDao.insertTask(
            DownloadTaskEntity(
                taskKey = taskKey,
                title = title,
                subtitle = subtitle,
                rootDir = rootDir,
                createdAt = now,
                updatedAt = now
            )
        )
        if (created > 0) return created
        return downloadDao.getTaskByKey(taskKey)?.id ?: 0L
    }
}

class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): ListenableWorker.Result {
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
            val dao = AppDatabaseProvider.get(applicationContext).downloadDao()
            val now0 = System.currentTimeMillis()
            val resolvedTaskKey = taskKey.ifBlank { "dir:${taskRootDir.ifBlank { targetDir }}" }
            val resolvedRootDir = taskRootDir.ifBlank { targetDir }
            val resolvedTitle = taskTitle.ifBlank { resolvedTaskKey.removePrefix("album:").ifBlank { File(resolvedRootDir).name.ifBlank { "download" } } }
            val resolvedSubtitle = taskSubtitle.trim()
            val taskId = run {
                val existing = dao.getTaskByKey(resolvedTaskKey)
                if (existing != null) {
                    if (existing.subtitle.isBlank() && resolvedSubtitle.isNotBlank()) {
                        runCatching { dao.updateTaskSubtitle(existing.id, resolvedSubtitle, now0) }
                    }
                    existing.id
                } else {
                    val inserted = dao.insertTask(
                        DownloadTaskEntity(
                            taskKey = resolvedTaskKey,
                            title = resolvedTitle,
                            subtitle = resolvedSubtitle,
                            rootDir = resolvedRootDir,
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

            val client = OkHttpClient()
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
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
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
                                dao.updateItemState(workId, "PAUSED", now)
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
                            
                            // Track daily traffic
                            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            AppDatabaseProvider.get(applicationContext).dailyStatDao().addTraffic(today, read.toLong())

                            val now = System.currentTimeMillis()
                            val shouldUpdate = downloaded - lastProgressAt >= 256 * 1024 || now - lastTs >= 1000
                            if (shouldUpdate) {
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
                                setProgress(
                                    workDataOf(
                                        "fileName" to fileName,
                                        "targetDir" to targetDir,
                                        "downloaded" to downloaded,
                                        "total" to total,
                                        "speed" to speed
                                    )
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
