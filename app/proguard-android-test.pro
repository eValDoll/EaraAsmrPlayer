# AndroidJUnitRunner 1.5.2 运行 Release instrumentation 时会从目标应用进程访问该类。
-keep class androidx.tracing.Trace { *; }

# AndroidX Test Platform 的 Kotlin 实现运行在目标应用的 instrumentation 进程中。
-keep class kotlin.** { *; }
-keep,includedescriptorclasses class javax.inject.** { *; }
-keep,includedescriptorclasses class jakarta.inject.** { *; }
-keep,includedescriptorclasses class dagger.** { *; }

# Release instrumentation 会直接调用目标应用中的挂起接口。
-keep class kotlinx.coroutines.** { *; }

# 页面翻译配置回归测试需要在 Release 进程中创建和重新打开独立 DataStore。
-keep class androidx.datastore.** { *; }
-keep class com.asmr.player.translation.** { *; }

# 设备端翻译配置测试需要创建独立的加密存储并重新读取配置。
-keep class com.asmr.player.subtitle.TranslationApi* { *; }
-keep class com.asmr.player.subtitle.TranslationProvider { *; }
-keep class com.asmr.player.subtitle.CustomThinkingMode { *; }
-keep class com.asmr.player.subtitle.CustomReasoningEffort { *; }

# 音频格式设备测试直接调用转录解码器并读取解码后的声道及时间信息。
-keep class com.asmr.player.subtitle.LocalAudioDecoder { *; }
-keep class com.asmr.player.subtitle.DecodedAudioChunk { *; }

# 设备端播放回归测试需要通过公开 Media3 API 连接 Release 播放服务。
-keep class androidx.media3.** { *; }
-keep class com.google.common.util.concurrent.** { *; }

# Release 设备测试验证 HLS 合并、章节裁切与取消，不接触用户的下载数据库。
-keep class com.asmr.player.data.remote.download.JapaneseAsmrHlsDownloaderKt { *; }
-keep class com.asmr.player.data.remote.download.JapaneseAsmrDownloadPreflight { *; }
-keep class com.asmr.player.data.remote.download.DownloadBatchRequest { *; }
-keep class com.asmr.player.data.remote.download.RelativeDownloadItem { *; }
-keep class com.asmr.player.util.ChapterMediaReference { *; }
-keep interface com.asmr.player.data.remote.download.DownloadWorker$DownloadWorkerEntryPoint { *; }
-keep class dagger.hilt.android.EntryPointAccessors { *; }

# 原生流光像素测试只创建内存位图，不访问应用数据库或播放服务。
-keep class com.asmr.player.ui.player.FlowingArtworkRenderer { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# 独立 AlbumItem 硬件像素 fixture 在测试 APK 中编译，保留它跨 APK 调用的 ABI。
# 本文件仅由 -PreleaseAndroidTest 加载，不影响标准 Release 的优化。
-keep class com.asmr.player.cache.CacheConfig { *; }
-keep class com.asmr.player.cache.CacheKeyFactory { *; }
-keep class com.asmr.player.cache.CacheStats { *; }
-keep class com.asmr.player.cache.MemoryCache { *; }
-keep class com.asmr.player.cache.DiskCache { *; }
-keep class com.asmr.player.cache.ImageLoaderFacade { *; }
-keep class com.asmr.player.cache.ImageCacheManager { *; }
-keep interface com.asmr.player.cache.ImageCacheEntryPoint { *; }
-keep class com.asmr.player.domain.model.Album { *; }
-keep class com.asmr.player.ui.library.AlbumItemKt { *; }
-keep class com.asmr.player.ui.library.AlbumInlineTextKt { *; }
-keep,includedescriptorclasses class com.asmr.player.ui.library.prepared.PreparedAlbumPageInputsKt { *; }
-keep,includedescriptorclasses class com.asmr.player.ui.library.prepared.PreparedParagraphTraceKt { *; }
-keep,includedescriptorclasses class com.asmr.player.ui.library.prepared.PreparedParagraphSession { *; }
-keep,includedescriptorclasses class com.asmr.player.ui.library.prepared.PreparedParagraphSession$* { *; }
-keep,includedescriptorclasses class com.asmr.player.ui.library.prepared.PreparedParagraphRequest { *; }
-keep,includedescriptorclasses class com.asmr.player.ui.library.prepared.PreparedLayoutStyle { *; }
-keep,includedescriptorclasses class com.asmr.player.ui.common.AlbumCoverShadowCacheKt { *; }
-keep,includedescriptorclasses class com.asmr.player.ui.common.AlbumCoverShadowCache** { *; }
-keep class com.asmr.player.ui.common.PageAnimationActivityKt { *; }
-keep class com.asmr.player.ui.common.AlbumRowRasterCacheKt { *; }
-keep class com.asmr.player.ui.theme.ThemeKt { *; }
-keep class com.asmr.player.ui.theme.AsmrTheme { *; }
-keep class com.asmr.player.ui.theme.AsmrColorScheme { *; }
-keep class com.asmr.player.ui.theme.ThemeMode { *; }

# 隔离硬件像素测试跨 APK 调用 Compose 的公开、内联 ABI。
# 仅 -PreleaseAndroidTest 加载本文件；标准 Release 不加入这些保留规则。
-keep,includedescriptorclasses class androidx.compose.** { *; }
-keep,includedescriptorclasses class androidx.collection.** { *; }

# Presentation 宿主的 owners 与隔离图片入口同样跨 APK 实现接口。
-keep class androidx.lifecycle.Lifecycle { *; }
-keep class androidx.lifecycle.Lifecycle$State { *; }
-keep interface androidx.lifecycle.LifecycleOwner { *; }
-keep class androidx.lifecycle.LifecycleRegistry { *; }
-keep class androidx.lifecycle.ViewModelStore { *; }
-keep interface androidx.lifecycle.ViewModelStoreOwner { *; }
-keep class androidx.lifecycle.ViewTreeLifecycleOwner { *; }
-keep class androidx.lifecycle.ViewTreeViewModelStoreOwner { *; }
-keep class androidx.savedstate.SavedStateRegistry { *; }
-keep class androidx.savedstate.SavedStateRegistryController { *; }
-keep class androidx.savedstate.SavedStateRegistryController$Companion { *; }
-keep interface androidx.savedstate.SavedStateRegistryOwner { *; }
-keep class androidx.savedstate.ViewTreeSavedStateRegistryOwner { *; }
-keep interface dagger.hilt.internal.GeneratedComponent { *; }
-keep interface dagger.hilt.internal.GeneratedComponentManager { *; }

# Preserve the cross-APK entry points used by the isolated shared-vector fixture.
-keep class com.asmr.player.ui.library.AlbumListIconScopeKt { *; }

# Actual metadata and prefetch entry points for isolated retained-measurement checks.
-keep,includedescriptorclasses class com.asmr.player.ui.library.AlbumMetaLightweightKt { *; }
-keep class com.asmr.player.ui.common.LazyListCompositionPrefetchKt { *; }
