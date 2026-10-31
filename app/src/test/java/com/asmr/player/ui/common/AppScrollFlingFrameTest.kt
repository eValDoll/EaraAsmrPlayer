package com.asmr.player.ui.common

import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppScrollFlingFrameTest {
    @Test
    fun scrollUpdatesRunInsideFrameCallback() {
        val clock = RecordingFrameClock()
        val deltas = mutableListOf<Float>()
        val scope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                assertTrue("Scroll must update before the frame callback returns", clock.insideFrame)
                deltas += pixels
                return pixels
            }
        }

        val remaining = runBlocking(clock) {
            with(behavior(stopVelocity = 999f)) { scope.performFling(1_000f) }
        }

        assertEquals(0f, remaining, 0f)
        assertEquals(1, deltas.size)
        assertEquals(8.333333f, deltas.single(), 0.00001f)
        assertEquals(2, clock.frameCount)
    }

    @Test
    fun unconsumedScrollReturnsRemainingVelocity() {
        val clock = RecordingFrameClock()
        var updates = 0
        val scope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                assertTrue(clock.insideFrame)
                updates += 1
                return 0f
            }
        }

        val remaining = runBlocking(clock) {
            with(behavior()) { scope.performFling(-1_000f) }
        }

        assertEquals(-1_000f, remaining, 0f)
        assertEquals(1, updates)
        assertEquals(2, clock.frameCount)
    }

    @Test
    fun interruptedScrollPropagatesCancellation() {
        val clock = RecordingFrameClock()
        val interruption = CancellationException("Pointer interrupted the fling")
        val scope = object : ScrollScope {
            override fun scrollBy(pixels: Float): Float = throw interruption
        }

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking(clock) {
                with(behavior()) { scope.performFling(1_000f) }
            }
        }

        assertSame(interruption, thrown)
    }

    private fun behavior(stopVelocity: Float = 32f) = CalmScrollableFlingBehavior(
        startVelocityPxPerSecond = 260f,
        stopVelocityPxPerSecond = stopVelocity,
        fastVelocityPxPerSecond = 1_800f,
        lowMidVelocityPxPerSecond = 360f,
    )

    private class RecordingFrameClock : MonotonicFrameClock {
        var insideFrame = false
            private set
        var frameCount = 0
            private set

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            frameCount += 1
            insideFrame = true
            return try {
                onFrame(frameCount * 8_333_333L)
            } finally {
                insideFrame = false
            }
        }
    }
}
