package com.asmr.player.data.repository

import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.local.db.entities.AlbumGroupItemEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AlbumGroupRepositoryOrderTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: AlbumGroupRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        repository = AlbumGroupRepository(
            groupDao = db.albumGroupDao(),
            groupItemDao = db.albumGroupItemDao(),
            trackDao = db.trackDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun moveTrackToBottom_onlyChangesCurrentAlbumSection() = runBlocking {
        val albumA = insertAlbum("Album A", "/albums/a")
        val albumB = insertAlbum("Album B", "/albums/b")
        insertTrack(albumA, "A1", "/albums/a/1.mp3")
        insertTrack(albumA, "A2", "/albums/a/2.mp3")
        insertTrack(albumB, "B1", "/albums/b/1.mp3")
        insertTrack(albumB, "B2", "/albums/b/2.mp3")
        val groupId = db.albumGroupDao().insertGroup(AlbumGroupEntity(name = "分组"))
        db.albumGroupItemDao().upsertItems(
            listOf(
                AlbumGroupItemEntity(groupId = groupId, mediaId = "/albums/a/1.mp3", itemOrder = 0),
                AlbumGroupItemEntity(groupId = groupId, mediaId = "/albums/a/2.mp3", itemOrder = 1),
                AlbumGroupItemEntity(groupId = groupId, mediaId = "/albums/b/1.mp3", itemOrder = 0),
                AlbumGroupItemEntity(groupId = groupId, mediaId = "/albums/b/2.mp3", itemOrder = 1)
            )
        )

        repository.moveTrackToBottom(groupId, albumA, "/albums/a/1.mp3")

        val albumAItems = db.albumGroupItemDao().getAlbumItemsOnce(groupId, albumA)
        val albumBItems = db.albumGroupItemDao().getAlbumItemsOnce(groupId, albumB)
        assertEquals(listOf("/albums/a/2.mp3", "/albums/a/1.mp3"), albumAItems.map { it.mediaId })
        assertEquals(listOf(0, 1), albumAItems.map { it.itemOrder })
        assertEquals(listOf("/albums/b/1.mp3", "/albums/b/2.mp3"), albumBItems.map { it.mediaId })
        assertEquals(listOf(0, 1), albumBItems.map { it.itemOrder })
    }

    @Test
    fun addAlbumToGroup_appendsOnlyMissingTracks_afterManualOrder() = runBlocking {
        val albumId = insertAlbum("Album A", "/albums/a")
        insertTrack(albumId, "A1", "/albums/a/1.mp3")
        insertTrack(albumId, "A2", "/albums/a/2.mp3")
        insertTrack(albumId, "A3", "/albums/a/3.mp3")
        val groupId = db.albumGroupDao().insertGroup(AlbumGroupEntity(name = "分组"))
        db.albumGroupItemDao().upsertItems(
            listOf(
                AlbumGroupItemEntity(groupId = groupId, mediaId = "/albums/a/2.mp3", itemOrder = 0),
                AlbumGroupItemEntity(groupId = groupId, mediaId = "/albums/a/1.mp3", itemOrder = 1)
            )
        )

        repository.addAlbumToGroup(groupId, albumId)

        val items = db.albumGroupItemDao().getAlbumItemsOnce(groupId, albumId)
        assertEquals(
            listOf("/albums/a/2.mp3", "/albums/a/1.mp3", "/albums/a/3.mp3"),
            items.map { it.mediaId }
        )
        assertEquals(listOf(0, 1, 2), items.map { it.itemOrder })
    }

    private suspend fun insertAlbum(title: String, path: String): Long {
        return db.albumDao().insertAlbum(
            AlbumEntity(
                title = title,
                path = path,
                coverUrl = "",
                coverPath = "",
                coverThumbPath = ""
            )
        )
    }

    private suspend fun insertTrack(albumId: Long, title: String, path: String) {
        db.trackDao().insertTrack(
            TrackEntity(
                albumId = albumId,
                title = title,
                path = path,
                duration = 12.0
            )
        )
    }
}
