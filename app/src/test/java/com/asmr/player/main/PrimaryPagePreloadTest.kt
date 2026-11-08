package com.asmr.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asmr.player.ui.createEaraComposeRule
import com.asmr.player.ui.nav.Routes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrimaryPagePreloadTest {
    @get:Rule
    val composeRule = createEaraComposeRule()

    @Test
    fun completionWaitsForContentAndTwoCommittedFrames() {
        composeRule.mainClock.autoAdvance = false
        val state = PrimaryPagePreloadState(pageCount = 5)
        val contentReady = CompletableDeferred<Unit>()
        var waitCount = 0

        composeRule.setContent {
            PrimaryPagePreloadEffect(
                state = state,
                route = Routes.Library,
                warming = true,
                awaitContent = {
                    waitCount++
                    contentReady.await()
                },
            )
        }

        composeRule.mainClock.advanceTimeBy(160)
        composeRule.runOnIdle {
            assertEquals(1, waitCount)
            assertFalse(state.isComplete(Routes.Library))
            contentReady.complete(Unit)
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertFalse(state.isComplete(Routes.Library)) }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertFalse(state.isComplete(Routes.Library)) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertTrue(state.isComplete(Routes.Library)) }
    }

    @Test
    fun stoppingWarmupCancelsContentWaitAndAllowsRetry() {
        composeRule.mainClock.autoAdvance = false
        val state = PrimaryPagePreloadState(pageCount = 5)
        val firstContentReady = CompletableDeferred<Unit>()
        val retryContentReady = CompletableDeferred<Unit>()
        var warming by mutableStateOf(true)
        var waitCount = 0
        var cancellationCount = 0

        composeRule.setContent {
            PrimaryPagePreloadEffect(
                state = state,
                route = Routes.Library,
                warming = warming,
                awaitContent = {
                    waitCount++
                    val contentReady = if (waitCount == 1) firstContentReady else retryContentReady
                    try {
                        contentReady.await()
                    } finally {
                        if (!contentReady.isCompleted) cancellationCount++
                    }
                },
            )
        }

        composeRule.runOnIdle {
            assertEquals(1, waitCount)
            warming = false
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeUntil(timeoutMillis = 5_000) { cancellationCount == 1 }
        composeRule.runOnIdle {
            assertEquals(1, waitCount)
            assertEquals(1, cancellationCount)
            assertFalse(state.isComplete(Routes.Library))
            firstContentReady.complete(Unit)
        }
        composeRule.mainClock.advanceTimeBy(160)
        composeRule.runOnIdle {
            assertFalse(state.isComplete(Routes.Library))
            warming = true
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeUntil(timeoutMillis = 5_000) { waitCount == 2 }
        composeRule.runOnIdle {
            assertEquals(2, waitCount)
            assertFalse(state.isComplete(Routes.Library))
            retryContentReady.complete(Unit)
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertFalse(state.isComplete(Routes.Library)) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertTrue(state.isComplete(Routes.Library)) }
    }

    @Test
    fun completedPageDoesNotRestartItsContentWait() {
        composeRule.mainClock.autoAdvance = false
        val state = PrimaryPagePreloadState(pageCount = 5)
        var warming by mutableStateOf(true)
        var waitCount = 0

        composeRule.setContent {
            PrimaryPagePreloadEffect(
                state = state,
                route = Routes.Library,
                warming = warming,
                awaitContent = { waitCount++ },
            )
        }

        composeRule.mainClock.advanceTimeBy(64)
        composeRule.runOnIdle {
            assertTrue(state.isComplete(Routes.Library))
            assertEquals(1, waitCount)
            warming = false
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { warming = true }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(160)
        composeRule.runOnIdle {
            assertTrue(state.isComplete(Routes.Library))
            assertEquals(1, waitCount)
        }
    }

    @Test
    fun lifecycleStopCancelsTemporaryCollectorWithoutRecompositionAndRestartRetries() {
        composeRule.mainClock.autoAdvance = false
        val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.STARTED
            }
        }
        val state = PrimaryPagePreloadState(pageCount = 5)
        val playback = MutableStateFlow(0)
        val firstContentReady = CompletableDeferred<Unit>()
        val retryContentReady = CompletableDeferred<Unit>()
        var waitCount = 0
        var compositionCount = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                SideEffect { compositionCount++ }
                PrimaryPagePreloadEffect(
                    state = state,
                    route = Routes.Library,
                    warming = true,
                    awaitContent = {
                        waitCount++
                        val contentReady = if (waitCount == 1) firstContentReady else retryContentReady
                        coroutineScope {
                            val collector = launch { playback.collect {} }
                            try {
                                contentReady.await()
                            } finally {
                                collector.cancel()
                            }
                        }
                    },
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { playback.subscriptionCount.value == 1 }
        val initialCompositionCount = compositionCount
        composeRule.runOnIdle {
            assertEquals(1, waitCount)
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.CREATED
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { playback.subscriptionCount.value == 0 }
        composeRule.runOnIdle {
            assertEquals(initialCompositionCount, compositionCount)
            assertFalse(state.isComplete(Routes.Library))
            firstContentReady.complete(Unit)
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.STARTED
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            waitCount == 2 && playback.subscriptionCount.value == 1
        }
        composeRule.runOnIdle {
            assertEquals(initialCompositionCount, compositionCount)
            assertFalse(state.isComplete(Routes.Library))
            retryContentReady.complete(Unit)
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertFalse(state.isComplete(Routes.Library)) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle { assertTrue(state.isComplete(Routes.Library)) }
        composeRule.waitUntil(timeoutMillis = 5_000) { playback.subscriptionCount.value == 0 }
    }
}
