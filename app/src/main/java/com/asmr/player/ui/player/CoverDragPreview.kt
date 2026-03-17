package com.asmr.player.ui.player

import android.view.Surface
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

@Stable
internal class CoverDragPreviewState {
    var horizontalBias by mutableFloatStateOf(0f)
        private set

    var verticalBias by mutableFloatStateOf(0f)
        private set

    fun reset() {
        horizontalBias = 0f
        verticalBias = 0f
    }

    fun dragBy(deltaX: Float, deltaY: Float, containerWidthPx: Float, containerHeightPx: Float) {
        horizontalBias = dragBiasAfterDelta(
            currentBias = horizontalBias,
            deltaPx = deltaX,
            containerPx = containerWidthPx
        )
        verticalBias = dragBiasAfterDelta(
            currentBias = verticalBias,
            deltaPx = deltaY,
            containerPx = containerHeightPx
        )
    }
}

@Composable
internal fun rememberCoverDragPreviewState(
    enabled: Boolean,
    resetKey: Any? = Unit
): CoverDragPreviewState {
    val state = remember { CoverDragPreviewState() }
    val displayRotation = LocalView.current.display?.rotation ?: Surface.ROTATION_0

    LaunchedEffect(enabled, resetKey, displayRotation) {
        state.reset()
    }

    return state
}

internal fun CoverDragPreviewState.toAlignment(): Alignment {
    return BiasAlignment(horizontalBias = horizontalBias, verticalBias = verticalBias)
}

internal fun Modifier.coverDragPreviewGesture(
    enabled: Boolean,
    state: CoverDragPreviewState,
    minPointers: Int
): Modifier {
    return pointerInput(enabled, state, minPointers) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            var dragging = false
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.any { change -> change.isConsumed }) continue
                if (!dragging) {
                    if (pressed.size < minPointers) continue
                    dragging = true
                }
                if (pressed.size < minPointers) break

                var deltaX = 0f
                var deltaY = 0f
                pressed.forEach { change ->
                    deltaX += change.position.x - change.previousPosition.x
                    deltaY += change.position.y - change.previousPosition.y
                }
                deltaX /= pressed.size.toFloat()
                deltaY /= pressed.size.toFloat()
                if (abs(deltaX) <= 0.01f && abs(deltaY) <= 0.01f) continue

                state.dragBy(
                    deltaX = deltaX,
                    deltaY = deltaY,
                    containerWidthPx = size.width.toFloat(),
                    containerHeightPx = size.height.toFloat()
                )
                pressed.forEach { change ->
                    if (change.positionChanged()) {
                        change.consume()
                    }
                }
            }
        }
    }
}

internal fun dragBiasAfterDelta(
    currentBias: Float,
    deltaPx: Float,
    containerPx: Float
): Float {
    if (!currentBias.isFinite() || !deltaPx.isFinite() || !containerPx.isFinite() || containerPx <= 1f) {
        return currentBias.coerceIn(-1f, 1f)
    }
    val normalizedDelta = (deltaPx * 2f) / containerPx
    return (currentBias + normalizedDelta).coerceIn(-1f, 1f)
}
