package com.asmr.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import com.asmr.player.performance.UiFrameWorkCoordinator
import com.asmr.player.performance.tracePrimaryPagePreloadEvent
import kotlinx.coroutines.delay

internal val LocalDeferredPageContent = staticCompositionLocalOf<DeferredPageContentState?> { null }

internal class DeferredPageContentState(internal val visible: State<Boolean>, private val route: String = "page") {
    internal class Item {
        var admitted by mutableStateOf(false)
    }

    private val pending = LinkedHashSet<Item>()

    internal fun register(item: Item) {
        if (!item.admitted) pending.add(item)
    }

    internal fun unregister(item: Item) {
        pending.remove(item)
    }

    internal fun admit(item: Item) {
        pending.remove(item)
        item.admitted = true
    }

    suspend fun awaitReady() {
        var emptyFrames = 0
        while (emptyFrames < 2) {
            UiFrameWorkCoordinator.awaitFrameQuiet()
            val next = pending.firstOrNull()
            if (next == null) {
                withFrameNanos { }
                emptyFrames = if (pending.isEmpty()) emptyFrames + 1 else 0
            } else {
                tracePrimaryPagePreloadEvent("admit:$route")
                admit(next)
                // 让本项完成组合与布局，再交出下一项的创建许可。
                withFrameNanos { }
                withFrameNanos { }
                delay(32)
                emptyFrames = 0
            }
        }
    }
}

@Composable
internal fun DeferredPageItem(
    placeholder: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = LocalDeferredPageContent.current
    if (state == null) {
        content()
        return
    }
    val item = remember(state) { DeferredPageContentState.Item() }
    DisposableEffect(state, item) {
        state.register(item)
        onDispose { state.unregister(item) }
    }
    if (state.visible.value || item.admitted) {
        content()
        // 活跃页立即显示完整内容，之后隐藏也不退回占位或丢失组合状态。
        SideEffect { state.admit(item) }
    } else {
        placeholder()
    }
}
