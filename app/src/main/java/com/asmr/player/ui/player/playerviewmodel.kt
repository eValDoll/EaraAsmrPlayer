package com.asmr.player.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.playback.PlayerConnection
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.FlowPreview
import javax.inject.Inject
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import androidx.core.net.toUri
import androidx.media3.common.Player
import android.os.Bundle
import java.io.File

import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.data.repository.TrackSliceRepository
import com.asmr.player.data.repository.SliceOverlapException
import com.asmr.player.data.settings.EqualizerSettings
import com.asmr.player.data.settings.AsmrPreset
import com.asmr.player.playback.SlicePlaybackController

import com.asmr.player.util.MessageManager
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import com.asmr.player.domain.model.Slice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltViewModel
@OptIn(FlowPreview::class)
class PlayerViewModel @Inject constructor(
    private val playerConnection: PlayerConnection,
    private val settingsRepository: SettingsRepository,
    private val playlistRepository: PlaylistRepository,
    private val trackDao: TrackDao,
    private val trackSliceRepository: TrackSliceRepository,
    private val slicePlaybackController: SlicePlaybackController,
    private val messageManager: MessageManager
) : ViewModel() {
    val playback: StateFlow<PlaybackSnapshot> = playerConnection.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackSnapshot())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val resolvedDurationMs: StateFlow<Long> = playback
        .map { it.currentMediaItem to it.durationMs.coerceAtLeast(0L) }
        .distinctUntilChanged { old, new ->
            old.first?.mediaId == new.first?.mediaId && old.second == new.second
        }
        .flatMapLatest { (item, durationMs) ->
            flow { emit(resolveDurationMs(item, durationMs)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val queue: StateFlow<List<MediaItem>> = playerConnection.queue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allPlaylists = playlistRepository.observeAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val floatingLyricsEnabled: StateFlow<Boolean> = settingsRepository.floatingLyricsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val sleepTimerEndAtMs: StateFlow<Long> = settingsRepository.sleepTimerEndAtMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val sleepTimerLastDurationMin: StateFlow<Int> = settingsRepository.sleepTimerLastDurationMin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)

    private val _sessionEqualizer = MutableStateFlow<EqualizerSettings?>(null)
    val sessionEqualizer: StateFlow<EqualizerSettings> = combine(
        settingsRepository.equalizerSettings,
        _sessionEqualizer
    ) { global, session ->
        session ?: global
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EqualizerSettings())

    val customPresets: StateFlow<List<AsmrPreset>> = settingsRepository.customEqualizerPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSleepTimerMinutes(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        val endAtMs = System.currentTimeMillis() + minutes.toLong() * 60_000L
        val endAtText = formatSleepTimerEndTime(endAtMs)
        viewModelScope.launch {
            settingsRepository.setSleepTimerLastDurationMin(minutes)
            settingsRepository.setSleepTimerEndAtMs(endAtMs)
            messageManager.showSuccess("设置成功，预计${endAtText}暂停播放")
        }
    }

    fun cancelSleepTimer() {
        viewModelScope.launch {
            settingsRepository.clearSleepTimer()
        }
    }

    private fun formatSleepTimerEndTime(endAtMs: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(endAtMs))
    }

    private val tempStartMs = MutableStateFlow<Long?>(null)
    private val selectedSliceId = MutableStateFlow<Long?>(null)
    private val editDrag = MutableStateFlow(SliceEditDrag.None)

    private val _sliceUiEvents = MutableSharedFlow<SliceUiEvent>(extraBufferCapacity = 8)
    val sliceUiEvents: SharedFlow<SliceUiEvent> = _sliceUiEvents.asSharedFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val trackMediaIdFlow = playback
        .map { it.currentMediaItem?.mediaId?.takeIf { id -> id.isNotBlank() } }
        .distinctUntilChanged()
        .onEach {
            tempStartMs.value = null
            selectedSliceId.value = null
            editDrag.value = SliceEditDrag.None
            slicePlaybackController.setUserScrubbing(false)
            slicePlaybackController.clearPreview()
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val slicesForCurrentTrack = trackMediaIdFlow.flatMapLatest { mediaId ->
        if (mediaId == null) flowOf(emptyList()) else trackSliceRepository.observeSlices(mediaId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val baseSliceUiState: StateFlow<SliceUiState> = combine(
        trackMediaIdFlow,
        slicesForCurrentTrack,
        tempStartMs,
        slicePlaybackController.sliceModeEnabled,
        selectedSliceId
    ) { mediaId, slices, tempStart, enabled, selectedId ->
        SliceUiState(
            trackMediaId = mediaId,
            slices = slices,
            tempStartMs = tempStart,
            sliceModeEnabled = enabled,
            selectedSliceId = selectedId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SliceUiState())

    val sliceUiState: StateFlow<SliceUiState> = combine(
        baseSliceUiState,
        editDrag,
        slicePlaybackController.userScrubbing
    ) { base, drag, scrubbing ->
        base.copy(editDrag = drag, userScrubbing = scrubbing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SliceUiState())

    init {
        viewModelScope.launch {
            _sessionEqualizer
                .filterNotNull()
                .distinctUntilChanged()
                .debounce(60)
                .collect { settings ->
                    settingsRepository.updateEqualizerSettings(settings)
                    val bundle = Bundle().apply {
                        putBoolean("enabled", settings.enabled)
                        putIntArray("levels", settings.bandLevels.toIntArray())
                        putInt("virtualizer", settings.virtualizerStrength)
                        putFloat("balance", settings.balance)
                        putString("preset", settings.presetName)
                        putFloat("gain", settings.originalGain)
                        putBoolean("reverbEnabled", settings.reverbEnabled)
                        putString("reverbPreset", settings.reverbPreset)
                        putInt("reverbWet", settings.reverbWet)
                        putBoolean("stereoEnabled", settings.stereoEnabled)
                        putBoolean("orbitEnabled", settings.orbitEnabled)
                        putFloat("orbitSpeed", settings.orbitSpeed)
                        putFloat("orbitDistance", settings.orbitDistance)
                        putFloat("orbitAzimuthDeg", settings.orbitAzimuthDeg)
                        putBoolean("channelEnabled", settings.channelEnabled)
                        putInt("channelMode", settings.channelMode)
                        putBoolean("volumeThresholdEnabled", settings.volumeThresholdEnabled)
                        putInt("volumeThresholdMode", settings.volumeThresholdMode)
                        putFloat("volumeThresholdMinDb", settings.volumeThresholdMinDb)
                        putFloat("volumeThresholdMaxDb", settings.volumeThresholdMaxDb)
                        putFloat("volumeLoudnessTargetDb", settings.volumeLoudnessTargetDb)
                    }
                    playerConnection.sendCustomCommand("UPDATE_SESSION_EQ", bundle)
                }
        }

        viewModelScope.launch {
            trackMediaIdFlow.collect { }
        }

    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val isFavorite: StateFlow<Boolean> = playback
        .map { it.currentMediaItem?.mediaId }
        .flatMapLatest { mediaId ->
            if (mediaId == null) kotlinx.coroutines.flow.flowOf(false)
            else playlistRepository.observeIsFavorite(mediaId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleFavorite() {
        val item = playback.value.currentMediaItem ?: return
        val mediaId = item.mediaId
        viewModelScope.launch {
            val favId = playlistRepository.getOrCreateFavoritesPlaylistId()
            if (isFavorite.value) {
                playlistRepository.removeItemFromPlaylist(favId, mediaId)
                messageManager.showInfo("已取消收藏")
            } else {
                playlistRepository.addItemToPlaylist(favId, item)
                messageManager.showSuccess("已添加到我的收藏")
            }
        }
    }

    fun togglePlayPause() = playerConnection.togglePlayPause()

    fun toggleFloatingLyrics() {
        viewModelScope.launch {
            val enabled = !floatingLyricsEnabled.value
            settingsRepository.setFloatingLyricsEnabled(enabled)
            messageManager.showInfo(if (enabled) "悬浮歌词已开启" else "悬浮歌词已关闭")
        }
    }

    fun addToQueue() {
        val item = playback.value.currentMediaItem ?: return
        val added = playerConnection.addMediaItem(item)
        if (added) {
            messageManager.showSuccess("已加入播放队列")
        } else {
            messageManager.showInfo("已在播放队列中")
        }
    }

    fun addTrackToQueue(album: Album, track: Track): Boolean {
        val item = MediaItemFactory.fromTrack(album, track)
        val added = playerConnection.addMediaItem(item)
        if (added) {
            messageManager.showSuccess("已加入播放队列")
        } else {
            messageManager.showInfo("已在播放队列中")
        }
        return added
    }

    suspend fun addToPlaylist(playlistId: Long): Boolean {
        val item = playback.value.currentMediaItem ?: return false
        val added = playlistRepository.addItemToPlaylist(playlistId, item)
        if (added) {
            messageManager.showSuccess("已添加到播放列表")
        } else {
            messageManager.showInfo("已在播放列表中")
        }
        return added
    }

    fun play() = playerConnection.play()
    fun pause() = playerConnection.pause()
    fun seekTo(positionMs: Long) = playerConnection.seekTo(positionMs)
    fun seekForward10s() = playerConnection.seekBy(10_000L)
    fun next() = playerConnection.skipToNext()
    fun previous() = playerConnection.skipToPrevious()
    fun playQueueIndex(index: Int) = playerConnection.seekToQueueIndex(index)
    fun removeFromQueue(index: Int) = playerConnection.removeMediaItem(index)
    fun setPlaybackSpeed(speed: Float) = playerConnection.setPlaybackSpeed(speed)
    fun setPlaybackPitch(pitch: Float) = playerConnection.setPlaybackPitch(pitch)
    fun setPlaybackParameters(speed: Float, pitch: Float) = playerConnection.setPlaybackParameters(speed, pitch)

    fun setUserScrubbing(scrubbing: Boolean) {
        slicePlaybackController.setUserScrubbing(scrubbing)
    }

    fun toggleSliceMode() {
        val enabled = slicePlaybackController.sliceModeEnabled.value
        if (!enabled) {
            slicePlaybackController.setSliceModeEnabled(true)
        } else {
            slicePlaybackController.setSliceModeEnabled(false)
        }
    }

    fun playSlicePreview(slice: Slice) {
        slicePlaybackController.startPreview(slice)
        seekTo(slice.startMs)
        play()
    }

    fun selectSlice(sliceId: Long?) {
        selectedSliceId.value = sliceId
        editDrag.value = SliceEditDrag.None
    }

    fun deleteSlice(sliceId: Long) {
        viewModelScope.launch {
            runCatching { trackSliceRepository.deleteSlice(sliceId) }
                .onSuccess {
                    if (selectedSliceId.value == sliceId) {
                        selectedSliceId.value = null
                        editDrag.value = SliceEditDrag.None
                    }
                    messageManager.showInfo("已删除切片")
                }
                .onFailure { messageManager.showError("删除切片失败") }
        }
    }

    fun clearSlicesForCurrentTrack() {
        val mediaId = sliceUiState.value.trackMediaId ?: return
        viewModelScope.launch {
            runCatching { trackSliceRepository.clearTrack(mediaId) }
                .onSuccess {
                    selectedSliceId.value = null
                    editDrag.value = SliceEditDrag.None
                    tempStartMs.value = null
                    messageManager.showInfo("已清空切片")
                }
                .onFailure { messageManager.showError("清空切片失败") }
        }
    }

    fun updateSliceRange(sliceId: Long, startMs: Long, endMs: Long, durationMs: Long) {
        val safeDuration = durationMs.coerceAtLeast(0L)
        val start = startMs.coerceIn(0L, safeDuration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        val end = endMs.coerceIn(0L, safeDuration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        if (end <= start) {
            messageManager.showInfo("结束时间不能早于开始时间")
            return
        }
        viewModelScope.launch {
            runCatching { trackSliceRepository.updateSliceRange(sliceId, start, end) }
                .onFailure { e ->
                    if (e is SliceOverlapException) {
                        messageManager.showInfo("切片与已有切片重叠")
                    } else {
                        messageManager.showError("更新切片失败")
                    }
                }
        }
    }

    fun onCutPressed(durationMs: Long) {
        val mediaId = sliceUiState.value.trackMediaId ?: run {
            messageManager.showInfo("暂无可播放曲目")
            return
        }
        if (durationMs <= 0L) {
            messageManager.showInfo("无法获取时长")
            return
        }
        val pos = playback.value.positionMs.coerceAtLeast(0L)
        val start = tempStartMs.value
        val existing = sliceUiState.value.slices
        if (start == null) {
            val clamped = pos.coerceIn(0L, durationMs)
            val hit = existing.any { s -> clamped >= s.startMs && clamped < s.endMs }
            if (hit) {
                _sliceUiEvents.tryEmit(SliceUiEvent.CutInvalidRange)
                messageManager.showInfo("起点落在已有切片内")
                return
            }
            tempStartMs.value = clamped
            _sliceUiEvents.tryEmit(SliceUiEvent.CutStartMarked)
            messageManager.showInfo("已标记起点")
            return
        }
        val end = pos.coerceIn(0L, durationMs)
        if (end <= start) {
            _sliceUiEvents.tryEmit(SliceUiEvent.CutInvalidRange)
            messageManager.showInfo("结束时间不能早于开始时间")
            return
        }
        val overlap = existing.any { s -> start < s.endMs && end > s.startMs }
        if (overlap) {
            _sliceUiEvents.tryEmit(SliceUiEvent.CutInvalidRange)
            messageManager.showInfo("切片与已有切片重叠")
            return
        }
        viewModelScope.launch {
            runCatching {
                trackSliceRepository.appendSlice(trackMediaId = mediaId, startMs = start, endMs = end)
            }.onSuccess {
                tempStartMs.value = null
                _sliceUiEvents.tryEmit(SliceUiEvent.CutSliceCreated)
                messageManager.showSuccess("已创建切片")
            }.onFailure {
                if (it is SliceOverlapException) {
                    _sliceUiEvents.tryEmit(SliceUiEvent.CutInvalidRange)
                    messageManager.showInfo("切片与已有切片重叠")
                } else {
                    messageManager.showError("创建切片失败")
                }
            }
        }
    }

    fun updateSessionEqualizer(settings: EqualizerSettings) {
        _sessionEqualizer.value = settings
    }

    fun saveCustomPreset(name: String, settings: EqualizerSettings) {
        viewModelScope.launch {
            settingsRepository.saveCustomPreset(
                AsmrPreset(
                    name = name,
                    bandLevels = settings.bandLevels,
                    virtualizerStrength = settings.virtualizerStrength
                )
            )
            updateSessionEqualizer(settings.copy(presetName = name))
        }
    }

    fun deleteCustomPreset(preset: AsmrPreset) {
        viewModelScope.launch { settingsRepository.deleteCustomPreset(preset.name) }
    }

    fun cyclePlayMode() {
        val snap = playback.value
        val currentMode = when {
            snap.shuffleEnabled -> 2
            snap.repeatMode == Player.REPEAT_MODE_ONE -> 1
            else -> 0
        }
        val nextMode = when (currentMode) {
            0 -> 1
            1 -> 2
            else -> 0
        }
        val modeText = when (nextMode) {
            1 -> {
                playerConnection.setRepeatMode(Player.REPEAT_MODE_ONE)
                playerConnection.setShuffleEnabled(false)
                "单曲循环"
            }
            2 -> {
                playerConnection.setRepeatMode(Player.REPEAT_MODE_ALL)
                playerConnection.setShuffleEnabled(true)
                "随机播放"
            }
            else -> {
                playerConnection.setRepeatMode(Player.REPEAT_MODE_ALL)
                playerConnection.setShuffleEnabled(false)
                "列表循环"
            }
        }
        messageManager.showInfo("播放模式：$modeText")
        viewModelScope.launch { settingsRepository.setPlayMode(nextMode) }
    }

    fun playTracks(album: Album, tracks: List<Track>, startTrack: Track) {
        playTracks(album = album, tracks = tracks, startTrack = startTrack, startPositionMs = 0L)
    }

    fun playAlbumResume(album: Album, resumeMediaId: String?, startPositionMs: Long) {
        viewModelScope.launch {
            val trackEntities = runCatching { trackDao.getTracksForAlbumOnce(album.id) }.getOrNull().orEmpty()
            val allTracks = trackEntities.map {
                Track(
                    id = it.id,
                    albumId = it.albumId,
                    title = it.title,
                    path = it.path,
                    duration = it.duration,
                    group = it.group
                )
            }
            if (allTracks.isEmpty()) {
                messageManager.showError("未找到可播放的音轨")
                return@launch
            }
            val resumeId = resumeMediaId?.trim().orEmpty().ifBlank { null }
            val folderKey = resumeId?.let { deriveParentKeyOrNull(it) }

            val scopedTracks = if (folderKey != null) {
                allTracks.filter { t -> deriveParentKeyOrNull(t.path) == folderKey }.takeIf { it.isNotEmpty() } ?: allTracks
            } else {
                allTracks
            }

            val startTrack =
                resumeId?.let { mid -> scopedTracks.firstOrNull { it.path == mid } }
                    ?: resumeId?.let { mid -> allTracks.firstOrNull { it.path == mid } }?.takeIf { it in scopedTracks }
                    ?: scopedTracks.first()

            playTracks(album = album, tracks = scopedTracks, startTrack = startTrack, startPositionMs = startPositionMs)
        }
    }

    private fun deriveParentKeyOrNull(raw: String): String? {
        val input = raw.trim()
        if (input.isBlank()) return null

        val normalized = input.replace('\\', '/')
        val isUriLike = normalized.contains("://") ||
            normalized.startsWith("content://", ignoreCase = true) ||
            normalized.startsWith("file://", ignoreCase = true) ||
            normalized.startsWith("http://", ignoreCase = true) ||
            normalized.startsWith("https://", ignoreCase = true)

        return if (isUriLike) {
            runCatching {
                val uri = Uri.parse(normalized)
                val scheme = uri.scheme?.trim().orEmpty()
                val path = uri.path?.trim().orEmpty()
                val parent = path.substringBeforeLast('/', "").trim().trimEnd('/')
                if (scheme.isBlank() || parent.isBlank()) return null
                val authority = uri.encodedAuthority?.trim().orEmpty()
                buildString {
                    append(scheme.lowercase())
                    append("://")
                    if (authority.isNotBlank()) append(authority)
                    append(parent)
                }.trimEnd('/').lowercase()
            }.getOrNull()
        } else {
            runCatching {
                val parent = File(input).parent?.trim().orEmpty()
                parent.takeIf { it.isNotBlank() }
                    ?.replace('\\', '/')
                    ?.trimEnd('/')
                    ?.lowercase()
            }.getOrNull()
        }
    }

    fun playTracks(album: Album, tracks: List<Track>, startTrack: Track, startPositionMs: Long) {
        if (playerConnection.getControllerOrNull() == null) {
            messageManager.showError("播放器未连接")
            return
        }
        if (startTrack.path.contains(".m3u8", ignoreCase = true)) {
            messageManager.showError("当前不支持 m3u8 流媒体，请先下载音频文件")
            return
        }
        val items = tracks.map { MediaItemFactory.fromTrack(album, it) }
        val index = tracks.indexOfFirst { it.path == startTrack.path }.coerceAtLeast(0)
        playerConnection.setQueue(items = items, startIndex = index, startPositionMs = startPositionMs, playWhenReady = true)
    }

    fun playVideo(title: String, uriOrPath: String) {
        playVideo(title = title, uriOrPath = uriOrPath, artworkUri = "", artist = "")
    }

    fun playVideo(title: String, uriOrPath: String, artworkUri: String, artist: String) {
        val trimmed = uriOrPath.trim()
        if (trimmed.isBlank()) return
        val uri = if (
            trimmed.startsWith("http", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
        ) {
            trimmed.toUri()
        } else {
            Uri.fromFile(File(trimmed))
        }
        val ext = trimmed.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
        val mimeType = when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            else -> "video/*"
        }
        val displayTitle = title.ifBlank { trimmed.substringAfterLast('/').substringAfterLast('\\') }
        val artwork = artworkUri.trim()
        val metadata = MediaMetadata.Builder()
            .setTitle(displayTitle)
            .setArtist(artist.trim())
            .setArtworkUri(artwork.takeIf { it.isNotBlank() }?.toUri())
            .setExtras(Bundle().apply { putBoolean("is_video", true) })
            .build()
        val item = MediaItem.Builder()
            .setMediaId(trimmed)
            .setUri(uri)
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()
        playerConnection.setQueue(listOf(item), 0, playWhenReady = true)
    }

    fun playMediaItems(items: List<MediaItem>, startIndex: Int) {
        if (playerConnection.getControllerOrNull() == null) {
            messageManager.showError("播放器未连接")
            return
        }
        if (items.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        playerConnection.setQueue(items, safeIndex, playWhenReady = true)
    }

    fun playPlaylistItems(items: List<PlaylistItemEntity>, startItem: PlaylistItemEntity) {
        val mapped = items.mapNotNull { it.toMediaItemOrNull()?.let { mi -> it to mi } }
        if (mapped.isEmpty()) return
        val mediaItems = mapped.map { it.second }
        val index = mapped.indexOfFirst { (entity, _) -> entity.mediaId == startItem.mediaId }
            .takeIf { it >= 0 } ?: 0
        playerConnection.setQueue(mediaItems, index, playWhenReady = true)
    }

    fun playerOrNull(): Player? = playerConnection.getControllerOrNull()

    private suspend fun resolveDurationMs(item: MediaItem?, fallback: Long): Long {
        val safeFallback = fallback.coerceAtLeast(0L)
        if (item == null) return safeFallback
        return withContext(Dispatchers.IO) {
            val extras = item.mediaMetadata.extras
            val trackId = extras?.getLong("track_id") ?: 0L
            if (trackId > 0L) {
                val d = runCatching { trackDao.getTrackByIdOnce(trackId) }.getOrNull()?.duration ?: 0.0
                if (d > 0.0) return@withContext (d * 1000.0).roundToLong()
            }

            val uriString = item.localConfiguration?.uri?.toString().orEmpty().trim()
            val path = when {
                uriString.startsWith("file://", ignoreCase = true) -> runCatching {
                    Uri.parse(uriString).path.orEmpty()
                }.getOrDefault("")
                uriString.startsWith("/") -> uriString
                else -> ""
            }.trim()
            if (path.isNotBlank()) {
                val d = runCatching { trackDao.getTrackByPathOnce(path) }.getOrNull()?.duration ?: 0.0
                if (d > 0.0) return@withContext (d * 1000.0).roundToLong()
            }
            safeFallback
        }
    }
}

private fun PlaylistItemEntity.toMediaItemOrNull(): MediaItem? {
    val trimmed = uri.trim()
    if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) return null
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setArtworkUri(artworkUri.takeIf { it.isNotBlank() }?.toUri())
        .build()

    fun repairDocumentUri(raw: String): String {
        val u = runCatching { Uri.parse(raw) }.getOrNull() ?: return raw
        val segs = u.pathSegments ?: return raw
        val docIndex = segs.indexOf("document")
        if (docIndex < 0) return raw
        if (segs.size <= docIndex + 2) return raw
        val docId = segs.subList(docIndex + 1, segs.size).joinToString("/")
        val encodedDocId = Uri.encode(docId)
        val encodedPath = "/" + segs.take(docIndex + 1).joinToString("/") + "/" + encodedDocId
        return u.buildUpon().encodedPath(encodedPath).build().toString()
    }

    val normalized = if (trimmed.startsWith("content://", ignoreCase = true)) repairDocumentUri(trimmed) else trimmed
    val normalizedMediaId = mediaId.trim().let { mid ->
        if (mid.startsWith("content://", ignoreCase = true)) repairDocumentUri(mid) else mid
    }.ifBlank { normalized }
    val parsedUri = when {
        normalized.startsWith("http", ignoreCase = true) ||
            normalized.startsWith("content://", ignoreCase = true) ||
            normalized.startsWith("file://", ignoreCase = true) -> normalized.toUri()
        trimmed.startsWith("/") -> Uri.fromFile(File(trimmed))
        else -> return null
    }
    return MediaItem.Builder()
        .setMediaId(normalizedMediaId)
        .setUri(parsedUri)
        .setMediaMetadata(metadata)
        .build()
}
