package com.asmr.player.cache

import android.content.Context
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheKeyFactoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun localFileKeyChangesWhenFileIsReplacedAtSamePath() {
        val file = File(context.cacheDir, "cache-key-image.jpg")
        try {
            file.writeBytes(byteArrayOf(1))
            val initialModifiedAt = file.lastModified()
            val firstKey = CacheKeyFactory.createKey(
                context = context,
                model = file,
                size = IntSize(320, 240),
                version = "test"
            )

            file.writeBytes(byteArrayOf(1, 2))
            file.setLastModified(initialModifiedAt + 1_000L)
            val replacedKey = CacheKeyFactory.createKey(
                context = context,
                model = file,
                size = IntSize(320, 240),
                version = "test"
            )

            assertNotEquals(firstKey, replacedKey)
            assertEquals(
                replacedKey,
                CacheKeyFactory.createKey(context, file.absolutePath, IntSize(320, 240), "test")
            )
            assertEquals(
                replacedKey,
                CacheKeyFactory.createKey(context, file, IntSize(320, 240), "test")
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun localPathAndFile_shareTheSameCacheKeys() {
        val file = File(context.cacheDir, "prefetched-cover.jpg")
        val size = IntSize(320, 240)

        assertEquals(
            CacheKeyFactory.createKey(context, file, size, "test"),
            CacheKeyFactory.createKey(context, file.absolutePath, size, "test"),
        )
        assertEquals(
            CacheKeyFactory.createDataKey(context, file, "test"),
            CacheKeyFactory.createDataKey(context, file.absolutePath, "test"),
        )
    }

    @Test
    fun memoryLookup_doesNotReadFileMetadata() {
        val file = object : File(context.cacheDir, "memory-only-cover.jpg") {
            override fun length(): Long = error("内存占位查找不应读取文件大小")
            override fun lastModified(): Long = error("内存占位查找不应读取修改时间")
        }

        assertEquals(
            CacheKeyFactory.createMemoryLookupKey(context, file, "test"),
            CacheKeyFactory.createMemoryLookupKey(context, file.absolutePath, "test"),
        )
        assertEquals(
            CacheKeyFactory.createMemoryLookupKey(context, CacheImageModel(file, keyTag = "cover"), "test"),
            CacheKeyFactory.createMemoryLookupKey(context, CacheImageModel(file.absolutePath, keyTag = "cover"), "test"),
        )
    }
}
