@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.asmr.player.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.PrefetchRequest
import androidx.compose.foundation.lazy.layout.PrefetchScheduler
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [33])
@OptIn(ExperimentalFoundationApi::class)
class LazyStaggeredGridPrefetchTest {
    @Test
    fun restoresEveryLaneFromTheOriginalSaverWithTheCurrentScheduler() {
        val scheduler = object : PrefetchScheduler {
            override fun schedulePrefetch(prefetchRequest: PrefetchRequest) = Unit
        }
        val initial = LazyStaggeredGridState(intArrayOf(7, 10, 8), intArrayOf(23, 91, 42), null)
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any) = true
        }
        val saved = with(LazyStaggeredGridState.Saver) { scope.save(initial) }!!
        val saver = staggeredGridStateSaver(scheduler)
        val restored = saver.restore(saved)!!

        assertArrayEquals(initial.scrollPosition.indices, restored.scrollPosition.indices)
        assertArrayEquals(initial.scrollPosition.scrollOffsets, restored.scrollPosition.scrollOffsets)
        assertSame(scheduler, restored.prefetchState.prefetchScheduler)

        val roundTrip = LazyStaggeredGridState.Saver.restore(with(saver) { scope.save(restored) }!!)!!
        assertArrayEquals(initial.scrollPosition.indices, roundTrip.scrollPosition.indices)
        assertArrayEquals(initial.scrollPosition.scrollOffsets, roundTrip.scrollPosition.scrollOffsets)
    }
}
