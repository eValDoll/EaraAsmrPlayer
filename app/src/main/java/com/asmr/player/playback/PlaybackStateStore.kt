package com.asmr.player.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.asmr.player.data.lyrics.EXTRA_REMOTE_SUBTITLE_SOURCES_JSON
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackStateDataStore by preferencesDataStore(name = "playback_state")

private data class LegacyMediaIdPlaybackState(
    val queueMediaIds: List<String>,
    val currentIndex: Int,
    val positionMs: Long,
    val playWhenReady: Boolean,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val speed: Float,
    val pitch: Float,
    val savedAtEpochMs: Long
)

data class PersistedPlaybackQueueItem(
    val mediaId: String,
    val uri: String,
    val mimeType: String?,
    val title: String?,
    val artist: String?,
    val albumTitle: String?,
    val artworkUri: String?,
    val albumId: Long?,
    val trackId: Long?,
    val rjCode: String?,
    val remoteSubtitleSources: List<PersistedRemoteSubtitleSource> = emptyList(),
    val albumCv: String? = null,
    val durationMs: Long? = null
)

data class PersistedRemoteSubtitleSource(
    val url: String,
    val language: String?,
    val ext: String?
)

data class PersistedPlaybackState(
    val queue: List<PersistedPlaybackQueueItem>,
    val currentIndex: Int,
    val positionMs: Long,
    val playWhenReady: Boolean,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val speed: Float,
    val pitch: Float,
    val savedAtEpochMs: Long
)

internal fun capturePersistedPlaybackState(
    player: Player,
    preferredItems: List<MediaItem> = emptyList()
): PersistedPlaybackState? {
    val items = preferredItems.ifEmpty {
        (0 until player.mediaItemCount).map { index -> player.getMediaItemAt(index) }
    }
    if (items.isEmpty()) return null

    val persistedQueue = items.mapNotNull { item ->
        val mediaId = item.mediaId.trim()
        if (mediaId.isBlank()) return@mapNotNull null
        val metadata = item.mediaMetadata
        val extras = metadata.extras
        PersistedPlaybackQueueItem(
            mediaId = mediaId,
            uri = item.localConfiguration?.uri?.toString().orEmpty().trim().ifBlank { mediaId },
            mimeType = item.localConfiguration?.mimeType,
            title = metadata.title?.toString(),
            artist = metadata.artist?.toString(),
            albumCv = extras?.getString(EXTRA_ALBUM_CV),
            durationMs = metadata.durationMs,
            albumTitle = metadata.albumTitle?.toString(),
            artworkUri = metadata.artworkUri?.toString(),
            albumId = extras?.takeIf { it.containsKey("album_id") }?.getLong("album_id"),
            trackId = extras?.takeIf { it.containsKey("track_id") }?.getLong("track_id"),
            rjCode = extras?.takeIf { it.containsKey("rj_code") }?.getString("rj_code"),
            remoteSubtitleSources = decodePersistedRemoteSubtitleSources(
                extras?.getString(EXTRA_REMOTE_SUBTITLE_SOURCES_JSON)
            )
        )
    }
    if (persistedQueue.isEmpty()) return null

    val currentIndex = player.currentMediaItemIndex
        .takeIf { it in persistedQueue.indices }
        ?: 0
    val speed = player.playbackParameters.speed.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
    val pitch = player.playbackParameters.pitch.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
    return PersistedPlaybackState(
        queue = persistedQueue,
        currentIndex = currentIndex,
        positionMs = player.currentPosition.coerceAtLeast(0L),
        playWhenReady = player.playWhenReady,
        repeatMode = player.repeatMode,
        shuffleEnabled = player.shuffleModeEnabled,
        speed = speed,
        pitch = pitch,
        savedAtEpochMs = System.currentTimeMillis()
    )
}

private fun decodePersistedRemoteSubtitleSources(raw: String?): List<PersistedRemoteSubtitleSource> {
    val trimmed = raw.orEmpty().trim()
    if (trimmed.isBlank()) return emptyList()
    return trimmed.split('\n').mapNotNull { line ->
        val parts = line.split('\t')
        val url = parts.getOrNull(0).orEmpty().trim()
        if (url.isBlank()) return@mapNotNull null
        PersistedRemoteSubtitleSource(
            url = url,
            language = parts.getOrNull(1)?.trim().orEmpty().ifBlank { "default" },
            ext = parts.getOrNull(2)?.trim().orEmpty().ifBlank { url.substringAfterLast('.', "vtt") }
        )
    }
}

@Singleton
class PlaybackStateStore internal constructor(
    private val dataStore: DataStore<Preferences>
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.playbackStateDataStore)

    private val gson: Gson = Gson()
    private val legacyStateKey: Preferences.Key<String> = stringPreferencesKey("persisted_playback_state_v1")
    private val stateKey: Preferences.Key<String> = stringPreferencesKey("persisted_playback_state_v2")

    suspend fun save(state: PersistedPlaybackState) {
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[stateKey] = gson.toJson(state)
                prefs.remove(legacyStateKey)
            }
        }
    }

    suspend fun load(): PersistedPlaybackState? {
        return withContext(Dispatchers.IO) {
            val prefs = dataStore.data.first()
            val stateJson = prefs[stateKey].orEmpty()
            if (stateJson.isNotBlank()) {
                return@withContext runCatching {
                    gson.fromJson(stateJson, PersistedPlaybackState::class.java)
                }.getOrNull()
            }

            val legacyStateJson = prefs[legacyStateKey].orEmpty()
            if (legacyStateJson.isBlank()) return@withContext null

            val legacyState = runCatching {
                gson.fromJson(legacyStateJson, LegacyMediaIdPlaybackState::class.java)
            }.getOrNull() ?: return@withContext null

            PersistedPlaybackState(
                queue = legacyState.queueMediaIds.mapNotNull { id ->
                    val trimmed = id.trim()
                    if (trimmed.isBlank()) return@mapNotNull null
                    PersistedPlaybackQueueItem(
                        mediaId = trimmed,
                        uri = trimmed,
                        mimeType = null,
                        title = null,
                        artist = null,
                        albumTitle = null,
                        artworkUri = null,
                        albumId = null,
                        trackId = null,
                        rjCode = null,
                        remoteSubtitleSources = emptyList()
                    )
                },
                currentIndex = legacyState.currentIndex,
                positionMs = legacyState.positionMs,
                playWhenReady = legacyState.playWhenReady,
                repeatMode = legacyState.repeatMode,
                shuffleEnabled = legacyState.shuffleEnabled,
                speed = legacyState.speed,
                pitch = legacyState.pitch,
                savedAtEpochMs = legacyState.savedAtEpochMs
            )
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs.remove(stateKey)
                prefs.remove(legacyStateKey)
            }
        }
    }
}
