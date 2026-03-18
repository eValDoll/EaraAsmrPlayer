package com.asmr.player.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object PlaybackMediaCache {
    private const val CACHE_DIR_NAME = "playback_media_cache"
    private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L

    @Volatile
    private var cache: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache {
        cache?.let { return it }
        return synchronized(this) {
            cache?.let { return@synchronized it }
            val directory = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
            SimpleCache(
                directory,
                LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { cache = it }
        }
    }

    fun release() {
        synchronized(this) {
            cache?.release()
            cache = null
        }
    }
}
