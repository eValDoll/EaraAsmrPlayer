# AndroidJUnitRunner 1.5.2 运行 Release instrumentation 时会从目标应用进程访问该类。
-keep class androidx.tracing.Trace { *; }

# AndroidX Test Platform 的 Kotlin 实现运行在目标应用的 instrumentation 进程中。
-keep class kotlin.** { *; }

# Release instrumentation 会直接调用目标应用中的挂起接口。
-keep class kotlinx.coroutines.** { *; }

# 设备端播放回归测试需要通过公开 Media3 API 连接 Release 播放服务。
-keep class androidx.media3.** { *; }
-keep class com.google.common.util.concurrent.** { *; }
