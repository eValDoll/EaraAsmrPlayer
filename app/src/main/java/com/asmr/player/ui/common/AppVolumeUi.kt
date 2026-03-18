package com.asmr.player.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.asmr.player.playback.AppVolume
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectTapGestures

@Composable
fun AppVolumeSlider(
    valuePercent: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color,
    onInteractionActiveChanged: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true
) {
    AppVolumeTrack(
        valuePercent = valuePercent,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        accentColor = accentColor,
        orientation = Orientation.Horizontal,
        onInteractionActiveChanged = onInteractionActiveChanged,
        enabled = enabled
    )
}

@Composable
fun AppVolumeVerticalSlider(
    valuePercent: Int,
    onValueChange: (Int) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onInteractionActiveChanged: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true
) {
    AppVolumeTrack(
        valuePercent = valuePercent,
        onValueChange = onValueChange,
        modifier = modifier
            .width(26.dp)
            .height(180.dp),
        accentColor = accentColor,
        orientation = Orientation.Vertical,
        onInteractionActiveChanged = onInteractionActiveChanged,
        enabled = enabled
    )
}

@Composable
private fun AppVolumeTrack(
    valuePercent: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier,
    accentColor: Color,
    orientation: Orientation,
    onInteractionActiveChanged: ((Boolean) -> Unit)?,
    enabled: Boolean
) {
    val clampedValue = remember(valuePercent) { AppVolume.clampPercent(valuePercent) }
    var dragPercent by remember(orientation) { mutableFloatStateOf(clampedValue.toFloat()) }
    var isDragging by remember(orientation) { androidx.compose.runtime.mutableStateOf(false) }
    var lastDispatchedPercent by remember(orientation) { mutableIntStateOf(clampedValue) }
    var trackLengthPx by remember(orientation) { mutableFloatStateOf(240f) }

    LaunchedEffect(clampedValue, isDragging) {
        if (!isDragging) {
            dragPercent = clampedValue.toFloat()
            lastDispatchedPercent = clampedValue
        }
    }

    val draggableState = rememberDraggableState { delta ->
        val range = AppVolume.MaxPercent.toFloat()
        val scaleMultiplier = if (orientation == Orientation.Vertical) 2.25f else 2.45f
        val scale = trackLengthPx.coerceAtLeast(1f) * scaleMultiplier
        val percentDelta = (delta / scale) * range
        if (kotlin.math.abs(percentDelta) < 0.01f) return@rememberDraggableState
        dragPercent = if (orientation == Orientation.Horizontal) {
            dragPercent + percentDelta
        } else {
            dragPercent - percentDelta
        }
        val next = AppVolume.clampPercent(dragPercent.roundToInt())
        if (next != lastDispatchedPercent) {
            lastDispatchedPercent = next
            onValueChange(next)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                trackLengthPx = if (orientation == Orientation.Horizontal) {
                    size.width.toFloat()
                } else {
                    size.height.toFloat()
                }
            }
            .pointerInput(enabled, clampedValue, orientation) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    if (clampedValue > AppVolume.NormalMaxPercent) return@detectTapGestures
                    val fraction = if (orientation == Orientation.Horizontal) {
                        (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    } else {
                        (1f - offset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                    }
                    onValueChange((fraction * AppVolume.NormalMaxPercent).roundToInt())
                }
            }
            .draggable(
                state = draggableState,
                orientation = orientation,
                enabled = enabled,
                onDragStarted = {
                    dragPercent = clampedValue.toFloat()
                    lastDispatchedPercent = clampedValue
                    isDragging = true
                    onInteractionActiveChanged?.invoke(true)
                },
                onDragStopped = {
                    isDragging = false
                    onInteractionActiveChanged?.invoke(false)
                }
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val thumbRadius = 8.dp.toPx()
            val stroke = if (orientation == Orientation.Horizontal) 4.dp.toPx() else 6.dp.toPx()
            val displayPercent = if (isDragging) {
                dragPercent.coerceIn(AppVolume.MinPercent.toFloat(), AppVolume.MaxPercent.toFloat())
            } else {
                clampedValue.toFloat()
            }
            val visualFraction = (displayPercent.coerceAtMost(AppVolume.NormalMaxPercent.toFloat()) / AppVolume.NormalMaxPercent.toFloat())
                .coerceIn(0f, 1f)

            if (orientation == Orientation.Horizontal) {
                val centerY = size.height / 2f
                val startX = thumbRadius
                val endX = (size.width - thumbRadius).coerceAtLeast(startX)
                val progressX = startX + (endX - startX) * visualFraction

                drawLine(
                    color = accentColor.copy(alpha = 0.2f),
                    start = Offset(startX, centerY),
                    end = Offset(endX, centerY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                if (progressX > startX) {
                    drawLine(
                        color = accentColor,
                        start = Offset(startX, centerY),
                        end = Offset(progressX, centerY),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(
                    color = accentColor,
                    radius = thumbRadius,
                    center = Offset(progressX, centerY)
                )
            } else {
                val centerX = size.width / 2f
                val top = thumbRadius
                val bottom = (size.height - thumbRadius).coerceAtLeast(top)
                val activeY = bottom - (bottom - top) * visualFraction

                drawLine(
                    color = accentColor.copy(alpha = 0.18f),
                    start = Offset(centerX, bottom),
                    end = Offset(centerX, top),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                if (activeY < bottom) {
                    drawLine(
                        color = accentColor,
                        start = Offset(centerX, bottom),
                        end = Offset(centerX, activeY),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(
                    color = accentColor,
                    radius = thumbRadius,
                    center = Offset(centerX, activeY)
                )
            }
        }
    }
}
