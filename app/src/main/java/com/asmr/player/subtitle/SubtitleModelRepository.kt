package com.asmr.player.subtitle

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.asmr.player.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val FILE_NAME_PLACEHOLDER = "{fileName}"

internal enum class SubtitleModelDownloadSource(
    val id: String,
    val displayName: String
) {
    GitHub("github", "GitHub"),
    HuggingFace("hugging_face", "Hugging Face");

    fun downloadBaseUrl(): String = when (this) {
        GitHub -> BuildConfig.SUBTITLE_MODEL_GITHUB_URL
        HuggingFace -> BuildConfig.SUBTITLE_MODEL_HUGGING_FACE_URL
    }

    fun artifactUrl(fileName: String): String = buildSubtitleModelArtifactUrl(
        baseUrl = downloadBaseUrl(),
        fileName = fileName
    )

    fun isConfigured(): Boolean = downloadBaseUrl().trim().startsWith("https://")

    companion object {
        fun fromId(id: String?): SubtitleModelDownloadSource? = entries.firstOrNull { it.id == id }
    }
}

internal sealed interface SubtitleModelState {
    data object Missing : SubtitleModelState

    data class Queued(
        val source: SubtitleModelDownloadSource
    ) : SubtitleModelState

    data class Downloading(
        val source: SubtitleModelDownloadSource,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : SubtitleModelState

    data class Verifying(
        val source: SubtitleModelDownloadSource
    ) : SubtitleModelState

    data class Available(
        val source: SubtitleModelDownloadSource?
    ) : SubtitleModelState

    data class Failed(
        val source: SubtitleModelDownloadSource,
        val message: String
    ) : SubtitleModelState
}

internal fun buildSubtitleModelArtifactUrl(baseUrl: String, fileName: String): String {
    val normalizedBaseUrl = baseUrl.trim()
    return if (normalizedBaseUrl.contains(FILE_NAME_PLACEHOLDER)) {
        normalizedBaseUrl.replace(FILE_NAME_PLACEHOLDER, fileName)
    } else {
        "${normalizedBaseUrl.trimEnd('/')}/$fileName"
    }
}

internal fun isInstalledSubtitleModelArtifact(
    file: File,
    expectedBytes: Long
): Boolean = file.isFile && file.length() == expectedBytes

internal class SubtitleModelRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val model = SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SubtitleModelState> = _state.asStateFlow()

    fun isModelAvailable(): Boolean = installedModelDirectoryOrNull() != null

    fun requireInstalledModelDirectory(): File {
        return installedModelDirectoryOrNull()
            ?: throw IllegalStateException(MODEL_REQUIRED_MESSAGE)
    }

