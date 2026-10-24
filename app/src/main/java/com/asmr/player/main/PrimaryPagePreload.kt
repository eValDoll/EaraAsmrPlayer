package com.asmr.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.asmr.player.performance.UiFrameWorkCoordinator
import com.asmr.player.performance.tracePrimaryPagePreloadEvent
import com.asmr.player.ui.nav.Routes
import com.asmr.player.ui.common.LocalDeferredPageContent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class PrimaryPagePreloadState(pageCount: Int) {
    var beyondBoundsPageCount by mutableIntStateOf(minOf(1, (pageCount - 1).coerceAtLeast(0)))
    var requestedRoute by mutableStateOf<String?>(null)
    var interactionActive by mutableStateOf(false)
        private set
    internal val interactions = Channel<Unit>(Channel.CONFLATED)
    private var completedRoutes by mutableStateOf(emptySet<String>())
    var finished by mutableStateOf(false)
        private set

    fun isComplete(route: String): Boolean = route in completedRoutes

    fun complete(route: String) {
        if (!isComplete(route)) {
            completedRoutes = completedRoutes + route
            tracePrimaryPagePreloadEvent("ready:$route")
        }
    }

    fun onInteraction() {
        if (finished) return
        interactionActive = true
        UiFrameWorkCoordinator.markFrameCritical()
        interactions.trySend(Unit)
    }

    fun finishInteraction() {
        interactionActive = false
    }

    fun finish() {
        tracePrimaryPagePreloadEvent("finished")
        finished = true
        interactionActive = false
    }
}

@Composable
internal fun Modifier.observePrimaryPagePreloadInteractions(state: PrimaryPagePreloadState): Modifier {
    if (state.finished) return this
    val connection = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                state.onInteraction()
                return Offset.Zero
            }
        }
    }
    return nestedScroll(connection).pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                if (awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }) {
                    state.onInteraction()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
internal fun PreloadPrimaryPages(
    state: PrimaryPagePreloadState,
    pagerState: PagerState,
    routes: List<String>,
    enabled: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestEnabled = rememberUpdatedState(enabled)
    LaunchedEffect(state, pagerState, routes, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            suspend fun awaitIdle() {
                do {
                    snapshotFlow {
                        latestEnabled.value && !state.interactionActive && !pagerState.isScrollInProgress
                    }.first { it }
                    UiFrameWorkCoordinator.awaitFrameQuiet()
                    // 分开页面创建帧，首屏与用户操作优先。
                    delay(150)
                    UiFrameWorkCoordinator.awaitFrameQuiet()
                    withFrameNanos { }
                } while (!latestEnabled.value || state.interactionActive || pagerState.isScrollInProgress)
            }

            try {
                coroutineScope {
                    val interactionObserver = launch {
                        state.interactions.receiveAsFlow().debounce(400).collect { state.finishInteraction() }
                    }
                    val retainedPageCount = resolvePrimaryPagerBeyondBoundsPageCount(routes.size)
                    while (state.beyondBoundsPageCount < retainedPageCount) {
                        awaitIdle()
                        state.beyondBoundsPageCount += 1
                        tracePrimaryPagePreloadEvent("expand:${state.beyondBoundsPageCount}")
                        withFrameNanos { }
                        withFrameNanos { }
                    }
                    // 本地页面先完成，网络延迟不阻塞其首份数据与布局的预热。
                    val preloadOrder = routes.filter { it != Routes.Search && it != Routes.HotListening } +
                        routes.filter { it == Routes.Search || it == Routes.HotListening }
                    for (route in preloadOrder) {
                        if (state.isComplete(route)) continue
                        awaitIdle()
                        state.requestedRoute = route
                        tracePrimaryPagePreloadEvent("warm:$route")
                        snapshotFlow { state.isComplete(route) }.first { it }
                    }
                    state.finish()
                    interactionObserver.cancel()
                }
            } finally {
                state.requestedRoute = null
                state.finishInteraction()
            }
        }
    }
}

@Composable
internal fun PrimaryPagePreloadEffect(
    state: PrimaryPagePreloadState,
    route: String,
    warming: Boolean,
    awaitContent: suspend () -> Unit = {},
) {
    val latestAwaitContent = rememberUpdatedState(awaitContent)
    val deferredPageContent = LocalDeferredPageContent.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state, route, warming, lifecycleOwner, deferredPageContent) {
        if (!warming || state.isComplete(route)) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (state.isComplete(route)) return@repeatOnLifecycle
            latestAwaitContent.value()
            // 数据流首发后，给 retained 状态与 Lazy 布局各一次提交机会再冻结订阅。
            if (deferredPageContent != null) {
                deferredPageContent.awaitReady()
            } else {
                withFrameNanos { }
                withFrameNanos { }
            }
            state.complete(route)
        }
    }
}
