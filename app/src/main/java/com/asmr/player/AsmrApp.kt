package com.asmr.player

import android.app.Application
import coil.disk.DiskCache
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Named

@HiltAndroidApp
class AsmrApp : Application(), ImageLoaderFactory {

    @Inject
    @Named("image")
    lateinit var imageOkHttpClient: OkHttpClient

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        runBlocking {
            runCatching { settingsRepository.clearSleepTimer() }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { AppDatabaseProvider.get(applicationContext) }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { imageOkHttpClient }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "coil_cache"))
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
