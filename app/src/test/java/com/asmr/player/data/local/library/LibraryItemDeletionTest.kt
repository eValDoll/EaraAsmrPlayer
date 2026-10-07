package com.asmr.player.data.local.library

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.AppDatabaseMigrations
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.data.local.db.entities.AlbumGroupItemEntity
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.PlaylistTrackCrossRef
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LibraryItemDeletionTest {
    private lateinit var db: AppDatabase
    private var groupId = 0L
    private var playlistId = 0L
    private var favoritesId = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        groupId = db.albumGroupDao().insertGroup(AlbumGroupEntity(name = "分组"))
        playlistId = db.playlistDao().insertPlaylist(PlaylistEntity(name = "列表", category = "user"))
        favoritesId = db.playlistDao().insertPlaylist(
            PlaylistEntity(name = PlaylistRepository.PLAYLIST_FAVORITES, category = "system")
        )
    }

    @After
    fun tearDown() = db.close()

    @Test fun deletingNumericAlbumOnlyRemovesItsOwnUnboundPlaylistItems() = runBlocking {
        val album = insertAlbum("UN124393")
        db.playlistItemDao().upsertItems(listOf(
            PlaylistItemEntity(playlistId, "numeric", "数字作品", uri = "https://audio.example/124393.m3u8", rjCode = "UN124393"),
            PlaylistItemEntity(playlistId, "rj", "DLsite 作品", uri = "https://audio.example/RJ124393.m3u8", rjCode = "RJ124393")
        ))
        db.deleteLibraryAlbum(album)
        assertEquals(listOf("rj"), db.playlistItemDao().getItemsOnce(playlistId).map { it.mediaId })
    }

    @Test
    fun deleteMixedAlbum_clearsGroupsPlaylistsAndFavorites() = runBlocking {
        val album = insertAlbum("RJ123456")
        val tracks = listOf("/albums/本地.mp3", "content://audio/document/1", "https://example.com/online.mp3")
            .map { insertTrack(album, it) }
        tracks.forEach { addMemberships(it) }
        db.deleteLibraryAlbum(album)

        assertNull(db.albumDao().getAlbumById(album.id))
        assertTrue(db.trackDao().getTracksForAlbumOnce(album.id).isEmpty())
        assertEmptyMemberships()
        // 重新导入同一路径不能让已删除的分组关联重新出现。
        val reimported = insertAlbum("RJ123456")
        tracks.forEach { insertTrack(reimported, it.path) }
        assertEmptyMemberships()
    }

    @Test
    fun deleteSingleTrack_clearsPathOnlyAndUriOnlyItems_butKeepsOtherTracks() = runBlocking {
        val album = insertAlbum("RJ123456")
        val removed = insertTrack(album, "/albums/音频 1.mp3")
        val retained = insertTrack(album, "https://example.com/retained.mp3")
        addMemberships(removed)
        addMemberships(retained, order = 7)
        db.playlistItemDao().upsertItems(
            listOf(
                PlaylistItemEntity(playlistId, removed.path, "无数据库编号", uri = removed.path),
                PlaylistItemEntity(favoritesId, "custom-media-id", "仅有 URI", uri = "file:///albums/%E9%9F%B3%E9%A2%91%201.mp3")
            )
        )
        db.deleteLibraryTracks(listOf(removed.id))

        assertNotNull(db.albumDao().getAlbumById(album.id))
        assertNull(db.trackDao().getTrackByIdOnce(removed.id))
        assertEquals(listOf(retained.path), db.albumGroupItemDao().observeGroupTracks(groupId).first().map { it.mediaId })
        listOf(playlistId, favoritesId).forEach { id ->
            val items = db.playlistItemDao().getItemsOnce(id)
            assertEquals(listOf(retained.path), items.map { it.mediaId })
            assertEquals(7, items.single().itemOrder)
            assertEquals(listOf(retained.id), db.playlistDao().getTracksForPlaylist(id).first().map { it.id })
        }
        val stats = db.albumGroupDao().observeGroupsWithStats().first().single()
        assertEquals(1, stats.albumCount)
        assertEquals(1, stats.itemCount)
    }

    @Test
    fun deleteAlbum_clearsUnboundOnlineItemsForSameWork_only() = runBlocking {
        val album = insertAlbum("RJ123456")
        db.playlistItemDao().upsertItems(
            listOf(
                PlaylistItemEntity(playlistId, "online-1", "在线音频", uri = "https://example.com/1", rjCode = "rj 123456"),
                PlaylistItemEntity(favoritesId, "online-2", "在线收藏", uri = "https://example.com/2", albumWorkId = "RJ123456"),
                PlaylistItemEntity(playlistId, "other", "其他作品", uri = "https://example.com/3", rjCode = "RJ654321"),
                PlaylistItemEntity(favoritesId, "independent", "独立在线收藏", uri = "https://example.com/4")
            )
        )
        db.deleteLibraryAlbum(album)
        assertEquals(listOf("other"), db.playlistItemDao().getItemsOnce(playlistId).map { it.mediaId })
        assertEquals(listOf("independent"), db.playlistItemDao().getItemsOnce(favoritesId).map { it.mediaId })
    }

    @Test
    fun deleteLargeAlbum_handlesSqliteBindLimit() = runBlocking {
        val album = insertAlbum("RJ123456")
        val ids = db.trackDao().insertTracks((1..1001).map { TrackEntity(albumId = album.id, title = "音频 $it", path = "/album/$it.mp3") })
        db.trackDao().getTracksForAlbumOnce(album.id).forEach { addMemberships(it) }
        db.deleteLibraryTracks(ids)
        assertEmptyMemberships()
    }

    @Test
    fun deletionFailure_rollsBackMembershipsAndAlbum() = runBlocking {
        val album = insertAlbum("RJ123456")
        val track = insertTrack(album, "/albums/1.mp3")
        addMemberships(track)
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_album_deletion BEFORE DELETE ON albums BEGIN SELECT RAISE(ABORT, 'test'); END"
        )
        assertTrue(runCatching { db.deleteLibraryAlbum(album) }.isFailure)
        assertNotNull(db.albumDao().getAlbumById(album.id))
        assertNotNull(db.trackDao().getTrackByIdOnce(track.id))
        assertEquals(1, db.albumGroupDao().observeGroupsWithStats().first().single().itemCount)
        listOf(playlistId, favoritesId).forEach { assertEquals(1, db.playlistItemDao().getItemsOnce(it).size) }
    }

    @Test
    fun indexRebuild_preservesMembershipsForReinsertedPaths() = runBlocking {
        val album = insertAlbum("RJ123456")
        val track = insertTrack(album, "/albums/1.mp3")
        addMemberships(track)
        db.withTransaction {
            db.trackDao().deleteTracksForAlbum(album.id)
            db.trackDao().insertTrack(track.copy(id = 0))
        }
        assertEquals(1, db.albumGroupDao().observeGroupsWithStats().first().single().itemCount)
        listOf(playlistId, favoritesId).forEach { assertEquals(listOf(track.path), db.playlistItemDao().getItemsOnce(it).map { item -> item.mediaId }) }
    }

    @Test
    fun migration_cleansOldAlbumDeletion_withoutRemovingIndependentOnlineItems() = runBlocking {
        val deletedAlbum = insertAlbum("RJ123456")
        val deletedTrack = insertTrack(deletedAlbum, "/albums/deleted.mp3")
        addMemberships(deletedTrack)
        db.trackDao().deleteTracksForAlbum(deletedAlbum.id)
        db.albumDao().deleteAlbum(deletedAlbum)
        val retainedAlbum = insertAlbum("RJ654321")
        val retainedTrack = insertTrack(retainedAlbum, "https://example.com/retained.mp3")
        addMemberships(retainedTrack)
        db.playlistItemDao().upsertItems(
            listOf(
                PlaylistItemEntity(playlistId, "online", "独立在线音频", uri = "https://example.com/1"),
                PlaylistItemEntity(favoritesId, "custom", "重新导入", uri = retainedTrack.path, albumId = deletedAlbum.id)
            )
        )
        AppDatabaseMigrations.MIGRATION_32_33.migrate(db.openHelper.writableDatabase)

        val stats = db.albumGroupDao().observeGroupsWithStats().first().single()
        assertEquals(1, stats.itemCount)
        assertEquals(1, stats.albumCount)
        assertEquals(setOf(retainedTrack.path, "online"), db.playlistItemDao().getItemsOnce(playlistId).map { it.mediaId }.toSet())
        assertEquals(setOf(retainedTrack.path, "custom"), db.playlistItemDao().getItemsOnce(favoritesId).map { it.mediaId }.toSet())
        assertEquals(0, countRows("album_group_items", "mediaId = '${deletedTrack.path}'"))
        assertEquals(0, countRows("playlist_track_cross_ref", "trackId = ${deletedTrack.id}"))
    }

    @Test
    fun groupStats_ignoreDanglingItems_andUseFirstSurvivingArtwork() = runBlocking {
        val album = insertAlbum("RJ123456")
        val track = insertTrack(album, "/albums/1.mp3")
        db.albumGroupItemDao().upsertItems(listOf(AlbumGroupItemEntity(groupId, "/deleted.mp3", itemOrder = 0)))
        addMemberships(track, order = 1)
        val stats = db.albumGroupDao().observeGroupsWithStats().first().single()
        assertEquals(1, stats.itemCount)
        assertEquals(1, stats.albumCount)
        assertEquals(album.coverUrl, stats.firstArtworkUri)
    }

    private suspend fun insertAlbum(workNo: String): AlbumEntity {
        val album = AlbumEntity(title = "作品", path = "web://$workNo", rjCode = workNo, coverUrl = "https://example.com/$workNo.jpg")
        return album.copy(id = db.albumDao().insertAlbum(album))
    }

    private suspend fun insertTrack(album: AlbumEntity, path: String): TrackEntity {
        val track = TrackEntity(albumId = album.id, title = "音频", path = path)
        return track.copy(id = db.trackDao().insertTrack(track))
    }

    private suspend fun addMemberships(track: TrackEntity, order: Int = 0) {
        db.albumGroupItemDao().upsertItems(listOf(AlbumGroupItemEntity(groupId, track.path, itemOrder = order)))
        listOf(playlistId, favoritesId).forEach { id ->
            db.playlistItemDao().upsertItems(listOf(PlaylistItemEntity(id, track.path, track.title, uri = track.path, albumId = track.albumId, trackId = track.id, itemOrder = order)))
            db.playlistDao().insertPlaylistTrackCrossRef(PlaylistTrackCrossRef(id, track.id, order))
        }
    }

    private suspend fun assertEmptyMemberships() {
        val stats = db.albumGroupDao().observeGroupsWithStats().first().single()
        assertEquals(0, stats.albumCount)
        assertEquals(0, stats.itemCount)
        assertNull(stats.firstArtworkUri)
        assertTrue(db.albumGroupItemDao().observeGroupTracks(groupId).first().isEmpty())
        listOf(playlistId, favoritesId).forEach { id ->
            assertTrue(db.playlistItemDao().getItemsOnce(id).isEmpty())
            assertTrue(db.playlistDao().getTracksForPlaylist(id).first().isEmpty())
        }
        assertTrue(db.playlistDao().observePlaylistsWithStats().first().all { it.itemCount == 0 })
        assertEquals(0, countRows("album_group_items"))
        assertEquals(0, countRows("playlist_track_cross_ref"))
    }

    private fun countRows(table: String, condition: String = "1"): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table WHERE $condition").use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        }
}
