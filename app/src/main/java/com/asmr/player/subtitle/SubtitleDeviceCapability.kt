package com.asmr.player.subtitle

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.util.Locale

internal data class SubtitleDeviceCapability(
    val supported: Boolean,
    val message: String
) {
    companion object {
        private const val MIN_TOTAL_MEMORY_BYTES = 5_500_000_000L
        private const val MIN_PROCESSOR_COUNT = 4

        fun evaluate(context: Context): SubtitleDeviceCapability {
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)
            val totalMemoryBytes = memoryInfo.totalMem
            val processorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val hasArm64 = Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" } ||
                Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
            val reasons = buildList {
                if (!hasArm64) add("需要 arm64-v8a 64 位 ARM 设备")
                if (activityManager?.isLowRamDevice == true) add("当前设备被系统标记为低内存设备")
                if (totalMemoryBytes in 1 until MIN_TOTAL_MEMORY_BYTES) {
                    add("内存不足 6GB")
                }
                if (processorCount < MIN_PROCESSOR_COUNT) {
                    add("CPU 核心少于 $MIN_PROCESSOR_COUNT 个")
                }
            }
            val supported = reasons.isEmpty()
            val deviceSummary = "当前设备：${formatMemory(totalMemoryBytes)} 内存，$processorCount 核 CPU"
            return SubtitleDeviceCapability(
                supported = supported,
                message = if (supported) {
                    "当前设备支持本地字幕生成。$deviceSummary"
                } else {
                    "当前设备不支持本地字幕生成：${reasons.joinToString("；")}。$deviceSummary"
                }
            )
        }

        private fun formatMemory(bytes: Long): String {
            if (bytes <= 0L) return "未知"
            val gib = bytes.toDouble() / 1024.0 / 1024.0 / 1024.0
            return String.format(Locale.US, "%.1fGB", gib)
        }
    }
}
