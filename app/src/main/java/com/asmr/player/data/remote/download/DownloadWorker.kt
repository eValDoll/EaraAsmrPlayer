package com.asmr.player.data.remote.download

import com.asmr.player.i18n.appString
import com.asmr.player.R
import com.asmr.player.nas.LibraryWorkspace
import com.asmr.player.util.ChapterMediaReference
import com.asmr.player.util.LocalFileOperation
import com.asmr.player.util.LocalFileScopes
import com.asmr.player.util.LocalFileOperationCoordinator
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.io.EOFException
import android.util.Log
import androidx.work.*
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.buildDlsiteCookieHeader
import com.asmr.player.data.remote.auth.mergeDlsiteCookieHeaders
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.dlsite.descrambleDlsitePlayImageFile
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.repository.AudioMetadataCache
import com.asmr.player.util.SubtitleMatchSupport
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import okhttp3.HttpUrl.Companion.toHttpUrl

private class SessionCookieJar : CookieJar {
    private val store = LinkedHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = url.host.lowercase()
        val list = store.getOrPut(key) { mutableListOf() }
        cookies.forEach { cookie ->
            list.removeAll { it.name == cookie.name && it.path == cookie.path && it.domain == cookie.domain }
            list.add(cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host.lowercase()
        val path = url.encodedPath
        val now = System.currentTimeMillis()
        val out = mutableListOf<Cookie>()
        store.values.forEach { cookies ->
            cookies.removeAll { it.expiresAt < now }
            cookies.forEach { cookie ->
                val domainMatches = if (cookie.hostOnly) {
                    host == cookie.domain.lowercase()
                } else {
                    host == cookie.domain.lowercase() || host.endsWith(".${cookie.domain.lowercase()}")
                }
                val pathMatches = path.startsWith(cookie.path)
                if (domainMatches && pathMatches) out += cookie
            }
        }
        return out
    }

    fun seedFromHeader(header: String, url: String) {
        val httpUrl = runCatching { url.toHttpUrl() }.getOrNull() ?: return
        header.split(';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { item ->
                val idx = item.indexOf('=')
                if (idx <= 0) return@forEach
                val name = item.substring(0, idx).trim()
                val value = item.substring(idx + 1).trim()
                if (name.isBlank()) return@forEach
                val cookie = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain(httpUrl.host)
                    .path("/")
                    .build()
                saveFromResponse(httpUrl, listOf(cookie))
            }
    }
}

/** Transfers one file, preserving resumable bytes and publishing it before requesting finalization. */
class DownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadWorkerEntryPoint {
        fun okHttpClient(): OkHttpClient
    }

    override suspend fun doWork(): ListenableWorker.Result = withContext(downloadDispatcher(applicationContext)) {
        val target = inputData.getString("targetDir").orEmpty()
        val resource = DownloadStorageGateway(applicationContext).stableIdentity(target) + "/" + inputData.getString("fileName").orEmpty()
        val database = AppDatabaseProvider.get(applicationContext)
        val item = database.downloadDao().getItemByWorkId(id.toString()) ?: return@withContext ListenableWorker.Result.failure()
        val scope = LocalFileScopes(database, DownloadStorageGateway(applicationContext)).downloadFile(item)
        LocalFileOperationCoordinator.shared.withOperation(LocalFileOperation.DOWNLOAD, resource, scope) {
            executeDownloadWork()
        }
    }

