package com.asmr.player.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.foundation.lazy.layout.PrefetchScheduler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

private const val DefaultForwardCompositionPrefetchCount = 4
private const val DefaultBackwardCompositionPrefetchCount = 4
private const val OppositeDirectionCompositionPrefetchCount = 1

@OptIn(ExperimentalFoundationApi::class)
internal class EaraLazyListPrefetchStrategy(
    private val forwardItemCount: Int,
    private val backwardItemCount: Int,
    override val prefetchScheduler: PrefetchScheduler,
) : LazyListPrefetchStrategy {
    private val handles = mutableMapOf<Int, LazyLayoutPrefetchState.PrefetchHandle>()
    private var lastFirstVisibleIndex = -1
    private var lastLastVisibleIndex = -1
    private var lastTotalItemCount = -1
    private var scrollDirection = 1

    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) {
        updatePrefetches(layoutInfo, delta)
        // 与 Foundation 1.7.8 一致，仅在下一帧预计会进屏时跳过耗时估计；
        // request 仍须 scheduler 当前帧有剩余截止预算才开始。
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return
        if (delta < 0f) {
            val lastItem = visibleItems.last()
            val distance = lastItem.offset + lastItem.size + layoutInfo.mainAxisItemSpacing -
                layoutInfo.viewportEndOffset
            if (distance < -delta) handles[lastItem.index + 1]?.markAsUrgent()
        } else if (delta > 0f) {
            val firstItem = visibleItems.first()
            val distance = layoutInfo.viewportStartOffset - firstItem.offset
            if (distance < delta) handles[firstItem.index - 1]?.markAsUrgent()
        }
    }

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {
        updatePrefetches(layoutInfo)
    }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) = Unit

    private fun LazyListPrefetchScope.updatePrefetches(
        layoutInfo: LazyListLayoutInfo,
        delta: Float = 0f,
    ) {
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            handles.values.forEach { it.cancel() }
            handles.clear()
            lastFirstVisibleIndex = -1
            lastLastVisibleIndex = -1
            lastTotalItemCount = -1
            return
        }
        val firstVisibleIndex = visibleItems.first().index
        val lastVisibleIndex = visibleItems.last().index
        val nextDirection = when {
            delta < 0f -> 1
            delta > 0f -> -1
            lastFirstVisibleIndex >= 0 && firstVisibleIndex > lastFirstVisibleIndex -> 1
            lastFirstVisibleIndex >= 0 && firstVisibleIndex < lastFirstVisibleIndex -> -1
            else -> scrollDirection
        }
        val directionChanged = nextDirection != scrollDirection
        scrollDirection = nextDirection
        if (
            !directionChanged &&
            firstVisibleIndex == lastFirstVisibleIndex &&
            lastVisibleIndex == lastLastVisibleIndex &&
            layoutInfo.totalItemsCount == lastTotalItemCount
        ) {
            return
        }
        lastFirstVisibleIndex = firstVisibleIndex
        lastLastVisibleIndex = lastVisibleIndex
        lastTotalItemCount = layoutInfo.totalItemsCount

        val activeForwardItemCount = if (scrollDirection >= 0) {
            forwardItemCount
        } else {
            minOf(forwardItemCount, OppositeDirectionCompositionPrefetchCount)
        }
        val activeBackwardItemCount = if (scrollDirection < 0) {
            backwardItemCount
        } else {
            minOf(backwardItemCount, OppositeDirectionCompositionPrefetchCount)
        }

        val targetIndices = resolveCompositionPrefetchIndices(
            firstVisibleIndex = firstVisibleIndex,
            lastVisibleIndex = lastVisibleIndex,
            totalItemCount = layoutInfo.totalItemsCount,
            forwardItemCount = activeForwardItemCount,
            backwardItemCount = activeBackwardItemCount,
            scrollingForward = scrollDirection >= 0,
        )

        val iterator = handles.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!targetIndices.containsIndex(entry.key)) {
                entry.value.cancel()
                iterator.remove()
            }
        }
        targetIndices.forEach { index ->
            if (index !in handles) {
                handles[index] = schedulePrefetch(index)
            }
        }
    }
}

private fun IntArray.containsIndex(index: Int): Boolean {
    for (candidate in this) {
        if (candidate == index) return true
    }
    return false
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun rememberSaveablePrefetchedLazyListState(
    stateKey: Any?,
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    forwardCompositionPrefetchCount: Int = DefaultForwardCompositionPrefetchCount,
    backwardCompositionPrefetchCount: Int = DefaultBackwardCompositionPrefetchCount,
): LazyListState {
    val scheduler = rememberFrameDeadlinePrefetchScheduler()
    val prefetchStrategy = remember(
        stateKey,
        forwardCompositionPrefetchCount,
        backwardCompositionPrefetchCount,
        scheduler,
    ) {
        EaraLazyListPrefetchStrategy(
            forwardItemCount = forwardCompositionPrefetchCount.coerceAtLeast(0),
            backwardItemCount = backwardCompositionPrefetchCount.coerceAtLeast(0),
            prefetchScheduler = scheduler,
        )
    }
    val saver = remember(prefetchStrategy) {
        Saver<LazyListState, List<Int>>(
            save = { state ->
                listOf(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
            },
            restore = { restored ->
                LazyListState(
                    firstVisibleItemIndex = restored.getOrElse(0) { 0 },
                    firstVisibleItemScrollOffset = restored.getOrElse(1) { 0 },
                    prefetchStrategy = prefetchStrategy,
                )
            },
        )
    }
    return rememberSaveable(stateKey, saver = saver) {
        LazyListState(
            firstVisibleItemIndex = initialFirstVisibleItemIndex,
            firstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset,
            prefetchStrategy = prefetchStrategy,
        )
    }
}

internal fun resolveCompositionPrefetchIndices(
    firstVisibleIndex: Int,
    lastVisibleIndex: Int,
    totalItemCount: Int,
    forwardItemCount: Int,
    backwardItemCount: Int,
    scrollingForward: Boolean = true,
): IntArray {
    if (totalItemCount <= 0 || firstVisibleIndex > lastVisibleIndex) return IntArray(0)
    val result = ArrayList<Int>(
        forwardItemCount.coerceAtLeast(0) + backwardItemCount.coerceAtLeast(0)
    )
    val leadingCount = if (scrollingForward) forwardItemCount else backwardItemCount
    val trailingCount = if (scrollingForward) backwardItemCount else forwardItemCount
    for (offset in 1..leadingCount.coerceAtLeast(0)) {
        val index = if (scrollingForward) lastVisibleIndex + offset else firstVisibleIndex - offset
        if (index in 0 until totalItemCount) result += index
    }
    for (offset in 1..trailingCount.coerceAtLeast(0)) {
        val index = if (scrollingForward) firstVisibleIndex - offset else lastVisibleIndex + offset
        if (index in 0 until totalItemCount) result += index
    }
    return result.toIntArray()
}
