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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

private const val MaxRememberedPreloadKeys = 96
private const val ScrollingLeadViewportMultiplier = 1
private const val IdleLeadViewportMultiplier = 3
private const val ScrollingPreloadDelayMs = 24L
private const val IdlePreloadDelayMs = 160L
private const val ScrollingPreloadParallelism = 1

private data class LazyListPreloadSnapshot(
    val firstVisibleIndex: Int,
    val lastVisibleIndex: Int,
    val visibleItemCount: Int,
    val isScrolling: Boolean,
)

internal enum class LazyListPreloadDirection {
    Forward,
    Backward,
}

@Composable
fun LazyListPreloader(
    state: LazyListState,
    models: List<Any>,
    preloadNext: Int = 24,
    preloadNextWhileScrolling: Int = 8,
    preloadSize: IntSize? = null,
    cacheManagerProvider: () -> ImageCacheManager,
) {
    val manager = remember { cacheManagerProvider() }
    val preloadedModels = remember { LinkedHashSet<Any>() }
    LaunchedEffect(state, models, preloadNext, preloadNextWhileScrolling, preloadSize) {
        preloadedModels.clear()
        snapshotFlow {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            LazyListPreloadSnapshot(
                firstVisibleIndex = visibleItems.minOfOrNull { it.index } ?: -1,
                lastVisibleIndex = visibleItems.maxOfOrNull { it.index } ?: -1,
                visibleItemCount = visibleItems.size,
                isScrolling = state.isScrollInProgress,
            )
        }
            .distinctUntilChanged()
            .collectImagePreloads(
                manager = manager,
                preloadedModels = preloadedModels,
                itemCount = models.size,
                preloadNext = preloadNext,
                preloadNextWhileScrolling = preloadNextWhileScrolling,
                preloadSize = preloadSize,
                modelAt = models::get,
            )
    }
}

@Composable
fun LazyListPreloader(
    state: LazyListState,
    itemCount: Int,
    preloadNext: Int = 24,
    preloadNextWhileScrolling: Int = 8,
    preloadSize: IntSize? = null,
    cacheManagerProvider: () -> ImageCacheManager,
    modelAt: (Int) -> Any?,
) {
    val manager = remember { cacheManagerProvider() }
    val preloadedModels = remember { LinkedHashSet<Any>() }
    val latestModelAt = rememberUpdatedState(modelAt)
    LaunchedEffect(state, itemCount, preloadNext, preloadNextWhileScrolling, preloadSize) {
        preloadedModels.clear()
        snapshotFlow {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            LazyListPreloadSnapshot(
                firstVisibleIndex = visibleItems.minOfOrNull { it.index } ?: -1,
                lastVisibleIndex = visibleItems.maxOfOrNull { it.index } ?: -1,
                visibleItemCount = visibleItems.size,
                isScrolling = state.isScrollInProgress,
            )
        }
            .distinctUntilChanged()
            .collectImagePreloads(
                manager = manager,
                preloadedModels = preloadedModels,
                itemCount = itemCount,
                preloadNext = preloadNext,
                preloadNextWhileScrolling = preloadNextWhileScrolling,
                preloadSize = preloadSize,
                modelAt = { index -> latestModelAt.value(index) },
            )
    }
}

@Composable
fun LazyStaggeredGridPreloader(
    state: LazyStaggeredGridState,
    itemCount: Int,
    preloadNext: Int = 24,
    preloadNextWhileScrolling: Int = 8,
    preloadSize: IntSize? = null,
    cacheManagerProvider: () -> ImageCacheManager,
    modelAt: (Int) -> Any?,
) {
    val manager = remember { cacheManagerProvider() }
    val preloadedModels = remember { LinkedHashSet<Any>() }
    val latestModelAt = rememberUpdatedState(modelAt)
    LaunchedEffect(state, itemCount, preloadNext, preloadNextWhileScrolling, preloadSize) {
        preloadedModels.clear()
        snapshotFlow {
            val visibleItems = state.layoutInfo.visibleItemsInfo
            LazyListPreloadSnapshot(
                firstVisibleIndex = visibleItems.minOfOrNull { it.index } ?: -1,
                lastVisibleIndex = visibleItems.maxOfOrNull { it.index } ?: -1,
                visibleItemCount = visibleItems.size,
                isScrolling = state.isScrollInProgress,
            )
        }
            .distinctUntilChanged()
            .collectImagePreloads(
                manager = manager,
                preloadedModels = preloadedModels,
                itemCount = itemCount,
                preloadNext = preloadNext,
                preloadNextWhileScrolling = preloadNextWhileScrolling,
                preloadSize = preloadSize,
                modelAt = { index -> latestModelAt.value(index) },
            )
    }
}

