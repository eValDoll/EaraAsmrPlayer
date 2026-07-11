package com.asmr.player.cache

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun put_trimsOldestEntryOnlyAfterSizeLimitIsExceeded() {
        val directory = temporaryFolder.newFolder("trim")
        val cache = DiskCache(
            directory = directory,
            maxSizeBytes = 100L,
            ttlMs = 0L,
        )

        cache.put("first", entry(size = 60, fill = 1))
        assertArrayEquals(ByteArray(60) { 1 }, cache.get("first")?.bytes)
        assertTrue(directory.resolve("first.bin").setLastModified(1L))

        cache.put("second", entry(size = 60, fill = 2))

        assertNull(cache.get("first"))
        assertArrayEquals(ByteArray(60) { 2 }, cache.get("second")?.bytes)
    }

    @Test
    fun put_replacingEntryUpdatesTrackedSize() {
        val cache = DiskCache(
            directory = temporaryFolder.newFolder("replace"),
            maxSizeBytes = 100L,
            ttlMs = 0L,
        )

        cache.put("first", entry(size = 60, fill = 1))
        cache.put("first", entry(size = 10, fill = 3))
        cache.put("second", entry(size = 50, fill = 4))

        assertArrayEquals(ByteArray(10) { 3 }, cache.get("first")?.bytes)
        assertArrayEquals(ByteArray(50) { 4 }, cache.get("second")?.bytes)
    }

    private fun entry(size: Int, fill: Int): DiskCache.Entry {
        return DiskCache.Entry(
            bytes = ByteArray(size) { fill.toByte() },
            width = 10,
            height = 10,
        )
    }
}
