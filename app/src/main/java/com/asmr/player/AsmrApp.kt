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
import com.asmr.player.subtitle.GeneratedSubtitleFileBackfill
import com.asmr.player.subtitle.SubtitleTaskRepository
import com.asmr.player.util.MessageManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
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

    @Inject
    lateinit var messageManager: MessageManager

    override fun onCreate() {
        super.onCreate()
        appCacheManager.start()
        runCatching { PDFBoxResourceLoader.init(applicationContext) }
        applicationScope.launch {
            runCatching { settingsRepository.clearSleepTimer() }
            val database = runCatching { AppDatabaseProvider.get(applicationContext) }.getOrNull()
                ?: return@launch
            if (runCatching { database.subtitleTaskDao().countAllItems() > 0 }.getOrDefault(false)) {
                runCatching { SubtitleTaskRepository.get(applicationContext).reconcileOnAppLaunch() }
            }
            if (runCatching { database.downloadDao().countRecoverableItems() > 0 }.getOrDefault(false)) {
                runCatching { DownloadQueueCoordinator.recoverDownloadsOnAppLaunch(applicationContext) }
            }
            runCatching { GeneratedSubtitleFileBackfill(applicationContext, database).runOnce() }
                .getOrNull()
                ?.let { summary ->
                    when {
                        summary.unavailableCount > 0 -> messageManager.showWarning(
                            "已为旧版字幕补导出 ${summary.exportedCount} 个 LRC，" +
                                "另有 ${summary.unavailableCount} 个因目录不可写未导出" +
                                if (summary.preservedCount > 0) {
                                    "，并保留 ${summary.preservedCount} 个已有同名文件"
                                } else {
                                    ""
                                }
                        )
                        summary.exportedCount > 0 -> messageManager.showSuccess(
                            "已为旧版生成字幕补导出 ${summary.exportedCount} 个 LRC 文件" +
                                if (summary.preservedCount > 0) {
                                    "，并保留 ${summary.preservedCount} 个已有同名文件"
                                } else {
                                    ""
                                }
                        )
                        summary.preservedCount > 0 -> messageManager.showInfo(
                            "检测到 ${summary.preservedCount} 个已有同名 LRC，补导出时未覆盖"
                        )
                    }
                }
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
