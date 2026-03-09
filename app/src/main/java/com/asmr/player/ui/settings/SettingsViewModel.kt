package com.asmr.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.BuildConfig
import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.remote.update.GitHubUpdateClient
import com.asmr.player.data.remote.update.UpdateRelease
import com.asmr.player.data.settings.FloatingLyricsSettings
import com.asmr.player.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import android.os.Environment
import android.os.SystemClock
import javax.inject.Inject

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val latestVersionName: String) : AppUpdateState
    data class UpdateAvailable(val release: UpdateRelease) : AppUpdateState
    data class Downloading(val release: UpdateRelease, val downloadedBytes: Long, val totalBytes: Long) : AppUpdateState
    data class ReadyToInstall(val release: UpdateRelease, val apkPath: String) : AppUpdateState
    data class Failed(val message: String) : AppUpdateState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val settingsDataStore: SettingsDataStore,
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val floatingLyricsEnabled: StateFlow<Boolean> = settingsRepository.floatingLyricsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val floatingLyricsSettings: StateFlow<FloatingLyricsSettings> = settingsRepository.floatingLyricsSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FloatingLyricsSettings())

    val dynamicPlayerHueEnabled: StateFlow<Boolean> = settingsDataStore.dynamicPlayerHueEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val themeMode: StateFlow<String> = settingsDataStore.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

    val staticHueArgb: StateFlow<Int?> = settingsDataStore.staticHueArgb
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val coverBackgroundEnabled: StateFlow<Boolean> = settingsDataStore.coverBackgroundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val coverBackgroundClarity: StateFlow<Float> = settingsDataStore.coverBackgroundClarity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.35f)

    private val updateClient = GitHubUpdateClient(okHttpClient)
    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState = _updateState.asStateFlow()
    private var updateJob: Job? = null

    fun setFloatingLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFloatingLyricsEnabled(enabled) }
    }

    fun updateFloatingLyricsSettings(settings: FloatingLyricsSettings) {
        viewModelScope.launch { settingsRepository.updateFloatingLyricsSettings(settings) }
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

    fun checkUpdate() {
        val cur = _updateState.value
        if (cur is AppUpdateState.Checking || cur is AppUpdateState.Downloading) return
        updateJob?.cancel()
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = AppUpdateState.Checking
            try {
                val release = updateClient.fetchLatestRelease(owner = "eValDoll", repo = "AsmrPlayer")
                val currentVersion = BuildConfig.VERSION_NAME
                val newer = updateClient.isNewerThanCurrent(release.versionName, currentVersion)
                _updateState.value = if (newer) {
                    AppUpdateState.UpdateAvailable(release)
                } else {
                    AppUpdateState.UpToDate(latestVersionName = release.versionName)
                }
            } catch (e: Exception) {
                val msg = e.message?.trim().orEmpty().ifBlank { "检查更新失败" }
                _updateState.value = AppUpdateState.Failed(msg)
            }
        }
    }

    fun downloadLatestApk() {
        val state = _updateState.value
        val release = (state as? AppUpdateState.UpdateAvailable)?.release ?: return
        updateJob?.cancel()
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = AppUpdateState.Downloading(release, 0L, 0L)
            try {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val safeTag = release.tagName.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "latest" }
                val file = File(dir, "eara-$safeTag.apk")
                val req = Request.Builder()
                    .url(release.apkUrl)
                    .header("User-Agent", "Eara-Android")
                    .get()
                    .build()

                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("下载失败：${resp.code} ${resp.message}")
                    }
                    val body = resp.body ?: throw IllegalStateException("下载失败：空响应体")
                    val total = body.contentLength().coerceAtLeast(0L)
                    val input = body.byteStream()
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
                                _updateState.value = AppUpdateState.Downloading(release, downloadedBytes = downloaded, totalBytes = total)
                                lastEmit = now
                            }
                        }
                        out.flush()
                        _updateState.value = AppUpdateState.Downloading(release, downloadedBytes = downloaded, totalBytes = total)
                    }
                }

                val ok = withContext(Dispatchers.IO) { file.exists() && file.length() > 0L }
                if (!ok) throw IllegalStateException("下载文件无效")
                _updateState.value = AppUpdateState.ReadyToInstall(release, apkPath = file.absolutePath)
            } catch (e: Exception) {
                val msg = e.message?.trim().orEmpty().ifBlank { "下载失败" }
                _updateState.value = AppUpdateState.Failed(msg)
            }
        }
    }

    fun resetUpdateState() {
        val cur = _updateState.value
        if (cur is AppUpdateState.Checking || cur is AppUpdateState.Downloading) return
        _updateState.value = AppUpdateState.Idle
    }
}
