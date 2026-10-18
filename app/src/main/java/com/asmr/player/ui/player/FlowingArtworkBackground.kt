package com.asmr.player.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Trace
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val FLOW_PADDING_PX = 29
private const val COVER_CROSSFADE_NANOS = 500_000_000L

private class FlowingArtworkClock {
    var artwork: Bitmap? = null
    var elapsedNanos = 0L
}

private class FlowingArtworkFrame(
    val bitmap: Bitmap,
    val artwork: Bitmap,
    val previous: Bitmap?,
    val alpha: Float
)

@Composable
internal fun FlowingArtworkBackground(
    artwork: Bitmap,
    isPlaying: Boolean,
    isActive: Boolean,
    dynamicEnabled: Boolean
) {
    val size = remember { mutableStateOf(IntSize.Zero) }
    val frame = remember { mutableStateOf<FlowingArtworkFrame?>(null) }
    val clock = remember { FlowingArtworkClock() }
    val latestAnimating by rememberUpdatedState(isPlaying && dynamicEnabled)
    val densityDpi = LocalConfiguration.current.densityDpi
    val downsample = if (densityDpi >= 420) 32 else 20
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val source = remember { Rect() }
    val destination = remember { RectF() }

    LaunchedEffect(artwork, size.value, isActive, downsample) {
        val dimensions = size.value
        if (!isActive || dimensions == IntSize.Zero || artwork.isRecycled) return@LaunchedEffect
        val bufferWidth = max(2, (dimensions.width.toFloat() / downsample).roundToInt()) + FLOW_PADDING_PX * 2
        val bufferHeight = max(2, (dimensions.height.toFloat() / downsample).roundToInt()) + FLOW_PADDING_PX * 2
        val renderer = withContext(Dispatchers.Default) { FlowingArtworkRenderer() }
        val buffers = arrayOfNulls<Bitmap>(3)
        val oldFrame = frame.value
        val crossfade = oldFrame?.artwork !== artwork
        val previous = if (crossfade && oldFrame != null) {
            withContext(Dispatchers.Default) { freezeFlowingFrame(oldFrame) }
        } else null
        if (clock.artwork !== artwork) {
            clock.artwork = artwork
            clock.elapsedNanos = 0L
        }
        var index = 0
        var lastFrameTime = 0L
        var transitionStart = 0L
        try {
            while (currentCoroutineContext().isActive) {
                val frameTime = withFrameNanos { it }
                if (lastFrameTime != 0L && latestAnimating) {
                    clock.elapsedNanos += (frameTime - lastFrameTime).coerceIn(0L, 50_000_000L)
                }
                lastFrameTime = frameTime
                val elapsed = clock.elapsedNanos
                val next = withContext(Dispatchers.Default) {
                    val target = buffers[index] ?: Bitmap.createBitmap(
                        bufferWidth,
                        bufferHeight,
                        Bitmap.Config.ARGB_8888
                    ).also { buffers[index] = it }
                    Trace.beginSection("player.flow.render")
                    try {
                        renderer.render(artwork, target, elapsed)
                    } finally {
                        Trace.endSection()
                    }
                    target
                }
                if (transitionStart == 0L) transitionStart = frameTime
                val alpha = if (!crossfade) 1f else {
                    ((frameTime - transitionStart).toDouble() / COVER_CROSSFADE_NANOS)
                        .toFloat().coerceIn(0f, 1f)
                }
                frame.value = FlowingArtworkFrame(next, artwork, previous.takeIf { alpha < 1f }, alpha)
                index = (index + 1) % buffers.size
                if (!latestAnimating && alpha >= 1f) {
                    snapshotFlow { latestAnimating }.first { it }
                    lastFrameTime = 0L
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Default) { renderer.close() }
        }
    }

    Canvas(Modifier.fillMaxSize().onSizeChanged { size.value = it }) {
        val current = frame.value ?: return@Canvas
        destination.set(0f, 0f, this.size.width, this.size.height)
        fun drawBitmap(bitmap: Bitmap, alpha: Float) {
            source.set(
                FLOW_PADDING_PX,
                FLOW_PADDING_PX,
                bitmap.width - FLOW_PADDING_PX,
                bitmap.height - FLOW_PADDING_PX
            )
            paint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
            drawContext.canvas.nativeCanvas.drawBitmap(bitmap, source, destination, paint)
        }
        current.previous?.let { drawBitmap(it, 1f) }
        drawBitmap(current.bitmap, current.alpha)
    }
}

private fun freezeFlowingFrame(frame: FlowingArtworkFrame): Bitmap {
    if (frame.alpha >= 1f) return frame.bitmap
    val frozen = Bitmap.createBitmap(frame.bitmap.width, frame.bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = BitmapCanvas(frozen)
    val destination = RectF(
        FLOW_PADDING_PX.toFloat(), FLOW_PADDING_PX.toFloat(),
        (frozen.width - FLOW_PADDING_PX).toFloat(), (frozen.height - FLOW_PADDING_PX).toFloat()
    )
    val source = Rect()
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    fun draw(bitmap: Bitmap, alpha: Float) {
        source.set(FLOW_PADDING_PX, FLOW_PADDING_PX, bitmap.width - FLOW_PADDING_PX, bitmap.height - FLOW_PADDING_PX)
        paint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawBitmap(bitmap, source, destination, paint)
    }
    frame.previous?.let { draw(it, 1f) }
    draw(frame.bitmap, frame.alpha)
    canvas.setBitmap(null)
    return frozen
}
