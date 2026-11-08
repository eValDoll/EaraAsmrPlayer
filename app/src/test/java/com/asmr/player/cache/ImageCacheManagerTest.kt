package com.asmr.player.cache

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImageCacheManagerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val size = IntSize(8, 8)
    private val version = "memory-preview-test"
    private lateinit var memory: MemoryCache
    private lateinit var disk: DiskCache
    private lateinit var stats: CacheStats
    private lateinit var manager: ImageCacheManager

    @Before
    fun setUp() {
        memory = MemoryCache(8 * 8 * 4)
        disk = DiskCache(temporaryFolder.newFolder("images"), maxSizeBytes = 1_048_576L, ttlMs = 0L)
        stats = CacheStats()
        manager = ImageCacheManager(
            appContext = context,
            config = CacheConfig(cacheVersion = version),
            memoryCache = memory,
            diskCache = disk,
            loaderFacade = ImageLoaderFacade(context, OkHttpClient(), Dispatchers.IO),
            stats = stats,
            decodeDispatcher = Dispatchers.IO,
        )
    }

    @Test
    fun prefetchedLocalPath_isReusedByTheDisplayFileModel() = runBlocking {
        val file = temporaryFolder.newFile("cover.jpg").apply { writeBytes(byteArrayOf(1)) }
        val bitmap = seedMemory(file.absolutePath)

        assertSame(bitmap, manager.loadImageFromCache(file, size)?.asAndroidBitmap())
        assertSame(bitmap, manager.peekAnySizeInMemory(file)?.bitmap?.asAndroidBitmap())
        assertEquals(2L, stats.snapshot().memoryHits)
        assertEquals(0L, stats.snapshot().decodeCount)
    }

    @Test
    fun initialMemoryPreview_doesNotReadFileMetadata() = runBlocking {
        val file = temporaryFolder.newFile("memory-only.jpg").apply { writeBytes(byteArrayOf(1)) }
        val bitmap = seedMemory(file.absolutePath)
        val noMetadataFile = object : File(file.absolutePath) {
            override fun length(): Long = error("内存占位查找不应读取文件大小")
            override fun lastModified(): Long = error("内存占位查找不应读取修改时间")
        }

        assertSame(bitmap, manager.peekAnySizeInMemory(noMetadataFile)?.bitmap?.asAndroidBitmap())
    }

    @Test
    fun backgroundValidation_removesPreviewWhenTheFileHasChanged() = runBlocking {
        val file = temporaryFolder.newFile("replaced.jpg").apply { writeBytes(byteArrayOf(1)) }
        seedMemory(file.absolutePath)
        replaceFile(file)

        assertNull(manager.loadImageFromCache(file, size))
        assertNull(manager.peekAnySizeInMemory(file))
    }

    @Test
    fun capturedPreview_rejectsAReplacedFileEvenAfterItsAliasHasChanged() = runBlocking {
        val file = temporaryFolder.newFile("captured.jpg").apply { writeBytes(byteArrayOf(1)) }
        seedMemory(file)
        val preview = requireNotNull(manager.peekAnySizeInMemory(file))
        replaceFile(file)
        seedMemory(file)

        assertFalse(manager.isMemoryPreviewValid(file, preview))
        assertTrue(manager.isMemoryPreviewValid(file, requireNotNull(manager.peekAnySizeInMemory(file))))
    }

    @Test
    fun capturedPreview_rejectsADeletedFile() = runBlocking {
        val file = temporaryFolder.newFile("deleted.jpg").apply { writeBytes(byteArrayOf(1)) }
        seedMemory(file)
        val preview = requireNotNull(manager.peekAnySizeInMemory(file))
        assertTrue(file.delete())

        assertFalse(manager.isMemoryPreviewValid(file, preview))
    }

    @Test
    fun capturedPreview_remainsValidAfterLocalOrRemoteCacheEviction() = runBlocking {
        val file = temporaryFolder.newFile("evicted.jpg").apply { writeBytes(byteArrayOf(1)) }
        val remote = "https://example.com/evicted.jpg"
        for (model in listOf(file, remote)) {
            seedMemory(model)
            val preview = requireNotNull(manager.peekAnySizeInMemory(model))
            memory.clear()

            assertNull(manager.peekAnySizeInMemory(model))
            assertTrue(manager.isMemoryPreviewValid(model, preview))
        }
    }

    @Test
    fun capturedPreview_validatesMetadataOnTheDecodeDispatcher() = runBlocking {
        val file = temporaryFolder.newFile("background.jpg").apply { writeBytes(byteArrayOf(1)) }
        seedMemory(file)
        val preview = requireNotNull(manager.peekAnySizeInMemory(file))
        val caller = Thread.currentThread()
        val checkedFile = object : File(file.absolutePath) {
            override fun length(): Long {
                assertNotSame(caller, Thread.currentThread())
                return super.length()
            }

            override fun lastModified(): Long {
                assertNotSame(caller, Thread.currentThread())
                return super.lastModified()
            }
        }

        assertTrue(manager.isMemoryPreviewValid(checkedFile, preview))
    }

    @Test
    fun validatedCrossSizePreview_rejectsAReplacedFile() = runBlocking {
        val file = temporaryFolder.newFile("validated.jpg").apply { writeBytes(byteArrayOf(1)) }
        seedMemory(file.absolutePath)
        replaceFile(file)

        assertNull(manager.peekAnySize(file))
        assertNull(manager.peekAnySizeInMemory(file))
    }

    @Test
    fun evictionOfAnOlderSize_keepsTheNewerPreviewForTheSameSource() = runBlocking {
        val model = "https://example.com/multiple-sizes.jpg"
        seedMemory(model, IntSize(4, 4))
        val latest = seedMemory(model, IntSize(6, 6))

        seedMemory("https://example.com/another.jpg", IntSize(4, 4))

        assertSame(latest, manager.peekAnySizeInMemory(model)?.bitmap?.asAndroidBitmap())
    }

    @Test
    fun replacementOfTheSameCacheEntry_removesThePreviousPreview() = runBlocking {
        val model = "https://example.com/replaced-bitmap.jpg"
        seedMemory(model)
        val replacement = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)

        memory.put(CacheKeyFactory.createKey(context, model, size, version), replacement)

        assertNull(manager.peekAnySizeInMemory(model))
        assertSame(replacement, manager.loadImageFromCache(model, size)?.asAndroidBitmap())
        assertSame(replacement, manager.peekAnySizeInMemory(model)?.bitmap?.asAndroidBitmap())
    }

    @Test
    fun oversizedDecodedBitmap_isReturnedWithoutKeepingAMemoryPreview() = runBlocking {
        val model = "https://example.com/oversized.jpg"
        val oversized = IntSize(16, 16)
        seedDisk(model, Color.RED, oversized)

        assertNotNull(manager.loadImageFromCache(model, oversized))
        assertNull(manager.peekAnySizeInMemory(model))
    }

    @Test
    fun evictionTrimAndClear_discardMemoryPreviews() = runBlocking {
        val first = "https://example.com/first.jpg"
        val second = "https://example.com/second.jpg"
        seedDisk(first, Color.RED)
        seedDisk(second, Color.BLUE)
        manager.loadImageFromCache(first, size)
        val latest = manager.loadImageFromCache(second, size)

        assertNotNull(latest)
        assertNull(manager.peekAnySizeInMemory(first))
        assertSame(latest?.asAndroidBitmap(), manager.peekAnySizeInMemory(second)?.bitmap?.asAndroidBitmap())

        manager.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
        assertNull(manager.peekAnySizeInMemory(second))
        manager.loadImageFromCache(second, size)
        manager.clearCaches()
        assertNull(manager.peekAnySizeInMemory(second))
        assertNull(manager.loadImageFromCache(second, size))
    }

    private suspend fun seedMemory(model: Any, requestSize: IntSize = size): Bitmap {
        val bitmap = Bitmap.createBitmap(requestSize.width, requestSize.height, Bitmap.Config.ARGB_8888)
        memory.put(CacheKeyFactory.createKey(context, model, requestSize, version), bitmap)
        manager.loadImageFromCache(model, requestSize)
        return bitmap
    }

    private fun seedDisk(model: Any, color: Int, requestSize: IntSize = size) {
        val bitmap = Bitmap.createBitmap(requestSize.width, requestSize.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        disk.put(
            CacheKeyFactory.createKey(context, model, requestSize, version),
            DiskCache.Entry(output.toByteArray(), requestSize.width, requestSize.height),
        )
    }

    private fun replaceFile(file: File) {
        val modifiedAt = file.lastModified()
        file.writeBytes(byteArrayOf(1, 2))
        file.setLastModified(modifiedAt + 1_000L)
    }
}
