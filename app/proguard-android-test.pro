# AndroidJUnitRunner 1.5.2 运行 Release instrumentation 时会从目标应用进程访问该类。
-keep class androidx.tracing.Trace { *; }

# AndroidX Test Platform 的 Kotlin 实现运行在目标应用的 instrumentation 进程中。
-keep class kotlin.** { *; }
