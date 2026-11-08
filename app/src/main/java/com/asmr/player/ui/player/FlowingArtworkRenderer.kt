package com.asmr.player.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Owns only its working buffers. Calls must be serialized on background workers. */
internal class FlowingArtworkRenderer {
    private val artworkPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(2.5f) })
    }
    private val matrix = Matrix()
    private val layerCanvas = Canvas()
    private val outputCanvas = Canvas()
    private var layerBitmap: Bitmap? = null
    private var pixels = IntArray(0)
    private var scratch = IntArray(0)
    private var previousPixels = IntArray(0)
    private val meshVertices = FloatArray((MESH_STEPS + 1) * (MESH_STEPS + 1) * 2)
    private var width = 0
    private var height = 0
    private var previousArtwork: Bitmap? = null
    private var artworkGeneration = 0
    private var fallbackColor = Color.BLACK
    private var hasPreviousFrame = false
    private val darkVeil = IntArray(256) { channel ->
        ((channel * 173 + 127) / 255 * 229 + 127) / 255
    }

    /** [target] is the entire low resolution canvas, including any caller supplied padding. */
    fun render(artwork: Bitmap, target: Bitmap, animationTimeNanos: Long) {
        require(!artwork.isRecycled && !target.isRecycled)
        require(target.isMutable && target.config == Bitmap.Config.ARGB_8888)
        require(artwork !== target)
        ensureSize(target.width, target.height)
        if (previousArtwork !== artwork || artworkGeneration != artwork.generationId) {
            previousArtwork = artwork
            artworkGeneration = artwork.generationId
            fallbackColor = sampleFallback(artwork)
            hasPreviousFrame = false
        }
        val working = checkNotNull(layerBitmap)
        working.eraseColor(fallbackColor)
        val targetWidth = width.toFloat()
        val targetHeight = height.toFloat()
        val layerScale = max(targetWidth, targetHeight) * 1.3f / artwork.height
        // The first layer covers every corner at every angle, even for very narrow artwork.
        val baseScale = max(
            layerScale,
            hypot(targetWidth, targetHeight) * 1.02f / min(artwork.width, artwork.height)
        )
        drawLayer(artwork, baseScale, angle(animationTimeNanos, 100L), 0f, 0f)
        drawLayer(
            artwork,
            layerScale,
            -angle(animationTimeNanos, 70L),
            -targetWidth * 0.95f,
            -targetHeight * 0.7f
        )
        val thirdAngle = -angle(animationTimeNanos, 40L)
        val radians = thirdAngle * (Math.PI / 180.0)
        val offsetX = -targetWidth * 0.5f
        val offsetY = targetHeight * 0.7f
        drawLayer(
            artwork,
            layerScale,
            thirdAngle * 2f,
            (offsetX * cos(radians) - offsetY * sin(radians)).toFloat(),
            (offsetX * sin(radians) + offsetY * cos(radians)).toFloat()
        )

        updateMesh(animationTimeNanos)
        target.eraseColor(fallbackColor)
        outputCanvas.setBitmap(target)
        outputCanvas.drawBitmapMesh(working, MESH_STEPS, MESH_STEPS, meshVertices, 0, null, 0, null)
        outputCanvas.setBitmap(null)
        target.getPixels(pixels, 0, width, 0, 0, width, height)

        // Three sliding box passes approximate a radius 25 intrinsic blur (sigma about 10.6).
        // Their cost is linear in pixel count and independent of the blur radius.
        repeat(3) { pass ->
            blurHorizontally()
            blurVertically(if (pass == 2) darkVeil else null)
        }
        target.setPixels(pixels, 0, width, 0, 0, width, height)
        hasPreviousFrame = true
    }

    fun reset() {
        previousArtwork = null
        hasPreviousFrame = false
    }

    fun close() {
        layerCanvas.setBitmap(null)
        outputCanvas.setBitmap(null)
        layerBitmap?.recycle()
        layerBitmap = null
        pixels = IntArray(0)
        scratch = IntArray(0)
        previousPixels = IntArray(0)
        width = 0
        height = 0
        reset()
    }

    private fun ensureSize(targetWidth: Int, targetHeight: Int) {
        if (width == targetWidth && height == targetHeight) return
        layerCanvas.setBitmap(null)
        layerBitmap?.recycle()
        width = targetWidth
        height = targetHeight
        layerBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            layerCanvas.setBitmap(it)
        }
        pixels = IntArray(width * height)
        scratch = IntArray(pixels.size)
        previousPixels = IntArray(pixels.size)
        hasPreviousFrame = false
    }

    private fun drawLayer(artwork: Bitmap, scale: Float, angle: Float, x: Float, y: Float) {
        matrix.reset()
        matrix.postTranslate(-artwork.width * 0.5f, -artwork.height * 0.5f)
        matrix.postScale(scale, scale)
        matrix.postRotate(angle)
        matrix.postTranslate(width * 0.5f + x, height * 0.5f + y)
        layerCanvas.drawBitmap(artwork, matrix, artworkPaint)
    }

    private fun updateMesh(timeNanos: Long) {
        val phase = (timeNanos % 140_000_000_000L).toDouble() / 140_000_000_000.0 * Math.PI * 2.0
        val amplitude = min(width, height) * 0.018f
        var index = 0
        for (row in 0..MESH_STEPS) {
            for (column in 0..MESH_STEPS) {
                val interior = row in 1 until MESH_STEPS && column in 1 until MESH_STEPS
                meshVertices[index++] = width * column.toFloat() / MESH_STEPS +
                    if (interior) sin(phase + row * 1.7 + column * 0.9).toFloat() * amplitude else 0f
                meshVertices[index++] = height * row.toFloat() / MESH_STEPS +
                    if (interior) cos(phase - row * 0.8 + column * 1.6).toFloat() * amplitude else 0f
            }
        }
    }

    private fun blurHorizontally() {
        val width = this.width
        val height = this.height
        val pixels = this.pixels
        val scratch = this.scratch
        val lastColumn = width - 1
        for (y in 0 until height) {
            val row = y * width
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -BOX_RADIUS..BOX_RADIUS) {
                val color = pixels[row + offset.coerceIn(0, lastColumn)]
                red += color ushr 16 and 255
                green += color ushr 8 and 255
                blue += color and 255
            }
            for (x in 0 until width) {
                scratch[row + x] = opaque(red / BOX_SIZE, green / BOX_SIZE, blue / BOX_SIZE)
                val leaving = pixels[row + (x - BOX_RADIUS).coerceIn(0, lastColumn)]
                val entering = pixels[row + (x + BOX_RADIUS + 1).coerceIn(0, lastColumn)]
                red += (entering ushr 16 and 255) - (leaving ushr 16 and 255)
                green += (entering ushr 8 and 255) - (leaving ushr 8 and 255)
                blue += (entering and 255) - (leaving and 255)
            }
        }
    }

    private fun blurVertically(finalVeil: IntArray?) {
        val width = this.width
        val height = this.height
        val pixels = this.pixels
        val scratch = this.scratch
        val previousPixels = this.previousPixels
        val blendPrevious = hasPreviousFrame
        val lastRow = height - 1
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            for (offset in -BOX_RADIUS..BOX_RADIUS) {
                val color = scratch[offset.coerceIn(0, lastRow) * width + x]
                red += color ushr 16 and 255
                green += color ushr 8 and 255
                blue += color and 255
            }
            for (y in 0 until height) {
                val index = y * width + x
                if (finalVeil == null) {
                    pixels[index] = opaque(red / BOX_SIZE, green / BOX_SIZE, blue / BOX_SIZE)
                } else {
                    var finalRed = finalVeil[red / BOX_SIZE]
                    var finalGreen = finalVeil[green / BOX_SIZE]
                    var finalBlue = finalVeil[blue / BOX_SIZE]
                    if (blendPrevious) {
                        val previous = previousPixels[index]
                        finalRed = (finalRed * 191 + (previous ushr 16 and 255) * 64 + 127) / 255
                        finalGreen = (finalGreen * 191 + (previous ushr 8 and 255) * 64 + 127) / 255
                        finalBlue = (finalBlue * 191 + (previous and 255) * 64 + 127) / 255
                    }
                    val color = opaque(finalRed, finalGreen, finalBlue)
                    pixels[index] = color
                    previousPixels[index] = color
                }
                val leaving = scratch[(y - BOX_RADIUS).coerceIn(0, lastRow) * width + x]
                val entering = scratch[(y + BOX_RADIUS + 1).coerceIn(0, lastRow) * width + x]
                red += (entering ushr 16 and 255) - (leaving ushr 16 and 255)
                green += (entering ushr 8 and 255) - (leaving ushr 8 and 255)
                blue += (entering and 255) - (leaving and 255)
            }
        }
    }

    private fun sampleFallback(artwork: Bitmap): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        var weight = 0L
        for (row in 0 until 5) {
            for (column in 0 until 5) {
                val pixel = artwork.getPixel(
                    min((column * 2 + 1) * artwork.width / 10, artwork.width - 1),
                    min((row * 2 + 1) * artwork.height / 10, artwork.height - 1)
                )
                val alpha = pixel ushr 24
                red += (pixel ushr 16 and 255) * alpha
                green += (pixel ushr 8 and 255) * alpha
                blue += (pixel and 255) * alpha
                weight += alpha
            }
        }
        if (weight == 0L) return Color.BLACK
        return opaque((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt())
    }

    private fun angle(timeNanos: Long, periodSeconds: Long): Float {
        val period = periodSeconds * 1_000_000_000L
        return (timeNanos % period).toDouble().div(period).times(360.0).toFloat()
    }

    private fun opaque(red: Int, green: Int, blue: Int): Int =
        (255 shl 24) or (red shl 16) or (green shl 8) or blue

    private companion object {
        const val MESH_STEPS = 5
        const val BOX_RADIUS = 10
        const val BOX_SIZE = BOX_RADIUS * 2 + 1
    }
}
