package com.asmr.player

import com.asmr.player.i18n.appString
import com.asmr.player.R

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
        com.asmr.player.i18n.AppLanguage.initialize(this)
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
                            appString(R.string.ui_lrcs_have_been_exported_for_legacy_subtitles, summary.exportedCount) +
                                appString(R.string.ui_another_were_not_exported_because_the_directory_was, summary.unavailableCount) +
                                if (summary.preservedCount > 0) {
                                    appString(R.string.ui_and_keep_existing_files_with_the_same_name, summary.preservedCount)
                                } else {
                                    ""
                                }
                        )
                        summary.exportedCount > 0 -> messageManager.showSuccess(
                            appString(R.string.ui_lrc_files_have_been_exported_to_generate_subtitles, summary.exportedCount) +
                                if (summary.preservedCount > 0) {
                                    appString(R.string.ui_and_keep_existing_files_with_the_same_name, summary.preservedCount)
                                } else {
                                    ""
                                }
                        )
                        summary.preservedCount > 0 -> messageManager.showInfo(
                            appString(R.string.ui_existing_lrcs_with_the_same_name_were_detected, summary.preservedCount)
                        )
                    }
                }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.asmr.player.i18n.AppLanguage.refresh()
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