    fun enqueueDownload(source: SubtitleModelDownloadSource) {
        if (isModelAvailable()) {
            refreshInstalledState()
            return
        }
        val baseUrl = source.downloadBaseUrl().trim()
        require(baseUrl.startsWith("https://")) { "模型下载地址未配置" }
        _state.value = SubtitleModelState.Queued(source)
        val request = OneTimeWorkRequestBuilder<SubtitleModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    SubtitleModelDownloadWorker.KEY_SOURCE to source.id,
                    SubtitleModelDownloadWorker.KEY_URL to baseUrl
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
            .addTag(SubtitleModelDownloadWorker.UNIQUE_WORK_NAME)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            SubtitleModelDownloadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun clearFailure() {
        if (_state.value is SubtitleModelState.Failed) {
            _state.value = SubtitleModelState.Missing
        }
    }

    suspend fun cancelDownload() = withContext(Dispatchers.IO) {
        WorkManager.getInstance(appContext)
            .cancelUniqueWork(SubtitleModelDownloadWorker.UNIQUE_WORK_NAME)
            .result
            .get()
        deletePartialModelFiles()
        _state.value = initialState()
    }

    suspend fun deleteModel() = withContext(Dispatchers.IO) {
        WorkManager.getInstance(appContext)
            .cancelUniqueWork(SubtitleModelDownloadWorker.UNIQUE_WORK_NAME)
            .result
            .get()
        val deletedModel = model.artifacts.all { artifact ->
            deleteIfPresent(artifactFile(artifact)) && deleteIfPresent(partialArtifactFile(artifact))
        }
        check(deletedModel) { "无法删除日语字幕模型" }
        modelDirectory().takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()
        preferences.edit().remove(KEY_INSTALLED_SOURCE).apply()
        _state.value = SubtitleModelState.Missing
    }

    internal fun updateDownloading(
        source: SubtitleModelDownloadSource,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        _state.value = SubtitleModelState.Downloading(
            source = source,
            downloadedBytes = downloadedBytes.coerceAtLeast(0L),
            totalBytes = totalBytes.coerceAtLeast(0L)
        )
    }

    internal fun updateVerifying(source: SubtitleModelDownloadSource) {
        _state.value = SubtitleModelState.Verifying(source)
    }

    internal fun updateAvailable(source: SubtitleModelDownloadSource) {
        preferences.edit().putString(KEY_INSTALLED_SOURCE, source.id).apply()
        _state.value = SubtitleModelState.Available(source)
    }

    internal fun updateFailure(source: SubtitleModelDownloadSource, message: String) {
        _state.value = SubtitleModelState.Failed(
            source = source,
            message = message.trim().ifBlank { "模型下载失败" }
        )
    }

    internal fun updateMissing() {
        _state.value = initialState()
    }

    internal fun artifactFile(artifact: SubtitleModelArtifact): File =
        File(modelDirectory(), artifact.fileName)

    internal fun partialArtifactFile(artifact: SubtitleModelArtifact): File =
        File(modelDirectory(), "${artifact.fileName}.part")

    internal fun downloadedModelBytes(): Long = model.artifacts.sumOf { artifact ->
        val installed = artifactFile(artifact)
        if (isInstalledSubtitleModelArtifact(installed, artifact.bytes)) {
            artifact.bytes
        } else {
            partialArtifactFile(artifact).length().coerceIn(0L, artifact.bytes)
        }
    }

    internal fun deletePartialModelFiles(): Boolean = model.artifacts.all { artifact ->
        deleteIfPresent(partialArtifactFile(artifact))
    }

    internal fun ensureModelDirectory(): File = modelDirectory().apply {
        check(exists() || mkdirs()) { "无法创建模型目录" }
    }

    private fun refreshInstalledState() {
        _state.value = initialState()
    }

    private fun installedModelDirectoryOrNull(): File? {
        val directory = modelDirectory()
        return directory.takeIf {
            it.isDirectory && model.artifacts.all { artifact ->
                isInstalledSubtitleModelArtifact(artifactFile(artifact), artifact.bytes)
            }
        }
    }

    private fun initialState(): SubtitleModelState {
        installedModelDirectoryOrNull() ?: return SubtitleModelState.Missing
        val source = SubtitleModelDownloadSource.fromId(
            preferences.getString(KEY_INSTALLED_SOURCE, null)
        )
        return SubtitleModelState.Available(source)
    }

    private fun modelDirectory(): File = File(modelRootDirectory(), model.id)

    private fun modelRootDirectory(): File = File(appContext.filesDir, MODEL_ROOT_DIRECTORY_NAME)

    private fun deleteIfPresent(file: File): Boolean = !file.exists() || file.delete()

    companion object {
        const val MODEL_REQUIRED_MESSAGE = "请先在设置中下载日语字幕模型"

        private const val MODEL_ROOT_DIRECTORY_NAME = "subtitle-models"
        private const val PREFERENCES_NAME = "subtitle_model_preferences"
        private const val KEY_INSTALLED_SOURCE = "installed_source"
        @Volatile
        private var instance: SubtitleModelRepository? = null

        fun get(context: Context): SubtitleModelRepository {
            return instance ?: synchronized(this) {
                instance ?: SubtitleModelRepository(context).also { instance = it }
            }
        }
    }
}
