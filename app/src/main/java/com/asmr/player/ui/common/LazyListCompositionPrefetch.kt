package com.asmr.player.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

private const val DefaultForwardCompositionPrefetchCount = 4
private const val DefaultBackwardCompositionPrefetchCount = 4
private const val OppositeDirectionCompositionPrefetchCount = 1

@OptIn(ExperimentalFoundationApi::class)
private class EaraLazyListPrefetchStrategy(
    private val forwardItemCount: Int,
    private val backwardItemCount: Int,
) : LazyListPrefetchStrategy {
    private val handles = mutableMapOf<Int, LazyLayoutPrefetchState.PrefetchHandle>()
    private var lastFirstVisibleIndex = -1
    private var lastLastVisibleIndex = -1
    private var lastTotalItemCount = -1
    private var scrollDirection = 1

    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) {
        updatePrefetches(layoutInfo)
    }

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {
        updatePrefetches(layoutInfo)
    }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) = Unit

    private fun LazyListPrefetchScope.updatePrefetches(layoutInfo: LazyListLayoutInfo) {
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return
        val firstVisibleIndex = visibleItems.first().index
        val lastVisibleIndex = visibleItems.last().index
        if (lastFirstVisibleIndex >= 0) {
            scrollDirection = when {
                firstVisibleIndex > lastFirstVisibleIndex -> 1
                firstVisibleIndex < lastFirstVisibleIndex -> -1
                else -> scrollDirection
            }
        }
        if (
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
    val prefetchStrategy = remember(
        stateKey,
        forwardCompositionPrefetchCount,
        backwardCompositionPrefetchCount,
    ) {
        EaraLazyListPrefetchStrategy(
            forwardItemCount = forwardCompositionPrefetchCount.coerceAtLeast(0),
            backwardItemCount = backwardCompositionPrefetchCount.coerceAtLeast(0),
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
): IntArray {
    if (totalItemCount <= 0 || firstVisibleIndex > lastVisibleIndex) return IntArray(0)
    val result = ArrayList<Int>(
        forwardItemCount.coerceAtLeast(0) + backwardItemCount.coerceAtLeast(0)
    )
    for (offset in 1..forwardItemCount.coerceAtLeast(0)) {
        val index = lastVisibleIndex + offset
        if (index < totalItemCount) result += index
    }
    for (offset in 1..backwardItemCount.coerceAtLeast(0)) {
        val index = firstVisibleIndex - offset
        if (index >= 0) result += index
    }
    return result.toIntArray()
}
