package com.asmr.player.ui.common

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DeferredPageContentTest {
    @After
    fun applyPendingSnapshots() {
        Snapshot.sendApplyNotifications()
        // 调度测试没有 ComposeRule；在 Robolectric 重置 Looper 前执行快照通知的主线程回调。
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun hiddenItemsAreAdmittedIndividuallyAcrossCommitFrames() = runFrameTest {
        val frames = Frames()
        val state = DeferredPageContentState(mutableStateOf(false))
        val first = DeferredPageContentState.Item()
        val second = DeferredPageContentState.Item()
        state.register(first)
        state.register(second)
        val warming = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { state.awaitReady() }
        try {
            assertTrue(first.admitted)
            assertFalse(second.admitted)
            frames.send()
            assertFalse(second.admitted)
            frames.send()
            assertFalse(second.admitted)

            frames.send()
            assertTrue(second.admitted)
            frames.send()
            frames.send()
            assertFalse(warming.isCompleted)
            frames.send()
            warming.join()
        } finally {
            warming.cancelAndJoin()
        }
    }

    @Test
    fun cancellationRetainsAdmittedItemsAndResumeWarmsOnlyTheRemainingItems() = runFrameTest {
        val frames = Frames()
        val state = DeferredPageContentState(mutableStateOf(false))
        val first = DeferredPageContentState.Item()
        val second = DeferredPageContentState.Item()
        state.register(first)
        state.register(second)
        val cancelled = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { state.awaitReady() }
        assertTrue(first.admitted)
        assertFalse(second.admitted)
        cancelled.cancelAndJoin()

        val resumed = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { state.awaitReady() }
        try {
            assertTrue(first.admitted)
            assertTrue(second.admitted)
            repeat(4) { frames.send() }
            resumed.join()
        } finally {
            resumed.cancelAndJoin()
        }
    }

    @Test
    fun itemsRegisteredBetweenEmptyFramesAreWarmedBeforeReadinessCompletes() = runFrameTest {
        val frames = Frames()
        val state = DeferredPageContentState(mutableStateOf(false))
        val warming = launch(frames.clock, start = CoroutineStart.UNDISPATCHED) { state.awaitReady() }
        try {
            frames.send()
            assertFalse(warming.isCompleted)
            val item = DeferredPageContentState.Item()
            state.register(item)
            frames.send()
            assertTrue(item.admitted)
            assertFalse(warming.isCompleted)
            repeat(4) { frames.send() }
            warming.join()
        } finally {
            warming.cancelAndJoin()
        }
    }

    private fun runFrameTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        // 保留 Robolectric 测试线程；JUnit timeout 会另建线程执行 Snapshot 与帧时钟操作。
        withTimeout(10_000, block)
    }

    private class Frames {
        val clock = BroadcastFrameClock()
        private var timeNanos = 0L

        suspend fun send() {
            withTimeout(5_000) {
                while (!clock.hasAwaiters) yield()
            }
            timeNanos += 16_000_000L
            clock.sendFrame(timeNanos)
            yield()
        }
    }
}
