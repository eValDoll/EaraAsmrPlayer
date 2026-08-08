package com.asmr.player.subtitle

import android.content.Context
import android.content.SharedPreferences
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

    companion object {
        fun fromId(id: String?): SubtitleModelDownloadSource? = entries.firstOrNull { it.id == id }
    }
}

internal sealed interface SubtitleModelInstallationState {
    data object Missing : SubtitleModelInstallationState

    data class Available(
        val source: SubtitleModelDownloadSource?
    ) : SubtitleModelInstallationState
}

internal sealed interface SubtitleModelOperation {
    val modelId: String
    val source: SubtitleModelDownloadSource

    data class Queued(
        override val modelId: String,
        override val source: SubtitleModelDownloadSource
    ) : SubtitleModelOperation

    data class Downloading(
        override val modelId: String,
        override val source: SubtitleModelDownloadSource,
        val stage: SubtitleModelInstallStage,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : SubtitleModelOperation

    data class Verifying(
        override val modelId: String,
        override val source: SubtitleModelDownloadSource,
        val stage: SubtitleModelInstallStage
    ) : SubtitleModelOperation

    data class Failed(
        override val modelId: String,
        override val source: SubtitleModelDownloadSource,
        val message: String
    ) : SubtitleModelOperation
}

internal data class SubtitleModelState(
    val activeModelId: String,
    val installations: Map<String, SubtitleModelInstallationState>,
    val operation: SubtitleModelOperation? = null
) {
    fun installation(modelId: String): SubtitleModelInstallationState =
        installations[modelId] ?: SubtitleModelInstallationState.Missing
}

internal data class InstalledSubtitleModel(
    val model: SubtitleTranscriptionModel,
    val directory: File
)

internal enum class SubtitleModelInstallStage(val displayName: String) {
    Runtime("正在下载字幕运行时"),
    RuntimeVerification("正在校验并安装字幕运行时"),
    Model("正在下载日语字幕模型"),
    ModelVerification("正在校验日语字幕模型")
}

internal fun subtitleModelDownloadBaseUrl(
    model: SubtitleTranscriptionModel,
    source: SubtitleModelDownloadSource
): String = when (model.id) {
    SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8.id -> when (source) {
        SubtitleModelDownloadSource.GitHub -> BuildConfig.SUBTITLE_MODEL_GITHUB_URL
        SubtitleModelDownloadSource.HuggingFace -> BuildConfig.SUBTITLE_MODEL_HUGGING_FACE_URL
    }
    SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8.id -> when (source) {
        SubtitleModelDownloadSource.GitHub -> BuildConfig.SUBTITLE_SENSEVOICE_GITHUB_URL
        SubtitleModelDownloadSource.HuggingFace -> BuildConfig.SUBTITLE_SENSEVOICE_HUGGING_FACE_URL
    }
    else -> ""
}

internal fun configuredSubtitleModelDownloadSources(
    model: SubtitleTranscriptionModel
): List<SubtitleModelDownloadSource> = SubtitleModelDownloadSource.entries.filter { source ->
    subtitleModelDownloadBaseUrl(model, source).trim().startsWith("https://")
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
    private val runtimeRepository = SherpaOnnxRuntimeRepository.get(appContext)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state: MutableStateFlow<SubtitleModelState>
    val state: StateFlow<SubtitleModelState>

    init {
        migrateLegacyPreferences(preferences)
        _state = MutableStateFlow(buildState())
        state = _state.asStateFlow()
    }

    fun activeModel(): SubtitleTranscriptionModel =
        SubtitleTranscriptionModels.fromId(preferences.getString(KEY_ACTIVE_MODEL_ID, null))
            ?: SubtitleTranscriptionModels.default

    fun isModelAvailable(modelId: String = activeModel().id): Boolean {
        val model = SubtitleTranscriptionModels.fromId(modelId) ?: return false
        return runtimeRepository.isInstalled() && installedModelDirectoryOrNull(model) != null
    }

    fun requireInstalledModel(modelId: String = activeModel().id): InstalledSubtitleModel {
        val model = SubtitleTranscriptionModels.fromId(modelId)
            ?: throw IllegalStateException(MODEL_REQUIRED_MESSAGE)
        val directory = installedModelDirectoryOrNull(model)
            ?.takeIf { runtimeRepository.isInstalled() }
            ?: throw IllegalStateException(MODEL_REQUIRED_MESSAGE)
        return InstalledSubtitleModel(model, directory)
    }

    fun selectModel(modelId: String) {
        require(isModelAvailable(modelId)) { "请先下载该日语字幕模型" }
        preferences.edit().putString(KEY_ACTIVE_MODEL_ID, modelId).apply()
        refreshState()
    }

    fun enqueueDownload(modelId: String, source: SubtitleModelDownloadSource) {
        val model = requireNotNull(SubtitleTranscriptionModels.fromId(modelId)) { "未知的字幕模型" }
        if (isModelAvailable(model.id)) {
            refreshState()
            return
        }
        val baseUrl = subtitleModelDownloadBaseUrl(model, source).trim()
        require(baseUrl.startsWith("https://")) { "模型下载地址未配置" }
        require(runtimeRepository.descriptor.url.trim().startsWith("https://")) {
            "字幕运行时下载地址未配置"
        }
        refreshState(SubtitleModelOperation.Queued(model.id, source))
        val request = OneTimeWorkRequestBuilder<SubtitleModelDownloadWorker>()
            .setInputData(
                workDataOf(
                    SubtitleModelDownloadWorker.KEY_MODEL_ID to model.id,
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

    fun clearFailure(modelId: String) {
        val failure = _state.value.operation as? SubtitleModelOperation.Failed ?: return
        if (failure.modelId == modelId) refreshState(operation = null)
    }

    suspend fun cancelDownload() = withContext(Dispatchers.IO) {
        val model = _state.value.operation?.modelId?.let(SubtitleTranscriptionModels::fromId)
        WorkManager.getInstance(appContext)
            .cancelUniqueWork(SubtitleModelDownloadWorker.UNIQUE_WORK_NAME)
            .result
            .get()
        model?.let(::deletePartialModelFiles)
        runtimeRepository.deletePartialArchive()
        refreshState(operation = null)
    }

    suspend fun deleteModel(modelId: String) = withContext(Dispatchers.IO) {
        val model = requireNotNull(SubtitleTranscriptionModels.fromId(modelId)) { "未知的字幕模型" }
        val operation = _state.value.operation
        if (operation?.modelId == model.id) {
            WorkManager.getInstance(appContext)
                .cancelUniqueWork(SubtitleModelDownloadWorker.UNIQUE_WORK_NAME)
                .result
                .get()
        }
        val deletedModel = model.artifacts.all { artifact ->
            deleteIfPresent(artifactFile(model, artifact)) &&
                deleteIfPresent(partialArtifactFile(model, artifact))
        }
        check(deletedModel) { "无法删除日语字幕模型" }
        modelDirectory(model).takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()

        val editor = preferences.edit().remove(installedSourceKey(model.id))
        if (activeModel().id == model.id) {
            val availableModelIds = SubtitleTranscriptionModels.all
                .filter { candidate -> candidate.id != model.id && isModelAvailable(candidate.id) }
                .mapTo(mutableSetOf(), SubtitleTranscriptionModel::id)
            editor.putString(
                KEY_ACTIVE_MODEL_ID,
                fallbackSubtitleModelId(model.id, availableModelIds)
            )
        }
        editor.apply()
        refreshState(operation = operation?.takeUnless { it.modelId == model.id })
    }

    internal fun updateDownloading(
        modelId: String,
        source: SubtitleModelDownloadSource,
        stage: SubtitleModelInstallStage,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        refreshState(
            SubtitleModelOperation.Downloading(
                modelId = modelId,
                source = source,
                stage = stage,
                downloadedBytes = downloadedBytes.coerceAtLeast(0L),
                totalBytes = totalBytes.coerceAtLeast(0L)
            )
        )
    }

    internal fun updateVerifying(
        modelId: String,
        source: SubtitleModelDownloadSource,
        stage: SubtitleModelInstallStage
    ) {
        refreshState(SubtitleModelOperation.Verifying(modelId, source, stage))
    }

    internal fun updateAvailable(modelId: String, source: SubtitleModelDownloadSource) {
        preferences.edit().putString(installedSourceKey(modelId), source.id).apply()
        refreshState(operation = null)
    }

    internal fun updateFailure(
        modelId: String,
        source: SubtitleModelDownloadSource,
        message: String
    ) {
        refreshState(
            SubtitleModelOperation.Failed(
                modelId = modelId,
                source = source,
                message = message.trim().ifBlank { "模型下载失败" }
            )
        )
    }

    internal fun updateMissing(modelId: String) {
        val operation = _state.value.operation
        refreshState(operation = operation?.takeUnless { it.modelId == modelId })
    }

    internal fun artifactFile(
        model: SubtitleTranscriptionModel,
        artifact: SubtitleModelArtifact
    ): File = File(modelDirectory(model), artifact.fileName)

    internal fun partialArtifactFile(
        model: SubtitleTranscriptionModel,
        artifact: SubtitleModelArtifact
    ): File = File(modelDirectory(model), "${artifact.fileName}.part")

    internal fun downloadedModelBytes(model: SubtitleTranscriptionModel): Long =
        model.artifacts.sumOf { artifact ->
            val installed = artifactFile(model, artifact)
            if (isInstalledSubtitleModelArtifact(installed, artifact.bytes)) {
                artifact.bytes
            } else {
                partialArtifactFile(model, artifact).length().coerceIn(0L, artifact.bytes)
            }
        }

    internal fun downloadedInstallationBytes(model: SubtitleTranscriptionModel): Long {
        val runtimeBytes = if (runtimeRepository.isInstalled()) {
            runtimeRepository.descriptor.archiveBytes
        } else {
            runtimeRepository.downloadedArchiveBytes()
        }
        return runtimeBytes + downloadedModelBytes(model)
    }

    internal fun installationBytes(model: SubtitleTranscriptionModel): Long =
        runtimeRepository.descriptor.archiveBytes + model.artifactBytes

    internal fun deletePartialModelFiles(model: SubtitleTranscriptionModel): Boolean =
        model.artifacts.all { artifact -> deleteIfPresent(partialArtifactFile(model, artifact)) }

    internal fun ensureModelDirectory(model: SubtitleTranscriptionModel): File =
        modelDirectory(model).apply {
            check(exists() || mkdirs()) { "无法创建模型目录" }
        }

    private fun refreshState(operation: SubtitleModelOperation? = _state.value.operation) {
        _state.value = buildState(operation)
    }

    private fun buildState(operation: SubtitleModelOperation? = null): SubtitleModelState {
        val installations = SubtitleTranscriptionModels.all.associate { model ->
            val installation = if (
                runtimeRepository.isInstalled() && installedModelDirectoryOrNull(model) != null
            ) {
                SubtitleModelInstallationState.Available(
                    SubtitleModelDownloadSource.fromId(
                        preferences.getString(installedSourceKey(model.id), null)
                    )
                )
            } else {
                SubtitleModelInstallationState.Missing
            }
            model.id to installation
        }
        return SubtitleModelState(
            activeModelId = activeModel().id,
            installations = installations,
            operation = operation
        )
    }

    private fun installedModelDirectoryOrNull(model: SubtitleTranscriptionModel): File? {
        val directory = modelDirectory(model)
        return directory.takeIf {
            it.isDirectory && model.artifacts.all { artifact ->
                isInstalledSubtitleModelArtifact(artifactFile(model, artifact), artifact.bytes)
            }
        }
    }

    private fun modelDirectory(model: SubtitleTranscriptionModel): File =
        File(modelRootDirectory(), model.id)

    private fun modelRootDirectory(): File = File(appContext.filesDir, MODEL_ROOT_DIRECTORY_NAME)

    private fun installedSourceKey(modelId: String): String = "$KEY_INSTALLED_SOURCE_PREFIX$modelId"

    private fun deleteIfPresent(file: File): Boolean = !file.exists() || file.delete()

    companion object {
        const val MODEL_REQUIRED_MESSAGE = "请先在设置中下载日语字幕组件"

        private const val MODEL_ROOT_DIRECTORY_NAME = "subtitle-models"
        private const val PREFERENCES_NAME = "subtitle_model_preferences"
        private const val KEY_ACTIVE_MODEL_ID = "active_model_id"
        private const val KEY_INSTALLED_SOURCE_PREFIX = "installed_source:"
        private const val KEY_LEGACY_INSTALLED_SOURCE = "installed_source"

        @Volatile
        private var instance: SubtitleModelRepository? = null

        fun get(context: Context): SubtitleModelRepository {
            return instance ?: synchronized(this) {
                instance ?: SubtitleModelRepository(context).also { instance = it }
            }
        }

        internal fun migrateLegacyPreferences(preferences: SharedPreferences) {
            val legacySource = preferences.getString(KEY_LEGACY_INSTALLED_SOURCE, null) ?: return
            val parakeetKey = "$KEY_INSTALLED_SOURCE_PREFIX${SubtitleTranscriptionModels.default.id}"
            val editor = preferences.edit()
            if (!preferences.contains(parakeetKey)) editor.putString(parakeetKey, legacySource)
            editor.remove(KEY_LEGACY_INSTALLED_SOURCE).apply()
        }
    }
}

internal fun fallbackSubtitleModelId(
    deletedModelId: String,
    availableModelIds: Set<String>
): String = SubtitleTranscriptionModels.all.firstOrNull { model ->
    model.id != deletedModelId && model.id in availableModelIds
}?.id ?: SubtitleTranscriptionModels.default.id
