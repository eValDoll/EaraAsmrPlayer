package com.asmr.player.data.remote.download

import android.app.ActivityManager
import android.content.Context
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

const val DOWNLOAD_STATE_QUEUED = "QUEUED"

object DownloadRuntimeConfig {
    private const val LOW_RAM_DOWNLOAD_PARALLELISM = 1
    private const val DEFAULT_DOWNLOAD_PARALLELISM = 2
    private const val LOW_RAM_WORK_MANAGER_PARALLELISM = 1
    private const val DEFAULT_WORK_MANAGER_PARALLELISM = 2
    private const val WORK_MANAGER_TASK_PARALLELISM = 2
    private const val MB = 1024L * 1024L
    private const val LOW_RAM_MIN_HEAP_HEADROOM_MB = 32L
    private const val DEFAULT_MIN_HEAP_HEADROOM_MB = 48L
    private const val LOW_RAM_MIN_SYSTEM_AVAIL_MB = 192L
    private const val DEFAULT_MIN_SYSTEM_AVAIL_MB = 256L

    fun isLowRamDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClass = activityManager?.memoryClass ?: Int.MAX_VALUE
        return activityManager?.isLowRamDevice == true || memoryClass <= 192
    }

    fun maxConcurrentDownloads(context: Context): Int {
        return if (isLowRamDevice(context)) LOW_RAM_DOWNLOAD_PARALLELISM else DEFAULT_DOWNLOAD_PARALLELISM
    }

    fun isMemoryConstrained(context: Context): Boolean {
        val lowRamDevice = isLowRamDevice(context)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()
        val heapHeadroom = (runtime.maxMemory() - usedHeap).coerceAtLeast(0L)
        val minHeapHeadroom = if (lowRamDevice) {
            LOW_RAM_MIN_HEAP_HEADROOM_MB * MB
        } else {
            DEFAULT_MIN_HEAP_HEADROOM_MB * MB
        }

        val minSystemAvail = if (lowRamDevice) {
            LOW_RAM_MIN_SYSTEM_AVAIL_MB * MB
        } else {
            DEFAULT_MIN_SYSTEM_AVAIL_MB * MB
        }

        return memoryInfo.lowMemory ||
            heapHeadroom < minHeapHeadroom ||
            memoryInfo.availMem in 1 until minSystemAvail
    }

    fun createWorkManagerExecutor(context: Context): Executor {
        val threadCount = if (isLowRamDevice(context)) {
            LOW_RAM_WORK_MANAGER_PARALLELISM
        } else {
            DEFAULT_WORK_MANAGER_PARALLELISM
        }
        return Executors.newFixedThreadPool(threadCount, namedThreadFactory("wm-download-"))
    }

    fun createWorkManagerTaskExecutor(): Executor {
        return Executors.newFixedThreadPool(WORK_MANAGER_TASK_PARALLELISM, namedThreadFactory("wm-task-"))
    }

    private fun namedThreadFactory(prefix: String): ThreadFactory {
        val threadId = AtomicInteger(0)
        return ThreadFactory { runnable ->
            Thread(runnable, "$prefix${threadId.incrementAndGet()}")
        }
    }
}
