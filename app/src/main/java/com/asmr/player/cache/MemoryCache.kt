package com.asmr.player.cache

import android.graphics.Bitmap
import android.util.LruCache

class MemoryCache(
    maxSizeBytes: Int
) {
    private val cache = object : LruCache<String, Bitmap>(maxSizeBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount.coerceAtLeast(1)
        }
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun clear() {
        cache.evictAll()
    }

    fun snapshotSizeBytes(): Int = cache.size()

    fun maxSizeBytes(): Int = cache.maxSize()
}

