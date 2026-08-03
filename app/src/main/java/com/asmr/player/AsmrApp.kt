package com.asmr.player

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.asmr.player.cache.AppCacheManager
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.cache.ImageCacheManager
import com.asmr.player.data.remote.download.DownloadQueueCoordinator
import com.asmr.player.data.remote.download.DownloadRuntimeConfig
import com.asmr.player.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Named

@HiltAndroidApp
class AsmrApp : Application(), ImageLoaderFactory, Configuration.Provider {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    @Named("image")
    lateinit var imageOkHttpClient: OkHttpClient

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var appCacheManager: AppCacheManager

    override fun onCreate() {
        super.onCreate()
        appCacheManager.start()
        applicationScope.launch {
            runCatching {
                getSharedPreferences("album_detail_tree_prefs", MODE_PRIVATE).all
            }
        }
        applicationScope.launch {
            runCatching { settingsRepository.clearSleepTimer() }
            runCatching { AppDatabaseProvider.get(applicationContext) }
            runCatching { DownloadQueueCoordinator.recoverDownloadsOnAppLaunch(applicationContext) }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        DownloadQueueCoordinator.onTrimMemory(applicationContext, level)
        ImageCacheManager.trimMemoryIfInitialized(level)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setExecutor(DownloadRuntimeConfig.createWorkManagerExecutor(this))
            .setTaskExecutor(DownloadRuntimeConfig.createWorkManagerTaskExecutor())
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { imageOkHttpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache(null)
            .build()
    }
}
