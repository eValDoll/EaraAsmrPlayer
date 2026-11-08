package com.asmr.player.subtitle

import com.asmr.player.i18n.appString
import com.asmr.player.R

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
                if (!hasArm64) add(appString(R.string.ui_requires_arm64_v8a_64_bit_arm_device))
                if (activityManager?.isLowRamDevice == true) add(appString(R.string.ui_the_current_device_is_marked_by_the_system))
                if (totalMemoryBytes in 1 until MIN_TOTAL_MEMORY_BYTES) {
                    add(appString(R.string.ui_not_enough_memory_6gb))
                }
                if (processorCount < MIN_PROCESSOR_COUNT) {
                    add(appString(R.string.ui_less_than_cpu_cores, MIN_PROCESSOR_COUNT))
                }
            }
            val supported = reasons.isEmpty()
            val deviceSummary = appString(R.string.ui_current_device_memory_cores_cpu, formatMemory(totalMemoryBytes), processorCount)
            return SubtitleDeviceCapability(
                supported = supported,
                message = if (supported) {
                    appString(R.string.ui_current_devices_support_local_subtitle_generation, deviceSummary)
                } else {
                    appString(R.string.ui_the_current_device_does_not_support_local_subtitle, reasons.joinToString("；"), deviceSummary)
                }
            )
        }

        private fun formatMemory(bytes: Long): String {
            if (bytes <= 0L) return appString(R.string.ui_unknown)
            val gib = bytes.toDouble() / 1024.0 / 1024.0 / 1024.0
            return String.format(Locale.US, "%.1fGB", gib)
        }
    }
}
