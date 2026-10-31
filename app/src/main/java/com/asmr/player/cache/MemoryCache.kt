package com.asmr.player.cache

import android.graphics.Bitmap
import android.util.LruCache

class MemoryCache(
    maxSizeBytes: Int
) {
    @Volatile
    private var removalListener: ((String) -> Unit)? = null

    private val cache = object : LruCache<String, Bitmap>(maxSizeBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.allocationByteCount.coerceAtLeast(1)
        }

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            // LruCache 在释放内部锁后回调，允许调用方更新自己的索引而不反转锁顺序。
            removalListener?.invoke(key)
        }
    }

    internal fun setRemovalListener(listener: (String) -> Unit) {
        removalListener = listener
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

    fun trimToSize(sizeBytes: Int) {
        cache.trimToSize(sizeBytes.coerceAtLeast(0))
    }

    fun maxSizeBytes(): Int = cache.maxSize()
}

