package com.asmr.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.asmr.player.data.local.db.dao.AlbumDao
import com.asmr.player.data.local.db.dao.TrackDao
import com.asmr.player.data.repository.TrackSliceRepository
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.service.PlaybackService
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.util.MessageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.asmr.player.util.NetworkMeteredChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton
import com.asmr.player.domain.model.Slice

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val trackSliceRepository: TrackSliceRepository,
    private val slicePlaybackController: SlicePlaybackController,
    private val messageManager: MessageManager,
    private val networkMeteredChecker: NetworkMeteredChecker,
    private val playbackStateStore: PlaybackStateStore,
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    private val _queue = MutableStateFlow<List<androidx.media3.common.MediaItem>>(emptyList())
    val queue: StateFlow<List<androidx.media3.common.MediaItem>> = _queue.asStateFlow()

    private var controller: MediaController? = null
    private var lastErrorAtMs: Long = 0L
    private var lastErrorKey: String = ""
    private val sliceLoopEngine = SliceLoopEngine()
    private val currentSlices = MutableStateFlow<List<Slice>>(emptyList())
    private var didRestorePlaybackState: Boolean = false
    private val meteredWarnedMediaIds = LinkedHashSet<String>()
    private var lastMeteredWarnAtMs: Long = 0L

    init {
        scope.launch {
            connect()
        }
        scope.launch {
            snapshot
                .map { it.currentMediaItem?.mediaId?.takeIf { id -> id.isNotBlank() } }
                .distinctUntilChanged()
                .flatMapLatest { mediaId ->
                    if (mediaId == null) flowOf(emptyList()) else trackSliceRepository.observeSlices(mediaId)
                }
                .collect { slices -> currentSlices.value = slices }
        }
        scope.launch {
            snapshot
                .map { it.currentMediaItem }
                .map { item ->
                    val id = item?.mediaId?.takeIf { it.isNotBlank() }.orEmpty()
                    val uri = item?.localConfiguration?.uri?.toString().orEmpty()
                    id to uri
                }
                .distinctUntilChanged()
                .collect { (mediaId, uriText) ->
                    if (mediaId.isBlank()) return@collect
                    if (!uriText.startsWith("http", ignoreCase = true)) return@collect
                    if (!networkMeteredChecker.isActiveNetworkMetered()) return@collect

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastMeteredWarnAtMs < 2_000) return@collect
                    if (!meteredWarnedMediaIds.add(mediaId)) return@collect

                    lastMeteredWarnAtMs = now
                    messageManager.showWarning("正在使用流量播放")
                }
        }
        scope.launch {
            while (isActive) {
                val c = controller
                if (c != null) {
                    val duration = c.duration.coerceAtLeast(0L)
                    var sessionId = _snapshot.value.audioSessionId
                    
                    // If session ID is missing, try to fetch it
                    if (sessionId == 0 || sessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                        try {
                            val cmd = androidx.media3.session.SessionCommand("GET_AUDIO_SESSION_ID", android.os.Bundle.EMPTY)
                            val result = awaitSessionResult(context, c.sendCustomCommand(cmd, android.os.Bundle.EMPTY))
                            if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                                val fetchedId = result.extras.getInt("AUDIO_SESSION_ID")
                                if (fetchedId != 0) {
                                    sessionId = fetchedId
                                    android.util.Log.d("PlayerConnection", "Polled Session ID: $sessionId")
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore errors during polling
                        }
                    }

                    _snapshot.value = _snapshot.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0L),
                        durationMs = duration,
                        audioSessionId = sessionId,
                        playbackSpeed = c.playbackParameters.speed,
                        playbackPitch = c.playbackParameters.pitch
                    )

                    applySliceLoopIfNeeded(c)
                }
                delay(250)
            }
        }
        scope.launch {
            queue
                .map { items -> items.map { it.mediaId } }
                .distinctUntilChanged()
                .collect {
                    savePlaybackState()
                }
        }
        scope.launch {
            snapshot
                .map { s ->
                    PlaybackStateKey(
                        currentMediaId = s.currentMediaItem?.mediaId.orEmpty(),
                        isPlaying = s.isPlaying,
                        repeatMode = s.repeatMode,
                        shuffleEnabled = s.shuffleEnabled,
                        speed = s.playbackSpeed,
                        pitch = s.playbackPitch
                    )
                }
                .distinctUntilChanged()
                .collect {
                    savePlaybackState()
                }
        }
        scope.launch {
            while (isActive) {
                delay(5_000)
                val c = controller ?: continue
                if (c.isPlaying || c.playWhenReady) {
                    savePlaybackState()
                }
            }
        }
    }

    private suspend fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val c = awaitMediaController(context, future)
        controller = c
        c.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    val shouldUpdateSnapshot =
                        events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                            events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                            events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                            events.contains(Player.EVENT_MEDIA_METADATA_CHANGED) ||
                            events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                            events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                            events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
                            events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED) ||
                            events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)

                    if (shouldUpdateSnapshot) {
                        val prev = _snapshot.value
                        _snapshot.value = player.toSnapshot(isConnected = true, audioSessionId = prev.audioSessionId)
                    }
                    if (
                        events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                        events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                    ) {
                        updateQueue()
                    }
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    _snapshot.value = _snapshot.value.copy(audioSessionId = audioSessionId)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val now = System.currentTimeMillis()
                    val key = "${error.errorCodeName}:${error.message.orEmpty()}"
                    if (key != lastErrorKey || now - lastErrorAtMs > 2_000L) {
                        lastErrorKey = key
                        lastErrorAtMs = now
                        val item = controller?.currentMediaItem
                        val uri = item?.localConfiguration?.uri?.toString().orEmpty()
                        val msg = if (uri.contains(".m3u8", ignoreCase = true)) {
                            "当前不支持 m3u8 流媒体，请先下载音频文件"
                        } else {
                            "播放失败：${error.errorCodeName}"
                        }
                        messageManager.showError(msg)
                        android.util.Log.e(
                            "PlayerConnection",
                            "Player error: ${error.errorCodeName} ${error.message} uri=$uri mediaId=${item?.mediaId}",
                            error
                        )
                    }
                }
            }
        )
        _snapshot.value = c.toSnapshot(isConnected = true, audioSessionId = _snapshot.value.audioSessionId)
        updateQueue()

        val restored = restorePlaybackStateIfNeeded(c)
        if (!restored) {
            val mode = runCatching { settingsRepository.playMode.first() }.getOrDefault(0)
            applyPlayModeToController(c, mode)
        }
        
        try {
            val cmd = androidx.media3.session.SessionCommand("GET_AUDIO_SESSION_ID", android.os.Bundle.EMPTY)
            val result = awaitSessionResult(context, c.sendCustomCommand(cmd, android.os.Bundle.EMPTY))
            if (result.resultCode == androidx.media3.session.SessionResult.RESULT_SUCCESS) {
                val sessionId = result.extras.getInt("AUDIO_SESSION_ID")
                if (sessionId != 0) {
                     _snapshot.value = _snapshot.value.copy(audioSessionId = sessionId)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun restorePlaybackStateIfNeeded(c: MediaController): Boolean {
        if (didRestorePlaybackState) return false
        didRestorePlaybackState = true
        if (c.mediaItemCount > 0) return false

        val saved = playbackStateStore.load() ?: return false
        val persisted = saved.queue.map { it.copy(mediaId = it.mediaId.trim(), uri = it.uri.trim()) }
            .filter { it.mediaId.isNotBlank() }
        if (persisted.isEmpty()) return false
        val items = buildMediaItemsFromPersistedItems(persisted)
        if (items.isEmpty()) return false

        val index = saved.currentIndex.coerceIn(0, items.lastIndex)
        val pos = saved.positionMs.coerceAtLeast(0L)
        val speed = saved.speed.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
        val pitch = saved.pitch.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f

        c.setMediaItems(items, index, pos)
        c.repeatMode = saved.repeatMode
        c.shuffleModeEnabled = saved.shuffleEnabled
        c.setPlaybackParameters(PlaybackParameters(speed, pitch))
        c.prepare()
        c.playWhenReady = false

        _queue.value = items
        _snapshot.value = c.toSnapshot(isConnected = true, audioSessionId = _snapshot.value.audioSessionId)
        return true
    }

    private suspend fun buildMediaItemsFromPersistedItems(items: List<PersistedPlaybackQueueItem>): List<MediaItem> {
        return items.mapNotNull { persisted ->
            val id = persisted.mediaId.trim()
            if (id.isBlank()) return@mapNotNull null
            val track = runCatching { trackDao.getTrackByPathOnce(id) }.getOrNull()
            if (track != null) {
                val albumEntity = runCatching { albumDao.getAlbumById(track.albumId) }.getOrNull()
                val album = Album(
                    id = albumEntity?.id ?: 0L,
                    title = albumEntity?.title.orEmpty(),
                    path = albumEntity?.path.orEmpty(),
                    localPath = albumEntity?.localPath,
                    downloadPath = albumEntity?.downloadPath,
                    circle = albumEntity?.circle.orEmpty(),
                    cv = albumEntity?.cv.orEmpty(),
                    tags = albumEntity?.tags?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
                    coverUrl = albumEntity?.coverUrl.orEmpty(),
                    coverPath = albumEntity?.coverPath.orEmpty(),
                    coverThumbPath = albumEntity?.coverThumbPath.orEmpty(),
                    workId = albumEntity?.workId.orEmpty(),
                    rjCode = albumEntity?.rjCode.orEmpty().ifBlank { albumEntity?.workId.orEmpty() }
                )
                val t = Track(
                    id = track.id,
                    albumId = track.albumId,
                    title = track.title,
                    path = track.path,
                    duration = track.duration,
                    group = track.group
                )
                MediaItemFactory.fromTrack(album, t)
            } else {
                val uri = toPlayableUri(persisted.uri.ifBlank { id })
                val title = persisted.title.orEmpty().ifBlank { deriveTitleFromId(id) }
                val meta = MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(persisted.artist.orEmpty())
                    .setAlbumTitle(persisted.albumTitle.orEmpty())
                    .setArtworkUri(parsePossiblyEncodedUri(persisted.artworkUri))
                    .setExtras(
                        android.os.Bundle().apply {
                            if (persisted.albumId != null) putLong("album_id", persisted.albumId)
                            if (persisted.trackId != null) putLong("track_id", persisted.trackId)
                            if (!persisted.rjCode.isNullOrBlank()) putString("rj_code", persisted.rjCode)
                        }
                    )
                    .build()
                MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(id)
                    .setMimeType(persisted.mimeType)
                    .setMediaMetadata(meta)
                    .build()
            }
        }
    }

    private fun deriveTitleFromId(mediaId: String): String {
        val id = mediaId.trim()
        if (id.isBlank()) return ""
        if (id.startsWith("http", ignoreCase = true)) {
            val last = runCatching { id.toUri().lastPathSegment }.getOrNull().orEmpty().ifBlank { id.substringAfterLast('/') }
            val clean = last.substringBefore('?').substringBefore('#')
            val decoded = runCatching { URLDecoder.decode(clean, "UTF-8") }.getOrDefault(clean)
            return decoded.substringBeforeLast('.', decoded).ifBlank { id }
        }
        return runCatching { File(id).nameWithoutExtension }.getOrDefault(id).ifBlank { id }
    }

    private fun parsePossiblyEncodedUri(value: String?): Uri? {
        val raw = value.orEmpty().trim()
        if (raw.isBlank()) return null
        val decoded = if (
            raw.startsWith("http%3A", ignoreCase = true) ||
                raw.startsWith("https%3A", ignoreCase = true) ||
                raw.startsWith("content%3A", ignoreCase = true) ||
                raw.startsWith("file%3A", ignoreCase = true)
        ) {
            runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        } else {
            raw
        }
        return runCatching { decoded.toUri() }.getOrNull()
    }

    private fun toPlayableUri(path: String): Uri {
        val trimmed = path.trim()
        return if (trimmed.startsWith("http", ignoreCase = true) || trimmed.startsWith("content://")) {
            trimmed.toUri()
        } else {
            Uri.fromFile(File(trimmed))
        }
    }

    private suspend fun savePlaybackState() {
        val c = controller ?: return
        val items = _queue.value.ifEmpty { (0 until c.mediaItemCount).map { idx -> c.getMediaItemAt(idx) } }
        if (items.isEmpty()) return
        val persistedQueue = items.mapNotNull { item ->
            val mediaId = item.mediaId.trim()
            if (mediaId.isBlank()) return@mapNotNull null
            val uri = item.localConfiguration?.uri?.toString().orEmpty().trim().ifBlank { mediaId }
            val meta = item.mediaMetadata
            val extras = meta.extras
            val albumId = if (extras?.containsKey("album_id") == true) extras.getLong("album_id") else null
            val trackId = if (extras?.containsKey("track_id") == true) extras.getLong("track_id") else null
            val rjCode = if (extras?.containsKey("rj_code") == true) extras.getString("rj_code") else null
            PersistedPlaybackQueueItem(
                mediaId = mediaId,
                uri = uri,
                mimeType = item.localConfiguration?.mimeType,
                title = meta.title?.toString(),
                artist = meta.artist?.toString(),
                albumTitle = meta.albumTitle?.toString(),
                artworkUri = meta.artworkUri?.toString(),
                albumId = albumId,
                trackId = trackId,
                rjCode = rjCode
            )
        }
        if (persistedQueue.isEmpty()) return

        val index0 = c.currentMediaItemIndex
        val index = if (index0 in persistedQueue.indices) index0 else 0
        val speed = c.playbackParameters.speed.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
        val pitch = c.playbackParameters.pitch.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f

        playbackStateStore.save(
            PersistedPlaybackStateV2(
                queue = persistedQueue,
                currentIndex = index,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                playWhenReady = c.playWhenReady,
                repeatMode = c.repeatMode,
                shuffleEnabled = c.shuffleModeEnabled,
                speed = speed,
                pitch = pitch,
                savedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    fun getControllerOrNull(): MediaController? = controller

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val duration = c.duration.coerceAtLeast(0L)
        val target = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        val bounded = if (duration > 0L) target.coerceAtMost(duration) else target
        c.seekTo(bounded)
    }

    fun seekToQueueIndex(index: Int) {
        val c = controller ?: return
        val safe = index.coerceIn(0, (c.mediaItemCount - 1).coerceAtLeast(0))
        c.seekToDefaultPosition(safe)
        c.play()
    }

    fun skipToNext() {
        controller?.seekToNext()
    }

    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    fun setRepeatMode(repeatMode: Int) {
        controller?.repeatMode = repeatMode
    }

    fun setShuffleEnabled(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun setPlaybackPitch(pitch: Float) {
        val c = controller ?: return
        val cur = c.playbackParameters
        c.setPlaybackParameters(PlaybackParameters(cur.speed, pitch.coerceIn(0.5f, 2f)))
    }

    fun setPlaybackParameters(speed: Float, pitch: Float) {
        val c = controller ?: return
        c.setPlaybackParameters(
            PlaybackParameters(
                speed.coerceIn(0.5f, 2f),
                pitch.coerceIn(0.5f, 2f)
            )
        )
    }

    fun setQueue(items: List<androidx.media3.common.MediaItem>, startIndex: Int, playWhenReady: Boolean) {
        setQueue(items = items, startIndex = startIndex, startPositionMs = 0L, playWhenReady = playWhenReady)
    }

    fun setQueue(
        items: List<androidx.media3.common.MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
        playWhenReady: Boolean
    ) {
        val c = controller ?: return
        c.setMediaItems(items, startIndex.coerceAtLeast(0), startPositionMs.coerceAtLeast(0L))
        c.prepare()
        if (playWhenReady) c.play()
        _queue.value = items
    }

    fun addMediaItem(item: androidx.media3.common.MediaItem): Boolean {
        val c = controller ?: return false
        val count = c.mediaItemCount
        for (i in 0 until count) {
            if (c.getMediaItemAt(i).mediaId == item.mediaId) {
                // Already exists, do not add
                return false
            }
        }
        c.addMediaItem(item)
        return true
    }

    fun removeMediaItem(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun sendCustomCommand(action: String, args: android.os.Bundle) {
        val c = controller ?: return
        val cmd = androidx.media3.session.SessionCommand(action, android.os.Bundle.EMPTY)
        c.sendCustomCommand(cmd, args)
    }

    private fun updateQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).map { idx -> c.getMediaItemAt(idx) }
    }

    private fun applySliceLoopIfNeeded(c: MediaController) {
        val preview = slicePlaybackController.previewSlice.value
        val action = sliceLoopEngine.decide(
            nowElapsedMs = SystemClock.elapsedRealtime(),
            input = SliceLoopInput(
                sliceModeEnabled = slicePlaybackController.sliceModeEnabled.value,
                userScrubbing = slicePlaybackController.userScrubbing.value,
                mediaId = c.currentMediaItem?.mediaId,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                durationMs = c.duration.coerceAtLeast(0L),
                repeatMode = c.repeatMode,
                previewSlice = preview,
                slices = currentSlices.value
            )
        )
        when (action) {
            is SliceLoopAction.SeekTo -> c.seekTo(action.positionMs)
            SliceLoopAction.SkipToNext -> c.seekToNext()
            SliceLoopAction.PauseAndClearPreview -> {
                c.pause()
                slicePlaybackController.clearPreview()
            }
            SliceLoopAction.None -> Unit
        }
    }
}

private fun applyPlayModeToController(controller: MediaController, mode: Int) {
    when (mode) {
        1 -> {
            controller.repeatMode = Player.REPEAT_MODE_ONE
            controller.shuffleModeEnabled = false
        }
        2 -> {
            controller.repeatMode = Player.REPEAT_MODE_ALL
            controller.shuffleModeEnabled = true
        }
        else -> {
            controller.repeatMode = Player.REPEAT_MODE_ALL
            controller.shuffleModeEnabled = false
        }
    }
}

private fun Player.toSnapshot(isConnected: Boolean, audioSessionId: Int): PlaybackSnapshot {
    return PlaybackSnapshot(
        isConnected = isConnected,
        isPlaying = isPlaying,
        playbackState = playbackState,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleModeEnabled,
        playbackSpeed = playbackParameters.speed,
        playbackPitch = playbackParameters.pitch,
        currentMediaItem = currentMediaItem,
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = duration.coerceAtLeast(0L),
        audioSessionId = audioSessionId
    )
}

private data class PlaybackStateKey(
    val currentMediaId: String,
    val isPlaying: Boolean,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val speed: Float,
    val pitch: Float
)

private suspend fun awaitMediaController(
    context: Context,
    future: com.google.common.util.concurrent.ListenableFuture<MediaController>
): MediaController {
    return suspendCancellableCoroutine { cont ->
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: CancellationException) {
                    cont.cancel(e)
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        cont.invokeOnCancellation { future.cancel(true) }
    }
}

private suspend fun awaitSessionResult(
    context: Context,
    future: com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.SessionResult>
): androidx.media3.session.SessionResult {
    return suspendCancellableCoroutine { cont ->
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        cont.invokeOnCancellation { future.cancel(true) }
    }
}
