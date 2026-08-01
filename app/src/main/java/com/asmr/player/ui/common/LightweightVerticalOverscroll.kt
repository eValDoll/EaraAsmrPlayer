package com.asmr.player.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val StretchDragResistance = 0.22f
private const val StretchScaleFraction = 0.028f
private const val StretchTranslationFraction = 0.14f

@Composable
fun Modifier.lightweightVerticalStretchOverscroll(
    isAtStart: () -> Boolean,
    isAtEnd: () -> Boolean,
): Modifier {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val latestIsAtStart by rememberUpdatedState(isAtStart)
    val latestIsAtEnd by rememberUpdatedState(isAtEnd)
    val maxDistancePx = with(density) { 72.dp.toPx() }
    val state = remember(coroutineScope, maxDistancePx) {
        LightweightVerticalOverscrollState(
            coroutineScope = coroutineScope,
            maxDistancePx = maxDistancePx,
        )
    }

    DisposableEffect(state) {
        onDispose(state::dispose)
    }

    return pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var trackedPointerId = down.id
            var previousPosition = down.position
            var dragFromDown = Offset.Zero
            var verticalGesture: Boolean? = null
            state.interruptSettle()

            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == trackedPointerId }
                    ?: event.changes.firstOrNull()
                if (change != null) {
                    trackedPointerId = change.id
                    val positionDelta = change.position - previousPosition
                    previousPosition = change.position
                    if (verticalGesture == null) {
                        dragFromDown += positionDelta
                        if (dragFromDown.getDistance() > viewConfiguration.touchSlop) {
                            verticalGesture = abs(dragFromDown.y) > abs(dragFromDown.x)
                        }
                    }
                    if (verticalGesture == true) {
                        state.dragBy(
                            deltaY = positionDelta.y,
                            isAtStart = latestIsAtStart(),
                            isAtEnd = latestIsAtEnd(),
                        )
                    }
                }
            } while (event.changes.any { it.pressed })

            state.settle()
        }
    }.drawWithContent {
        val distancePx = state.distancePx
        if (abs(distancePx) < 0.5f) {
            drawContent()
            return@drawWithContent
        }

        val progress = (abs(distancePx) / state.maxDistancePx).coerceIn(0f, 1f)
        val pivotY = if (distancePx > 0f) 0f else size.height
        clipRect {
            withTransform({
                translate(top = distancePx * StretchTranslationFraction)
                scale(
                    scaleX = 1f,
                    scaleY = 1f + progress * StretchScaleFraction,
                    pivot = Offset(size.width / 2f, pivotY),
                )
            }) {
                this@drawWithContent.drawContent()
            }
        }
    }
}

private class LightweightVerticalOverscrollState(
    private val coroutineScope: CoroutineScope,
    val maxDistancePx: Float,
) {
    var distancePx by mutableFloatStateOf(0f)
        private set

    private var settleJob: Job? = null

    fun interruptSettle() {
        settleJob?.cancel()
    }

    fun dragBy(deltaY: Float, isAtStart: Boolean, isAtEnd: Boolean) {
        if (deltaY == 0f) return
        interruptSettle()

        distancePx = when {
            distancePx > 0f && deltaY < 0f -> (distancePx + deltaY).coerceAtLeast(0f)
            distancePx < 0f && deltaY > 0f -> (distancePx + deltaY).coerceAtMost(0f)
            isAtStart && deltaY > 0f -> resistedDistance(distancePx, deltaY)
            isAtEnd && deltaY < 0f -> resistedDistance(distancePx, deltaY)
            else -> 0f
        }
    }

    fun settle() {
        settleJob?.cancel()
        if (distancePx == 0f) return

        settleJob = coroutineScope.launch {
            animate(
                initialValue = distancePx,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { value, _ ->
                distancePx = value
            }
            distancePx = 0f
        }
    }

    fun dispose() {
        settleJob?.cancel()
    }

    private fun resistedDistance(currentDistance: Float, deltaY: Float): Float {
        val remainingFraction =
            (1f - abs(currentDistance) / maxDistancePx).coerceIn(0.08f, 1f)
        return (currentDistance + deltaY * StretchDragResistance * remainingFraction)
            .coerceIn(-maxDistancePx, maxDistancePx)
    }
}
