package com.asmr.player.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageAnimationActivityTest {
    @Test
    fun hiddenAnimationStopsRequestingFramesAndResumesFromRetainedPhase() = runBlocking {
        val parent = BroadcastFrameClock()
        val visible = mutableStateOf(true)
        var nowNanos = 0L
        val clock = VisibilityFrameClock(visible, parent) { nowNanos }
        val animation = Animatable(0f)
        val running = launch(clock, start = CoroutineStart.UNDISPATCHED) {
            animation.animateTo(1f, tween(durationMillis = 1_000, easing = LinearEasing))
        }

        suspend fun sendFrame(timeNanos: Long) {
            nowNanos = timeNanos
            withTimeout(5_000) {
                while (!parent.hasAwaiters) yield()
            }
            parent.sendFrame(timeNanos)
            yield()
        }

        try {
            withTimeout(10_000) {
                sendFrame(0L)
                sendFrame(250_000_000L)
                assertEquals(0.25f, animation.value, 0.0001f)

                withTimeout(5_000) {
                    while (!parent.hasAwaiters) yield()
                }
                visible.value = false
                Snapshot.sendApplyNotifications()
                withTimeout(5_000) {
                    while (parent.hasAwaiters) yield()
                }
                assertFalse(parent.hasAwaiters)
                val pausedPhase = animation.value
                nowNanos = 5_250_000_000L
                parent.sendFrame(nowNanos)
                yield()
                assertEquals(pausedPhase, animation.value, 0f)
                assertFalse(parent.hasAwaiters)
                assertTrue(running.isActive)

                visible.value = true
                Snapshot.sendApplyNotifications()
                sendFrame(5_250_000_000L)
                assertEquals(pausedPhase, animation.value, 0.0001f)
                sendFrame(5_500_000_000L)
                assertEquals(0.5f, animation.value, 0.0001f)
                sendFrame(6_000_000_000L)
                running.join()
                assertEquals(1f, animation.value, 0f)
                assertFalse(parent.hasAwaiters)
            }
        } finally {
            running.cancelAndJoin()
            Snapshot.sendApplyNotifications()
        }
    }
}
