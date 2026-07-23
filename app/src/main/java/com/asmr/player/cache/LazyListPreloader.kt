package com.asmr.player.cache

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

private const val MaxRememberedPreloadKeys = 96
private const val ScrollingLeadViewportMultiplier = 2
private const val IdleLeadViewportMultiplier = 3
private const val IdlePreloadDelayMs = 160L

private data class LazyListPreloadSnapshot(
    val lastVisibleIndex: Int,
    val visibleItemCount: Int,
    val isScrolling: Boolean
)

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
            LazyListPreloadSnapshot(
                lastVisibleIndex = visibleItems.maxOfOrNull { it.index } ?: -1,
                visibleItemCount = visibleItems.size,
                isScrolling = state.isScrollInProgress
            )
        }
            .distinctUntilChanged()
            .collectLatest { snapshot ->
                val range = resolveLazyListPreloadRequest(
                    lastVisibleIndex = snapshot.lastVisibleIndex,
                    visibleItemCount = snapshot.visibleItemCount,
                    itemCount = models.size,
                    preloadNext = preloadNext,
                    preloadNextWhileScrolling = preloadNextWhileScrolling,
                    isScrolling = snapshot.isScrolling
                ) ?: return@collectLatest
                delay(IdlePreloadDelayMs)
                val toPreload = range.mapNotNull { index ->
                    models[index].takeUnless { model -> model in preloadedModels }
                }
                if (toPreload.isNotEmpty()) {
                    coroutineScope {
                        manager.preload(this, toPreload, preloadSize).join()
                    }
                    preloadedModels.addAll(toPreload)
                    preloadedModels.trimOldest(maxSize = MaxRememberedPreloadKeys)
                }
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
    val latestModelAt = rememberUpdatedState(modelAt)
    LaunchedEffect(state, itemCount, preloadNext, preloadNextWhileScrolling, preloadSize) {
        preloadedModels.clear()
        snapshotFlow {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            LazyListPreloadSnapshot(
                lastVisibleIndex = visibleItems.maxOfOrNull { it.index } ?: -1,
                visibleItemCount = visibleItems.size,
                isScrolling = state.isScrollInProgress
            )
        }
            .distinctUntilChanged()
            .collectLatest { snapshot ->
                val range = resolveLazyListPreloadRequest(
                    lastVisibleIndex = snapshot.lastVisibleIndex,
                    visibleItemCount = snapshot.visibleItemCount,
                    itemCount = itemCount,
                    preloadNext = preloadNext,
                    preloadNextWhileScrolling = preloadNextWhileScrolling,
                    isScrolling = snapshot.isScrolling
                ) ?: return@collectLatest
                delay(IdlePreloadDelayMs)
                val toPreload = range.mapNotNull { index ->
                    latestModelAt.value(index)?.takeUnless { model -> model in preloadedModels }
                }
                if (toPreload.isNotEmpty()) {
                    coroutineScope {
                        manager.preload(this, toPreload, preloadSize).join()
                    }
                    preloadedModels.addAll(toPreload)
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
    val latestModelAt = rememberUpdatedState(modelAt)
    LaunchedEffect(state, itemCount, preloadNext, preloadNextWhileScrolling, preloadSize) {
        preloadedModels.clear()
        snapshotFlow {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            LazyListPreloadSnapshot(
                lastVisibleIndex = visibleItems.maxOfOrNull { it.index } ?: -1,
                visibleItemCount = visibleItems.size,
                isScrolling = state.isScrollInProgress
            )
        }
            .distinctUntilChanged()
            .collectLatest { snapshot ->
                val range = resolveLazyListPreloadRequest(
                    lastVisibleIndex = snapshot.lastVisibleIndex,
                    visibleItemCount = snapshot.visibleItemCount,
                    itemCount = itemCount,
                    preloadNext = preloadNext,
                    preloadNextWhileScrolling = preloadNextWhileScrolling,
                    isScrolling = snapshot.isScrolling
                ) ?: return@collectLatest
                delay(IdlePreloadDelayMs)
                val toPreload = range.mapNotNull { index ->
                    latestModelAt.value(index)?.takeUnless { model -> model in preloadedModels }
                }
                if (toPreload.isNotEmpty()) {
                    coroutineScope {
                        manager.preload(this, toPreload, preloadSize).join()
                    }
                    preloadedModels.addAll(toPreload)
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

internal fun resolveLazyListPreloadRequest(
    lastVisibleIndex: Int,
    visibleItemCount: Int,
    itemCount: Int,
    preloadNext: Int,
    preloadNextWhileScrolling: Int,
    isScrolling: Boolean
): IntRange? {
    if (!shouldRunLazyListPreload(isScrolling)) return null
    return resolveLazyListPreloadRange(
        lastVisibleIndex = lastVisibleIndex,
        visibleItemCount = visibleItemCount,
        itemCount = itemCount,
        preloadNext = preloadNext,
        preloadNextWhileScrolling = preloadNextWhileScrolling,
        isScrolling = false
    )
}

internal fun shouldRunLazyListPreload(isScrolling: Boolean): Boolean = !isScrolling

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
