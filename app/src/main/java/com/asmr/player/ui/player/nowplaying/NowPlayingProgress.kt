package com.asmr.player.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import com.asmr.player.R
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AudioOutputRouteIcon
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.DismissOutsideBoundsOverlay
import com.asmr.player.ui.common.AppVolumeHearingWarningDialog
import com.asmr.player.ui.common.AppVolumeSlider
import com.asmr.player.ui.common.AppVolumeWarningSessionState
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.playback.AppVolume
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.ui.common.EqualizerPanel
import com.asmr.player.ui.common.rememberProtectedAppVolumeChangeState
import com.asmr.player.ui.common.rememberComputedDominantColorCenterWeighted
import com.asmr.player.ui.common.rememberComputedVideoFrameDominantColorCenterWeighted
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.smoothScrollToIndex
import com.asmr.player.ui.library.TagAssignDialog
import com.asmr.player.service.AudioOutputRouteKind
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.Formatting
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleIndexFinder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PlayerProgress(
    positionMs: Long,
    durationMs: Long,
    sliceUiState: SliceUiState,
    onSeekTo: (Long) -> Unit,
    onCutPressed: () -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onSelectSlice: (Long?) -> Unit,
    onLongPressSlice: (Long) -> Unit,
    onUpdateSliceRange: (sliceId: Long, startMs: Long, endMs: Long) -> Unit,
    activeColor: Color,
    inactiveColor: Color
) {
    val colorScheme = AsmrTheme.colorScheme
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = positionMs.coerceIn(0L, safeDuration.takeIf { it > 0 } ?: Long.MAX_VALUE)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var pendingSeekMs by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(safePosition, pendingSeekMs) {
        if (pendingSeekMs >= 0L && abs(safePosition - pendingSeekMs) <= 500L) {
            pendingSeekMs = -1L
        }
    }

    val effectivePosition = if (!isDragging && pendingSeekMs >= 0L && safeDuration > 0L) {
        pendingSeekMs.coerceIn(0L, safeDuration)
    } else {
        safePosition
    }
    val safeFraction = remember(effectivePosition, safeDuration) {
        if (safeDuration > 0L) (effectivePosition.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    }
    val rangeDuration = safeDuration
    val highlightedSliceId = currentSliceIdForPosition(
        positionMs = effectivePosition,
        slices = sliceUiState.slices,
        sliceModeEnabled = sliceUiState.sliceModeEnabled
    )
    val sliderValue = if (isDragging) dragFraction else safeFraction
    val displayPosition = if (isDragging && rangeDuration > 0L) {
        (sliderValue.toDouble() * rangeDuration.toDouble()).roundToLong().coerceIn(0L, rangeDuration)
    } else {
        effectivePosition
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SliceScrubbableSeekBar(
                enabled = rangeDuration > 0L,
                fraction = sliderValue.coerceIn(0f, 1f),
                rangeDurationMs = rangeDuration,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                slices = sliceUiState.slices,
                tempStartMs = sliceUiState.tempStartMs,
                highlightedSliceId = highlightedSliceId,
                selectedSliceId = sliceUiState.selectedSliceId,
                onSelectSlice = onSelectSlice,
                onLongPressSlice = onLongPressSlice,
                onEditCommit = onUpdateSliceRange,
                onGestureActiveChanged = onScrubbingChanged,
                modifier = Modifier.weight(1f),
                onScrubStart = { f ->
                    onScrubbingChanged(true)
                    isDragging = true
                    dragFraction = f
                },
                onScrub = { f ->
                    isDragging = true
                    dragFraction = f
                },
                onScrubStop = { f ->
                    if (rangeDuration > 0L) {
                        val seekMs = (f.toDouble() * rangeDuration.toDouble()).roundToLong().coerceIn(0L, rangeDuration)
                        pendingSeekMs = seekMs
                        onSeekTo(seekMs)
                    }
                    isDragging = false
                    onScrubbingChanged(false)
                }
            )
            IconButton(onClick = onCutPressed, enabled = rangeDuration > 0L) {
                Icon(
                    imageVector = Icons.Outlined.ContentCut,
                    contentDescription = "Cut",
                    tint = if (sliceUiState.tempStartMs != null) activeColor else colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Formatting.formatTrackTime(displayPosition),
                modifier = Modifier.widthIn(min = 45.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textTertiary,
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                Formatting.formatTrackTime(rangeDuration),
                modifier = Modifier.widthIn(min = 45.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textTertiary,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun SliceScrubbableSeekBar(
    enabled: Boolean,
    fraction: Float,
    rangeDurationMs: Long,
    activeColor: Color,
    inactiveColor: Color,
    slices: List<com.asmr.player.domain.model.Slice>,
    tempStartMs: Long?,
    highlightedSliceId: Long?,
    selectedSliceId: Long?,
    onSelectSlice: (Long?) -> Unit,
    onLongPressSlice: (Long) -> Unit,
    onEditCommit: (sliceId: Long, startMs: Long, endMs: Long) -> Unit,
    onGestureActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onScrubStart: (Float) -> Unit,
    onScrub: (Float) -> Unit,
    onScrubStop: (Float) -> Unit
) {
    val f = fraction.coerceIn(0f, 1f)
    var lastFraction by remember(f) { mutableFloatStateOf(f) }

    val thumbRadius = 8.dp
    val trackHeight = 4.dp
    val density = LocalDensity.current
    val tooltipTextSizePx = remember(density) { with(density) { 12.sp.toPx() } }
    val tooltipPadX = remember(density) { with(density) { 8.dp.toPx() } }
    val tooltipPadY = remember(density) { with(density) { 6.dp.toPx() } }
    val tooltipRadius = remember(density) { with(density) { 10.dp.toPx() } }
    val tooltipTextPaint = remember(tooltipTextSizePx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tooltipTextSizePx
        }
    }

    val sliceBorderPx = remember(density) { with(density) { 0.9.dp.toPx() } }
    val selectedSliceExtraHeightPx = remember(density) { with(density) { 3.dp.toPx() } }
    val selectedSliceBorderPx = remember(density) { with(density) { 1.25.dp.toPx() } }
    val tempMarkerWidthPx = remember(density) { with(density) { 3.dp.toPx() } }
    val tempMarkerHeightPx = remember(density) { with(density) { 16.dp.toPx() } }

    val selectedSlice = remember(selectedSliceId, slices) {
        val id = selectedSliceId ?: return@remember null
        slices.firstOrNull { it.id == id }
    }
    var editStartMs by remember(selectedSlice?.id, selectedSlice?.startMs) {
        mutableLongStateOf(selectedSlice?.startMs ?: 0L)
    }
    var editEndMs by remember(selectedSlice?.id, selectedSlice?.endMs) {
        mutableLongStateOf(selectedSlice?.endMs ?: 0L)
    }
    var tooltipMs by remember { mutableLongStateOf(-1L) }
    var tooltipX by remember { mutableFloatStateOf(0f) }
    var tooltipY by remember { mutableFloatStateOf(0f) }

    val inHighlightedSlice = highlightedSliceId != null

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled, rangeDurationMs, slices, selectedSliceId) {
                if (!enabled) return@pointerInput
                val thumbRadiusPx = thumbRadius.toPx()
                detectTapGestures(
                    onTap = { offset ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        val nf = if (endX > startX) {
                            ((offset.x - startX) / (endX - startX)).coerceIn(0f, 1f)
                        } else 0f
                        onScrubStart(nf)
                        onScrubStop(nf)
                    },
                    onLongPress = { offset ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        val nf = if (endX > startX) {
                            ((offset.x - startX) / (endX - startX)).coerceIn(0f, 1f)
                        } else 0f
                        if (rangeDurationMs <= 0L) return@detectTapGestures
                        val ms = (nf.toDouble() * rangeDurationMs.toDouble()).roundToLong().coerceIn(0L, rangeDurationMs)
                        val hit = slices
                            .asSequence()
                            .filter { s -> ms >= s.startMs && ms <= s.endMs }
                            .maxByOrNull { it.id }
                        if (hit != null) {
                            onSelectSlice(hit.id)
                            onLongPressSlice(hit.id)
                        }
                    }
                )
            }
            .pointerInput(enabled, rangeDurationMs, slices, selectedSliceId) {
                if (!enabled) return@pointerInput
                val thumbRadiusPx = thumbRadius.toPx()
                val hitRadiusPx = 16.dp.toPx()
                var mode = 0
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        fun toFraction(x: Float): Float {
                            return if (endX > startX) ((x - startX) / (endX - startX)).coerceIn(0f, 1f) else 0f
                        }
                        val nf = toFraction(offset.x)
                        val sel = selectedSlice
                        mode = 0
                        if (sel != null && rangeDurationMs > 0L) {
                            val selStartX = startX + (endX - startX) * (sel.startMs.toFloat() / rangeDurationMs.toFloat()).coerceIn(0f, 1f)
                            val selEndX = startX + (endX - startX) * (sel.endMs.toFloat() / rangeDurationMs.toFloat()).coerceIn(0f, 1f)
                            mode = when {
                                kotlin.math.abs(offset.x - selStartX) <= hitRadiusPx -> 1
                                kotlin.math.abs(offset.x - selEndX) <= hitRadiusPx -> 2
                                else -> 0
                            }
                        }
                        onGestureActiveChanged(true)
                        if (mode == 0) {
                            lastFraction = nf
                            onScrubStart(nf)
                        } else {
                            tooltipMs = if (mode == 1) editStartMs else editEndMs
                            tooltipX = offset.x
                            tooltipY = offset.y
                        }
                    },
                    onDrag = { change, _ ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        val nf = if (endX > startX) {
                            ((change.position.x - startX) / (endX - startX)).coerceIn(0f, 1f)
                        } else 0f
                        lastFraction = nf
                        if (mode == 0) {
                            onScrub(nf)
                        } else if (rangeDurationMs > 0L) {
                            val ms = (nf.toDouble() * rangeDurationMs.toDouble()).roundToLong().coerceIn(0L, rangeDurationMs)
                            if (mode == 1) {
                                editStartMs = ms
                            } else {
                                editEndMs = ms
                            }
                            tooltipMs = ms
                            tooltipX = change.position.x
                            tooltipY = change.position.y
                        }
                    },
                    onDragCancel = {
                        tooltipMs = -1L
                        mode = 0
                        onGestureActiveChanged(false)
                        onScrubStop(lastFraction)
                    },
                    onDragEnd = {
                        val sel = selectedSlice
                        if (mode == 0) {
                            onScrubStop(lastFraction)
                        } else if (sel != null) {
                            val start = editStartMs
                            val end = editEndMs
                            if (end > start) {
                                onEditCommit(sel.id, start, end)
                            } else {
                                editStartMs = sel.startMs
                                editEndMs = sel.endMs
                            }
                        }
                        tooltipMs = -1L
                        mode = 0
                        onGestureActiveChanged(false)
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val trackHeightPx = trackHeight.toPx()
        val thumbRadiusPx = thumbRadius.toPx()
        val centerY = height / 2f
        val startX = thumbRadiusPx
        val endX = (width - thumbRadiusPx).coerceAtLeast(startX)
        val x = if (endX > startX) {
            startX + (endX - startX) * f
        } else {
            startX
        }

        val sliceHeightPx = (trackHeightPx * 2.35f).coerceAtLeast(trackHeightPx + 3.dp.toPx())
        val tooltipAnchorHeightPx = sliceHeightPx + selectedSliceExtraHeightPx

        drawLine(
            color = inactiveColor,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = trackHeightPx,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        if (rangeDurationMs > 0L) {
            val span = (endX - startX).coerceAtLeast(1f)
            for (s in slices) {
                val sf = (s.startMs.toFloat() / rangeDurationMs.toFloat()).coerceIn(0f, 1f)
                val ef = (s.endMs.toFloat() / rangeDurationMs.toFloat()).coerceIn(0f, 1f)
                val sx = startX + span * sf
                val ex = startX + span * ef
                val w = (ex - sx).coerceAtLeast(1f)
                val isSelected = s.id == selectedSliceId
                val isHighlighted = s.id == highlightedSliceId
                val visualHeightPx = if (isSelected) sliceHeightPx + selectedSliceExtraHeightPx else sliceHeightPx
                val sliceTop = centerY - visualHeightPx / 2f
                val sliceCorner = visualHeightPx / 2f
                val fillAlpha = when {
                    isSelected -> 0.42f
                    isHighlighted -> 0.28f
                    else -> 0.16f
                }
                val borderAlpha = when {
                    isSelected -> 0.82f
                    isHighlighted -> 0.40f
                    else -> 0.22f
                }
                val borderWidthPx = if (isSelected) selectedSliceBorderPx else sliceBorderPx
                drawRoundRect(
                    color = activeColor.copy(alpha = fillAlpha),
                    topLeft = Offset(sx, sliceTop),
                    size = Size(w, visualHeightPx),
                    cornerRadius = CornerRadius(sliceCorner, sliceCorner)
                )
                drawRoundRect(
                    color = activeColor.copy(alpha = borderAlpha),
                    topLeft = Offset(sx, sliceTop),
                    size = Size(w, visualHeightPx),
                    cornerRadius = CornerRadius(sliceCorner, sliceCorner),
                    style = Stroke(width = borderWidthPx)
                )
            }

            val tmp = tempStartMs
            if (tmp != null) {
                val tf = (tmp.toFloat() / rangeDurationMs.toFloat()).coerceIn(0f, 1f)
                val tx = startX + span * tf
                drawRoundRect(
                    color = activeColor.copy(alpha = 0.92f),
                    topLeft = Offset(tx - tempMarkerWidthPx / 2f, centerY - tempMarkerHeightPx / 2f),
                    size = Size(tempMarkerWidthPx, tempMarkerHeightPx),
                    cornerRadius = CornerRadius(tempMarkerWidthPx, tempMarkerWidthPx)
                )
            }
        }
        drawLine(
            color = if (inHighlightedSlice) activeColor else activeColor.copy(alpha = 0.85f),
            start = Offset(startX, centerY),
            end = Offset(x, centerY),
            strokeWidth = trackHeightPx,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawCircle(
            color = activeColor,
            radius = thumbRadiusPx,
            center = Offset(x, centerY)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = (thumbRadiusPx * 0.45f).coerceAtLeast(1f),
            center = Offset(x, centerY)
        )

        if (tooltipMs >= 0L) {
            val t = Formatting.formatTrackTime(tooltipMs)
            val textWidth = tooltipTextPaint.measureText(t).coerceAtLeast(1f)
            val boxW = textWidth + tooltipPadX * 2f
            val boxH = tooltipTextSizePx + tooltipPadY * 2f
            val bx = (tooltipX - boxW / 2f).coerceIn(0f, width - boxW)
            val by = (centerY - tooltipAnchorHeightPx / 2f - boxH - 6.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(bx, by),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(tooltipRadius, tooltipRadius)
            )
            drawIntoCanvas { canvas ->
                tooltipTextPaint.color = android.graphics.Color.WHITE
                canvas.nativeCanvas.drawText(
                    t,
                    bx + tooltipPadX,
                    by + tooltipPadY + tooltipTextSizePx * 0.82f,
                    tooltipTextPaint
                )
            }
        }
    }
}

@Composable
internal fun SliceOverviewBar(
    positionMs: Long,
    durationMs: Long,
    slices: List<com.asmr.player.domain.model.Slice>,
    highlightedSliceId: Long?,
    selectedSliceId: Long?,
    activeColor: Color,
    inactiveColor: Color,
    onSeekTo: (Long) -> Unit,
    onSelectSlice: (Long?) -> Unit,
    onLongPressSlice: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = positionMs.coerceIn(0L, safeDuration.takeIf { it > 0 } ?: Long.MAX_VALUE)
    val fraction = remember(safePosition, safeDuration) {
        if (safeDuration > 0L) (safePosition.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    }
    val thumbRadius = 7.dp
    val trackHeight = 3.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(safeDuration, slices) {
                if (safeDuration <= 0L) return@pointerInput
                val thumbRadiusPx = thumbRadius.toPx()
                detectTapGestures(
                    onTap = { offset ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        val nf = if (endX > startX) ((offset.x - startX) / (endX - startX)).coerceIn(0f, 1f) else 0f
                        val ms = (nf.toDouble() * safeDuration.toDouble()).roundToLong().coerceIn(0L, safeDuration)
                        onSeekTo(ms)
                    },
                    onLongPress = { offset ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        val nf = if (endX > startX) ((offset.x - startX) / (endX - startX)).coerceIn(0f, 1f) else 0f
                        val ms = (nf.toDouble() * safeDuration.toDouble()).roundToLong().coerceIn(0L, safeDuration)
                        val hit = slices
                            .asSequence()
                            .filter { s -> ms >= s.startMs && ms <= s.endMs }
                            .maxByOrNull { it.id }
                        if (hit != null) {
                            onSelectSlice(hit.id)
                            onLongPressSlice(hit.id)
                        }
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val trackHeightPx = trackHeight.toPx()
        val thumbRadiusPx = thumbRadius.toPx()
        val centerY = height / 2f
        val startX = thumbRadiusPx
        val endX = (width - thumbRadiusPx).coerceAtLeast(startX)
        val span = (endX - startX).coerceAtLeast(1f)
        val x = startX + span * fraction

        drawLine(
            color = inactiveColor,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = trackHeightPx,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        if (safeDuration > 0L) {
            val sliceHeightPx = (trackHeightPx * 2.4f).coerceAtLeast(trackHeightPx + 3.dp.toPx())
            val sliceBorderPx = 0.85.dp.toPx()
            val selectedSliceExtraHeightPx = 2.dp.toPx()
            val selectedSliceBorderPx = 1.dp.toPx()
            for (s in slices) {
                val sf = (s.startMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                val ef = (s.endMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                val sx = startX + span * sf
                val ex = startX + span * ef
                val w = (ex - sx).coerceAtLeast(1f)
                val selected = s.id == selectedSliceId
                val highlighted = s.id == highlightedSliceId
                val visualHeightPx = if (selected) sliceHeightPx + selectedSliceExtraHeightPx else sliceHeightPx
                val top = centerY - visualHeightPx / 2f
                val corner = visualHeightPx / 2f
                val fillAlpha = when {
                    selected -> 0.34f
                    highlighted -> 0.24f
                    else -> 0.14f
                }
                val borderAlpha = when {
                    selected -> 0.72f
                    highlighted -> 0.34f
                    else -> 0.20f
                }
                val borderWidthPx = if (selected) selectedSliceBorderPx else sliceBorderPx

                drawRoundRect(
                    color = activeColor.copy(alpha = fillAlpha),
                    topLeft = Offset(sx, top),
                    size = Size(w, visualHeightPx),
                    cornerRadius = CornerRadius(corner, corner)
                )
                drawRoundRect(
                    color = activeColor.copy(alpha = borderAlpha),
                    topLeft = Offset(sx, top),
                    size = Size(w, visualHeightPx),
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = borderWidthPx)
                )
            }
        }
        drawLine(
            color = activeColor,
            start = Offset(startX, centerY),
            end = Offset(x, centerY),
            strokeWidth = trackHeightPx,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawCircle(
            color = activeColor,
            radius = thumbRadiusPx,
            center = Offset(x, centerY)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = (thumbRadiusPx * 0.45f).coerceAtLeast(1f),
            center = Offset(x, centerY)
        )
    }
}

internal fun currentSliceIdForPosition(
    positionMs: Long,
    slices: List<com.asmr.player.domain.model.Slice>,
    sliceModeEnabled: Boolean
): Long? {
    if (!sliceModeEnabled) return null
    return slices.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }?.id
}

@Composable
internal fun SliceTimeEditDialog(
    title: String,
    durationMs: Long,
    currentMs: Long,
    initialMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val boundedInitial = if (safeDuration > 0L) initialMs.coerceIn(0L, safeDuration) else initialMs.coerceAtLeast(0L)
    var selectedMs by remember(boundedInitial) { mutableLongStateOf(boundedInitial) }
    var fraction by remember(boundedInitial, safeDuration) {
        mutableFloatStateOf(if (safeDuration > 0L) (boundedInitial.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f) else 0f)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = Formatting.formatTrackTime(selectedMs),
                    style = MaterialTheme.typography.titleLarge,
                    color = AsmrTheme.colorScheme.onSurface
                )

                if (safeDuration > 0L) {
                    Slider(
                        value = fraction,
                        onValueChange = { f ->
                            fraction = f.coerceIn(0f, 1f)
                            selectedMs = (fraction.toDouble() * safeDuration.toDouble()).roundToLong().coerceIn(0L, safeDuration)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = AsmrTheme.colorScheme.primary,
                            activeTrackColor = AsmrTheme.colorScheme.primary,
                            inactiveTrackColor = AsmrTheme.colorScheme.primary.copy(alpha = 0.18f)
                        )
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = AsmrTheme.colorScheme.primary,
                        trackColor = AsmrTheme.colorScheme.primary.copy(alpha = 0.18f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val v = (selectedMs - 5_000L).coerceAtLeast(0L)
                            selectedMs = if (safeDuration > 0L) v.coerceAtMost(safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("-5s") }
                    OutlinedButton(
                        onClick = {
                            val v = (selectedMs - 1_000L).coerceAtLeast(0L)
                            selectedMs = if (safeDuration > 0L) v.coerceAtMost(safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("-1s") }
                    OutlinedButton(
                        onClick = {
                            val v = (selectedMs - 200L).coerceAtLeast(0L)
                            selectedMs = if (safeDuration > 0L) v.coerceAtMost(safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("-0.2s") }
                    OutlinedButton(
                        onClick = {
                            val v = selectedMs + 200L
                            selectedMs = if (safeDuration > 0L) v.coerceIn(0L, safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("+0.2s") }
                    OutlinedButton(
                        onClick = {
                            val v = selectedMs + 1_000L
                            selectedMs = if (safeDuration > 0L) v.coerceIn(0L, safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("+1s") }
                    OutlinedButton(
                        onClick = {
                            val v = selectedMs + 5_000L
                            selectedMs = if (safeDuration > 0L) v.coerceIn(0L, safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("+5s") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            val v = currentMs.coerceAtLeast(0L)
                            selectedMs = if (safeDuration > 0L) v.coerceAtMost(safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        }
                    ) { Text("设为当前") }
                    TextButton(
                        onClick = {
                            selectedMs = 0L
                            fraction = 0f
                        }
                    ) { Text("设为 0") }
                    if (safeDuration > 0L) {
                        TextButton(
                            onClick = {
                                selectedMs = safeDuration
                                fraction = 1f
                            }
                        ) { Text("设为末尾") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedMs)
                }
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
