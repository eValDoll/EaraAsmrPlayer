package com.asmr.player.data.local.library

import android.app.Application
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.remote.download.DownloadStorageGateway
import com.asmr.player.util.LocalFileScopes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LocalAlbumMergeServiceTest {
    @Test fun dmmOnlineAndDownloadedAlbumMergeWithoutTouchingSameDigitsWorks() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = database.albumDao()
            val onlineId = dao.insertAlbum(AlbumEntity(title = "DMM 作品", path = "web://rj/UND353674", workId = "UND353674", rjCode = "UND353674"))
            dao.insertAlbum(AlbumEntity(title = "下载作品", path = "/downloads/UND353674", downloadPath = "/downloads/UND353674", workId = "und353674", rjCode = "und353674"))
            val numericId = dao.insertAlbum(AlbumEntity(title = "数字作品", path = "web://rj/UN353674", workId = "UN353674", rjCode = "UN353674"))
            val dlsiteId = dao.insertAlbum(AlbumEntity(title = "DLsite 作品", path = "web://rj/RJ353674", workId = "RJ353674", rjCode = "RJ353674"))
            val stream = "https://audio.example/d_353674.mp3#eara-chapter=0,&file=01.m4a"
            val trackId = database.trackDao().insertTrack(TrackEntity(albumId = onlineId, title = "章节", path = stream))
            val storage = DownloadStorageGateway(context)
            val merged = LocalAlbumMergeService(database, storage).resolveAndMerge("UND353674", "/downloads/UND353674", "DMM 作品", null, "/downloads/UND353674")!!
            assertEquals(1, dao.getAlbumsByNormalizedWorkIdOnce("UND353674").size)
            assertEquals(numericId, dao.getAlbumsByNormalizedWorkIdOnce("UN353674").single().id)
            assertEquals(dlsiteId, dao.getAlbumsByNormalizedWorkIdOnce("RJ353674").single().id)
            assertEquals(stream, database.trackDao().getTrackByIdOnce(trackId)!!.path)
            assertEquals(merged.id, database.trackDao().getTrackByIdOnce(trackId)!!.albumId)
            val scopes = LocalFileScopes(database, storage)
            val dmmScope = scopes.create(workNos = listOf("UND353674"))
            assertTrue(dmmScope.overlaps(scopes.create(workNos = listOf("und353674"))))
            assertFalse(dmmScope.overlaps(scopes.create(workNos = listOf("UN353674"))))
            assertFalse(dmmScope.overlaps(scopes.create(workNos = listOf("RJ353674"))))
        } finally { database.close() }
    }

    @Test fun numericOnlineAndDownloadedAlbumMergeWithoutTouchingSameDigitsRJ() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = database.albumDao()
            val onlineId = dao.insertAlbum(AlbumEntity(title = "数字作品", path = "web://rj/UN124393", workId = "UN124393", rjCode = "UN124393"))
            dao.insertAlbum(AlbumEntity(title = "下载作品", path = "/downloads/UN124393", downloadPath = "/downloads/UN124393", workId = "un124393", rjCode = "un124393"))
            val otherId = dao.insertAlbum(AlbumEntity(title = "DLsite 作品", path = "/albums/RJ124393", workId = "RJ124393", rjCode = "RJ124393"))
            val stream = "https://audio.example/124393.m3u8#eara-chapter=0,106000&file=01.m4a"
            val trackId = database.trackDao().insertTrack(TrackEntity(albumId = onlineId, title = "章节", path = stream))
            val storage = DownloadStorageGateway(context)
            val merged = LocalAlbumMergeService(database, storage).resolveAndMerge("UN124393", "/downloads/UN124393", "数字作品", null, "/downloads/UN124393")!!
            assertEquals(1, dao.getAlbumsByNormalizedWorkIdOnce("UN124393").size)
            assertEquals(otherId, dao.getAlbumsByNormalizedWorkIdOnce("RJ124393").single().id)
            assertEquals(stream, database.trackDao().getTrackByIdOnce(trackId)!!.path)
            assertEquals(merged.id, database.trackDao().getTrackByIdOnce(trackId)!!.albumId)
            val scopes = LocalFileScopes(database, storage)
            assertTrue(scopes.create(workNos = listOf("un124393")).overlaps(scopes.create(workNos = listOf("UN124393"))))
            assertFalse(scopes.create(workNos = listOf("UN124393")).overlaps(scopes.create(workNos = listOf("RJ124393"))))
        } finally { database.close() }
    }

    @Test
    fun sameRj_mergesSourcesAndKeepsEarlierPhysicalTrackId() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val albumDao = database.albumDao()
            val trackDao = database.trackDao()
            val importRoot = File(context.cacheDir, "import/RJ12345678").absolutePath
            val downloadRoot = File(context.cacheDir, "download/RJ12345678").absolutePath
            val physicalTrack = File(context.cacheDir, "shared/voice.mp3").absolutePath
            val importAlbumId = albumDao.insertAlbum(
                AlbumEntity(
                    title = "导入作品",
                    path = importRoot,
                    localPath = importRoot,
                    workId = "RJ12345678",
                    rjCode = "RJ12345678",
                ),
            )
            val downloadAlbumId = albumDao.insertAlbum(
                AlbumEntity(
                    title = "下载作品",
                    path = downloadRoot,
                    downloadPath = downloadRoot,
                    workId = "rj12345678",
                    rjCode = "rj12345678",
                ),
            )
            val earlierTrackId = trackDao.insertTrack(
                TrackEntity(albumId = importAlbumId, title = "音轨", path = physicalTrack),
            )
            val duplicateTrackId = trackDao.insertTrack(
                TrackEntity(albumId = downloadAlbumId, title = "音轨副本", path = physicalTrack),
            )
            trackDao.insertSubtitle(
                SubtitleEntity(trackId = duplicateTrackId, startMs = 0L, endMs = 1_000L, text = "保留字幕"),
            )

            val merged = LocalAlbumMergeService(database, DownloadStorageGateway(context)).resolveAndMerge(
                rj = "RJ12345678",
                fallbackPath = downloadRoot,
                fallbackTitle = "作品",
                localPath = null,
                downloadPath = downloadRoot,
            )

            assertNotNull(merged)
            assertEquals(1, albumDao.getAlbumsByWorkIdOnce("RJ12345678").size)
            assertEquals(importRoot, merged?.localPath)
            assertEquals(downloadRoot, merged?.downloadPath)
            val tracks = trackDao.getTracksForAlbumOrderedOnce(requireNotNull(merged).id)
            assertEquals(listOf(earlierTrackId), tracks.map { it.id })
            assertEquals("保留字幕", trackDao.getSubtitlesForTrack(earlierTrackId).single().text)
        } finally {
            database.close()
        }
    }
}
