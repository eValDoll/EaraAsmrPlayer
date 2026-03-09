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

@Composable
fun LazyListPreloader(
    state: LazyListState,
    models: List<Any>,
    preloadNext: Int = 8,
    preloadSize: IntSize? = null,
    cacheManagerProvider: () -> ImageCacheManager
) {
    val manager = remember { cacheManagerProvider() }
    LaunchedEffect(state, models, preloadNext, preloadSize) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1 }
            .map { last ->
                val start = (last + 1).coerceAtLeast(0)
                val end = (start + preloadNext).coerceAtMost(models.size)
                if (start >= end) null else start until end
            }
            .filter { it != null }
            .distinctUntilChanged()
            .collect { range ->
                val r = range ?: return@collect
                val toPreload = r.map { models[it] }
                manager.preload(toPreload, preloadSize)
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
    LaunchedEffect(state, itemCount, preloadNext, preloadSize) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1 }
            .map { last ->
                val start = (last + 1).coerceAtLeast(0)
                val end = (start + preloadNext).coerceAtMost(itemCount)
                if (start >= end) null else start until end
            }
            .filter { it != null }
            .distinctUntilChanged()
            .collect { range ->
                val r = range ?: return@collect
                val toPreload = r.mapNotNull(modelAt)
                if (toPreload.isNotEmpty()) {
                    manager.preload(toPreload, preloadSize)
                }
            }
    }
}
