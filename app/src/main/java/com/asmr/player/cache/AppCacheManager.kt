package com.asmr.player.cache

import android.content.Context
import android.util.Log
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.playback.PlaybackMediaCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppCacheState(
    val maxSizeMb: Int = AppCacheLimits.DefaultSizeMb,
    val usedSizeBytes: Long = 0L,
    val isClearing: Boolean = false,
)

@Singleton
class AppCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val imageCacheManager: ImageCacheManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val clearing = AtomicBoolean(false)
    private val previewDirectory = File(context.cacheDir, DLSITE_PREVIEW_CACHE_DIR_NAME)
    private val legacyCoilDirectory = File(context.cacheDir, LEGACY_COIL_CACHE_DIR_NAME)
    private val _state = MutableStateFlow(AppCacheState())
    val state: StateFlow<AppCacheState> = _state.asStateFlow()
    private var sizeRefreshJob: Job? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            settingsRepository.appCacheMaxSizeMb
                .distinctUntilChanged()
                .collect { requestedSizeMb ->
                    applyMaxSize(AppCacheLimits.clampSizeMb(requestedSizeMb))
                }
        }
    }

    fun refreshSize() {
        start()
        sizeRefreshJob?.cancel()
        sizeRefreshJob = scope.launch { refreshSizeNow() }
    }

    fun clearCache() {
        start()
        if (!clearing.compareAndSet(false, true)) return
        scope.launch {
            _state.update { it.copy(isClearing = true) }
            try {
                runCacheOperation("clear image cache") { imageCacheManager.clearCaches() }
                runCacheOperation("clear playback cache") { PlaybackMediaCache.clear(context) }
                runCacheOperation("clear preview cache") { clearDirectoryContents(previewDirectory) }
                runCacheOperation("clear legacy Coil cache") { clearDirectoryContents(legacyCoilDirectory) }
                refreshSizeNow()
            } finally {
                clearing.set(false)
                _state.update { it.copy(isClearing = false) }
            }
        }
    }

    fun onPreviewCacheChanged(retainedFile: File) {
        start()
        scope.launch {
            trimDirectoryToSize(
                directory = previewDirectory,
                maxSizeBytes = AppCacheLimits.previewMaxSizeBytes(_state.value.maxSizeMb),
                retainedFile = retainedFile,
            )
            refreshSizeNow()
        }
    }

    private fun applyMaxSize(sizeMb: Int) {
        _state.update { it.copy(maxSizeMb = sizeMb) }
        runCacheOperation("resize image cache") {
            imageCacheManager.updateDiskMaxSizeBytes(AppCacheLimits.imageMaxSizeBytes(sizeMb))
        }
        runCacheOperation("resize playback cache") {
            PlaybackMediaCache.updateMaxCacheBytes(AppCacheLimits.playbackMaxSizeBytes(sizeMb))
        }
        runCacheOperation("resize preview cache") {
            trimDirectoryToSize(previewDirectory, AppCacheLimits.previewMaxSizeBytes(sizeMb))
        }
        runCacheOperation("clear legacy Coil cache") { clearDirectoryContents(legacyCoilDirectory) }
    }

    private fun refreshSizeNow() {
        val usedBytes = cacheSizeOrZero("measure image cache") { imageCacheManager.diskSizeBytes() } +
            cacheSizeOrZero("measure playback cache") { PlaybackMediaCache.sizeBytes(context) } +
            cacheSizeOrZero("measure preview cache") { directorySizeBytes(previewDirectory) } +
            cacheSizeOrZero("measure legacy Coil cache") { directorySizeBytes(legacyCoilDirectory) }
        _state.update { it.copy(usedSizeBytes = usedBytes.coerceAtLeast(0L)) }
    }

    private fun trimDirectoryToSize(
        directory: File,
        maxSizeBytes: Long,
        retainedFile: File? = null,
    ) {
        val files = directory.listFiles()
            ?.asSequence()
            ?.filter(File::isFile)
            ?.sortedBy(File::lastModified)
            ?.toList()
            .orEmpty()
        var currentSize = files.sumOf(File::length)
        if (currentSize <= maxSizeBytes) return
        val targetSize = (maxSizeBytes - maxSizeBytes / 10L).coerceAtLeast(0L)
        for (file in files) {
            if (currentSize <= targetSize) break
            if (file == retainedFile) continue
            val length = file.length()
            if (file.delete()) currentSize = (currentSize - length).coerceAtLeast(0L)
        }
    }

    private fun directorySizeBytes(directory: File): Long {
        return directory.walkTopDown()
            .filter(File::isFile)
            .sumOf(File::length)
    }

    private fun clearDirectoryContents(directory: File) {
        if (!directory.isDirectory) return
        directory.walkBottomUp()
            .filter { it != directory }
            .forEach(File::delete)
    }

    private inline fun runCacheOperation(operation: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            Log.w(TAG, "$operation failed", error)
        }
    }

    private inline fun cacheSizeOrZero(operation: String, block: () -> Long): Long {
        return runCatching(block).onFailure { error ->
            Log.w(TAG, "$operation failed", error)
        }.getOrDefault(0L)
    }

    companion object {
        private const val TAG = "AppCacheManager"
        const val DLSITE_PREVIEW_CACHE_DIR_NAME = "dlsite_play_preview"
        private const val LEGACY_COIL_CACHE_DIR_NAME = "coil_cache"
    }
}
