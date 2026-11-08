package com.asmr.player.ui.common

import android.app.Application
import android.os.Build
import android.os.Looper
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@OptIn(ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [24, 31, 32, 33, 34])
@LooperMode(LooperMode.Mode.PAUSED)
class FrameDeadlinePrefetchSchedulerTest {
    private val view = PrefetchView()
    // 直接构建真实调度器，不能经过 Robolectric 下返回空实现的 remember 入口。
    private val scheduler = FrameDeadlinePrefetchScheduler(view)
    private var executions = 0

    @Test
    fun attachedViewCanStartAndDisposeScheduler() {
        scheduler.onRemembered()
        enqueueRequest()
        executeFrame()
        assertEquals(1, executions)

        enqueueRequest()
        scheduler.doFrame(System.nanoTime())
        scheduler.onForgotten()
        assertNull(view.posted)
        scheduler.onAbandoned()
    }

    @Test
    fun inactivePageDefersWorkUntilReactivated() {
        scheduler.onRemembered()
        scheduler.setPageActive(false)
        enqueueRequest()
        executeFrame()
        assertEquals(0, executions)

        scheduler.setPageActive(true)
        executeFrame()
        assertEquals(1, executions)
        scheduler.onForgotten()
    }

    @Test
    fun hiddenWindowDefersWorkUntilVisibleDraw() {
        scheduler.onRemembered()
        enqueueRequest()
        scheduler.doFrame(System.nanoTime())
        view.windowVisibilityState = View.GONE
        view.runPosted()
        assertEquals(0, executions)
        view.viewTreeObserver.dispatchOnGlobalLayout()

        view.windowVisibilityState = View.VISIBLE
        if (Build.VERSION.SDK_INT < 34) {
            assertFalse(view.viewTreeObserver.dispatchOnPreDraw())
        } else {
            view.viewTreeObserver.dispatchOnGlobalLayout()
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        assertNotNull(view.posted)
        executeFrame()
        assertEquals(1, executions)
        scheduler.onForgotten()
    }

    @Test
    fun detachCancelsPostedWorkAndReattachRestoresObservation() {
        scheduler.onRemembered()
        enqueueRequest()
        scheduler.doFrame(System.nanoTime())
        view.attached = false
        scheduler.onViewDetachedFromWindow(view)
        assertNull(view.posted)
        executeFrame()
        assertEquals(0, executions)

        view.attached = true
        scheduler.onViewAttachedToWindow(view)
        executeFrame()
        assertEquals(1, executions)
        scheduler.onForgotten()
    }

    private fun enqueueRequest() {
        scheduler.schedulePrefetch(RecordingPrefetchRequest { scope ->
            assertTrue(scope.availableTimeNanos() > 0L)
            executions += 1
        })
    }

    private fun executeFrame() {
        scheduler.doFrame(System.nanoTime())
        view.runPosted()
    }

    private class PrefetchView : View(ApplicationProvider.getApplicationContext<Application>()) {
        var attached = true
        var windowVisibilityState = VISIBLE
        var posted: Runnable? = null

        override fun isAttachedToWindow(): Boolean = attached
        override fun getWindowVisibility(): Int = windowVisibilityState
        override fun isShown(): Boolean = true
        override fun post(action: Runnable): Boolean {
            posted = action
            return true
        }

        override fun removeCallbacks(action: Runnable): Boolean {
            if (posted !== action) return false
            posted = null
            return true
        }

        fun runPosted() {
            val action = posted
            posted = null
            action?.run()
        }
    }
}
