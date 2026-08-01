package com.asmr.player.performance

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

/**
 * 协调可延后的后台工作与高刷新率交互帧。
 *
 * 这里只保存单调时钟截止点，不进入 Compose 状态系统，因此滚动上报不会引发重组。
 */
object UiFrameWorkCoordinator {
    private const val DefaultQuietWindowMs = 400L
    private val criticalUntilNanos = AtomicLong(0L)

    fun markFrameCritical(quietWindowMs: Long = DefaultQuietWindowMs) {
        val candidate = SystemClock.elapsedRealtimeNanos() +
            quietWindowMs.coerceAtLeast(0L) * 1_000_000L
        while (true) {
            val current = criticalUntilNanos.get()
            if (candidate <= current || criticalUntilNanos.compareAndSet(current, candidate)) return
        }
    }

    fun isFrameCritical(): Boolean {
        return criticalUntilNanos.get() > SystemClock.elapsedRealtimeNanos()
    }

    suspend fun awaitFrameQuiet() {
        while (true) {
            val remainingNanos = criticalUntilNanos.get() - SystemClock.elapsedRealtimeNanos()
            if (remainingNanos <= 0L) return
            delay((remainingNanos / 1_000_000L).coerceAtLeast(1L))
        }
    }
}
