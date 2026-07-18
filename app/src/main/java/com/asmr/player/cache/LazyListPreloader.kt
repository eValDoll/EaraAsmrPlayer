package com.asmr.player.cache

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull

private const val MaxRememberedPreloadKeys = 96
private const val ScrollingLeadViewportMultiplier = 2
private const val IdleLeadViewportMultiplier = 3

@Composable
fun LazyListPreloader(
    state: LazyListState,
    models: List<Any>,
    preloadNext: Int = 24,
    preloadNextWhileScrolling: Int = 16,
    preloadSize: IntSize? = null,
    cacheManagerProvider: () -> ImageCacheManager
) {
    val manager = remember { cacheManagerProvider() }
    val preloadedModels = remember { LinkedHashSet<Any>() }
    LaunchedEffect(state, models, preloadNext, preloadNextWhileScrolling, preloadSize) {
        preloadedModels.clear()
        snapshotFlow {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            val scrolling = state.isScrollInProgress
            val lastVisibleIndex = visibleItems.maxOfOrNull { it.index } ?: -1
            scrolling to (lastVisibleIndex to visibleItems.size)
        }
            .mapNotNull { (scrolling, window) ->
                val (lastVisibleIndex, visibleItemCount) = window
                resolveLazyListPreloadRange(
                    lastVisibleIndex = lastVisibleIndex,
                    visibleItemCount = visibleItemCount,
                    itemCount = models.size,
                    preloadNext = preloadNext,
                    preloadNextWhileScrolling = preloadNextWhileScrolling,
                    isScrolling = scrolling
                )
            }
            .distinctUntilChanged()
            .collect { range ->
                val toPreload = range.mapNotNull { index ->
                    models[index].takeIf { model -> preloadedModels.add(model) }
                }
                manager.preload(toPreload, preloadSize)
                preloadedModels.trimOldest(maxSize = MaxRememberedPreloadKeys)
            }
    }
}

@Composable
fun LazyListPreloader(
    state: LazyListState,
    itemCount: Int,
    preloadNext: Int = 24,
    preloadNextWhileScrolling: Int = 16,
    preloadSize: IntSize? = null,
    cacheManagerProvider: () -> ImageCacheManager,
    modelAt: (Int) -> Any?
) {
    val manager = remember { cacheManagerProvider() }
    val preloadedModels = remember { LinkedHashSet<Any>() }
    LaunchedEffect(state, itemCount, preloadNext, preloadNextWhileScrolling, preloadSize) {
        preloadedModels.clear()
        snapshotFlow {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            val scrolling = state.isScrollInProgress
            val lastVisibleIndex = visibleItems.maxOfOrNull { it.index } ?: -1
            scrolling to (lastVisibleIndex to visibleItems.size)
        }
            .mapNotNull { (scrolling, window) ->
                val (lastVisibleIndex, visibleItemCount) = window
                resolveLazyListPreloadRange(
                    lastVisibleIndex = lastVisibleIndex,
                    visibleItemCount = visibleItemCount,
                    itemCount = itemCount,
                    preloadNext = preloadNext,
                    preloadNextWhileScrolling = preloadNextWhileScrolling,
                    isScrolling = scrolling
                )
            }
            .distinctUntilChanged()
            .collect { range ->
                val toPreload = range.mapNotNull { index ->
                    modelAt(index)?.takeIf { model -> preloadedModels.add(model) }
                }
                if (toPreload.isNotEmpty()) {
                    manager.preload(toPreload, preloadSize)
                    preloadedModels.trimOldest(maxSize = MaxRememberedPreloadKeys)
                }
            }
    }
}

@Composable
fun LazyStaggeredGridPreloader(
    state: LazyStaggeredGridState,
    itemCount: Int,
    preloadNext: Int = 24,
    preloadNextWhileScrolling: Int = 16,
    preloadSize: IntSize? = null,
    cacheManagerProvider: () -> ImageCacheManager,
    modelAt: (Int) -> Any?
) {
    val manager = remember { cacheManagerProvider() }
    val preloadedModels = remember { LinkedHashSet<Any>() }
    LaunchedEffect(state, itemCount, preloadNext, preloadNextWhileScrolling, preloadSize) {
        preloadedModels.clear()
        snapshotFlow {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            val scrolling = state.isScrollInProgress
            val lastVisibleIndex = visibleItems.maxOfOrNull { it.index } ?: -1
            scrolling to (lastVisibleIndex to visibleItems.size)
        }
            .mapNotNull { (scrolling, window) ->
                val (lastVisibleIndex, visibleItemCount) = window
                resolveLazyListPreloadRange(
                    lastVisibleIndex = lastVisibleIndex,
                    visibleItemCount = visibleItemCount,
                    itemCount = itemCount,
                    preloadNext = preloadNext,
                    preloadNextWhileScrolling = preloadNextWhileScrolling,
                    isScrolling = scrolling
                )
            }
            .distinctUntilChanged()
            .collect { range ->
                val toPreload = range.mapNotNull { index ->
                    modelAt(index)?.takeIf { model -> preloadedModels.add(model) }
                }
                if (toPreload.isNotEmpty()) {
                    manager.preload(toPreload, preloadSize)
                    preloadedModels.trimOldest(maxSize = MaxRememberedPreloadKeys)
                }
            }
    }
}

internal fun resolveLazyListPreloadRange(
    lastVisibleIndex: Int,
    visibleItemCount: Int,
    itemCount: Int,
    preloadNext: Int,
    preloadNextWhileScrolling: Int,
    isScrolling: Boolean
): IntRange? {
    val leadCount = resolveLazyListPreloadLeadCount(
        visibleItemCount = visibleItemCount,
        preloadNext = preloadNext,
        preloadNextWhileScrolling = preloadNextWhileScrolling,
        isScrolling = isScrolling
    )
    val start = (lastVisibleIndex + 1).coerceAtLeast(0)
    val end = (start + leadCount).coerceAtMost(itemCount)
    return if (start >= end) null else start until end
}

internal fun resolveLazyListPreloadLeadCount(
    visibleItemCount: Int,
    preloadNext: Int,
    preloadNextWhileScrolling: Int,
    isScrolling: Boolean
): Int {
    val viewportLead = visibleItemCount.coerceAtLeast(1) *
        if (isScrolling) ScrollingLeadViewportMultiplier else IdleLeadViewportMultiplier
    val baseLead = if (isScrolling) preloadNextWhileScrolling else preloadNext
    return maxOf(baseLead, viewportLead)
}

private fun <T> LinkedHashSet<T>.trimOldest(maxSize: Int) {
    while (size > maxSize) {
        val iterator = iterator()
        if (!iterator.hasNext()) return
        iterator.next()
        iterator.remove()
    }
}
