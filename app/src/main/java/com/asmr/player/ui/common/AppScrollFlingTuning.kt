package com.asmr.player.ui.common

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlinx.coroutines.CancellationException

private const val VerticalFlingDampingStartDpPerSecond = 850f
private const val MaxVerticalFlingVelocityDpPerSecond = 3200f
private const val VerticalFlingVelocityScale = 0.6f
private const val CalmFlingStartVelocityDpPerSecond = 260f
private const val FastFlingVelocityDpPerSecond = 1800f
private const val LowMidFlingVelocityDpPerSecond = 360f
private const val FastFlingDecayRatePerSecond = 2.6f
private const val MidFlingDecayRatePerSecond = 0.95f
private const val LowFlingDecayRatePerSecond = 2.0f
private const val CalmFlingStopVelocityDpPerSecond = 32f
private const val MaxFlingFrameSeconds = 1f / 30f

@Composable
fun Modifier.calmVerticalFling(): Modifier {
    val density = LocalDensity.current
    val nestedScrollConnection = remember(density) {
        val dampingStartPxPerSecond = with(density) {
            VerticalFlingDampingStartDpPerSecond.dp.toPx()
        }
        val maxVelocityPxPerSecond = with(density) {
            MaxVerticalFlingVelocityDpPerSecond.dp.toPx()
        }
        object : NestedScrollConnection {
            override suspend fun onPreFling(available: Velocity): Velocity {
                val adjustedY = calmVerticalFlingVelocity(
                    velocity = available.y,
                    dampingStartPxPerSecond = dampingStartPxPerSecond,
                    maxVelocityPxPerSecond = maxVelocityPxPerSecond,
                    velocityScale = VerticalFlingVelocityScale
                )
                if (adjustedY == available.y) return Velocity.Zero
                return Velocity(x = 0f, y = available.y - adjustedY)
            }
        }
    }
    return nestedScroll(nestedScrollConnection)
}

fun Modifier.interruptScrollableFlingOnPointerDown(onPointerDown: () -> Unit): Modifier {
    return pointerInput(onPointerDown) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onPointerDown()
        }
    }
}

@Composable
fun rememberCalmScrollableFlingBehavior(): FlingBehavior {
    val density = LocalDensity.current
    return remember(density) {
        CalmScrollableFlingBehavior(
            startVelocityPxPerSecond = with(density) {
                CalmFlingStartVelocityDpPerSecond.dp.toPx()
            },
            stopVelocityPxPerSecond = with(density) {
                CalmFlingStopVelocityDpPerSecond.dp.toPx()
            },
            fastVelocityPxPerSecond = with(density) {
                FastFlingVelocityDpPerSecond.dp.toPx()
            },
            lowMidVelocityPxPerSecond = with(density) {
                LowMidFlingVelocityDpPerSecond.dp.toPx()
            }
        )
    }
}

internal fun shouldStartCalmFling(
    velocity: Float,
    startVelocityPxPerSecond: Float
): Boolean {
    if (!velocity.isFinite()) return false
    return abs(velocity) > startVelocityPxPerSecond.coerceAtLeast(0f)
}

internal fun calmVerticalFlingVelocity(
    velocity: Float,
    dampingStartPxPerSecond: Float,
    maxVelocityPxPerSecond: Float,
    velocityScale: Float
): Float {
    if (!velocity.isFinite()) return velocity

    val maxVelocity = maxVelocityPxPerSecond.coerceAtLeast(0f)
    val dampingStart = dampingStartPxPerSecond.coerceIn(0f, maxVelocity)
    val scale = velocityScale.coerceIn(0f, 1f)
    val magnitude = abs(velocity)
    val adjustedMagnitude = if (magnitude <= dampingStart) {
        magnitude
    } else {
        dampingStart + (magnitude - dampingStart) * scale
    }
    val cappedMagnitude = min(adjustedMagnitude, maxVelocity)
    return if (velocity < 0f) -cappedMagnitude else cappedMagnitude
}

internal fun decayedCalmFlingVelocity(
    velocity: Float,
    frameSeconds: Float,
    decayRatePerSecond: Float
): Float {
    if (!velocity.isFinite()) return velocity
    val seconds = frameSeconds.coerceIn(0f, MaxFlingFrameSeconds)
    val rate = decayRatePerSecond.coerceAtLeast(0f)
    return velocity * exp((-rate * seconds).toDouble()).toFloat()
}

internal fun calmFlingDecayRateForVelocity(
    velocity: Float,
    fastVelocityPxPerSecond: Float,
    lowMidVelocityPxPerSecond: Float
): Float {
    val magnitude = abs(velocity)
    val fastVelocity = fastVelocityPxPerSecond.coerceAtLeast(0f)
    val lowMidVelocity = lowMidVelocityPxPerSecond.coerceIn(0f, fastVelocity)
    return when {
        magnitude > fastVelocity -> FastFlingDecayRatePerSecond
        magnitude > lowMidVelocity -> MidFlingDecayRatePerSecond
        else -> LowFlingDecayRatePerSecond
    }
}

private class CalmScrollableFlingBehavior(
    private val startVelocityPxPerSecond: Float,
    private val stopVelocityPxPerSecond: Float,
    private val fastVelocityPxPerSecond: Float,
    private val lowMidVelocityPxPerSecond: Float
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        if (!initialVelocity.isFinite()) return initialVelocity
        var velocity = initialVelocity
        val stopVelocity = stopVelocityPxPerSecond.coerceAtLeast(0f)
        if (!shouldStartCalmFling(velocity, startVelocityPxPerSecond)) return 0f
        if (abs(velocity) <= stopVelocity) return velocity

        try {
            var previousFrameTime = withFrameNanos { it }
            while (abs(velocity) > stopVelocity) {
                val frameTime = withFrameNanos { it }
                val frameSeconds = ((frameTime - previousFrameTime) / 1_000_000_000f)
                    .coerceIn(0f, MaxFlingFrameSeconds)
                previousFrameTime = frameTime
                if (frameSeconds == 0f) continue

                val delta = velocity * frameSeconds
                val consumed = scrollBy(delta)
                if (abs(delta - consumed) > 0.5f) return velocity

                velocity = decayedCalmFlingVelocity(
                    velocity = velocity,
                    frameSeconds = frameSeconds,
                    decayRatePerSecond = calmFlingDecayRateForVelocity(
                        velocity = velocity,
                        fastVelocityPxPerSecond = fastVelocityPxPerSecond,
                        lowMidVelocityPxPerSecond = lowMidVelocityPxPerSecond
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
        return 0f
    }
}
