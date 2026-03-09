package com.asmr.player.di

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {
    @Provides
    @Singleton
    fun provideCacheConfig(@ApplicationContext context: Context): CacheConfig {
        val pkg = context.packageName
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0)
        }
        val name = info.versionName ?: "0"
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return CacheConfig(cacheVersion = "$name-$code")
    }

    @Provides
    @Singleton
    fun provideCacheStats(): CacheStats = CacheStats()

    @Provides
    @Singleton
    fun provideDecodeDispatcher(config: CacheConfig): CoroutineDispatcher {
        return Executors.newFixedThreadPool(config.decodeParallelism).asCoroutineDispatcher()
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
        @Named("image") okHttpClient: OkHttpClient
    ): ImageLoaderFacade {
        return ImageLoaderFacade(context = context, okHttpClient = okHttpClient)
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
