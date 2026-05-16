package com.asmr.player.ui.common

import androidx.compose.foundation.lazy.LazyListState

suspend fun LazyListState.smoothScrollToIndex(
    index: Int,
    anchorOffsetPx: Int
) {
    if (index < 0) return
    runCatching {
        // 直接使用内置动画，避免重复调用和自定义不支持的参数
        this.animateScrollToItem(index, anchorOffsetPx)
    }.onFailure {
        // 如果动画被打断，直接跳转到位
        runCatching { scrollToItem(index, anchorOffsetPx) }
    }
}
