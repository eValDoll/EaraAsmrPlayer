package com.asmr.player.data.local.library

import android.app.Application
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.data.remote.download.DownloadStorageGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LocalAlbumMergeServiceTest {
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
