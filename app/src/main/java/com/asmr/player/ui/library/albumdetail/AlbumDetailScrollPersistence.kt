package com.asmr.player.ui.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 只在滚动结束或页面离开时保存位置，避免滚动过程中逐像素分配对象并写入 ViewModel。
 */
@Composable
internal fun PersistAlbumDetailListScroll(
    listState: LazyListState,
    stateKey: String,
    indexOffset: Int = 0,
    onPersistScroll: (Int, Int) -> Unit
) {
    val latestPersistScroll by rememberUpdatedState(onPersistScroll)

    fun persistCurrentPosition() {
        latestPersistScroll(
            (listState.firstVisibleItemIndex - indexOffset).coerceAtLeast(0),
            listState.firstVisibleItemScrollOffset.coerceAtLeast(0)
        )
    }

    LaunchedEffect(listState, stateKey, indexOffset) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling) persistCurrentPosition()
            }
    }

    DisposableEffect(listState, stateKey, indexOffset) {
        onDispose { persistCurrentPosition() }
    }
}
