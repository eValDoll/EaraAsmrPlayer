package com.asmr.player

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.ui.library.LibraryQueryBuilder
import com.asmr.player.ui.library.LibraryQuerySpec
import com.asmr.player.ui.library.LibraryTrackQueryBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryPagingStressTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun loadFirstPages_with10kTracks() = runBlocking {
        val albumDao = db.albumDao()
        val trackDao = db.trackDao()

        val albumCount = 200
        val tracksPerAlbum = 50

        repeat(albumCount) { i ->
            val albumId = albumDao.insertAlbum(
                AlbumEntity(
                    title = "Album $i",
                    path = "/test/album/$i",
                    localPath = "/test/album/$i",
                    circle = "Circle ${(i % 20)}",
                    cv = "CV ${(i % 30)}",
                    tags = ""
                )
            )

            val tracks = (0 until tracksPerAlbum).map { j ->
                TrackEntity(
                    albumId = albumId,
                    title = "Track $j",
                    path = "/test/album/$i/$j.mp3",
                    duration = 0.0,
                    group = ""
                )
            }
            trackDao.insertTracks(tracks)
        }

        val spec = LibraryQuerySpec()
        val albumsPagingSource = albumDao.queryAlbumsPaged(LibraryQueryBuilder.build(spec))

        val albumsRefresh = albumsPagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 40,
                placeholdersEnabled = false
            )
        )
        assertTrue(albumsRefresh is PagingSource.LoadResult.Page)

        val headersPagingSource = trackDao.queryLibraryTrackAlbumHeadersPaged(LibraryTrackQueryBuilder.buildAlbumHeaders(spec))
        val headersRefresh = headersPagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 40,
                placeholdersEnabled = false
            )
        )
        assertTrue(headersRefresh is PagingSource.LoadResult.Page)
    }
}