    private suspend fun executeDownloadWork(): ListenableWorker.Result {
        val url = inputData.getString("url") ?: return ListenableWorker.Result.failure()
        val fileName = inputData.getString("fileName") ?: return ListenableWorker.Result.failure()
        val targetDir = inputData.getString("targetDir") ?: return ListenableWorker.Result.failure()
        val taskKey = inputData.getString("taskKey").orEmpty()
        val taskRootDir = inputData.getString("taskRootDir").orEmpty()
        val relativePath = inputData.getString("relativePath").orEmpty().ifBlank { fileName }.replace('\\', '/')
        val dlsitePlayImageSeed = inputData.getInt("dlsitePlayImageSeed", -1).takeIf { it >= 0 }
        val dlsitePlayImageWidth = inputData.getInt("dlsitePlayImageWidth", -1).takeIf { it > 0 }
        val dlsitePlayImageHeight = inputData.getInt("dlsitePlayImageHeight", -1).takeIf { it > 0 }
        val hasDlsitePlayImageTransform = dlsitePlayImageSeed != null &&
            dlsitePlayImageWidth != null &&
            dlsitePlayImageHeight != null

        val nas = LibraryWorkspace.owns(Uri.parse(targetDir))
        return try {
            val workId = id.toString()
            val appDb = AppDatabaseProvider.get(applicationContext)
            val dao = appDb.downloadDao()
            val currentItem = dao.getItemByWorkId(workId)
                ?: return ListenableWorker.Result.failure()
            if (currentItem.state !in setOf(WorkInfo.State.ENQUEUED.name, WorkInfo.State.RUNNING.name)) {
                return ListenableWorker.Result.success()
            }
            val dailyStatDao = appDb.dailyStatDao()
            val storage = DownloadStorageGateway(applicationContext)
            val now0 = System.currentTimeMillis()
            val resolvedTaskKey = taskKey.ifBlank { "dir:${taskRootDir.ifBlank { targetDir }}" }
            if (dao.getTaskById(currentItem.taskId) == null || currentItem.targetDir != targetDir) {
                return ListenableWorker.Result.failure()
            }

            val usesDocumentTree = storage.isDocumentReference(targetDir)
            val targetFolder = if (usesDocumentTree) null else File(targetDir)
            val file = if (usesDocumentTree) {
                downloadStagingFile(applicationContext, currentItem)
            } else {
                File(checkNotNull(targetFolder), fileName)
            }
            if (nas) {
                foregroundNasTransfer(inputData.getString("taskTitle").orEmpty().ifBlank { fileName })
                // An existing task can already have a published file when a previous import failed.
                if (!file.exists() && currentItem.filePath.isNotBlank() && currentItem.total > 0 &&
                    currentItem.downloaded == currentItem.total &&
                    !hasDlsitePlayImageTransform && !fileName.equals("dlsite_lossless_archive.zip", true) &&
                    storage.nasFileSize(currentItem.filePath) == currentItem.total
                ) {
                    dao.updateRunningItemProgress(workId, DOWNLOAD_STATE_FINALIZING, currentItem.total, currentItem.total, 0, now0)
                    dao.getTaskById(currentItem.taskId)?.let { enqueueDownloadFinalization(applicationContext, it) }
                    DownloadQueueCoordinator.requestSchedule(applicationContext)
                    return ListenableWorker.Result.success()
                }
            }
            val partialFile = dlsitePlayImagePartFile(file)
            val transferFile = if (hasDlsitePlayImageTransform) partialFile else file
            file.parentFile?.mkdirs()
            if (targetFolder != null && !targetFolder.exists()) targetFolder.mkdirs()
            runCatching {
                if (usesDocumentTree) {
                    storage.ensureFile(targetDir, ".nomedia", "application/octet-stream")
                } else {
                    val albumsRoot = File(applicationContext.getExternalFilesDir(null), LibraryWorkspace.name("albums"))
                    if (!albumsRoot.exists()) albumsRoot.mkdirs()
                    val rootMarker = File(albumsRoot, ".nomedia")
                    if (!rootMarker.exists()) rootMarker.createNewFile()
                    val marker = File(checkNotNull(targetFolder), ".nomedia")
                    if (!marker.exists()) marker.createNewFile()
                }
                Unit
            }

            val entryPoint = EntryPointAccessors.fromApplication(applicationContext, DownloadWorkerEntryPoint::class.java)
            val baseClient = entryPoint.okHttpClient()
            val isJapaneseAsmr = relativePath.startsWith("japaneseasmr.com/")
            val chapter = if (isJapaneseAsmr) ChapterMediaReference.parse(url)?.takeIf { it.isHls } else null
            val nativeUrl = chapter?.downloadUrl?.takeIf { it.isNotBlank() } ?: url
            val requestBuilder = Request.Builder()
                .header("Accept-Encoding", "identity")
                .url(nativeUrl)
                .header("User-Agent", NetworkHeaders.USER_AGENT)
            if (isJapaneseAsmr) requestBuilder
                .header("Referer", "https://japaneseasmr.com/")
                .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)

            val transformAlreadyApplied = hasDlsitePlayImageTransform && file.exists() && !partialFile.exists()
            var existingBytes = if (transferFile.exists()) transferFile.length().coerceAtLeast(0L) else 0L
            val lowerUrl = nativeUrl.lowercase()
            val sessionCookieJar = SessionCookieJar()
            if (lowerUrl.contains("play.dlsite.com")) {
                val cookie = mergeDlsiteCookieHeaders(
                    buildDlsiteCookieHeader(DlsiteAuthStore(applicationContext).getDlsiteCookie()),
                    buildDlsiteCookieHeader(DlsiteAuthStore(applicationContext).getPlayCookie())
                )
                requestBuilder
                    .addHeader("Referer", "https://play.dlsite.com/library")
                    .addHeader("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
                if (cookie.isNotBlank()) {
                    requestBuilder.addHeader("Cookie", cookie)
                    sessionCookieJar.seedFromHeader(cookie, url)
                }
            } else if (lowerUrl.contains("dlsite")) {
                val cookie = mergeDlsiteCookieHeaders(
                    buildDlsiteCookieHeader(DlsiteAuthStore(applicationContext).getDlsiteCookie()),
                    buildDlsiteCookieHeader(DlsiteAuthStore(applicationContext).getPlayCookie())
                )
                requestBuilder
                    .addHeader("Referer", "https://play.dlsite.com/")
                    .addHeader("Accept-Language", NetworkHeaders.ACCEPT_LANGUAGE)
                if (cookie.isNotBlank()) {
                    requestBuilder.addHeader("Cookie", cookie)
                    sessionCookieJar.seedFromHeader(cookie, url)
                }
            }
            val client = if (lowerUrl.contains("dlsite")) {
                baseClient.newBuilder().cookieJar(sessionCookieJar).build()
            } else {
                baseClient
            }
            if (existingBytes > 0L) {
                requestBuilder.addHeader("Range", "bytes=$existingBytes-")
            }

            val knownTotal = currentItem.total.takeIf { it > 0L } ?: -1L
            var total = knownTotal
            var downloaded = if (transformAlreadyApplied) knownTotal.coerceAtLeast(file.length()) else existingBytes
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now0))
            var pendingTrafficBytes = 0L