private suspend fun Flow<LazyListPreloadSnapshot>.collectImagePreloads(
    manager: ImageCacheManager,
    preloadedModels: LinkedHashSet<Any>,
    itemCount: Int,
    preloadNext: Int,
    preloadNextWhileScrolling: Int,
    preloadSize: IntSize?,
    modelAt: (Int) -> Any?,
) {
    var previousSnapshot: LazyListPreloadSnapshot? = null
    var direction = LazyListPreloadDirection.Forward
    collectLatest { snapshot ->
        direction = resolveLazyListPreloadDirection(
            previousFirstVisibleIndex = previousSnapshot?.firstVisibleIndex,
            previousLastVisibleIndex = previousSnapshot?.lastVisibleIndex,
            firstVisibleIndex = snapshot.firstVisibleIndex,
            lastVisibleIndex = snapshot.lastVisibleIndex,
            previousDirection = direction,
        )
        previousSnapshot = snapshot

        val range = resolveLazyListPreloadRange(
            firstVisibleIndex = snapshot.firstVisibleIndex,
            lastVisibleIndex = snapshot.lastVisibleIndex,
            visibleItemCount = snapshot.visibleItemCount,
            itemCount = itemCount,
            preloadNext = preloadNext,
            preloadNextWhileScrolling = preloadNextWhileScrolling,
            isScrolling = snapshot.isScrolling,
            direction = direction,
        ) ?: return@collectLatest

        delay(if (snapshot.isScrolling) ScrollingPreloadDelayMs else IdlePreloadDelayMs)
        val toPreload = range.mapNotNull { index ->
            modelAt(index)?.takeUnless { model -> model in preloadedModels }
        }
        if (toPreload.isEmpty()) return@collectLatest

        coroutineScope {
            manager.preload(
                scope = this,
                models = toPreload,
                size = preloadSize,
                maxConcurrency = if (snapshot.isScrolling) ScrollingPreloadParallelism else null,
            ).join()
        }
        preloadedModels.addAll(toPreload)
        preloadedModels.trimOldest(maxSize = MaxRememberedPreloadKeys)
    }
}

internal fun resolveLazyListPreloadDirection(
    previousFirstVisibleIndex: Int?,
    previousLastVisibleIndex: Int?,
    firstVisibleIndex: Int,
    lastVisibleIndex: Int,
    previousDirection: LazyListPreloadDirection = LazyListPreloadDirection.Forward,
): LazyListPreloadDirection {
    if (
        previousFirstVisibleIndex == null || previousLastVisibleIndex == null ||
        previousFirstVisibleIndex < 0 || previousLastVisibleIndex < 0 ||
        firstVisibleIndex < 0 || lastVisibleIndex < 0
    ) {
        return previousDirection
    }
    return when {
        firstVisibleIndex > previousFirstVisibleIndex -> LazyListPreloadDirection.Forward
        firstVisibleIndex < previousFirstVisibleIndex -> LazyListPreloadDirection.Backward
        lastVisibleIndex > previousLastVisibleIndex -> LazyListPreloadDirection.Forward
        lastVisibleIndex < previousLastVisibleIndex -> LazyListPreloadDirection.Backward
        else -> previousDirection
    }
}

internal fun resolveLazyListPreloadRange(
    firstVisibleIndex: Int,
    lastVisibleIndex: Int,
    visibleItemCount: Int,
    itemCount: Int,
    preloadNext: Int,
    preloadNextWhileScrolling: Int,
    isScrolling: Boolean,
    direction: LazyListPreloadDirection,
): IntRange? {
    if (firstVisibleIndex < 0 || lastVisibleIndex < 0 || itemCount <= 0) return null
    val leadCount = resolveLazyListPreloadLeadCount(
        visibleItemCount = visibleItemCount,
        preloadNext = preloadNext,
        preloadNextWhileScrolling = preloadNextWhileScrolling,
        isScrolling = isScrolling,
    )
    val range = when (direction) {
        LazyListPreloadDirection.Forward -> {
            val start = (lastVisibleIndex + 1).coerceIn(0, itemCount)
            start until (start + leadCount).coerceAtMost(itemCount)
        }

        LazyListPreloadDirection.Backward -> {
            val end = firstVisibleIndex.coerceIn(0, itemCount)
            (end - leadCount).coerceAtLeast(0) until end
        }
    }
    return range.takeUnless { it.isEmpty() }
}

internal fun resolveLazyListPreloadLeadCount(
    visibleItemCount: Int,
    preloadNext: Int,
    preloadNextWhileScrolling: Int,
    isScrolling: Boolean,
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
