package com.asmr.player.playback

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.google.gson.Gson
import com.google.gson.JsonNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [34])
class PlaybackQueueRestorerTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var stateStore: PlaybackStateStore
    private lateinit var restorer: PlaybackQueueRestorer
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        stateStore = PlaybackStateStore(InMemoryPreferencesDataStore())
        restorer = PlaybackQueueRestorer(context, database.trackDao(), database.albumDao(), stateStore)
    }

    @After
    fun tearDown() = runBlocking {
        stateStore.clear()
        database.close()
        files.forEach { it.delete() }
    }

    @Test
    fun deletedCurrentFileIsClearedEvenWhenItsDatabaseRowStillExists() = runBlocking {
        val file = localFile()
        val albumId = database.albumDao().insertAlbum(AlbumEntity(title = "本地作品", path = file.parent!!))
        val trackId = database.trackDao().insertTrack(
            TrackEntity(albumId = albumId, title = "音频", path = file.absolutePath)
        )
        val entry = item(file.absolutePath).copy(trackId = trackId, albumId = albumId)
        stateStore.save(state(listOf(entry), positionMs = 5_000L))
        assertNotNull(restorer.restore())

        assertTrue(file.delete())
        assertNull(restorer.restore())
        assertNull(stateStore.load())
        assertNull(restorer.restore())
        assertNotNull(database.trackDao().getTrackByIdOnce(trackId))
    }

    @Test
    fun deletionBeforeCurrentTrackKeepsTheSameTrackAndPosition() = runBlocking {
        val missing = localFile().also { assertTrue(it.delete()) }
        val first = item(localFile().absolutePath)
        val current = item(localFile().absolutePath)
        stateStore.save(state(listOf(item(missing.absolutePath), first, current), 2, 9_876L))

        val restored = requireNotNull(restorer.restore())
        assertEquals(listOf(first.mediaId, current.mediaId), restored.items.map { it.mediaId })
        assertEquals(1, restored.state.currentIndex)
        assertEquals(9_876L, restored.state.positionMs)
        assertEquals(restored.state, stateStore.load())
        assertEquals(restored.state, restorer.restore()?.state)
    }

    @Test
    fun deletionOfCurrentTrackSelectsNextTrackWithoutReusingTheDeletedTracksPosition() = runBlocking {
        val first = item(localFile().absolutePath)
        val missing = localFile().also { assertTrue(it.delete()) }
        val next = item(localFile().absolutePath)
        stateStore.save(state(listOf(first, item(missing.absolutePath), next), 1, 90_000L))

        val restored = requireNotNull(restorer.restore())
        assertEquals(next.mediaId, restored.items[restored.state.currentIndex].mediaId)
        assertEquals(0L, restored.state.positionMs)
        assertEquals(restored.state, stateStore.load())
    }

    @Test
    fun deletionOfLastTrackFallsBackToLastSurvivorAtItsStart() = runBlocking {
        val first = item(localFile().absolutePath)
        val missing = localFile().also { assertTrue(it.delete()) }
        stateStore.save(state(listOf(first, item(missing.absolutePath)), 1, 90_000L))

        val restored = requireNotNull(restorer.restore())
        assertEquals(listOf(first.mediaId), restored.items.map { it.mediaId })
        assertEquals(0, restored.state.currentIndex)
        assertEquals(0L, restored.state.positionMs)
    }

    @Test
    fun duplicateTracksKeepTheSelectedOccurrenceAfterPruning() = runBlocking {
        val current = item(localFile().absolutePath)
        val missing = localFile().also { assertTrue(it.delete()) }
        stateStore.save(state(listOf(current, item(missing.absolutePath), current), 2, 1_234L))

        val restored = requireNotNull(restorer.restore())
        assertEquals(2, restored.items.size)
        assertEquals(1, restored.state.currentIndex)
        assertEquals(1_234L, restored.state.positionMs)
    }

    @Test
    fun invalidEntriesKeepOriginalIndicesAndAnEmptyQueueIsCleared() = runBlocking {
        val current = item(localFile().absolutePath)
        stateStore.save(state(listOf(item(""), current), 1, 987L))
        val restored = requireNotNull(restorer.restore())
        assertEquals(0, restored.state.currentIndex)
        assertEquals(987L, restored.state.positionMs)

        val gson = Gson()
        val json = gson.toJsonTree(state(listOf(item(""), current), 1, 987L)).asJsonObject
        json.getAsJsonArray("queue").set(0, JsonNull.INSTANCE)
        stateStore.save(gson.fromJson(json, PersistedPlaybackStateV2::class.java))
        assertEquals(987L, restorer.restore()?.state?.positionMs)

        stateStore.save(state(emptyList(), -1))
        assertNull(restorer.restore())
        assertNull(stateStore.load())
    }

    @Test
    fun fileUrisAndStandaloneMediaIdsUseTheActualPlaybackSource() = runBlocking {
        val file = localFile()
        val entry = item("standalone-id").copy(uri = Uri.fromFile(file).toString())
        stateStore.save(state(listOf(entry)))
        assertEquals(entry.uri, restorer.restore()?.items?.single()?.localConfiguration?.uri.toString())

        assertTrue(file.delete())
        assertNull(restorer.restore())
        assertNull(stateStore.load())
    }

    @Test
    fun remoteTracksRestoreWithoutNetworkAccessAndPreserveMetadata() = runBlocking {
        val entry = item("https://127.0.0.1:1/audio.mp3").copy(
            albumCv = "测试 CV",
            durationMs = 123_456L,
            remoteSubtitleSources = listOf(PersistedRemoteSubtitleSource("https://example.com/a.lrc", "ja", "lrc")),
        )
        stateStore.save(state(listOf(entry), positionMs = 2_000L))

        val restored = requireNotNull(restorer.restore())
        assertEquals(entry, restored.state.queue.single())
        assertEquals("测试 CV", restored.items.single().mediaMetadata.extras?.getString(EXTRA_ALBUM_CV))
        assertEquals(123_456L, restored.items.single().mediaMetadata.durationMs)
        assertFalse(restored.state.playWhenReady)
        assertEquals(Player.REPEAT_MODE_ONE, restored.state.repeatMode)
        assertTrue(restored.state.shuffleEnabled)
        assertEquals(1.25f, restored.state.speed)
        assertEquals(0.9f, restored.state.pitch)
    }

    @Test
    fun deletedDatabaseEntriesAreStillPrunedWithoutAffectingSurvivorSelection() = runBlocking {
        val first = item(localFile().absolutePath).copy(trackId = 999L)
        val second = item(localFile().absolutePath).copy(albumId = 999L)
        val current = item(localFile().absolutePath)
        stateStore.save(state(listOf(first, second, current), 2, 432L))

        val restored = requireNotNull(restorer.restore())
        assertEquals(listOf(current.mediaId), restored.items.map { it.mediaId })
        assertEquals(0, restored.state.currentIndex)
        assertEquals(432L, restored.state.positionMs)
    }

    @Test
    fun safFailuresAreFilteredOnIoAndPermissionOrProviderFailuresAreRetained() = runBlocking {
        val provider = SourceProvider()
        ShadowContentResolver.registerProviderInternal("restore-test", provider)
        val sources = listOf(
            "content://restore-test/tree/root/document/root%2Fmissing.wav",
            "content://restore-test/document/empty.wav",
            "content://restore-test/document/existing.wav",
            "content://restore-test/audio/denied.wav",
            "content://restore-test/audio/unavailable.wav",
            "content://restore-test/audio/null.wav",
        )
        stateStore.save(state(sources.map(::item), 2, 7_000L))

        val restored = requireNotNull(restorer.restore())
        assertEquals(sources.drop(2), restored.items.map { it.mediaId })
        assertEquals(0, restored.state.currentIndex)
        assertEquals(7_000L, restored.state.positionMs)
        assertFalse(provider.queriedOnMainThread)
        assertEquals(sources, provider.queriedUris)
        assertEquals(restored.state, stateStore.load())
    }

    @Test
    fun duplicateSourcesAreOnlyQueriedOncePerRestore() = runBlocking {
        val provider = SourceProvider()
        ShadowContentResolver.registerProviderInternal("restore-test", provider)
        val source = "content://restore-test/audio/existing.wav"
        stateStore.save(state(List(4) { item(source) }, 3, 4_321L))

        val restored = requireNotNull(restorer.restore())
        assertEquals(4, restored.items.size)
        assertEquals(3, restored.state.currentIndex)
        assertEquals(listOf(source), provider.queriedUris)
    }

    @Test
    fun cancelledProviderQueryDoesNotRewriteTheSavedQueue() = runBlocking {
        ShadowContentResolver.registerProviderInternal("restore-test", SourceProvider())
        val saved = state(listOf(item("content://restore-test/audio/cancelled.wav")))
        stateStore.save(saved)
        val result = runCatching { restorer.restore() }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(saved, stateStore.load())
    }

    private fun localFile(): File = File.createTempFile("restore-", ".wav", context.cacheDir).also {
        files += it
    }

    private fun item(source: String) = PersistedPlaybackQueueItem(
        mediaId = source, uri = source, mimeType = "audio/wav", title = "音频", artist = "社团",
        albumTitle = "作品", artworkUri = null, albumId = null, trackId = null, rjCode = null,
    )

    private fun state(queue: List<PersistedPlaybackQueueItem>, index: Int = 0, positionMs: Long = 0L) =
        PersistedPlaybackStateV2(
            queue, index, positionMs, true, Player.REPEAT_MODE_ONE, true, 1.25f, 0.9f, 123L
        )

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val mutex = Mutex()
        override val data = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock { transform(state.value).also { state.value = it } }
    }

    private class SourceProvider : ContentProvider() {
        val queriedUris = mutableListOf<String>()
        var queriedOnMainThread = false

        override fun onCreate() = true
        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?,
        ): Cursor? {
            queriedUris += uri.toString()
            queriedOnMainThread = queriedOnMainThread || Thread.currentThread() == Looper.getMainLooper().thread
            val name = uri.lastPathSegment.orEmpty()
            return when {
                name.endsWith("missing.wav") -> throw IllegalArgumentException(
                    "Failed to determine child: java.io.FileNotFoundException: Missing file for $name"
                )
                name == "denied.wav" -> throw SecurityException("permission denied")
                name == "unavailable.wav" -> throw IllegalStateException("provider unavailable")
                name == "cancelled.wav" -> throw CancellationException("cancelled")
                name == "null.wav" -> null
                else -> MatrixCursor(requireNotNull(projection)).apply {
                    if (name != "empty.wav") addRow(arrayOf(name))
                }
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
