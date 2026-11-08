package com.asmr.player.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.PrefetchRequest
import androidx.compose.foundation.lazy.layout.PrefetchScheduler
import java.lang.reflect.Proxy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class LazyListCompositionPrefetchTest {

    @Test
    fun prefetchIndices_includeForwardAndBackwardItemsWithinBounds() {
        assertArrayEquals(
            intArrayOf(10, 11, 12, 13, 4),
            resolveCompositionPrefetchIndices(
                firstVisibleIndex = 5,
                lastVisibleIndex = 9,
                totalItemCount = 20,
                forwardItemCount = 4,
                backwardItemCount = 1,
            ),
        )
        assertArrayEquals(
            intArrayOf(3, 4),
            resolveCompositionPrefetchIndices(
                firstVisibleIndex = 0,
                lastVisibleIndex = 2,
                totalItemCount = 5,
                forwardItemCount = 4,
                backwardItemCount = 1,
            ),
        )
    }

    @Test
    fun prefetchIndices_supportsSmallSymmetricWindow() {
        assertArrayEquals(
            intArrayOf(17, 18, 9, 8),
            resolveCompositionPrefetchIndices(
                firstVisibleIndex = 10,
                lastVisibleIndex = 16,
                totalItemCount = 30,
                forwardItemCount = 2,
                backwardItemCount = 2,
            ),
        )
    }

    @Test
    fun reverseScroll_updatesWindowBeforeAnyVisibleIndexChanges() {
        val strategy = strategy()
        val scope = RecordingPrefetchScope()
        val info = layout()
        with(strategy) { scope.onVisibleItemsUpdated(info) }
        val nearestBackward = scope.active.getValue(4)

        with(strategy) { scope.onScroll(20f, info) }

        assertEquals(setOf(1, 2, 3, 4, 10), scope.active.keys)
        assertSame(nearestBackward, scope.active.getValue(4))
        assertEquals(listOf(3, 2, 1), scope.scheduled.takeLast(3))
        assertTrue(scope.requests.filter { it.index in 11..13 }.all { it.canceled })

        with(strategy) { scope.onScroll(-20f, info) }
        assertEquals(setOf(4, 10, 11, 12, 13), scope.active.keys)
    }

    @Test
    fun backwardRequests_startWithTheNearestItemInScrollDirection() {
        val strategy = strategy()
        val scope = RecordingPrefetchScope()

        with(strategy) { scope.onScroll(1f, layout()) }

        assertEquals(listOf(4, 3, 2, 1, 10), scope.scheduled)
    }

    @Test
    fun unchangedVisibleIndices_stillMarkOnlyTheItemExpectedNextFrameUrgent() {
        val strategy = strategy()
        val scope = RecordingPrefetchScope()
        with(strategy) { scope.onScroll(-10f, layout(lastOffset = 800)) }
        assertFalse(scope.active.getValue(10).urgent)
        val scheduledBefore = scope.scheduled.toList()

        with(strategy) { scope.onScroll(-10f, layout(lastOffset = 799)) }

        assertTrue(scope.active.getValue(10).urgent)
        assertTrue(scope.active.filterKeys { it != 10 }.values.none { it.urgent })
        assertEquals(scheduledBefore, scope.scheduled)
    }

    @Test
    fun backwardUrgency_usesViewportStartAndDoesNotAffectDistantItems() {
        val strategy = strategy()
        val scope = RecordingPrefetchScope()
        with(strategy) { scope.onScroll(10f, layout(firstOffset = -10)) }
        assertFalse(scope.active.getValue(4).urgent)

        with(strategy) { scope.onScroll(10f, layout(firstOffset = -9)) }

        assertTrue(scope.active.getValue(4).urgent)
        assertTrue(scope.active.filterKeys { it != 4 }.values.none { it.urgent })
    }

    @Test
    fun zeroDeltaAndListBounds_doNotMarkOtherRequestsUrgent() {
        val strategy = strategy()
        val scope = RecordingPrefetchScope()
        with(strategy) { scope.onScroll(0f, layout()) }
        assertTrue(scope.active.values.none { it.urgent })

        with(strategy) { scope.onScroll(20f, layout(firstIndex = 0, lastIndex = 4)) }
        assertEquals(setOf(5), scope.active.keys)
        assertTrue(scope.active.values.none { it.urgent })
    }

    @Test
    fun emptyListCancelsStaleHandlesAndRestoresTheSameWindowInItsLastDirection() {
        val strategy = strategy()
        val scope = RecordingPrefetchScope()
        val info = layout()
        with(strategy) { scope.onScroll(20f, info) }
        val oldHandles = scope.requests.toList()
        assertEquals(listOf(4, 3, 2, 1, 10), scope.scheduled)

        val empty = layout(firstIndex = 1, lastIndex = 0, itemCount = 0)
        with(strategy) { scope.onVisibleItemsUpdated(empty) }
        assertTrue(oldHandles.all { it.canceled })
        assertTrue(scope.active.isEmpty())
        with(strategy) { scope.onVisibleItemsUpdated(empty) }
        assertEquals(5, scope.requests.size)

        with(strategy) { scope.onVisibleItemsUpdated(info) }
        assertEquals(listOf(4, 3, 2, 1, 10), scope.scheduled.takeLast(5))
        assertEquals(setOf(1, 2, 3, 4, 10), scope.active.keys)
        assertEquals(10, scope.requests.size)
        assertTrue(scope.active.values.none { active -> oldHandles.any { it === active } })
    }

    private fun strategy() = EaraLazyListPrefetchStrategy(
        forwardItemCount = 4,
        backwardItemCount = 4,
        prefetchScheduler = object : PrefetchScheduler {
            override fun schedulePrefetch(prefetchRequest: PrefetchRequest) = Unit
        },
    )

    private fun layout(
        firstIndex: Int = 5,
        lastIndex: Int = 9,
        firstOffset: Int = -20,
        lastOffset: Int = 800,
        itemCount: Int = 20,
    ): LazyListLayoutInfo = object : LazyListLayoutInfo {
        override val visibleItemsInfo = (firstIndex..lastIndex).map { itemIndex ->
            object : LazyListItemInfo {
                override val index = itemIndex
                override val key: Any = itemIndex
                override val offset = if (itemIndex == firstIndex) firstOffset else lastOffset
                override val size = 200
            }
        }
        override val viewportStartOffset = 0
        override val viewportEndOffset = 1000
        override val mainAxisItemSpacing = 10
        override val totalItemsCount = itemCount
    }

    private class RecordingPrefetchScope : LazyListPrefetchScope {
        val requests = mutableListOf<RecordingHandle>()
        val scheduled get() = requests.map { it.index }
        val active get() = requests.filterNot { it.canceled }.associateBy { it.index }

        override fun schedulePrefetch(index: Int): LazyLayoutPrefetchState.PrefetchHandle =
            RecordingHandle(index).also(requests::add).handle
    }

    private class RecordingHandle(val index: Int) {
        var canceled = false
        var urgent = false
        // Foundation 的 Kotlin sealed 接口以 JVM 8 发布，用代理记录 API 调用，
        // 避免为测试在生产代码新增 handle 适配层或额外 mock 依赖。
        val handle = Proxy.newProxyInstance(
            LazyLayoutPrefetchState.PrefetchHandle::class.java.classLoader,
            arrayOf(LazyLayoutPrefetchState.PrefetchHandle::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "cancel" -> { canceled = true; null }
                "markAsUrgent" -> { urgent = true; null }
                "equals" -> proxy === args?.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PrefetchHandle($index)"
                else -> error("Unexpected PrefetchHandle method: ${method.name}")
            }
        } as LazyLayoutPrefetchState.PrefetchHandle
    }
}
