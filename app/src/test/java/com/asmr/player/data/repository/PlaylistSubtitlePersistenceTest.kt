package com.asmr.player.data.repository

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.lyrics.LyricsLoader
import com.asmr.player.data.lyrics.ManualLyricsSourceRepository
import com.asmr.player.data.lyrics.lyricsTargetContextFromMediaItem
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.ui.playlists.toPlaybackEntity
import com.asmr.player.util.OnlineLyricsStore
import com.asmr.player.util.RemoteSubtitleSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PlaylistSubtitlePersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val databaseName = "playlist-subtitles-${System.nanoTime()}.db"
    private lateinit var db: AppDatabase
    private lateinit var repository: PlaylistRepository
    private val mediaId = "https://example.com/音频/01.mp3"
    private val sources = listOf(
        RemoteSubtitleSource("https://example.com/01.ja.vtt?token=original", "ja", "vtt"),
        RemoteSubtitleSource("https://example.com/01.zh-CN.lrc?token=original", "zh", "lrc")
    )

    @Before
    fun setUp() {
        OnlineLyricsStore.clear()
        openDatabase()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(databaseName)
        OnlineLyricsStore.clear()
    }

    @Test
    fun favoriteOnlineAudio_afterDatabaseReopen_loadsSubtitlesWithoutMemoryCache() = runBlocking {
        val favoritesId = repository.getOrCreateFavoritesPlaylistId()
        assertTrue(repository.addItemToPlaylist(favoritesId, onlineItem(sources)))

        restartStorage()

        val stored = db.playlistItemDao().getItemsOnce(favoritesId).single()
        val rows = repository.observePlaylistItemsWithSubtitles(favoritesId).first()
        assertTrue(rows.single().hasSubtitles)
        assertEquals(stored.remoteSubtitleSources, rows.single().remoteSubtitleSources)
        // Exercise the same projection and mapper used when tapping a favorite.
        val restored = PlaylistMediaItemMapper.toMediaItemOrNull(rows.single().toPlaybackEntity())!!
        assertEquals(sources, lyricsTargetContextFromMediaItem(restored)!!.remoteSubtitleSources)
        assertTrue(OnlineLyricsStore.get(mediaId).isEmpty())

        val requestedUrls = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requestedUrls += chain.request().url.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("[00:00.00]晚安\n[00:02.00]祝你好梦".toResponseBody())
                .build()
        }.build()
        val loader = LyricsLoader(
            database = db,
            trackDao = db.trackDao(),
            albumDao = db.albumDao(),
            remoteSubtitleSourceDao = db.remoteSubtitleSourceDao(),
            manualLyricsSourceRepository = ManualLyricsSourceRepository(db.manualLyricsSourceDao(), context),
            okHttpClient = client,
            context = context
        )
        val result = loader.load(restored)

        assertEquals(listOf("晚安", "祝你好梦"), result.lyrics.map { it.text })
        assertEquals(listOf(sources[1].url), requestedUrls)
    }

    @Test
    fun favoriteWithOnlyMemorySources_persistsThemBeforeCacheIsCleared() = runBlocking {
        val favoritesId = repository.getOrCreateFavoritesPlaylistId()
        OnlineLyricsStore.set(mediaId, sources)
        repository.addItemToPlaylist(favoritesId, onlineItem())

        restartStorage()

        val restored = PlaylistMediaItemMapper.toMediaItemOrNull(db.playlistItemDao().getItemsOnce(favoritesId).single())!!
        assertEquals(sources, lyricsTargetContextFromMediaItem(restored)!!.remoteSubtitleSources)
    }

    @Test
    fun addingExistingFavorite_repairsSourcesWithoutDuplicatingOrReordering() = runBlocking {
        val favoritesId = repository.getOrCreateFavoritesPlaylistId()
        repository.addItemsToPlaylist(favoritesId, listOf(onlineItem(), otherItem()))
        repository.movePlaylistItemToBottom(favoritesId, mediaId)
        val before = db.playlistItemDao().getItemsOnce(favoritesId)

        val summary = repository.addItemsToPlaylist(favoritesId, listOf(onlineItem(sources)))
        // An incomplete item must not erase the repaired sources on another add.
        repository.addItemToPlaylist(favoritesId, onlineItem())
        restartStorage()

        assertEquals(PlaylistAddSummary(addedCount = 0, skippedCount = 1), summary)
        val after = db.playlistItemDao().getItemsOnce(favoritesId)
        assertEquals(before.map { it.mediaId to it.itemOrder }, after.map { it.mediaId to it.itemOrder })
        val restored = PlaylistMediaItemMapper.toMediaItemOrNull(after.last())!!
        assertEquals(sources, lyricsTargetContextFromMediaItem(restored)!!.remoteSubtitleSources)
        val rows = repository.observePlaylistItemsWithSubtitles(favoritesId).first()
        assertFalse(rows.first().hasSubtitles)
        assertTrue(rows.last().hasSubtitles)
    }

    @Test
    fun replacingCustomPlaylist_preservesSourcesAndPrefersItemMetadata() = runBlocking {
        val playlistId = repository.createUserPlaylist("睡前听")!!
        OnlineLyricsStore.set(mediaId, listOf(sources.first()))
        repository.replacePlaylistWithMediaItems(playlistId, listOf(onlineItem(sources), otherItem()))

        restartStorage()

        val stored = db.playlistItemDao().getItemsOnce(playlistId)
        val restored = PlaylistMediaItemMapper.toMediaItemOrNull(stored.first())!!
        assertEquals(sources, lyricsTargetContextFromMediaItem(restored)!!.remoteSubtitleSources)
        assertEquals("", stored.last().remoteSubtitleSources)
    }

    private fun restartStorage() {
        OnlineLyricsStore.clear()
        db.close()
        openDatabase()
    }

    private fun openDatabase() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        repository = PlaylistRepository(db.playlistDao(), db.playlistItemDao(), db.trackDao(), db.albumDao())
    }

    private fun onlineItem(subtitles: List<RemoteSubtitleSource> = emptyList()): MediaItem =
        MediaItemFactory.fromDetails(
            mediaId = mediaId,
            uri = mediaId,
            title = "晚安音声",
            albumTitle = "在线专辑",
            rjCode = "RJ123456",
            lyricsRelativePathNoExt = "音频/01",
            remoteSubtitleSources = subtitles
        )

    private fun otherItem(): MediaItem = MediaItemFactory.fromDetails(
        mediaId = "https://example.com/02.mp3",
        uri = "https://example.com/02.mp3",
        title = "无字幕音频"
    )
}
