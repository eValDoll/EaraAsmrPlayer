package com.asmr.player.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
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
import com.asmr.player.data.lyrics.EXTRA_REMOTE_SUBTITLE_SOURCES_JSON
import com.asmr.player.data.repository.TrackSliceRepository
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.service.PlaybackService
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.util.MessageManager
import com.asmr.player.playback.AppVolume
import dagger.hilt.android.qualifiers.ApplicationContext
import com.asmr.player.util.NetworkMeteredChecker
import com.asmr.player.util.RemoteSubtitleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton
import com.asmr.player.domain.model.Slice

data class QueueAddSummary(
    val addedCount: Int,
    val skippedCount: Int
) {
    val totalCount: Int get() = addedCount + skippedCount
}

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
    private val _lyricsReloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val lyricsReloadRequests: SharedFlow<Unit> = _lyricsReloadRequests.asSharedFlow()

    private val _queue = MutableStateFlow<List<androidx.media3.common.MediaItem>>(emptyList())
    val queue: StateFlow<List<androidx.media3.common.MediaItem>> = _queue.asStateFlow()

    private var controller: MediaController? = null
    private var lastErrorAtMs: Long = 0L
    private var lastErrorKey: String = ""
    private val sliceLoopEngine = SliceLoopEngine()
    private val currentSlices = MutableStateFlow<List<Slice>>(emptyList())
    private var didRestorePlaybackState: Boolean = false
    private var restoreAttemptResolved: Boolean = false
    private val meteredWarnedMediaIds = LinkedHashSet<String>()
    private var lastMeteredWarnAtMs: Long = 0L
    private val connectMutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var videoOutputEnabled: Boolean = false
    private val removedAlbumIds = linkedSetOf<Long>()
    private val removedMediaIds = linkedSetOf<String>()
    @Volatile private var reconnecting = false

    val appVolumePercent: StateFlow<Int> = settingsRepository.appVolumePercent
        .stateIn(scope, SharingStarted.Eagerly, AppVolume.DefaultPercent)

    init {
        scheduleReconnect(delayMs = 0L)
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
                if (controller != null) {
                    awaitFrameCommit()
                }
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

                    updateSnapshotIfChanged(
                        positionMs = c.currentPosition.coerceAtLeast(0L),
                        durationMs = duration,
                        audioSessionId = sessionId,
                        playbackSpeed = c.playbackParameters.speed,
                        playbackPitch = c.playbackParameters.pitch
                    )

                    applySliceLoopIfNeeded(c)
                }
                delay(resolvePositionPollIntervalMs())
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
        connectMutex.withLock {
            if (!PlaybackConnectionLifecycle.canConnect() || controller != null) return
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token)
                .setListener(
                    object : MediaController.Listener {
                        override fun onDisconnected(controller: MediaController) {
                            if (this@PlayerConnection.controller !== controller) return
                            this@PlayerConnection.controller = null
                            _snapshot.value = _snapshot.value.copy(
                                isConnected = false,
                                startupRestoreResolved = restoreAttemptResolved,
                                isPlaying = false
                            )
                            if (PlaybackConnectionLifecycle.canConnect()) {
                                _queue.value = emptyList()
                                scheduleReconnect()
                            }
                        }
                    }
                )
                .buildAsync()
            val c = awaitMediaController(context, future)
            if (!PlaybackConnectionLifecycle.canConnect()) {
                c.release()
                return
            }
            controller = c
            sendVideoOutputEnabled(c, videoOutputEnabled)
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
                        _snapshot.value = player.toSnapshot(
                            isConnected = true,
                            audioSessionId = prev.audioSessionId,
                            startupRestoreResolved = restoreAttemptResolved
                        )
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
            val restored = restorePlaybackStateIfNeeded(c)
            restoreAttemptResolved = true
            if (!restored) {
                val mode = runCatching { settingsRepository.playMode.first() }.getOrDefault(0)
                applyPlayModeToController(c, mode)
            }
            updateQueue()
            pruneRemovedMediaItems(c)
            _snapshot.value = c.toSnapshot(
                isConnected = true,
                audioSessionId = _snapshot.value.audioSessionId,
                startupRestoreResolved = restoreAttemptResolved
            )
        
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
    }

    private fun scheduleReconnect(delayMs: Long = 150L) {
        if (!PlaybackConnectionLifecycle.canConnect() || controller != null || reconnecting) return
        reconnecting = true
        scope.launch {
            if (delayMs > 0L) delay(delayMs)
            if (!PlaybackConnectionLifecycle.canConnect() || controller != null) {
                reconnecting = false
                return@launch
            }
            val failure = runCatching { connect() }.exceptionOrNull()
            reconnecting = false
            if (failure != null && PlaybackConnectionLifecycle.canConnect()) {
                android.util.Log.w("PlayerConnection", "Failed to reconnect controller", failure)
                delay(1_000L)
                scheduleReconnect(delayMs = 0L)
            }
        }
    }

    fun resumeAfterAppOpen() {
        if (PlaybackConnectionLifecycle.markAppOpened()) {
            didRestorePlaybackState = false
            restoreAttemptResolved = false
            _snapshot.value = _snapshot.value.copy(startupRestoreResolved = false)
        }
        scheduleReconnect(delayMs = 0L)
    }

    private suspend fun restorePlaybackStateIfNeeded(c: MediaController): Boolean {
        if (didRestorePlaybackState) return false
        if (c.mediaItemCount > 0) return false
        didRestorePlaybackState = true

        val saved = playbackStateStore.load() ?: return false
        val persisted = runCatching { saved.queue }.getOrNull().orEmpty()
            .mapNotNull { item ->
                val mediaId = runCatching { item.mediaId }.getOrNull().orEmpty().trim()
                if (mediaId.isBlank()) return@mapNotNull null
                val uri = runCatching { item.uri }.getOrNull().orEmpty().trim()
                val remoteSubtitleSources = runCatching { item.remoteSubtitleSources }.getOrNull().orEmpty()
                    .mapNotNull subtitleSource@{ sourceItem ->
                        val url = runCatching { sourceItem.url }.getOrNull().orEmpty().trim()
                        if (url.isBlank()) return@subtitleSource null
                        PersistedRemoteSubtitleSource(
                            url = url,
                            language = sourceItem.language,
                            ext = sourceItem.ext
                        )
                    }
                PersistedPlaybackQueueItem(
                    mediaId = mediaId,
                    uri = uri,
                    mimeType = item.mimeType,
                    title = item.title,
                    artist = item.artist,
                    albumTitle = item.albumTitle,
                    artworkUri = item.artworkUri,
                    albumId = item.albumId,
                    trackId = item.trackId,
                    rjCode = item.rjCode,
                    remoteSubtitleSources = remoteSubtitleSources
                )
            }
        if (persisted.isEmpty()) {
            playbackStateStore.clear()
            return false
        }
        val items = buildMediaItemsFromPersistedItems(persisted)
        if (items.isEmpty()) {
            playbackStateStore.clear()
            return false
        }

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
        _snapshot.value = c.toSnapshot(
            isConnected = true,
            audioSessionId = _snapshot.value.audioSessionId,
            startupRestoreResolved = restoreAttemptResolved
        )
        return true
    }

    private suspend fun buildMediaItemsFromPersistedItems(items: List<PersistedPlaybackQueueItem>): List<MediaItem> {
        return items.mapNotNull { persisted ->
            val id = persisted.mediaId.trim()
            if (id.isBlank()) return@mapNotNull null
            val track = runCatching { trackDao.getTrackByPathOnce(id) }.getOrNull()
            if (track == null && persisted.trackId?.let { it > 0L } == true) {
                return@mapNotNull null
            }
            val persistedAlbumId = persisted.albumId
            if (track == null && persistedAlbumId != null && persistedAlbumId > 0L) {
                val albumExists = runCatching {
                    albumDao.getAlbumById(persistedAlbumId) != null
                }.getOrDefault(true)
                if (!albumExists) return@mapNotNull null
            }
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
                    group = track.group,
                    lyricsRelativePathNoExt = "",
                    remoteSubtitleSources = persisted.remoteSubtitleSources.mapNotNull subtitleSource@{ persistedSource ->
                        val url = persistedSource.url.trim()
                        if (url.isBlank()) return@subtitleSource null
                        RemoteSubtitleSource(
                            url = url,
                            language = persistedSource.language.orEmpty().ifBlank { "default" },
                            ext = persistedSource.ext.orEmpty().ifBlank { url.substringAfterLast('.', "vtt") }
                        )
                    }
                )
                MediaItemFactory.fromTrack(album, t)
            } else {
                val restoredRemoteSubtitleSources = persisted.remoteSubtitleSources.mapNotNull subtitleSource@{ persistedSource ->
                    val url = persistedSource.url.trim()
                    if (url.isBlank()) return@subtitleSource null
                    RemoteSubtitleSource(
                        url = url,
                        language = persistedSource.language.orEmpty().ifBlank { "default" },
                        ext = persistedSource.ext.orEmpty().ifBlank { url.substringAfterLast('.', "vtt") }
                    )
                }
                val uri = MediaItemFactory.toPlayableUri(persisted.uri.ifBlank { id })
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
                            encodeRemoteSubtitleSources(restoredRemoteSubtitleSources)?.let { encoded ->
                                putString(EXTRA_REMOTE_SUBTITLE_SOURCES_JSON, encoded)
                            }
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

    private fun encodeRemoteSubtitleSources(sources: List<RemoteSubtitleSource>): String? {
        val normalized = sources.mapNotNull { source ->
            val url = source.url.trim()
            if (url.isBlank()) return@mapNotNull null
            val language = source.language.trim().ifBlank { "default" }
            val ext = source.ext.trim().ifBlank { url.substringAfterLast('.', "vtt") }
            listOf(url, language, ext).joinToString("\t")
        }
        if (normalized.isEmpty()) return null
        return normalized.joinToString("\n")
    }

    private suspend fun savePlaybackState() {
        val c = controller ?: return
        val state = capturePersistedPlaybackState(c, _queue.value)
        if (state == null) {
            playbackStateStore.clear()
            return
        }
        playbackStateStore.save(state)
    }

    fun getControllerOrNull(): MediaController? = controller

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
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
        return addMediaItems(listOf(item)).addedCount > 0
    }

    fun addMediaItems(items: List<androidx.media3.common.MediaItem>): QueueAddSummary {
        val c = controller ?: return QueueAddSummary(addedCount = 0, skippedCount = items.size)
        if (items.isEmpty()) return QueueAddSummary(addedCount = 0, skippedCount = 0)

        val existingIds = mutableSetOf<String>()
        for (i in 0 until c.mediaItemCount) {
            existingIds += c.getMediaItemAt(i).mediaId
        }

        val seenIds = linkedSetOf<String>()
        val toAdd = mutableListOf<androidx.media3.common.MediaItem>()
        var skipped = 0
        items.forEach { item ->
            val mediaId = item.mediaId
            if (mediaId.isBlank() || !existingIds.add(mediaId) || !seenIds.add(mediaId)) {
                skipped += 1
            } else {
                toAdd += item
            }
        }

        if (toAdd.isNotEmpty()) {
            c.addMediaItems(toAdd)
        }
        return QueueAddSummary(
            addedCount = toAdd.size,
            skippedCount = skipped
        )
    }

    fun removeMediaItem(index: Int) {
        controller?.removeMediaItem(index)
    }

    fun removeMediaItemsForAlbum(albumId: Long, mediaIds: Set<String>) {
        if (albumId <= 0L && mediaIds.isEmpty()) return
        scope.launch {
            if (albumId > 0L) removedAlbumIds += albumId
            removedMediaIds += mediaIds.map(String::trim).filter(String::isNotBlank)
            controller?.let(::pruneRemovedMediaItems)
        }
    }

    private fun pruneRemovedMediaItems(controller: MediaController) {
        var removedAny = false
        for (index in controller.mediaItemCount - 1 downTo 0) {
            if (
                shouldRemovePlaybackItem(
                    item = controller.getMediaItemAt(index),
                    removedAlbumIds = removedAlbumIds,
                    removedMediaIds = removedMediaIds,
                )
            ) {
                controller.removeMediaItem(index)
                removedAny = true
            }
        }
        if (!removedAny) return
        if (controller.mediaItemCount == 0) controller.pause()
        updateQueue()
        _snapshot.value = controller.toSnapshot(
            isConnected = true,
            audioSessionId = _snapshot.value.audioSessionId,
            startupRestoreResolved = restoreAttemptResolved,
        )
    }

    fun sendCustomCommand(action: String, args: android.os.Bundle) {
        scope.launch {
            val c = controller ?: return@launch
            val cmd = androidx.media3.session.SessionCommand(action, android.os.Bundle.EMPTY)
            c.sendCustomCommand(cmd, args)
        }
    }

    fun requestLyricsReload() {
        _lyricsReloadRequests.tryEmit(Unit)
        sendCustomCommand("RELOAD_LYRICS", android.os.Bundle.EMPTY)
    }

    fun setVideoOutputEnabled(enabled: Boolean) {
        if (videoOutputEnabled == enabled) return
        videoOutputEnabled = enabled
        controller?.let { sendVideoOutputEnabled(it, enabled) }
    }

    private fun sendVideoOutputEnabled(controller: MediaController, enabled: Boolean) {
        val command = androidx.media3.session.SessionCommand(
            "SET_VIDEO_OUTPUT_ENABLED",
            android.os.Bundle.EMPTY
        )
        controller.sendCustomCommand(
            command,
            android.os.Bundle().apply { putBoolean("enabled", enabled) }
        )
    }

    fun setAppVolumePercent(percent: Int) {
        scope.launch {
            settingsRepository.setAppVolumePercent(percent)
        }
    }

    fun adjustAppVolumePercent(deltaPercent: Int) {
        scope.launch {
            settingsRepository.adjustAppVolumePercent(deltaPercent)
        }
    }

    private fun updateQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).map { idx -> c.getMediaItemAt(idx) }
    }

    private fun updateSnapshotIfChanged(
        positionMs: Long,
        durationMs: Long,
        audioSessionId: Int,
        playbackSpeed: Float,
        playbackPitch: Float
    ) {
        val current = _snapshot.value
        if (
            current.positionMs == positionMs &&
            current.durationMs == durationMs &&
            current.audioSessionId == audioSessionId &&
            current.playbackSpeed == playbackSpeed &&
            current.playbackPitch == playbackPitch
        ) {
            return
        }

        _snapshot.value = current.copy(
            positionMs = positionMs,
            durationMs = durationMs,
            audioSessionId = audioSessionId,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch
        )
    }

    private fun resolvePositionPollIntervalMs(): Long {
        val snapshot = _snapshot.value
        if (slicePlaybackController.sliceModeEnabled.value || slicePlaybackController.previewSlice.value != null) {
            return 250L
        }
        return if (snapshot.currentMediaItem.isVideoMediaItem() && !videoOutputEnabled) {
            1_000L
        } else {
            250L
        }
    }

    /**
     * 播放位置轮询会更新多个 Compose 状态。把这段工作放到当前帧提交之后，避免固定 250ms
     * 定时器随机插进测量、绘制或 RenderThread 同步阶段；轮询频率和进度显示行为保持不变。
     */
    private suspend fun awaitFrameCommit() {
        suspendCancellableCoroutine { continuation ->
            val choreographer = Choreographer.getInstance()
            val commitAction = Runnable {
                if (continuation.isActive) continuation.resume(Unit)
            }
            val frameCallback = Choreographer.FrameCallback {
                // Choreographer 的帧回调先于 traversal；投递到主消息队列后会在整个
                // doFrame（包括绘制与提交）返回之后执行。
                mainHandler.post(commitAction)
            }
            choreographer.postFrameCallback(frameCallback)
            continuation.invokeOnCancellation {
                choreographer.removeFrameCallback(frameCallback)
                mainHandler.removeCallbacks(commitAction)
            }
        }
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

private fun Player.toSnapshot(
    isConnected: Boolean,
    audioSessionId: Int,
    startupRestoreResolved: Boolean
): PlaybackSnapshot {
    return PlaybackSnapshot(
        isConnected = isConnected,
        startupRestoreResolved = startupRestoreResolved,
        isPlaying = isPlaying,
        playWhenReady = playWhenReady,
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

private fun MediaItem?.isVideoMediaItem(): Boolean {
    val item = this ?: return false
    val uriString = item.localConfiguration?.uri?.toString().orEmpty()
    val mime = item.localConfiguration?.mimeType.orEmpty()
    val ext = uriString
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('.', "")
        .lowercase()
    return item.mediaMetadata.extras?.getBoolean("is_video") == true ||
        mime.startsWith("video/") ||
        ext in setOf("mp4", "m4v", "webm", "mkv", "mov")
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
