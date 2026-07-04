package com.asmr.player.cache

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

private const val MaxRememberedPreloadKeys = 96

@Composable
fun LazyListPreloader(
    state: LazyListState,
    models: List<Any>,
    preloadNext: Int = 8,
    preloadSize: IntSize? = null,
    cacheManagerProvider: () -> ImageCacheManager
) {
    val manager = remember { cacheManagerProvider() }
    val preloadedModels = remember { LinkedHashSet<Any>() }
    LaunchedEffect(state, models, preloadNext, preloadSize) {
        preloadedModels.clear()
        snapshotFlow {
            if (state.isScrollInProgress) {
                null
            } else {
                state.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            }
        }
            .mapNotNull { it }
            .map { last ->
                val start = (last + 1).coerceAtLeast(0)
                val end = (start + preloadNext).coerceAtMost(models.size)
                if (start >= end) null else start until end
            }
            .filter { it != null }
            .distinctUntilChanged()
            .collect { range ->
                val r = range ?: return@collect
                val toPreload = r.mapNotNull { index ->
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
    preloadNext: Int = 8,
    preloadSize: IntSize? = null,
    cacheManagerProvider: () -> ImageCacheManager,
    modelAt: (Int) -> Any?
) {
    val manager = remember { cacheManagerProvider() }
    val preloadedModels = remember { LinkedHashSet<Any>() }
    LaunchedEffect(state, itemCount, preloadNext, preloadSize) {
        preloadedModels.clear()
        snapshotFlow {
            if (state.isScrollInProgress) {
                null
            } else {
                state.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            }
        }
            .mapNotNull { it }
            .map { last ->
                val start = (last + 1).coerceAtLeast(0)
                val end = (start + preloadNext).coerceAtMost(itemCount)
                if (start >= end) null else start until end
            }
            .filter { it != null }
            .distinctUntilChanged()
            .collect { range ->
                val r = range ?: return@collect
                val toPreload = r.mapNotNull { index ->
                    modelAt(index)?.takeIf { model -> preloadedModels.add(model) }
                }
                if (toPreload.isNotEmpty()) {
                    manager.preload(toPreload, preloadSize)
                    preloadedModels.trimOldest(maxSize = MaxRememberedPreloadKeys)
                }
            }
    }
}

private fun <T> LinkedHashSet<T>.trimOldest(maxSize: Int) {
    while (size > maxSize) {
        val iterator = iterator()
        if (!iterator.hasNext()) return
        iterator.next()
        iterator.remove()
    }
}
