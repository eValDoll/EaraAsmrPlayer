package com.asmr.player.data.local.library

import android.app.Application
import android.graphics.Bitmap
import androidx.room.Room
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.util.EmbeddedMediaExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LocalAlbumCoverResolverTest {
    @Test
    fun cachedEmbeddedArtworkTakesPriorityOverDirectoryCover() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val albumId = database.albumDao().insertAlbum(AlbumEntity(
            title = "作品", path = "/albums/work", coverPath = "/albums/work/cover.jpg",
        ))
        val artwork = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val saved = EmbeddedMediaExtractor.saveArtworkToCache(context, albumId, artwork)
        artwork.recycle()
        try {
            refreshScannedAlbumCover(context, database.albumDao(), albumId, "/albums/work/voice.mp3", "/albums/work/cover.jpg")
            assertEquals(saved, database.albumDao().getAlbumById(albumId)?.coverPath)
        } finally {
            database.close()
            EmbeddedMediaExtractor.getArtworkCacheFile(context, "album:$albumId").delete()
        }
    }

    @Test
    fun noEmbeddedArtworkFallsBackToDirectoryCoverAndThenPreviousCover() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = database.albumDao()
            val albumId = dao.insertAlbum(AlbumEntity(title = "作品", path = "/albums/work", coverPath = "/previous.jpg"))
            refreshScannedAlbumCover(context, dao, albumId, null, "/albums/work/cover.jpg")
            assertEquals("/albums/work/cover.jpg", dao.getAlbumById(albumId)?.coverPath)
            refreshScannedAlbumCover(context, dao, albumId, null, "")
            assertEquals("/albums/work/cover.jpg", dao.getAlbumById(albumId)?.coverPath)
        } finally {
            database.close()
        }
    }
}
