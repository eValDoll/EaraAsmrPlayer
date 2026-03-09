package com.asmr.player.playback

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackStateDataStore by preferencesDataStore(name = "playback_state")

data class PersistedPlaybackStateV1(
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
    val rjCode: String?
)

data class PersistedPlaybackStateV2(
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

@Singleton
class PlaybackStateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson: Gson = Gson()
    private val keyV1: Preferences.Key<String> = stringPreferencesKey("persisted_playback_state_v1")
    private val keyV2: Preferences.Key<String> = stringPreferencesKey("persisted_playback_state_v2")

    suspend fun save(state: PersistedPlaybackStateV2) {
        withContext(Dispatchers.IO) {
            context.playbackStateDataStore.edit { prefs ->
                prefs[keyV2] = gson.toJson(state)
                prefs.remove(keyV1)
            }
        }
    }

    suspend fun load(): PersistedPlaybackStateV2? {
        return withContext(Dispatchers.IO) {
            val prefs = context.playbackStateDataStore.data.first()
            val jsonV2 = prefs[keyV2].orEmpty()
            if (jsonV2.isNotBlank()) {
                return@withContext runCatching {
                    gson.fromJson(jsonV2, PersistedPlaybackStateV2::class.java)
                }.getOrNull()
            }

            val jsonV1 = prefs[keyV1].orEmpty()
            if (jsonV1.isBlank()) return@withContext null

            val v1 = runCatching {
                gson.fromJson(jsonV1, PersistedPlaybackStateV1::class.java)
            }.getOrNull() ?: return@withContext null

            PersistedPlaybackStateV2(
                queue = v1.queueMediaIds.mapNotNull { id ->
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
                        rjCode = null
                    )
                },
                currentIndex = v1.currentIndex,
                positionMs = v1.positionMs,
                playWhenReady = v1.playWhenReady,
                repeatMode = v1.repeatMode,
                shuffleEnabled = v1.shuffleEnabled,
                speed = v1.speed,
                pitch = v1.pitch,
                savedAtEpochMs = v1.savedAtEpochMs
            )
        }
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            context.playbackStateDataStore.edit { prefs ->
                prefs.remove(keyV2)
                prefs.remove(keyV1)
            }
        }
    }
}
