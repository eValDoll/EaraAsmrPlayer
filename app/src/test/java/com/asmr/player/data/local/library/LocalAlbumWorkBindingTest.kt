package com.asmr.player.data.local.library

import android.app.Application
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.OnlineSavedResourceEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LocalAlbumWorkBindingTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun occupiedRjCode_rejectsCaseAndWhitespaceVariantsWithoutChangingAlbumsOrTracks() = runBlocking {
        val variants = listOf("RJ01718403", " rj01718403 ", "\trj \t01718403\t")
        variants.forEachIndexed { index, occupiedRj ->
            val workno = "RJ0171840$index"
            val requested = insertAlbum(album("/requested/$index", "RJ0170642$index"))
            val occupied = insertAlbum(
                album("/occupied/$index", "custom-$index").copy(
                    rjCode = occupiedRj.replace("01718403", "0171840$index"),
                ),
            )
            val track = TrackEntity(albumId = requested.id, title = "本地音轨", path = "${requested.path}/01.mp3")
            val trackId = database.trackDao().insertTrack(track)

            val result = bindLocalAlbumWorkNumber(database, requested.id, workno)

            assertEquals(LocalAlbumWorkBindingResult.Conflict, result)
            assertEquals(requested, database.albumDao().getAlbumById(requested.id))
            assertEquals(occupied, database.albumDao().getAlbumById(occupied.id))
            assertEquals(track.copy(id = trackId), database.trackDao().getTrackByIdOnce(trackId))
            assertEquals((index + 1) * 2, database.albumDao().getAllAlbumsOnce().size)
        }
    }

    @Test
    fun occupiedWorkId_rejectsCaseAndWhitespaceVariants() = runBlocking {
        listOf("RJ01718403", " rj01718403 ", "\trj \t01718403\t").forEachIndexed { index, occupiedWorkId ->
            val requested = insertAlbum(album("/requested/$index", "RJ0170642$index"))
            val occupied = insertAlbum(
                album("/occupied/$index", occupiedWorkId.replace("01718403", "0171840$index"))
                    .copy(rjCode = ""),
            )

            assertEquals(
                LocalAlbumWorkBindingResult.Conflict,
                bindLocalAlbumWorkNumber(database, requested.id, "rj\t0171840$index"),
            )
            assertEquals(requested, database.albumDao().getAlbumById(requested.id))
            assertEquals(occupied, database.albumDao().getAlbumById(occupied.id))
        }
    }

    @Test
    fun ownWorkNumber_canBeNormalizedWithoutClearingCoverOrTranslatedTitle() = runBlocking {
        val previous = insertAlbum(album("/album", " rj \t01706426 ").copy(rjCode = "\trj01706426\t"))
        val expected = previous.copy(workId = "RJ01706426", rjCode = "RJ01706426")

        assertEquals(
            LocalAlbumWorkBindingResult.Bound(previous, expected),
            bindLocalAlbumWorkNumber(database, previous.id, "rj 01706426"),
        )
        assertEquals(expected, database.albumDao().getAlbumById(previous.id))
    }

    @Test
    fun workNumberInWorkIdOnly_keepsCoverWhenBindingSameNumber() = runBlocking {
        val previous = insertAlbum(album("/album", "RJ01706426").copy(rjCode = ""))
        val expected = previous.copy(rjCode = "RJ01706426")

        assertEquals(
            LocalAlbumWorkBindingResult.Bound(previous, expected),
            bindLocalAlbumWorkNumber(database, previous.id, "RJ01706426"),
        )
        assertEquals(expected, database.albumDao().getAlbumById(previous.id))
    }

    @Test
    fun changedWorkNumber_clearsOldCoverAndTranslatedTitleButPreservesLocalData() = runBlocking {
        val previous = insertAlbum(album("/album", "RJ01706426"))
        val expected = previous.copy(
            workId = "RJ01718403",
            rjCode = "RJ01718403",
            displayTitle = "",
            coverUrl = "",
            coverPath = "",
            coverThumbPath = "",
            manualCoverPath = "",
        )

        assertEquals(
            LocalAlbumWorkBindingResult.Bound(previous, expected),
            bindLocalAlbumWorkNumber(database, previous.id, "RJ01718403"),
        )
        assertEquals(expected, database.albumDao().getAlbumById(previous.id))
    }

    @Test
    fun savedOnlineAlbum_workNumberRoundTripPreservesAlbumAndSavedResources() = runBlocking {
        val originalWorkno = "RJ01287595"
        val previous = insertAlbum(album("web://rj/$originalWorkno", originalWorkno).copy(
            localPath = "/saved/$originalWorkno",
            downloadPath = "/downloads/$originalWorkno",
        ))
        val track = TrackEntity(
            albumId = previous.id,
            title = "已保存音轨",
            path = "https://example.test/$originalWorkno/01.mp3",
        )
        val savedTrack = track.copy(id = database.trackDao().insertTrack(track))
        val resource = OnlineSavedResourceEntity(
            albumId = previous.id,
            relativePath = "特典/封面.jpg",
            url = "https://example.test/$originalWorkno/cover.jpg",
            fileType = "Image",
        )
        val savedResource = resource.copy(
            id = database.onlineSavedResourceDao().insertAll(listOf(resource)).single(),
        )

        val changed = bindLocalAlbumWorkNumber(database, previous.id, "RJ01718403")
        assertTrue(changed is LocalAlbumWorkBindingResult.Bound)
        val changedAlbum = (changed as LocalAlbumWorkBindingResult.Bound).album
        assertEquals("RJ01718403", changedAlbum.workId)
        assertEquals("RJ01718403", changedAlbum.rjCode)

        val restored = bindLocalAlbumWorkNumber(database, previous.id, originalWorkno)

        assertTrue(restored is LocalAlbumWorkBindingResult.Bound)
        val expected = previous.copy(
            displayTitle = "",
            coverUrl = "",
            coverPath = "",
            coverThumbPath = "",
            manualCoverPath = "",
        )
        assertEquals(LocalAlbumWorkBindingResult.Bound(changedAlbum, expected), restored)
        assertEquals(listOf(expected), database.albumDao().getAllAlbumsOnce())
        assertEquals(listOf(expected), database.albumDao().getAlbumsByNormalizedWorkIdOnce(originalWorkno))
        assertEquals(listOf(savedTrack), database.trackDao().getTracksForAlbumOnce(previous.id))
        assertEquals(listOf(savedResource), database.onlineSavedResourceDao().getForAlbumOnce(previous.id))
    }

    @Test
    fun customWorkId_isPreservedWhenChangingDlsiteNumber() = runBlocking {
        val previous = insertAlbum(album("/album", "custom-id").copy(rjCode = "RJ01706426"))

        val result = bindLocalAlbumWorkNumber(database, previous.id, " bj02370869 ")

        assertTrue(result is LocalAlbumWorkBindingResult.Bound)
        val updated = (result as LocalAlbumWorkBindingResult.Bound).album
        assertEquals("custom-id", updated.workId)
        assertEquals("BJ02370869", updated.rjCode)
        assertEquals(previous, result.previous)
        assertEquals(updated, database.albumDao().getAlbumById(previous.id))
    }

    @Test
    fun blankWorkId_canBeBoundToSupportedVideoNumber() = runBlocking {
        val previous = insertAlbum(album("/album", "").copy(rjCode = ""))

        val result = bindLocalAlbumWorkNumber(database, previous.id, " vj01005620 ")

        assertTrue(result is LocalAlbumWorkBindingResult.Bound)
        val updated = (result as LocalAlbumWorkBindingResult.Bound).album
        assertEquals("VJ01005620", updated.workId)
        assertEquals("VJ01005620", updated.rjCode)
    }

    @Test
    fun missingAlbum_returnsMissingWithoutChangingOtherAlbums() = runBlocking {
        val existing = insertAlbum(album("/existing", "RJ01706426"))

        assertEquals(
            LocalAlbumWorkBindingResult.Missing,
            bindLocalAlbumWorkNumber(database, existing.id + 1, "RJ01718403"),
        )
        assertEquals(listOf(existing), database.albumDao().getAllAlbumsOnce())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidWorkNumber_isRejected() = runBlocking {
        bindLocalAlbumWorkNumber(database, 1L, "作品 RJ01718403")
        Unit
    }

    @Test
    fun concurrentBindingsToSameNumber_onlyOneAlbumCanClaimIt() = runBlocking {
        val originals = listOf(
            insertAlbum(album("/first", "RJ01706426")),
            insertAlbum(album("/second", "RJ01706427")),
        )
        val start = CompletableDeferred<Unit>()
        val contenders = originals.map { previous ->
            async(Dispatchers.IO) {
                start.await()
                bindLocalAlbumWorkNumber(database, previous.id, "RJ01718403")
            }
        }

        start.complete(Unit)
        val results = contenders.awaitAll()

        assertEquals(1, results.count { it is LocalAlbumWorkBindingResult.Bound })
        assertEquals(1, results.count { it == LocalAlbumWorkBindingResult.Conflict })
        val bound = results.filterIsInstance<LocalAlbumWorkBindingResult.Bound>().single()
        assertEquals(bound.album, database.albumDao().getAlbumsByNormalizedWorkIdOnce("RJ01718403").single())
        val unchanged = originals.single { it.id != bound.album.id }
        assertEquals(unchanged, database.albumDao().getAlbumById(unchanged.id))
        assertEquals(2, database.albumDao().getAllAlbumsOnce().size)
    }

    private suspend fun insertAlbum(album: AlbumEntity): AlbumEntity =
        album.copy(id = database.albumDao().insertAlbum(album))

    private fun album(path: String, workId: String) = AlbumEntity(
        title = "本地作品 $path",
        displayTitle = "原作品译名",
        path = path,
        localPath = "$path/local",
        downloadPath = "$path/download",
        circle = "社团",
        cv = "声优",
        tags = "标签一,标签二",
        coverUrl = "https://example.test/cover.jpg",
        coverPath = "$path/cover.jpg",
        coverThumbPath = "$path/cover-thumb.jpg",
        manualCoverPath = "$path/manual-cover.jpg",
        workId = workId,
        rjCode = workId,
        description = "作品描述",
        audioTrackCount = 3,
        audioTotalDuration = 120.0,
        audioTotalSizeBytes = 1024L,
    )
}
