package com.asmr.player.playback

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [34])
class PlaybackStateStoreTest {
    private val legacyKey = stringPreferencesKey("persisted_playback_state_v1")
    private val stateKey = stringPreferencesKey("persisted_playback_state_v2")
    private val legacyJson = """
        {
          "queueMediaIds": [" https://example.com/audio.mp3 ", ""],
          "currentIndex": 0, "positionMs": 4321, "playWhenReady": true,
          "repeatMode": 1, "shuffleEnabled": true, "speed": 1.25,
          "pitch": 0.9, "savedAtEpochMs": 123
        }
    """.trimIndent()

    @Test
    fun legacyMediaIdQueueRestoresAndSavesUnderTheExistingStorageKey() = runBlocking {
        val dataStore = InMemoryPreferencesDataStore(mutablePreferencesOf(legacyKey to legacyJson))
        val store = PlaybackStateStore(dataStore)

        val restored = requireNotNull(store.load())
        assertEquals(
            PersistedPlaybackState(
                queue = listOf(
                    PersistedPlaybackQueueItem(
                        mediaId = "https://example.com/audio.mp3",
                        uri = "https://example.com/audio.mp3",
                        mimeType = null, title = null, artist = null, albumTitle = null,
                        artworkUri = null, albumId = null, trackId = null, rjCode = null
                    )
                ),
                currentIndex = 0, positionMs = 4321, playWhenReady = true, repeatMode = 1,
                shuffleEnabled = true, speed = 1.25f, pitch = 0.9f, savedAtEpochMs = 123
            ),
            restored
        )

        store.save(restored)

        val saved = dataStore.data.first()
        assertNull(saved[legacyKey])
        val savedJson = Gson().fromJson(requireNotNull(saved[stateKey]), Map::class.java)
        assertEquals(
            setOf("queue", "currentIndex", "positionMs", "playWhenReady", "repeatMode",
                "shuffleEnabled", "speed", "pitch", "savedAtEpochMs"),
            savedJson.keys
        )
        assertEquals(restored, store.load())
    }

    @Test
    fun existingQueuePayloadWinsOverLegacyDataAndBothKeysAreCleared() = runBlocking {
        val currentJson = """
            {
              "queue": [{
                "mediaId": "remote-track", "uri": "https://example.com/current.mp3",
                "mimeType": "audio/mpeg", "title": "音频", "artist": "社团",
                "albumTitle": "作品", "artworkUri": "https://example.com/cover.jpg",
                "albumId": 7, "trackId": 8, "rjCode": "RJ123456",
                "remoteSubtitleSources": [{"url": "https://example.com/a.vtt", "language": "ja", "ext": "vtt"}],
                "albumCv": "测试 CV", "durationMs": 123456
              }],
              "currentIndex": 0, "positionMs": 9876, "playWhenReady": false,
              "repeatMode": 2, "shuffleEnabled": false, "speed": 1.5,
              "pitch": 1.1, "savedAtEpochMs": 456
            }
        """.trimIndent()
        val dataStore = InMemoryPreferencesDataStore(
            mutablePreferencesOf(legacyKey to legacyJson, stateKey to currentJson)
        )
        val store = PlaybackStateStore(dataStore)

        assertEquals(
            PersistedPlaybackState(
                queue = listOf(
                    PersistedPlaybackQueueItem(
                        mediaId = "remote-track", uri = "https://example.com/current.mp3",
                        mimeType = "audio/mpeg", title = "音频", artist = "社团", albumTitle = "作品",
                        artworkUri = "https://example.com/cover.jpg", albumId = 7, trackId = 8,
                        rjCode = "RJ123456",
                        remoteSubtitleSources = listOf(
                            PersistedRemoteSubtitleSource("https://example.com/a.vtt", "ja", "vtt")
                        ),
                        albumCv = "测试 CV", durationMs = 123456
                    )
                ),
                currentIndex = 0, positionMs = 9876, playWhenReady = false, repeatMode = 2,
                shuffleEnabled = false, speed = 1.5f, pitch = 1.1f, savedAtEpochMs = 456
            ),
            store.load()
        )

        store.clear()

        assertNull(dataStore.data.first()[legacyKey])
        assertNull(dataStore.data.first()[stateKey])
        assertNull(store.load())
    }

    @Test
    fun capturedPlaylistSourceSurvivesStorageWithoutMutatingTheOriginalMetadata() = runBlocking {
        val original = MediaItemFactory.fromDetails(
            mediaId = "playlist-track", uri = "https://example.com/audio.mp3", title = "音频", albumId = 12L,
        )
        val sourced = original.withSourcePlaylist(42L)
        assertNull(original.sourcePlaylistId())
        assertEquals(12L, sourced.mediaMetadata.extras?.getLong("album_id"))
        assertEquals(42L, sourced.sourcePlaylistId())
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getMediaItemCount" -> 1
                "getMediaItemAt" -> sourced
                "getCurrentMediaItemIndex" -> 0
                "getCurrentPosition" -> 4_321L
                "getPlayWhenReady" -> true
                "getRepeatMode" -> Player.REPEAT_MODE_OFF
                "getShuffleModeEnabled" -> false
                "getPlaybackParameters" -> PlaybackParameters.DEFAULT
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
        val captured = requireNotNull(capturePersistedPlaybackState(player))
        val store = PlaybackStateStore(InMemoryPreferencesDataStore(mutablePreferencesOf()))

        store.save(captured)

        val restored = requireNotNull(store.load())
        assertEquals(42L, restored.queue.single().sourcePlaylistId)
        assertEquals(4_321L, restored.positionMs)
    }

    private class InMemoryPreferencesDataStore(initial: Preferences) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        private val mutex = Mutex()
        override val data = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }
}
