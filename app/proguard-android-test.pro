# AndroidJUnitRunner 1.5.2 运行 Release instrumentation 时会从目标应用进程访问该类。
-keep class androidx.tracing.Trace { *; }

# AndroidX Test Platform 的 Kotlin 实现运行在目标应用的 instrumentation 进程中。
-keep class kotlin.** { *; }

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
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
