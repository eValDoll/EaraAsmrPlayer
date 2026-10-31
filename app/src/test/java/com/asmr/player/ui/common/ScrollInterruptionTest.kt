package com.asmr.player.ui.common

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
@OptIn(ExperimentalFoundationApi::class)
class ScrollInterruptionTest {
    @After
    fun flushStandaloneScrollState() {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun touchesBeforeFirstLayoutNeverQueueScrollTransactions() {
        val parent = Job()
        val scope = CoroutineScope(parent + Dispatchers.Unconfined)
        val list = LazyListState()
        val grid = LazyStaggeredGridState()
        try {
            repeat(10) {
                list.interruptOngoingScroll(scope)
                grid.interruptOngoingScroll(scope)
            }
            assertFalse(parent.children.any())
        } finally {
            parent.cancel()
        }
    }

    @Test
    fun touchingAnActiveScrollInterruptsItsOwner() = runBlocking {
        val scroll = ScrollableState { it }
        val fling = launch(start = CoroutineStart.UNDISPATCHED) {
            scroll.scroll { awaitCancellation() }
        }
        assertTrue(scroll.isScrollInProgress)
        scroll.interruptOngoingScroll(this)
        withTimeout(2_000) { fling.join() }
        assertTrue(fling.isCancelled)
        assertFalse(scroll.isScrollInProgress)
    }
}
