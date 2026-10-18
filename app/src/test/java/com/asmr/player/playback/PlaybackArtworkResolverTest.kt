package com.asmr.player.playback

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.local.db.AppDatabase
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.util.EmbeddedMediaExtractor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [34])
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackArtworkResolverTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var resolver: PlaybackArtworkResolver
    private val files = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        resolver = PlaybackArtworkResolver(context, database.albumDao(), database.trackDao())
    }

    @After
    fun tearDown() {
        database.close()
        files.forEach { it.delete() }
    }

    @Test
    fun oldQueueWithoutAlbumExtrasUsesCurrentManualCoverFromDatabase() = runBlocking {
        val automatic = image(Color.BLUE)
        val manual = image(Color.RED)
        val track = File.createTempFile("artwork-track-", ".mp3", context.cacheDir).also { files += it }
        val albumId = database.albumDao().insertAlbum(
            AlbumEntity(title = "作品", path = context.cacheDir.path, coverPath = automatic.path, manualCoverPath = manual.path)
        )
        database.trackDao().insertTrack(TrackEntity(albumId = albumId, title = "音频", path = track.path))
        val item = MediaItemFactory.fromDetails(
            mediaId = track.path, uri = track.path, title = "音频", artworkUri = automatic.path,
        )
        embeddedCache(item.mediaId)

        val source = resolver.observeSource(item).first()
        val artworkUri = resolver.resolve(item, source)

        assertEquals(albumId, source.albumId)
        assertEquals(Uri.fromFile(manual), artworkUri)
    }

    @Test
    fun manuallySelectingDefaultCoverStillOverridesEmbeddedArtworkAndClearingRestoresIt() = runBlocking {
        val cover = image(Color.BLUE)
        val albumId = database.albumDao().insertAlbum(
            AlbumEntity(title = "作品", path = context.cacheDir.path, coverPath = cover.path, manualCoverPath = cover.path)
        )
        val item = MediaItemFactory.fromDetails(
            mediaId = "local-cover-track", uri = "file:///music/track.mp3", title = "音频", albumId = albumId,
            artworkUri = cover.path,
        )
        val embedded = embeddedCache(item.mediaId)
        val source = resolver.observeSource(item).first()
        val manualItem = item.withPlaybackArtwork(resolver.resolve(item, source))
        val staticMetadata = MediaMetadata.Builder()
            .setArtworkData(embedded.readBytes(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()

        // 与 ExoPlayer 的静态元数据合并顺序一致，明确让通知读取手动封面 URI。
        val manualMetadata = staticMetadata.buildUpon().populate(manualItem.mediaMetadata).build()
        assertNull(manualMetadata.artworkData)
        assertNull(manualMetadata.artworkDataType)
        assertEquals(Uri.fromFile(cover), manualMetadata.artworkUri)

        val album = requireNotNull(database.albumDao().getAlbumById(albumId))
        database.albumDao().updateAlbum(album.copy(manualCoverPath = ""))
        assertFalse(resolver.isCurrentSource(source))
        val automaticSource = resolver.observeSource(manualItem).first()
        val restored = manualItem.withPlaybackArtwork(resolver.resolve(manualItem, automaticSource))
        assertEquals(Uri.fromFile(embedded), restored.mediaMetadata.artworkUri)
        assertNull(restored.mediaMetadata.artworkData)
        val restoredMetadata = staticMetadata.buildUpon().populate(restored.mediaMetadata).build()
        assertNull(restoredMetadata.artworkData)
        assertNull(restoredMetadata.artworkDataType)
        assertEquals(Uri.fromFile(embedded), restoredMetadata.artworkUri)
        val restoredBitmap = requireNotNull(BitmapFactory.decodeFile(requireNotNull(restoredMetadata.artworkUri?.path)))
        assertTrue(Color.green(restoredBitmap.getPixel(0, 0)) > Color.blue(restoredBitmap.getPixel(0, 0)))
        restoredBitmap.recycle()
    }

    @Test
    fun coverChangesAreObservedWhileTheSameQueueItemRemainsCurrent() = runBlocking {
        val manual = image(Color.RED)
        val albumId = database.albumDao().insertAlbum(AlbumEntity(title = "作品", path = "music"))
        val item = MediaItemFactory.fromDetails(
            mediaId = "https://example.com/audio.mp3", uri = "https://example.com/audio.mp3", title = "音频", albumId = albumId,
        )
        val changes = Channel<PlaybackArtworkSource>(Channel.UNLIMITED)
        val observer = launch { resolver.observeSource(item).collect { changes.send(it) } }
        try {
            assertEquals("", withTimeout(5_000) { changes.receive() }.manualCoverPath)
            val album = requireNotNull(database.albumDao().getAlbumById(albumId))
            database.albumDao().updateAlbum(album.copy(manualCoverPath = manual.path))
            val selected = withTimeout(5_000) { changes.receive() }
            assertEquals(manual.path, selected.manualCoverPath)
            database.albumDao().updateAlbum(album.copy(manualCoverPath = ""))
            assertEquals("", withTimeout(5_000) { changes.receive() }.manualCoverPath)
            assertFalse(resolver.isCurrentSource(selected))
        } finally {
            observer.cancel()
            changes.close()
        }
    }

    @Test
    fun defaultArtworkIsUsedWhenTheTrackCannotHaveLocalEmbeddedArtwork() = runBlocking {
        val cover = image(Color.BLUE)
        val item = MediaItemFactory.fromDetails(
            mediaId = "https://example.com/audio.mp3", uri = "https://example.com/audio.mp3", title = "音频",
            artworkUri = cover.path,
        )
        assertEquals(Uri.fromFile(cover), resolver.resolve(item, resolver.observeSource(item).first()))
    }

    private fun image(color: Int): File {
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return File.createTempFile("artwork-cover-", ".jpg", context.cacheDir).also { file ->
            files += file
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun embeddedCache(mediaId: String): File =
        EmbeddedMediaExtractor.getArtworkCacheFile(context, "track:$mediaId").also { file ->
            files += file
            file.parentFile?.mkdirs()
            image(Color.GREEN).copyTo(file, overwrite = true)
        }
}
