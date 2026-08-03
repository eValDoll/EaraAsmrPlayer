package com.asmr.player.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import com.asmr.player.cache.AppCacheLimits
import java.io.File
import java.util.TreeSet

object PlaybackMediaCache {
    private const val CACHE_DIR_NAME = "playback_media_cache"

    @Volatile
    private var cache: SimpleCache? = null
    private var maxCacheBytes = AppCacheLimits.playbackMaxSizeBytes(AppCacheLimits.DefaultSizeMb)
    private var evictor: AdjustableLruCacheEvictor? = null

    fun getInstance(context: Context): SimpleCache {
        cache?.let { return it }
        return synchronized(this) {
            cache?.let { return@synchronized it }
            val directory = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
            val newEvictor = AdjustableLruCacheEvictor(maxCacheBytes)
            SimpleCache(
                directory,
                newEvictor,
                StandaloneDatabaseProvider(context.applicationContext)
            ).also {
                evictor = newEvictor
                cache = it
            }
        }
    }

    fun updateMaxCacheBytes(maxCacheBytes: Long) {
        synchronized(this) {
            this.maxCacheBytes = maxCacheBytes.coerceAtLeast(0L)
            val activeCache = cache ?: return
            synchronized(activeCache) {
                evictor?.updateMaxBytes(activeCache, this.maxCacheBytes)
            }
        }
    }

    fun sizeBytes(context: Context): Long {
        return synchronized(this) {
            cache?.let { return@synchronized it.cacheSpace }
            File(context.cacheDir, CACHE_DIR_NAME)
                .walkTopDown()
                .filter(File::isFile)
                .sumOf(File::length)
        }
    }

    fun clear(context: Context) {
        synchronized(this) {
            val activeCache = cache
            if (activeCache != null) {
                synchronized(activeCache) {
                    activeCache.keys.toList().forEach(activeCache::removeResource)
                }
                return
            }
            val directory = File(context.cacheDir, CACHE_DIR_NAME)
            if (!directory.isDirectory) return
            directory.listFiles()
                ?.filter { file ->
                    file.isFile && (file.name.endsWith(".exo") || file.name.endsWith(".tmp"))
                }
                ?.forEach(File::delete)
        }
    }

    fun release() {
        synchronized(this) {
            cache?.release()
            cache = null
            evictor = null
        }
    }
}

private class AdjustableLruCacheEvictor(initialMaxBytes: Long) : CacheEvictor {
    private var maxBytes = initialMaxBytes.coerceAtLeast(0L)
    private val leastRecentlyUsed = TreeSet<CacheSpan> { first, second ->
        if (first.lastTouchTimestamp == second.lastTouchTimestamp) {
            first.compareTo(second)
        } else {
            first.lastTouchTimestamp.compareTo(second.lastTouchTimestamp)
        }
    }
    private var currentSizeBytes = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != -1L) evictCache(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        currentSizeBytes += span.length
        evictCache(cache, 0L)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        currentSizeBytes = (currentSizeBytes - span.length).coerceAtLeast(0L)
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    fun updateMaxBytes(cache: Cache, maxBytes: Long) {
        this.maxBytes = maxBytes.coerceAtLeast(0L)
        evictCache(cache, 0L)
    }

    private fun evictCache(cache: Cache, incomingLength: Long) {
        while (currentSizeBytes + incomingLength > maxBytes && leastRecentlyUsed.isNotEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }
}
