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

    @Test
    fun replaceMatchedOnlineTracksWithLocalTracks_preservesSameNamedTrackInOtherFormatFolder() = runBlocking {
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
        val onlineMp3Id = db.trackDao().insertTrack(
            TrackEntity(
                albumId = albumId,
                title = "Track A",
                path = "https://example.com/mp3/Track%20A.mp3",
                group = "mp3"
            )
        )
        val onlineWavId = db.trackDao().insertTrack(
            TrackEntity(
                albumId = albumId,
                title = "Track A",
                path = "https://example.com/wav/Track%20A.wav",
                group = "wav"
            )
        )
        val localMp3Id = db.trackDao().insertTrack(
            TrackEntity(
                albumId = albumId,
                title = "Track A",
                path = "/downloads/RJ123456/mp3/Track A.mp3",
                group = "mp3"
            )
        )

        replaceMatchedOnlineTracksWithLocalTracks(
            db = db,
            albumId = albumId,
            preferredLocalPrefix = "/downloads/RJ123456/"
        )

        val tracks = db.trackDao().getTracksForAlbumOnce(albumId)
        assertEquals(setOf(onlineWavId, localMp3Id), tracks.map { it.id }.toSet())
        assertFalse(tracks.any { it.id == onlineMp3Id })
        assertTrue(tracks.any { it.group == "wav" && it.path.startsWith("https://") })
    }

    @Test
    fun replaceMatchedOnlineTracksWithLocalTracks_usesGroupFallbackOnlyWhenUnambiguous() = runBlocking {
        val albumId = db.albumDao().insertAlbum(
            AlbumEntity(
                title = "Album",
                path = "web://rj/RJ123456",
                workId = "RJ123456",
                rjCode = "RJ123456"
            )
        )
        val onlineId = db.trackDao().insertTrack(
            TrackEntity(
                albumId = albumId,
                title = "Track A",
                path = "https://example.com/disc1/Track%20A.mp3",
                group = "disc1"
            )
        )
        val localId = db.trackDao().insertTrack(
            TrackEntity(
                albumId = albumId,
                title = "Track A",
                path = "/downloads/RJ123456/Track A.mp3"
            )
        )

        replaceMatchedOnlineTracksWithLocalTracks(
            db = db,
            albumId = albumId,
            preferredLocalPrefix = "/downloads/RJ123456/"
        )

        val tracks = db.trackDao().getTracksForAlbumOnce(albumId)
        assertEquals(listOf(localId), tracks.map { it.id })
        assertFalse(tracks.any { it.id == onlineId })
    }

    @Test
    fun resolveDownloadedAlbumCoverPath_preservesExistingUserCover() {
        assertEquals(
            "content://library/custom-cover.jpg",
            resolveDownloadedAlbumCoverPath(
                existingCoverPath = "content://library/custom-cover.jpg",
                downloadedCoverPath = "/downloads/RJ123456/cover.jpg"
            )
        )
    }

    @Test
    fun resolveDownloadedAlbumCoverPath_usesDownloadedCoverWhenExistingCoverIsBlank() {
        assertEquals(
            "/downloads/RJ123456/cover.jpg",
            resolveDownloadedAlbumCoverPath(
                existingCoverPath = "",
                downloadedCoverPath = "/downloads/RJ123456/cover.jpg"
            )
        )
    }

    @Test
    fun downloadMimeType_preservesSubtitleFileExtensionsForSafProviders() {
        assertEquals("text/vtt", downloadMimeType("voice.vtt"))
        assertEquals("application/x-subrip", downloadMimeType("voice.srt"))
        assertEquals("application/octet-stream", downloadMimeType("voice.lrc"))
        assertEquals("text/plain", downloadMimeType("notes.txt"))
    }

    @Test
    fun parseDownloadedSubtitles_matchesAndParsesVttForSafAudio() {
        val audio = DownloadStorageEntry(
            reference = "content://provider/audio",
            relativePath = "voice/track01.mp3",
            displayName = "track01.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = 1L,
            isDirectory = false,
        )
        val subtitle = DownloadStorageEntry(
            reference = "content://provider/subtitle",
            relativePath = "voice/track01.vtt",
            displayName = "track01.vtt",
            mimeType = "text/vtt",
            sizeBytes = 32L,
            isDirectory = false,
        )

        val parsed = parseDownloadedSubtitles(listOf(audio, subtitle)) {
            "WEBVTT\n\n00:00:00.000 --> 00:00:01.000\n字幕内容".toByteArray()
        }

        assertEquals("字幕内容", parsed.getValue(audio.reference).single().text)
    }
}
