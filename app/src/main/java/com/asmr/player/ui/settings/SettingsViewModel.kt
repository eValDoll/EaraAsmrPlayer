package com.asmr.player.ui.settings

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.BuildConfig
import com.asmr.player.cache.AppCacheManager
import com.asmr.player.cache.AppCacheState
import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.update.GitHubUpdateClient
import com.asmr.player.data.remote.update.UpdateRelease
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.DeepSeekReasoningEffort
import com.asmr.player.data.settings.DeepSeekTranslationSettings
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.subtitle.SubtitleModelDownloadSource
import com.asmr.player.subtitle.SubtitleModelRepository
import com.asmr.player.subtitle.SubtitleModelState
import com.asmr.player.subtitle.DeepSeekApiKeyStore
import com.asmr.player.subtitle.DeepSeekAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class UpdateCheckSource {
    Manual,
    Automatic
}

private const val UPDATE_APK_PREFIX = "eara-"
private const val UPDATE_APK_SUFFIX = ".apk"

internal data class DeepSeekApiKeyUiState(
    val configured: Boolean = false,
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val saveVersion: Long = 0L
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data class Checking(val source: UpdateCheckSource = UpdateCheckSource.Manual) : AppUpdateState
    data class UpToDate(
        val latestVersionName: String,
        val source: UpdateCheckSource = UpdateCheckSource.Manual
    ) : AppUpdateState
    data class UpdateAvailable(
        val release: UpdateRelease,
        val source: UpdateCheckSource = UpdateCheckSource.Manual
    ) : AppUpdateState
    data class Downloading(
        val release: UpdateRelease,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val source: UpdateCheckSource = UpdateCheckSource.Manual
    ) : AppUpdateState
    data class ReadyToInstall(
        val release: UpdateRelease,
        val apkPath: String,
        val source: UpdateCheckSource = UpdateCheckSource.Manual
    ) : AppUpdateState
    data class Failed(
        val message: String,
        val source: UpdateCheckSource = UpdateCheckSource.Manual
    ) : AppUpdateState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val settingsDataStore: SettingsDataStore,
    private val appCacheManager: AppCacheManager,
    private val okHttpClient: OkHttpClient,
    private val deepSeekAccountRepository: DeepSeekAccountRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val subtitleModelRepository = SubtitleModelRepository.get(context)
    private val deepSeekApiKeyStore = DeepSeekApiKeyStore.get(context)

    val floatingLyricsEnabled: StateFlow<Boolean> = settingsRepository.floatingLyricsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val floatingLyricsSettings: StateFlow<FloatingLyricsSettings> = settingsRepository.floatingLyricsSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FloatingLyricsSettings())

    val lyricsPageSettings: StateFlow<LyricsPageSettings> = settingsDataStore.lyricsPageSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LyricsPageSettings())

    val dynamicPlayerHueEnabled: StateFlow<Boolean> = settingsDataStore.dynamicPlayerHueEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val themeMode: StateFlow<String> = settingsDataStore.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

    val staticHueArgb: StateFlow<Int?> = settingsDataStore.staticHueArgb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val staticHueArgbLight: StateFlow<Int?> = settingsDataStore.staticHueArgbLight
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val staticHueArgbDark: StateFlow<Int?> = settingsDataStore.staticHueArgbDark
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val coverBackgroundEnabled: StateFlow<Boolean> = settingsDataStore.coverBackgroundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val coverBackgroundClarity: StateFlow<Float> = settingsDataStore.coverBackgroundClarity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.35f)

    val coverPreviewMode: StateFlow<CoverPreviewMode> = settingsDataStore.coverPreviewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoverPreviewMode.Disabled)

    val autoUpdateCheckEnabled: StateFlow<Boolean> = settingsDataStore.autoUpdateCheckEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val pauseOnOutputDisconnect: StateFlow<Boolean> = settingsRepository.pauseOnOutputDisconnect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val resumeOnOutputConnect: StateFlow<Boolean> = settingsRepository.resumeOnOutputConnect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pauseOnOtherAudio: StateFlow<Boolean> = settingsRepository.pauseOnOtherAudio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val playFadeInMs: StateFlow<Int> = settingsRepository.playFadeInMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 500)

    val pauseFadeOutMs: StateFlow<Int> = settingsRepository.pauseFadeOutMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 500)

    val sfwHideSystemControls: StateFlow<Boolean> = settingsRepository.sfwHideSystemControls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val showMiniPlayerBar: StateFlow<Boolean> = settingsRepository.showMiniPlayerBar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val searchBlockedKeywords: StateFlow<List<String>> = settingsRepository.searchBlockedKeywords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    internal val deepSeekTranslationSettings: StateFlow<DeepSeekTranslationSettings> =
        settingsRepository.deepSeekTranslationSettings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DeepSeekTranslationSettings()
        )

    val appCacheState: StateFlow<AppCacheState> = appCacheManager.state
    internal val subtitleModelState: StateFlow<SubtitleModelState> = subtitleModelRepository.state
    private val _deepSeekApiKeyState = MutableStateFlow(DeepSeekApiKeyUiState())
    internal val deepSeekApiKeyState = _deepSeekApiKeyState.asStateFlow()
    internal val deepSeekAccountState = deepSeekAccountRepository.state

    private val updateClient = GitHubUpdateClient(okHttpClient)
    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState = _updateState.asStateFlow()
    private var updateJob: Job? = null
    private var automaticCheckStarted = false

    init {
        appCacheManager.start()
        viewModelScope.launch(Dispatchers.IO) {
            val apiKey = deepSeekApiKeyStore.read()
            val configured = apiKey.isNotBlank()
            _deepSeekApiKeyState.value = _deepSeekApiKeyState.value.copy(configured = configured)
            if (configured) {
                deepSeekAccountRepository.bindApiKey(apiKey)
                deepSeekAccountRepository.refreshBalance(apiKey)
            }
        }
    }

    fun setFloatingLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFloatingLyricsEnabled(enabled) }
    }

    fun updateFloatingLyricsSettings(settings: FloatingLyricsSettings) {
        viewModelScope.launch { settingsRepository.updateFloatingLyricsSettings(settings) }
    }

    fun updateLyricsPageSettings(settings: LyricsPageSettings) {
        viewModelScope.launch { settingsDataStore.setLyricsPageSettings(settings) }
    }

    fun setDynamicPlayerHueEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setDynamicPlayerHueEnabled(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { settingsDataStore.setTheme(mode) }
    }

    fun setStaticHueArgb(argb: Int?) {
        viewModelScope.launch { settingsDataStore.setStaticHueArgb(argb) }
    }

    fun setCoverBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setCoverBackgroundEnabled(enabled) }
    }

    fun setCoverBackgroundClarity(clarity: Float) {
        viewModelScope.launch { settingsDataStore.setCoverBackgroundClarity(clarity) }
    }

    fun setCoverPreviewMode(mode: CoverPreviewMode) {
        viewModelScope.launch { settingsDataStore.setCoverPreviewMode(mode) }
    }

    fun setPauseOnOutputDisconnect(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPauseOnOutputDisconnect(enabled) }
    }

    fun setResumeOnOutputConnect(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setResumeOnOutputConnect(enabled) }
    }

    fun setPauseOnOtherAudio(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPauseOnOtherAudio(enabled) }
    }

    fun setPlayFadeInMs(durationMs: Int) {
        viewModelScope.launch { settingsRepository.setPlayFadeInMs(durationMs) }
    }

    fun setPauseFadeOutMs(durationMs: Int) {
        viewModelScope.launch { settingsRepository.setPauseFadeOutMs(durationMs) }
    }

    fun setSfwHideSystemControls(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSfwHideSystemControls(enabled) }
    }

    fun setShowMiniPlayerBar(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowMiniPlayerBar(enabled) }
    }

    fun addSearchBlockedKeyword(keyword: String) {
        viewModelScope.launch { settingsRepository.addSearchBlockedKeyword(keyword) }
    }

    fun removeSearchBlockedKeyword(keyword: String) {
        viewModelScope.launch { settingsRepository.removeSearchBlockedKeyword(keyword) }
    }

    fun setAppCacheMaxSizeMb(sizeMb: Int) {
        viewModelScope.launch { settingsRepository.setAppCacheMaxSizeMb(sizeMb) }
    }

    fun refreshAppCacheSize() {
        appCacheManager.refreshSize()
    }

    fun clearAppCache() {
        appCacheManager.clearCache()
    }

    internal fun downloadSubtitleModel(source: SubtitleModelDownloadSource) {
        runCatching { subtitleModelRepository.enqueueDownload(source) }
            .onFailure { error ->
                subtitleModelRepository.updateFailure(
                    source,
                    error.message?.takeIf { it.isNotBlank() } ?: "无法开始模型下载"
                )
            }
    }

    fun cancelSubtitleModelDownload() {
        viewModelScope.launch { subtitleModelRepository.cancelDownload() }
    }

    fun deleteSubtitleModel() {
        viewModelScope.launch { subtitleModelRepository.deleteModel() }
    }

    fun clearSubtitleModelFailure() {
        subtitleModelRepository.clearFailure()
    }

    internal fun saveDeepSeekApiKey(apiKey: String) {
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _deepSeekApiKeyState.value = _deepSeekApiKeyState.value.copy(
                saving = true,
                errorMessage = null
            )
            val saved = runCatching { deepSeekApiKeyStore.save(normalized) }.isSuccess
            if (saved) {
                deepSeekAccountRepository.bindApiKey(normalized)
                val current = _deepSeekApiKeyState.value
                _deepSeekApiKeyState.value = current.copy(
                    configured = true,
                    saving = false,
                    errorMessage = null,
                    saveVersion = current.saveVersion + 1L
                )
                deepSeekAccountRepository.refreshBalance(normalized)
            } else {
                _deepSeekApiKeyState.value = _deepSeekApiKeyState.value.copy(
                    saving = false,
                    errorMessage = "API Key 保存失败"
                )
            }
        }
    }

    internal fun setDeepSeekThinkingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDeepSeekThinkingEnabled(enabled) }
    }

    internal fun setDeepSeekReasoningEffort(effort: DeepSeekReasoningEffort) {
        viewModelScope.launch { settingsRepository.setDeepSeekReasoningEffort(effort) }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setAutoUpdateCheckEnabled(enabled) }
    }

    fun disableAutoUpdateCheck() {
        setAutoUpdateCheckEnabled(false)
        val cur = _updateState.value
        if (cur is AppUpdateState.UpdateAvailable && cur.source == UpdateCheckSource.Automatic) {
            _updateState.value = AppUpdateState.Idle
        }
    }

    fun checkUpdate() {
        startUpdateCheck(UpdateCheckSource.Manual)
    }

    fun checkUpdateAutomatically() {
        if (automaticCheckStarted) return
        automaticCheckStarted = true
        val cur = _updateState.value
        if (cur is AppUpdateState.Checking || cur is AppUpdateState.Downloading) return
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            if (!settingsDataStore.autoUpdateCheckEnabled.first()) return@launch
            performUpdateCheck(UpdateCheckSource.Automatic)
        }
    }

    private fun startUpdateCheck(source: UpdateCheckSource) {
        val cur = _updateState.value
        if (cur is AppUpdateState.Checking || cur is AppUpdateState.Downloading) return
        updateJob?.cancel()
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            performUpdateCheck(source)
        }
    }

    private suspend fun performUpdateCheck(source: UpdateCheckSource) {
        _updateState.value = AppUpdateState.Checking(source)
        try {
            val release =
                updateClient.fetchLatestRelease(
                    owner = BuildConfig.UPDATE_REPO_OWNER,
                    repo = BuildConfig.UPDATE_REPO_NAME
            )
            val currentVersion = BuildConfig.VERSION_NAME
            val newer = updateClient.isNewerThanCurrent(release.versionName, currentVersion)
            _updateState.value = if (newer) {
                AppUpdateState.UpdateAvailable(release, source)
            } else {
                AppUpdateState.UpToDate(latestVersionName = release.versionName, source = source)
            }
        } catch (e: Exception) {
            val msg = e.message?.trim().orEmpty().ifBlank { "检查更新失败" }
            _updateState.value = AppUpdateState.Failed(msg, source)
        }
    }

    fun downloadLatestApk() {
        val state = _updateState.value
        val available = state as? AppUpdateState.UpdateAvailable ?: return
        val release = available.release
        val source = available.source
        updateJob?.cancel()
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = AppUpdateState.Downloading(release, 0L, 0L, source)
            var targetFile: File? = null
            var touchedTargetFile = false
            try {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val safeTag = release.tagName.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "latest" }
                val file = File(dir, "$UPDATE_APK_PREFIX$safeTag$UPDATE_APK_SUFFIX")
                targetFile = file
                cleanupStaleUpdateApks(dir, file)
                val req = Request.Builder()
                    .url(release.apkUrl)
                    .header("User-Agent", "Eara-Android")
                    .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
                    .get()
                    .build()

                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("下载失败：${resp.code} ${resp.message}")
                    }
                    val body = resp.body ?: throw IllegalStateException("下载失败：空响应体")
                    val total = body.contentLength().coerceAtLeast(0L)
                    val input = body.byteStream()
                    touchedTargetFile = true
                    FileOutputStream(file).use { out ->
                        val buf = ByteArray(256 * 1024)
                        var read: Int
                        var downloaded = 0L
                        var lastEmit = 0L
                        while (true) {
                            read = input.read(buf)
                            if (read <= 0) break
                            out.write(buf, 0, read)
                            downloaded += read.toLong()
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastEmit >= 200L) {
                                _updateState.value = AppUpdateState.Downloading(
                                    release = release,
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    source = source
                                )
                                lastEmit = now
                            }
                        }
                        out.flush()
                        _updateState.value = AppUpdateState.Downloading(
                            release = release,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            source = source
                        )
                    }
                }

                val ok = withContext(Dispatchers.IO) { file.exists() && file.length() > 0L }
                if (!ok) throw IllegalStateException("下载文件无效")
                _updateState.value = AppUpdateState.ReadyToInstall(release, apkPath = file.absolutePath, source = source)
            } catch (e: Exception) {
                if (touchedTargetFile) {
                    runCatching { targetFile?.takeIf { it.exists() }?.delete() }
                }
                val msg = e.message?.trim().orEmpty().ifBlank { "下载失败" }
                _updateState.value = AppUpdateState.Failed(msg, source)
            }
        }
    }

    private fun cleanupStaleUpdateApks(dir: File, keepFile: File) {
        dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith(UPDATE_APK_PREFIX) &&
                file.name.endsWith(UPDATE_APK_SUFFIX) &&
                file.absolutePath != keepFile.absolutePath
        }?.forEach { staleFile ->
            runCatching { staleFile.delete() }
        }
    }

    fun resetUpdateState() {
        val cur = _updateState.value
        if (cur is AppUpdateState.Checking || cur is AppUpdateState.Downloading) return
        _updateState.value = AppUpdateState.Idle
    }
}
