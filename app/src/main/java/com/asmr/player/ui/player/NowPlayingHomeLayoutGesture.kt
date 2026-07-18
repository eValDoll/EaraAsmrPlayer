package com.asmr.player.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import kotlin.math.abs

private val NowPlayingHomeLayoutSwipeThreshold = 56.dp
private const val NowPlayingHomeLayoutVerticalDominance = 1.15f

internal fun Modifier.nowPlayingHomeLayoutSwipeGesture(
    enabled: Boolean,
    currentMode: NowPlayingHomeLayoutMode,
    onModeChange: (NowPlayingHomeLayoutMode) -> Unit
): Modifier {
    if (!enabled) return this
    return pointerInput(enabled, currentMode, onModeChange) {
        val thresholdPx = NowPlayingHomeLayoutSwipeThreshold.toPx()
        awaitEachGesture {
            var totalDragX = 0f
            var totalDragY = 0f
            var multiPointerGesture = false
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size > 1) {
                    multiPointerGesture = true
                    continue
                }
                val change = pressed.first()
                totalDragX += change.position.x - change.previousPosition.x
                totalDragY += change.position.y - change.previousPosition.y
                if (isNowPlayingHomeLayoutSwipeCandidate(totalDragX, totalDragY, thresholdPx * 0.4f) &&
                    change.positionChanged()
                ) {
                    change.consume()
                }
            }
            if (!multiPointerGesture) {
                val nextMode = resolveNowPlayingHomeLayoutModeAfterSwipe(
                    currentMode = currentMode,
                    totalDragX = totalDragX,
                    totalDragY = totalDragY,
                    thresholdPx = thresholdPx
                )
                if (nextMode != currentMode) {
                    onModeChange(nextMode)
                }
            }
        }
    }
}

internal fun resolveNowPlayingHomeLayoutModeAfterSwipe(
    currentMode: NowPlayingHomeLayoutMode,
    totalDragX: Float,
    totalDragY: Float,
    thresholdPx: Float
): NowPlayingHomeLayoutMode {
    if (!isNowPlayingHomeLayoutSwipeCandidate(totalDragX, totalDragY, thresholdPx)) {
        return currentMode
    }
    return if (totalDragY < 0f) {
        NowPlayingHomeLayoutMode.Expanded
    } else {
        NowPlayingHomeLayoutMode.Classic
    }
}

private fun isNowPlayingHomeLayoutSwipeCandidate(
    totalDragX: Float,
    totalDragY: Float,
    thresholdPx: Float
): Boolean {
    if (!totalDragX.isFinite() || !totalDragY.isFinite() || !thresholdPx.isFinite()) return false
    val verticalDistance = abs(totalDragY)
    val horizontalDistance = abs(totalDragX)
    val safeThreshold = thresholdPx.coerceAtLeast(1f)
    return verticalDistance >= safeThreshold &&
        verticalDistance >= horizontalDistance * NowPlayingHomeLayoutVerticalDominance
}
