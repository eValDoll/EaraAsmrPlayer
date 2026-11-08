/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.asmr.player.ui.common

import android.os.Build
import android.view.Choreographer
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.PrefetchRequest
import androidx.compose.foundation.lazy.layout.PrefetchRequestScope
import androidx.compose.foundation.lazy.layout.PrefetchScheduler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import java.util.ArrayDeque

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun rememberFrameDeadlinePrefetchScheduler(): PrefetchScheduler {
    // 与 Foundation 保持一致：Robolectric 的帧时间不能用于实际预取调度。
    if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) return TestPrefetchScheduler
    val view = LocalView.current
    val scheduler = remember(view) { FrameDeadlinePrefetchScheduler(view) }
    val pageActive = LocalPageAnimationsActive.current
    SideEffect { scheduler.setPageActive(pageActive) }
    return scheduler
}

/** 每帧共享一个绝对截止时间；刷新率变化和超时不会给后续请求重新分配预算。 */
@OptIn(ExperimentalFoundationApi::class)
internal class PrefetchFrameBudget(
    private val nanoTime: () -> Long = System::nanoTime,
) : PrefetchRequestScope {
    private var deadlineNanos = 0L
    private var intervalNanos = 0L

    fun beginFrame(frameTimeNanos: Long, refreshRate: Float) {
        val validRate = refreshRate.takeIf { it.isFinite() && it in 30f..360f } ?: 60f
        intervalNanos = (1_000_000_000.0 / validRate).toLong()
        deadlineNanos = frameTimeNanos + intervalNanos
    }

    fun clear() {
        deadlineNanos = 0L
        intervalNanos = 0L
    }

    override fun availableTimeNanos(): Long =
        (deadlineNanos - nanoTime()).coerceIn(0L, intervalNanos)
}

// 基于 Foundation 1.7.8 AndroidPrefetchScheduler；避免其静态缓存启动时的刷新率。
@OptIn(ExperimentalFoundationApi::class)
private class FrameDeadlinePrefetchScheduler(
    private val view: View,
) : PrefetchScheduler, RememberObserver, Runnable, Choreographer.FrameCallback,
    View.OnAttachStateChangeListener, ViewTreeObserver.OnWindowVisibilityChangeListener,
    ViewTreeObserver.OnGlobalLayoutListener {
    private val requests = ArrayDeque<PrefetchRequest>()
    private val choreographer = Choreographer.getInstance()
    private val budget = PrefetchFrameBudget()
    private var observedTree: ViewTreeObserver? = null
    private var remembered = false
    private var pageActive = true
    private var frameCallbackScheduled = false
    private var runnableScheduled = false
    private var running = false

    private val canPrefetch: Boolean
        get() = remembered && pageActive && view.isAttachedToWindow &&
            view.windowVisibility == View.VISIBLE && view.isShown

    override fun schedulePrefetch(prefetchRequest: PrefetchRequest) {
        requests.addLast(prefetchRequest)
        scheduleNextFrame()
    }

    fun setPageActive(active: Boolean) {
        if (pageActive == active) return
        pageActive = active
        updateVisibility()
    }

    private fun scheduleNextFrame() {
        if (requests.isEmpty() || frameCallbackScheduled || runnableScheduled || running || !canPrefetch) return
        budget.clear()
        frameCallbackScheduled = true
        choreographer.postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameCallbackScheduled = false
        if (!canPrefetch || requests.isEmpty()) {
            budget.clear()
            return
        }
        // 只在有预取请求的帧查询，不能沿用页面创建时可能为 60 Hz 的值。
        budget.beginFrame(frameTimeNanos, view.display?.refreshRate ?: 60f)
        runnableScheduled = view.post(this)
    }

    override fun run() {
        if (!runnableScheduled) return
        runnableScheduled = false
        if (!canPrefetch) {
            budget.clear()
            return
        }
        running = true
        try {
            while (requests.isNotEmpty() && budget.availableTimeNanos() > 0L) {
                val request = requests.peekFirst() ?: break
                val hasMoreWork = with(request) { budget.execute() }
                if (hasMoreWork) break
                requests.removeFirst()
            }
        } finally {
            running = false
        }
        if (requests.isEmpty()) budget.clear() else scheduleNextFrame()
    }

    private fun cancelCallbacks() {
        view.removeCallbacks(this)
        choreographer.removeFrameCallback(this)
        runnableScheduled = false
        frameCallbackScheduled = false
        budget.clear()
    }

    private fun updateVisibility() {
        if (canPrefetch) scheduleNextFrame() else cancelCallbacks()
    }

    override fun onWindowVisibilityChanged(visibility: Int) = updateVisibility()

    override fun onGlobalLayout() = updateVisibility()

    private fun observeTree() {
        val tree = view.viewTreeObserver
        if (observedTree === tree || !tree.isAlive) return
        stopObservingTree()
        tree.addOnWindowVisibilityChangeListener(this)
        tree.addOnGlobalLayoutListener(this)
        observedTree = tree
    }

    private fun stopObservingTree() {
        observedTree?.takeIf { it.isAlive }?.let { tree ->
            tree.removeOnWindowVisibilityChangeListener(this)
            tree.removeOnGlobalLayoutListener(this)
        }
        observedTree = null
    }

    override fun onViewAttachedToWindow(view: View) {
        observeTree()
        updateVisibility()
    }

    override fun onViewDetachedFromWindow(view: View) {
        stopObservingTree()
        cancelCallbacks()
    }

    override fun onRemembered() {
        remembered = true
        view.addOnAttachStateChangeListener(this)
        if (view.isAttachedToWindow) observeTree()
        scheduleNextFrame()
    }

    override fun onForgotten() {
        remembered = false
        view.removeOnAttachStateChangeListener(this)
        stopObservingTree()
        cancelCallbacks()
        requests.clear()
    }

    override fun onAbandoned() = onForgotten()
}

@OptIn(ExperimentalFoundationApi::class)
private object TestPrefetchScheduler : PrefetchScheduler {
    override fun schedulePrefetch(prefetchRequest: PrefetchRequest) = Unit
}