            suspend fun flushTrafficStats() {
                if (pendingTrafficBytes <= 0L) return
                dailyStatDao.addTraffic(today, pendingTrafficBytes)
                pendingTrafficBytes = 0L
            }

            val transferAlreadyComplete = transformAlreadyApplied ||
                if (nas) file == readyNasStagingFile(file) && file.isFile
                else knownTotal > 0L && existingBytes == knownTotal
            suspend fun downloadHls() {
                val fallback = checkNotNull(chapter)
                check(fallback.useHlsDownload) { appString(R.string.ui_online_audio_download_has_not_been_confirmed) }
                var progressAt = System.currentTimeMillis()
                downloadJapaneseAsmrHls(applicationContext, baseClient, fallback, file) { bytes, networkBytes ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (isStopped) throw kotlinx.coroutines.CancellationException(appString(R.string.ui_download_stopped))
                    downloaded = bytes
                    pendingTrafficBytes += networkBytes
                    flushTrafficStats()
                    val now = System.currentTimeMillis()
                    val speed = networkBytes * 1_000L / (now - progressAt).coerceAtLeast(1L)
                    progressAt = now
                    if (dao.updateRunningItemProgress(
                            workId, WorkInfo.State.RUNNING.name, bytes, -1L, speed, now,
                        ) == 0) throw kotlinx.coroutines.CancellationException(appString(R.string.ui_download_paused_or_canceled))
                }
                downloaded = file.length()
                total = downloaded
                existingBytes = 0L
            }
            if (!transferAlreadyComplete && chapter?.useHlsDownload == true) {
                downloadHls()
            } else if (!transferAlreadyComplete) {
                if (chapter != null && chapter.downloadUrl.isNullOrBlank()) throw IOException(appString(R.string.ui_online_audio_download_has_not_been_confirmed))
                if (isJapaneseAsmr) requestBuilder.url(resolveJapaneseAsmrDownload(baseClient, nativeUrl, fileName))
                client.readStagedDownload(requestBuilder.build(), existingBytes) { response ->
                    if (response.code == 416 && existingBytes > 0L &&
                        response.header("Content-Range").equals("bytes */$existingBytes", ignoreCase = true)) {
                        total = existingBytes
                        return@readStagedDownload
                    }
                    if (!response.isSuccessful) {
                        throw DownloadHttpException(response.code)
                    }
                    val body = response.body ?: throw EOFException("Empty download response")
                    if (isJapaneseAsmr) {
                        val contentType = response.header("Content-Type").orEmpty().lowercase()
                        val dispositionName = response.header("Content-Disposition").orEmpty()
                        if (contentType.contains("text/") || contentType.contains("html") ||
                            Regex("(?i)\\.(exe|scr|lnk|py|bat|cmd)(?:[\"';\\s]|$)").containsMatchIn(dispositionName)) {
                            throw IOException(appString(R.string.ui_the_download_link_did_not_return_audio))
                        }
                        val header = if (response.code == 206 && existingBytes > 0) {
                            transferFile.inputStream().use { it.readAudioHeader() }
                        } else {
                            body.source().peek().inputStream().use { it.readAudioHeader() }
                        }
                        if (!isJapaneseAsmrAudioHeader(fileName, header)) throw IOException(appString(R.string.ui_the_download_link_did_not_return_audio))
                    }
                    val supportsRange = response.code == 206
                    if (!supportsRange && existingBytes > 0L) {
                        existingBytes = 0L
                        downloaded = 0L
                    }
                    val contentLen = body.contentLength().takeIf { it > 0 } ?: -1L
                    total = when {
                        supportsRange -> resumedDownloadTotal(response.header("Content-Range"), existingBytes, body.contentLength())
                        contentLen > 0 -> contentLen
                        else -> -1L
                    }
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    var progressBaselineBytes = downloaded
                    var progressBaselineTs = System.currentTimeMillis()

                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    if (dao.updateRunningItemProgress(
                            workId, WorkInfo.State.RUNNING.name, downloaded, total, 0L, now0
                        ) == 0) throw kotlinx.coroutines.CancellationException(appString(R.string.ui_download_paused_or_canceled))

                    body.byteStream().use { input ->
                        FileOutputStream(transferFile, existingBytes > 0L).use { output ->
                            while (true) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                if (isStopped) throw kotlinx.coroutines.CancellationException(appString(R.string.ui_download_stopped))
                                val read = input.read(buffer)
                                if (read <= 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                pendingTrafficBytes += read.toLong()

                                val now = System.currentTimeMillis()
                                val shouldUpdate = now - progressBaselineTs >= PROGRESS_UPDATE_INTERVAL_MS
                                if (shouldUpdate) {
                                    flushTrafficStats()
                                    val dt = (now - progressBaselineTs).coerceAtLeast(1)
                                    val speed = ((downloaded - progressBaselineBytes) * 1000 / dt).coerceAtLeast(0)
                                    dao.updateRunningItemProgress(
                                        workId = workId,
                                        state = WorkInfo.State.RUNNING.name,
                                        downloaded = downloaded,
                                        total = total,
                                        speed = speed,
                                        updatedAt = now
                                    )
                                    progressBaselineBytes = downloaded
                                    progressBaselineTs = now
                                }
                            }
                        }
                    }
                }
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (!transformAlreadyApplied) {
                check(transferFile.isFile && transferFile.length() == downloaded) { appString(R.string.ui_the_downloaded_file_was_changed_by_another_app) }
                if (total >= 0L && downloaded != total) throw EOFException(appString(R.string.ui_the_downloaded_file_is_incomplete))
            } else {
                check(file.isFile) { appString(R.string.ui_the_downloaded_file_was_moved_or_deleted) }
            }
            if (dao.getItemByWorkId(workId)?.state !in setOf(WorkInfo.State.RUNNING.name, WorkInfo.State.ENQUEUED.name)) {
                throw kotlinx.coroutines.CancellationException(appString(R.string.ui_download_paused_or_canceled))
            }
            if (hasDlsitePlayImageTransform && !transformAlreadyApplied) {
                descrambleDlsitePlayImageFile(
                    scrambledFile = partialFile,
                    outputFile = file,
                    seed = checkNotNull(dlsitePlayImageSeed),
                    width = checkNotNull(dlsitePlayImageWidth),
                    height = checkNotNull(dlsitePlayImageHeight)
                )
                if (partialFile.exists() && !partialFile.delete()) {
                    throw IOException("Unable to remove scrambled DLsite Play image")
                }
            }
            val finalTotal = if (total > 0) total else downloaded
            val staged = if (nas) sealNasStagingFile(file) else file
            val publishedReference = if (usesDocumentTree) {
                if (dao.updateRunningItemProgress(
                        workId, WorkInfo.State.RUNNING.name, downloaded, finalTotal, 0L, System.currentTimeMillis()
                    ) == 0) throw kotlinx.coroutines.CancellationException(appString(R.string.ui_download_paused_or_canceled))
                val size = staged.length()
                val reference = storage.ensureFile(targetDir, fileName, downloadMimeType(fileName))
                val alreadyUploaded = nas && staged == file && storage.nasFileSize(reference) == size &&
                    storage.matchesNasDownload(reference, staged::inputStream)
                if (!alreadyUploaded) publishDownloadFiles(targetDir, workId, { size }) { copy ->
                    storage.write(reference, size) { output ->
                        staged.inputStream().use { input -> copy(input, output) }
                    }
                }
                if (nas && staged.extension.lowercase() in SubtitleMatchSupport.AudioExtensions) {
                    AudioMetadataCache.recordNasDownload(applicationContext, reference, staged.absolutePath)
                }
                reference
            } else {
                file.absolutePath
            }
            if (nas) {
                if (storage.nasFileSize(publishedReference) != staged.length()) throw EOFException("Incomplete NAS upload")
            } else {
                check(storage.exists(publishedReference)) { appString(R.string.ui_the_downloaded_file_was_moved_or_deleted) }
                if (!hasDlsitePlayImageTransform) {
                    check(storage.size(publishedReference) == downloaded) { appString(R.string.ui_the_downloaded_file_was_changed_by_another_app) }
                }
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val now = System.currentTimeMillis()
            try {
                flushTrafficStats()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Unable to flush download traffic for work $id", failure)
            }
            withContext(NonCancellable) {
                val published = appDb.withTransaction {
                    val updated = dao.updateRunningItemProgress(workId, DOWNLOAD_STATE_FINALIZING, downloaded, finalTotal, 0L, now)
                    if (updated > 0 && usesDocumentTree) dao.updateItemDestination(workId, publishedReference, targetDir, now)
                    updated > 0
                }
                if (published && usesDocumentTree && !fileName.equals("dlsite_lossless_archive.zip", true)) staged.delete()
                dao.getTaskById(currentItem.taskId)?.let { enqueueDownloadFinalization(applicationContext, it) }
            }
            DownloadQueueCoordinator.requestSchedule(applicationContext)
            ListenableWorker.Result.success(
                workDataOf(
                    "fileName" to fileName,
                    "targetDir" to targetDir,
                    "filePath" to publishedReference,
                    "relativePath" to relativePath,
                    "taskKey" to resolvedTaskKey
                )
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (nas && runAttemptCount < 3 && e.isTransientNasTransferFailure()) {
                val dao = AppDatabaseProvider.get(applicationContext).downloadDao()
                val item = dao.getItemByWorkId(id.toString()) ?: return ListenableWorker.Result.success()
                val staged = downloadStagingFile(applicationContext, item)
                val bytes = if (item.hasDlsitePlayImageTransform()) item.downloaded else staged.length()
                if (dao.updateRunningItemProgress(item.workId, WorkInfo.State.ENQUEUED.name, bytes, item.total, 0L,
                        System.currentTimeMillis()) == 0) return ListenableWorker.Result.success()
                return ListenableWorker.Result.retry()
            }
            Log.w(TAG, "Download failed for work $id, file $fileName", e)
            val now = System.currentTimeMillis()
            runCatching {
                val workId = id.toString()
                val dao = AppDatabaseProvider.get(applicationContext).downloadDao()
                dao.failRunningItem(workId, now)
            }
            DownloadQueueCoordinator.requestSchedule(applicationContext)
            ListenableWorker.Result.failure(
                workDataOf(
                    "fileName" to fileName,
                    "targetDir" to targetDir,
                    "filePath" to if (targetDir.startsWith("content://")) "" else File(targetDir, fileName).absolutePath,
                    "relativePath" to relativePath,
                    "taskKey" to taskKey
                )
            )
        }
    }

    companion object {
        private const val TAG = "DownloadWorker"
        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
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

private fun InputStream.readAudioHeader(): ByteArray {
    val bytes = ByteArray(12)
    var size = 0
    while (size < bytes.size) {
        val count = read(bytes, size, bytes.size - size)
        if (count <= 0) break
        size += count
    }
    return bytes.copyOf(size)
}
