package com.asmr.player.data.repository

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun playlistItem(playlistId: Long, mediaId: String, order: Int): PlaylistItemEntity {
        return PlaylistItemEntity(
            playlistId = playlistId,
            mediaId = mediaId,
            title = mediaId.uppercase(),
            artist = "",
            uri = "file:///$mediaId.mp3",
            artworkUri = "",
            itemOrder = order
        )
    }

    private fun mediaItem(mediaId: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri("file:///$mediaId.mp3")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()
    }
}
