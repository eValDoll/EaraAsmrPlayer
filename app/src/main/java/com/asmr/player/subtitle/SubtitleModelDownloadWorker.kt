package com.asmr.player.subtitle

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.asmr.player.R
import com.asmr.player.data.remote.NetworkHeaders
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class SubtitleModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun okHttpClient(): OkHttpClient
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val source = SubtitleModelDownloadSource.fromId(inputData.getString(KEY_SOURCE))
            ?: return@withContext Result.failure()
        val baseUrl = inputData.getString(KEY_URL).orEmpty()
        if (!baseUrl.startsWith("https://")) return@withContext Result.failure()

        val repository = SubtitleModelRepository.get(applicationContext)
        val model = SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8
        try {
            val modelDirectory = repository.ensureModelDirectory()
            if (repository.isModelAvailable()) {
                repository.updateAvailable(source)
                return@withContext Result.success()
            }
            setForeground(createForegroundInfo(0L, model.artifactBytes, "准备下载"))
            ensureFreeSpace(
                destinationDirectory = modelDirectory,
                expectedBytes = model.artifactBytes,
                existingBytes = repository.downloadedModelBytes()
            )
            val client = EntryPointAccessors.fromApplication(
                applicationContext,
                WorkerEntryPoint::class.java
            ).okHttpClient()
            model.artifacts.forEach { artifact ->
                if (isInstalledSubtitleModelArtifact(repository.artifactFile(artifact), artifact.bytes)) {
                    return@forEach
                }
                val partialFile = repository.partialArtifactFile(artifact)
                val currentPartialBytes = partialFile.length().coerceIn(0L, artifact.bytes)
                val completedBefore = repository.downloadedModelBytes() - currentPartialBytes
                download(
                    client = client,
                    url = buildSubtitleModelArtifactUrl(baseUrl, artifact.fileName),
                    source = source,
                    destination = partialFile,
                    expectedBytes = artifact.bytes,
                    completedBefore = completedBefore,
                    totalBytes = model.artifactBytes,
                    repository = repository
                )
            }
            repository.updateVerifying(source)
            setProgress(workDataOf(KEY_STAGE to STAGE_VERIFYING))
            setForeground(createForegroundInfo(model.artifactBytes, model.artifactBytes, "正在校验"))
            model.artifacts.forEach { artifact ->
                val targetFile = repository.artifactFile(artifact)
                if (isInstalledSubtitleModelArtifact(targetFile, artifact.bytes)) return@forEach

                val partialFile = repository.partialArtifactFile(artifact)
                val actualHash = sha256(partialFile)
                check(actualHash.equals(artifact.sha256, ignoreCase = true)) {
                    partialFile.delete()
                    "模型校验失败：${artifact.fileName}，请重新下载"
                }
                if (targetFile.exists()) check(targetFile.delete()) { "无法替换旧模型文件" }
                check(partialFile.renameTo(targetFile)) { "无法保存日语字幕模型" }
            }
            repository.updateAvailable(source)
            Result.success(
                workDataOf(
                    KEY_DOWNLOADED_BYTES to model.artifactBytes,
                    KEY_TOTAL_BYTES to model.artifactBytes
                )
            )
        } catch (cancelled: CancellationException) {
            repository.updateMissing()
            throw cancelled
        } catch (error: Throwable) {
            val message = error.message?.trim().orEmpty().ifBlank { "模型下载失败" }
            if (runAttemptCount < MAX_RETRY_COUNT && error is IOException) {
                repository.updateDownloading(
                    source,
                    repository.downloadedModelBytes(),
                    model.artifactBytes
                )
                Result.retry()
            } else {
                repository.updateFailure(source, message)
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
            }
        }
    }

    private suspend fun download(
        client: OkHttpClient,
        url: String,
        source: SubtitleModelDownloadSource,
        destination: File,
        expectedBytes: Long,
        completedBefore: Long,
        totalBytes: Long,
        repository: SubtitleModelRepository
    ) {
        var existingBytes = destination.length().coerceIn(0L, expectedBytes)
        if (destination.length() != existingBytes) {
            check(destination.delete()) { "无法清理无效的临时模型" }
            existingBytes = 0L
        }
        repository.updateDownloading(source, completedBefore + existingBytes, totalBytes)
        if (existingBytes == expectedBytes) {
            publishProgress(
                source,
                repository,
                completedBefore + existingBytes,
                totalBytes
            )
            return
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "Eara-Android")
            .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
        if (existingBytes > 0L) requestBuilder.header("Range", "bytes=$existingBytes-")

        client.newCall(requestBuilder.get().build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载失败：${response.code} ${response.message}")
            }
            val append = existingBytes > 0L && response.code == 206
            if (append) {
                val expectedRangePrefix = "bytes $existingBytes-"
                val contentRange = response.header("Content-Range").orEmpty()
                if (!contentRange.startsWith(expectedRangePrefix)) {
                    destination.delete()
                    throw IOException("下载源返回了无效的断点数据")
                }
            }
            if (!append) {
                existingBytes = 0L
            }
            val body = response.body ?: throw IOException("下载失败：空响应体")
            var downloadedBytes = existingBytes
            var lastUpdateAt = 0L
            FileOutputStream(destination, append).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        downloadedBytes += read.toLong()
                        check(downloadedBytes <= expectedBytes) { "下载的模型文件大小异常" }
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastUpdateAt >= PROGRESS_UPDATE_INTERVAL_MS) {
                            publishProgress(
                                source,
                                repository,
                                completedBefore + downloadedBytes,
                                totalBytes
                            )
                            lastUpdateAt = now
                        }
                    }
                }
                output.flush()
                output.fd.sync()
            }
            if (downloadedBytes != expectedBytes) {
                throw IOException("模型下载不完整（${downloadedBytes}/${expectedBytes} 字节）")
            }
            publishProgress(
                source,
                repository,
                completedBefore + downloadedBytes,
                totalBytes
            )
        }
    }

    private suspend fun publishProgress(
        source: SubtitleModelDownloadSource,
        repository: SubtitleModelRepository,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        repository.updateDownloading(source, downloadedBytes, totalBytes)
        setProgress(
            workDataOf(
                KEY_STAGE to STAGE_DOWNLOADING,
                KEY_DOWNLOADED_BYTES to downloadedBytes,
                KEY_TOTAL_BYTES to totalBytes
            )
        )
        setForeground(createForegroundInfo(downloadedBytes, totalBytes, "正在下载"))
    }

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun ensureFreeSpace(
        destinationDirectory: File,
        expectedBytes: Long,
        existingBytes: Long
    ) {
        val requiredBytes = expectedBytes - existingBytes + FREE_SPACE_RESERVE_BYTES
        val availableBytes = StatFs(destinationDirectory.absolutePath)
            .availableBytes
        check(availableBytes >= requiredBytes) { "存储空间不足，至少需要额外可用空间" }
    }

    private fun createForegroundInfo(
        downloadedBytes: Long,
        totalBytes: Long,
        stage: String
    ): ForegroundInfo {
        val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "日语字幕模型下载",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val progress = if (totalBytes > 0L) {
            ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("日语字幕模型")
            .setContentText(stage)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, totalBytes <= 0L)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "subtitle_model_download"
        const val KEY_SOURCE = "source"
        const val KEY_URL = "url"
        const val KEY_STAGE = "stage"
        const val KEY_DOWNLOADED_BYTES = "downloadedBytes"
        const val KEY_TOTAL_BYTES = "totalBytes"
        const val KEY_ERROR_MESSAGE = "errorMessage"
        const val STAGE_DOWNLOADING = "downloading"
        const val STAGE_VERIFYING = "verifying"

        private const val DOWNLOAD_BUFFER_BYTES = 256 * 1024
        private const val HASH_BUFFER_BYTES = 1024 * 1024
        private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
        private const val FREE_SPACE_RESERVE_BYTES = 128L * 1024L * 1024L
        private const val MAX_RETRY_COUNT = 2
        private const val NOTIFICATION_CHANNEL_ID = "subtitle_model_download"
        private const val NOTIFICATION_ID = 0x534D
    }
}
