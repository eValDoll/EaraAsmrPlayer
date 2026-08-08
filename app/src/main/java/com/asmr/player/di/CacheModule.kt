package com.asmr.player.di

import android.content.Context
import android.os.Process
import com.asmr.player.BuildConfig
import com.asmr.player.cache.CacheConfig
import com.asmr.player.cache.CacheStats
import com.asmr.player.cache.DiskCache
import com.asmr.player.cache.ImageCacheManager
import com.asmr.player.cache.ImageLoaderFacade
import com.asmr.player.cache.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {
    @Provides
    @Singleton
    fun provideCacheConfig(): CacheConfig = CacheConfig(
        cacheVersion = "${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}"
    )

    @Provides
    @Singleton
    fun provideCacheStats(): CacheStats = CacheStats()

    @Provides
    @Singleton
    fun provideDecodeDispatcher(config: CacheConfig): CoroutineDispatcher {
        val threadIndex = AtomicInteger(0)
        return Executors.newFixedThreadPool(config.decodeParallelism) { task ->
            Thread(
                {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    task.run()
                },
                "eara-image-${threadIndex.incrementAndGet()}"
            ).apply {
                isDaemon = true
            }
        }.asCoroutineDispatcher()
    }

    @Provides
    @Singleton
    fun provideMemoryCache(config: CacheConfig): MemoryCache {
        val max = Runtime.getRuntime().maxMemory().coerceAtLeast(16L * 1024 * 1024)
        val bytes = (max * config.memoryMaxSizePercent).toLong().coerceAtLeast(8L * 1024 * 1024)
        return MemoryCache(bytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    @Provides
    @Singleton
    fun provideDiskCache(@ApplicationContext context: Context, config: CacheConfig): DiskCache {
        val dir = File(context.cacheDir, "images")
        return DiskCache(directory = dir, maxSizeBytes = config.diskMaxSizeBytes, ttlMs = config.diskTtlMs)
    }

    @Provides
    @Singleton
    fun provideImageLoaderFacade(
        @ApplicationContext context: Context,
        @Named("image") okHttpClient: OkHttpClient,
        decodeDispatcher: CoroutineDispatcher
    ): ImageLoaderFacade {
        return ImageLoaderFacade(
            context = context,
            okHttpClient = okHttpClient,
            imageDispatcher = decodeDispatcher
        )
    }

    @Provides
    @Singleton
    fun provideImageCacheManager(
        @ApplicationContext context: Context,
        config: CacheConfig,
        memoryCache: MemoryCache,
        diskCache: DiskCache,
        facade: ImageLoaderFacade,
        stats: CacheStats,
        decodeDispatcher: CoroutineDispatcher
    ): ImageCacheManager {
        return ImageCacheManager(
            appContext = context,
            config = config,
            memoryCache = memoryCache,
            diskCache = diskCache,
            loaderFacade = facade,
            stats = stats,
            decodeDispatcher = decodeDispatcher
        )
    }
}
