package com.asmr.player.data.repository

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.os.Bundle
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.lyrics.EXTRA_ALBUM_WORK_ID
import com.asmr.player.data.lyrics.EXTRA_LYRICS_RELATIVE_PATH_NO_EXT
import com.asmr.player.data.lyrics.EXTRA_TRACK_GROUP
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryOrderTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: PlaylistRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = PlaylistRepository(
            playlistDao = db.playlistDao(),
            playlistItemDao = db.playlistItemDao(),
            trackDao = db.trackDao(),
            albumDao = db.albumDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun movePlaylistItemToTop_reindexesContinuously() = runBlocking {
        val playlistId = db.playlistDao().insertPlaylist(
            PlaylistEntity(name = "我的列表", category = PlaylistRepository.CATEGORY_USER)
        )
        db.playlistItemDao().upsertItems(
            listOf(
                playlistItem(playlistId, "a", order = 0),
                playlistItem(playlistId, "b", order = 1),
                playlistItem(playlistId, "c", order = 2)
            )
        )

        repository.movePlaylistItemToTop(playlistId, "c")

        val items = db.playlistItemDao().getItemsOnce(playlistId)
        assertEquals(listOf("c", "a", "b"), items.map { it.mediaId })
        assertEquals(listOf(0, 1, 2), items.map { it.itemOrder })
    }

    @Test
    fun reorderPlaylistItems_thenAddItem_appendsWithoutBreakingManualOrder() = runBlocking {
        val playlistId = db.playlistDao().insertPlaylist(
            PlaylistEntity(name = "我的列表", category = PlaylistRepository.CATEGORY_USER)
        )
        db.playlistItemDao().upsertItems(
            listOf(
                playlistItem(playlistId, "a", order = 3),
                playlistItem(playlistId, "b", order = 8),
                playlistItem(playlistId, "c", order = 12)
            )
        )

        repository.reorderPlaylistItems(playlistId, listOf("b", "c", "a"))
        repository.addItemToPlaylist(
            playlistId = playlistId,
            item = mediaItem(mediaId = "d", title = "Track D")
        )

        val items = db.playlistItemDao().getItemsOnce(playlistId)
        assertEquals(listOf("b", "c", "a", "d"), items.map { it.mediaId })
        assertEquals(listOf(0, 1, 2, 3), items.map { it.itemOrder })
    }

    @Test
    fun playlistMediaItem_roundTrip_preservesPlaybackContext() {
        val item = mediaItem(
            mediaId = "content://media/external/audio/document/primary:Album/01.mp3",
            title = "Track A"
        )

        val entity = PlaylistMediaItemMapper.fromMediaItem(
            playlistId = 3L,
            item = item,
            itemOrder = 7
        )

        assertEquals("Album Title", entity.albumTitle)
        assertEquals(12L, entity.albumId)
        assertEquals(34L, entity.trackId)
        assertEquals("RJ123456", entity.rjCode)
        assertEquals("WORK123", entity.albumWorkId)
        assertEquals("Disc 1", entity.trackGroup)
        assertEquals("Disc 1/01", entity.lyricsRelativePathNoExt)
        assertEquals("audio/flac", entity.mimeType)
        assertFalse(entity.isVideo)

        val restored = PlaylistMediaItemMapper.toMediaItemOrNull(entity)
        assertNotNull(restored)
        val restoredItem = restored!!
        val extras = restoredItem.mediaMetadata.extras
        assertEquals(entity.mediaId, restoredItem.mediaId)
        assertEquals(entity.albumTitle, restoredItem.mediaMetadata.albumTitle?.toString())
        assertEquals(entity.mimeType, restoredItem.localConfiguration?.mimeType)
        assertEquals(entity.albumId, extras?.getLong("album_id"))
        assertEquals(entity.trackId, extras?.getLong("track_id"))
        assertEquals(entity.rjCode, extras?.getString("rj_code"))
        assertEquals(entity.albumWorkId, extras?.getString(EXTRA_ALBUM_WORK_ID))
        assertEquals(entity.trackGroup, extras?.getString(EXTRA_TRACK_GROUP))
        assertEquals(entity.lyricsRelativePathNoExt, extras?.getString(EXTRA_LYRICS_RELATIVE_PATH_NO_EXT))
        assertTrue(restoredItem.localConfiguration?.uri.toString().contains("primary%3AAlbum%2F01.mp3"))
    }

    private fun playlistItem(playlistId: Long, mediaId: String, order: Int): PlaylistItemEntity {
        return PlaylistItemEntity(
            playlistId = playlistId,
            mediaId = mediaId,
            title = mediaId.uppercase(),
            artist = "",
            albumTitle = "",
            uri = "file:///$mediaId.mp3",
            artworkUri = "",
            albumId = 0L,
            trackId = 0L,
            rjCode = "",
            albumWorkId = "",
            trackGroup = "",
            lyricsRelativePathNoExt = "",
            mimeType = "",
            isVideo = false,
            itemOrder = order
        )
    }

    private fun mediaItem(mediaId: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(mediaId)
            .setMimeType("audio/flac")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("Artist")
                    .setAlbumTitle("Album Title")
                    .setArtworkUri(android.net.Uri.parse("https://example.com/cover.jpg"))
                    .setExtras(
                        Bundle().apply {
                            putLong("album_id", 12L)
                            putLong("track_id", 34L)
                            putString("rj_code", "RJ123456")
                            putString(EXTRA_ALBUM_WORK_ID, "WORK123")
                            putString(EXTRA_TRACK_GROUP, "Disc 1")
                            putString(EXTRA_LYRICS_RELATIVE_PATH_NO_EXT, "Disc 1/01")
                        }
                    )
                    .build()
            )
            .build()
    }
}
