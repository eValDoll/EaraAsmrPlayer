@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.asmr.player.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.PrefetchScheduler
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun rememberSaveablePrefetchedStaggeredGridState(stateKey: Any?): LazyStaggeredGridState {
    val scheduler = rememberFrameDeadlinePrefetchScheduler()
    val saver = remember(scheduler) { staggeredGridStateSaver(scheduler) }
    return rememberSaveable(stateKey, saver = saver) {
        LazyStaggeredGridState(intArrayOf(0), intArrayOf(0), scheduler)
    }
}

// Foundation 1.7.8 的公开构造函数不能传入调度器；保存格式与其原有 Saver 一致。
@OptIn(ExperimentalFoundationApi::class)
internal fun staggeredGridStateSaver(scheduler: PrefetchScheduler) =
    listSaver<LazyStaggeredGridState, IntArray>(
        save = { listOf(it.scrollPosition.indices, it.scrollPosition.scrollOffsets) },
        restore = { LazyStaggeredGridState(it[0], it[1], scheduler) },
    )
