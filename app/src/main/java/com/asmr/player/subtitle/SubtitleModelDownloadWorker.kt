package com.asmr.player.subtitle

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.StatFs
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.asmr.player.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

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
        val model = SubtitleTranscriptionModels.fromId(inputData.getString(KEY_MODEL_ID))
            ?: return@withContext Result.failure()
        val source = SubtitleModelDownloadSource.fromId(inputData.getString(KEY_SOURCE))
            ?: return@withContext Result.failure()
        val baseUrl = inputData.getString(KEY_URL).orEmpty()
        if (!baseUrl.startsWith("https://")) return@withContext Result.failure()

        val repository = SubtitleModelRepository.get(applicationContext)
        val runtimeRepository = SherpaOnnxRuntimeRepository.get(applicationContext)
        val runtime = runtimeRepository.descriptor
        if (!runtime.url.startsWith("https://")) return@withContext Result.failure()
        val totalBytes = repository.installationBytes(model)
        var activeStage = SubtitleModelInstallStage.Runtime
        try {
            val modelDirectory = repository.ensureModelDirectory(model)
            if (repository.isModelAvailable(model.id)) {
                repository.updateAvailable(model.id, source)
                return@withContext Result.success()
            }
            setForeground(
                createForegroundInfo(
                    repository.downloadedInstallationBytes(model),
                    totalBytes,
                    "准备下载字幕组件"
                )
            )
            val client = EntryPointAccessors.fromApplication(
                applicationContext,
                WorkerEntryPoint::class.java
            ).okHttpClient()

            if (!runtimeRepository.isInstalled()) {
                val runtimeDirectory = runtimeRepository.ensureDownloadDirectory()
                val partialRuntime = runtimeRepository.partialArchiveFile()
                ensureFreeSpace(
                    destinationDirectory = runtimeDirectory,
                    expectedBytes = runtime.archiveBytes + runtime.extractedBytes,
                    existingBytes = runtimeRepository.downloadedArchiveBytes()
                )
                download(
                    client = client,
                    url = runtime.url,
                    source = source,
                    stage = SubtitleModelInstallStage.Runtime,
                    destination = partialRuntime,
                    expectedBytes = runtime.archiveBytes,
                    completedBefore = repository.downloadedModelBytes(model),
                    totalBytes = totalBytes,
                    modelId = model.id,
                    repository = repository
                )
                activeStage = SubtitleModelInstallStage.RuntimeVerification
                repository.updateVerifying(model.id, source, activeStage)
                publishStage(
                    modelId = model.id,
                    stage = activeStage,
                    downloadedBytes = runtime.archiveBytes + repository.downloadedModelBytes(model),
                    totalBytes = totalBytes
                )
                runtimeRepository.installDownloadedArchive()
            }

            activeStage = SubtitleModelInstallStage.Model
            ensureFreeSpace(
                destinationDirectory = modelDirectory,
                expectedBytes = model.artifactBytes,
                existingBytes = repository.downloadedModelBytes(model)
            )
            model.artifacts.forEach { artifact ->
                if (isInstalledSubtitleModelArtifact(repository.artifactFile(model, artifact), artifact.bytes)) {
                    return@forEach
                }
                val partialFile = repository.partialArtifactFile(model, artifact)
                val currentPartialBytes = partialFile.length().coerceIn(0L, artifact.bytes)
                val completedBefore = runtime.archiveBytes +
                    repository.downloadedModelBytes(model) - currentPartialBytes
                download(
                    client = client,
                    url = buildSubtitleModelArtifactUrl(baseUrl, artifact.fileName),
                    source = source,
                    stage = activeStage,
                    destination = partialFile,
                    expectedBytes = artifact.bytes,
                    completedBefore = completedBefore,
                    totalBytes = totalBytes,
                    modelId = model.id,
                    repository = repository
                )
            }
            activeStage = SubtitleModelInstallStage.ModelVerification
            repository.updateVerifying(model.id, source, activeStage)
            publishStage(model.id, activeStage, totalBytes, totalBytes)
            model.artifacts.forEach { artifact ->
                val targetFile = repository.artifactFile(model, artifact)
                if (isInstalledSubtitleModelArtifact(targetFile, artifact.bytes)) return@forEach

                val partialFile = repository.partialArtifactFile(model, artifact)
                val actualHash = sha256(partialFile)
                check(actualHash.equals(artifact.sha256, ignoreCase = true)) {
                    partialFile.delete()
                    "模型校验失败：${artifact.fileName}，请重新下载"
                }
                if (targetFile.exists()) check(targetFile.delete()) { "无法替换旧模型文件" }
                check(partialFile.renameTo(targetFile)) { "无法保存日语字幕模型" }
            }
            repository.updateAvailable(model.id, source)
            Result.success(
                workDataOf(
                    KEY_MODEL_ID to model.id,
                    KEY_DOWNLOADED_BYTES to totalBytes,
                    KEY_TOTAL_BYTES to totalBytes
                )
            )
        } catch (cancelled: CancellationException) {
            repository.updateMissing(model.id)
            throw cancelled
        } catch (error: Throwable) {
            val message = error.message?.trim().orEmpty().ifBlank { "字幕组件下载失败" }
            if (runAttemptCount < MAX_RETRY_COUNT && error is IOException) {
                repository.updateDownloading(
                    model.id,
                    source,
                    activeStage,
                    repository.downloadedInstallationBytes(model),
                    totalBytes
                )
                Result.retry()
            } else {
                repository.updateFailure(model.id, source, message)
                Result.failure(
                    workDataOf(
                        KEY_MODEL_ID to model.id,
                        KEY_ERROR_MESSAGE to message
                    )
                )
            }
        }
    }

    private suspend fun download(
        client: OkHttpClient,
        url: String,
        source: SubtitleModelDownloadSource,
        stage: SubtitleModelInstallStage,
        destination: File,
        expectedBytes: Long,
        completedBefore: Long,
        totalBytes: Long,
        modelId: String,
        repository: SubtitleModelRepository
    ) {
        SubtitleArtifactDownloader.download(
            client = client,
            url = url,
            destination = destination,
            expectedBytes = expectedBytes
        ) { downloadedBytes ->
            publishProgress(
                modelId,
                source,
                stage,
                repository,
                completedBefore + downloadedBytes,
                totalBytes
            )
        }
    }

    private suspend fun publishProgress(
        modelId: String,
        source: SubtitleModelDownloadSource,
        stage: SubtitleModelInstallStage,
        repository: SubtitleModelRepository,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        repository.updateDownloading(modelId, source, stage, downloadedBytes, totalBytes)
        setProgress(
            workDataOf(
                KEY_MODEL_ID to modelId,
                KEY_STAGE to stage.name,
                KEY_DOWNLOADED_BYTES to downloadedBytes,
                KEY_TOTAL_BYTES to totalBytes
            )
        )
        setForeground(createForegroundInfo(downloadedBytes, totalBytes, stage.displayName))
    }

    private suspend fun publishStage(
        modelId: String,
        stage: SubtitleModelInstallStage,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        setProgress(
            workDataOf(
                KEY_MODEL_ID to modelId,
                KEY_STAGE to stage.name,
                KEY_DOWNLOADED_BYTES to downloadedBytes,
                KEY_TOTAL_BYTES to totalBytes
            )
        )
        setForeground(createForegroundInfo(downloadedBytes, totalBytes, stage.displayName))
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
                    "日语字幕组件下载",
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
            .setContentTitle("日语字幕组件")
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
        const val KEY_MODEL_ID = "modelId"
        const val KEY_SOURCE = "source"
        const val KEY_URL = "url"
        const val KEY_STAGE = "stage"
        const val KEY_DOWNLOADED_BYTES = "downloadedBytes"
        const val KEY_TOTAL_BYTES = "totalBytes"
        const val KEY_ERROR_MESSAGE = "errorMessage"
        private const val HASH_BUFFER_BYTES = 1024 * 1024
        private const val FREE_SPACE_RESERVE_BYTES = 128L * 1024L * 1024L
        private const val MAX_RETRY_COUNT = 2
        private const val NOTIFICATION_CHANNEL_ID = "subtitle_model_download"
        private const val NOTIFICATION_ID = 0x534D
    }
}
