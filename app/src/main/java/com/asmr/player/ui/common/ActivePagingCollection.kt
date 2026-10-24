package com.asmr.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private class RetainedPagingItems<T : Any> {
    var value: LazyPagingItems<T>? = null
}

/**
 * 离屏时移除分页收集协程，但保留最后一帧的数据和加载状态供列表继续展示。
 * 数据源应先 cachedIn，以便返回页面时同步恢复已经加载的页和滚动位置。
 */
@Composable
fun <T : Any> Flow<PagingData<T>>.collectAsLazyPagingItemsWhileActive(
    isActive: Boolean,
): LazyPagingItems<T> {
    val retained = remember(this) { RetainedPagingItems<T>() }
    if (!isActive) {
        retained.value?.let { return it }
    }
    val source = if (isActive) this else remember { flowOf(PagingData.empty<T>()) }
    val items = source.collectAsLazyPagingItems()
    SideEffect { retained.value = items }
    return items
}
