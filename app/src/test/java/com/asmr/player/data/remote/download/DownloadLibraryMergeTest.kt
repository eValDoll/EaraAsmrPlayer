package com.asmr.player.data.remote.download

import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.RemoteSubtitleSourceEntity
import com.asmr.player.data.local.db.entities.SubtitleEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DownloadLibraryMergeTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun replaceMatchedOnlineTracksWithLocalTracks_migratesSubtitlesAndDeletesOnlineTrack() = runBlocking {
        val albumId = db.albumDao().insertAlbum(
            AlbumEntity(
                title = "Album",
                path = "web://rj/RJ123456",
                localPath = "/albums/RJ123456",
                downloadPath = "/downloads/RJ123456",
                workId = "RJ123456",
                rjCode = "RJ123456"
            )
        )
        val onlineId = db.trackDao().insertTrack(
            TrackEntity(
                albumId = albumId,
                title = "Track A",
                path = "https://example.com/Track%20A.mp3",
                group = "disc1"
            )
        )
        val localId = db.trackDao().insertTrack(
            TrackEntity(
                albumId = albumId,
                title = "Track A",
                path = "/downloads/RJ123456/disc1/Track A.mp3",
                group = "disc1"
            )
        )
        db.trackDao().insertSubtitle(
            SubtitleEntity(
                trackId = onlineId,
                startMs = 100L,
                endMs = 200L,
                text = "line"
            )
        )
        db.remoteSubtitleSourceDao().insertAll(
            listOf(
                RemoteSubtitleSourceEntity(
                    trackId = onlineId,
                    url = "https://example.com/Track%20A.vtt",
                    language = "ja",
                    ext = "vtt"
                )
            )
        )

        replaceMatchedOnlineTracksWithLocalTracks(
            db = db,
            albumId = albumId,
            preferredLocalPrefix = "/downloads/RJ123456/"
        )

        val tracks = db.trackDao().getTracksForAlbumOnce(albumId)
        assertEquals(listOf(localId), tracks.map { it.id })
        assertFalse(tracks.any { it.path.startsWith("http") })
        assertEquals("line", db.trackDao().getSubtitlesForTrack(localId).single().text)
        assertEquals("https://example.com/Track%20A.vtt", db.remoteSubtitleSourceDao().getSourcesForTrackOnce(localId).single().url)
        assertTrue(db.trackDao().getSubtitlesForTrack(onlineId).isEmpty())
        assertTrue(db.remoteSubtitleSourceDao().getSourcesForTrackOnce(onlineId).isEmpty())
    }
}
