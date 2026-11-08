package com.asmr.player.ui.common

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** 数据预热不启动隐藏页面的无限动画。其他页面默认保留原有动画。 */
val LocalPageAnimationsActive = compositionLocalOf { true }

internal suspend fun <T> withPageAnimationClock(active: State<Boolean>, block: suspend () -> T): T {
    val parent = checkNotNull(coroutineContext[MonotonicFrameClock])
    return withContext(VisibilityFrameClock(active, parent)) { block() }
}

/** 单个动画使用的可见性时钟：离屏取消父帧请求，恢复后扣除暂停时间。 */
internal class VisibilityFrameClock(
    private val visible: State<Boolean>,
    private val parent: MonotonicFrameClock,
    private val nowNanos: () -> Long = System::nanoTime,
) : MonotonicFrameClock {
    private var pausedAtNanos: Long? = null
    private var pausedDurationNanos = 0L
    private var lastFrameNanos = Long.MIN_VALUE

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
        snapshotFlow { visible.value }
            .onEach { active ->
                if (active) {
                    pausedAtNanos?.let { pausedAt ->
                        pausedDurationNanos += (nowNanos() - pausedAt).coerceAtLeast(0L)
                        pausedAtNanos = null
                    }
                } else if (pausedAtNanos == null) {
                    pausedAtNanos = nowNanos()
                }
            }
            .transformLatest<Boolean, R> { active ->
                if (active) emit(parent.withFrameNanos { frameNanos ->
                    val animationFrame = (frameNanos - pausedDurationNanos).coerceAtLeast(lastFrameNanos)
                    lastFrameNanos = animationFrame
                    onFrame(animationFrame)
                })
            }.first()
}

private class RetainedPageAnimation {
    val progress = mutableFloatStateOf(0f)
    var playTimeNanos = 0L
}

@Composable
internal fun rememberPageAnimationProgress(animationSpec: InfiniteRepeatableSpec<Float>): State<Float> {
    val active = LocalPageAnimationsActive.current
    val animation = remember(animationSpec) {
        TargetBasedAnimation(animationSpec, Float.VectorConverter, 0f, 1f)
    }
    val retained = remember(animation) { RetainedPageAnimation() }
    LaunchedEffect(animation, active) {
        if (!active) return@LaunchedEffect
        val motionDurationScale = coroutineContext[MotionDurationScale]
        var previousFrameNanos: Long? = null
        var previousScale = motionDurationScale?.scaleFactor ?: 1f
        while (true) {
            withInfiniteAnimationFrameNanos { frameNanos ->
                val scale = motionDurationScale?.scaleFactor ?: 1f
                if (scale == 0f) {
                    retained.progress.floatValue = 1f
                } else {
                    val previousFrame = previousFrameNanos
                    if (scale != previousScale) {
                        retained.playTimeNanos = 0L
                    } else if (previousFrame != null) {
                        retained.playTimeNanos += ((frameNanos - previousFrame) / scale).toLong()
                    }
                    retained.progress.floatValue = animation.getValueFromNanos(retained.playTimeNanos)
                }
                previousFrameNanos = frameNanos
                previousScale = scale
            }
            if (previousScale == 0f) {
                snapshotFlow { motionDurationScale?.scaleFactor ?: 1f }.first { it > 0f }
            }
        }
    }
    return retained.progress
}
