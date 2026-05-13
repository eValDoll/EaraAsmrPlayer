package com.asmr.player.data.remote.dlsite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor

internal const val DLSITE_PLAY_PREVIEW_CACHE_VERSION = 2

internal fun parseDlsitePlayCryptFlag(value: Any?): Boolean {
    return when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> {
            val normalized = value.trim().lowercase()
            when {
                normalized.isBlank() -> false
                normalized == "true" || normalized == "yes" || normalized == "y" || normalized == "on" -> true
                normalized == "false" || normalized == "no" || normalized == "n" || normalized == "off" -> false
                else -> normalized.toIntOrNull()?.let { it != 0 } ?: false
            }
        }
        else -> false
    }
}

internal fun parseDlsitePlayImageSeed(optimizedName: String?): Int? {
    val normalized = optimizedName?.trim().orEmpty()
    if (normalized.length < 12) return null
    return normalized.substring(5, 12).toIntOrNull(16)
}

internal fun descrambleDlsitePlayBitmap(src: Bitmap, seed: Int, width: Int, height: Int): Bitmap {
    val tileSize = 128
    val tilesW = ceil(width / tileSize.toDouble()).toInt().coerceAtLeast(1)
    val tilesH = ceil(height / tileSize.toDouble()).toInt().coerceAtLeast(1)
    val tileCount = tilesW * tilesH
    val order = dlsitePlayMtShuffledTiles(seed, tileCount)
    val reverse = IntArray(tileCount)
    order.forEachIndexed { index, value ->
        if (value in reverse.indices) reverse[value] = index
    }

    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    for (i in 0 until tileCount) {
        val srcIndex = reverse[i]
        val srcX = (srcIndex % tilesW) * tileSize
        val srcY = (srcIndex / tilesW) * tileSize
        val dstX = (i % tilesW) * tileSize
        val dstY = (i / tilesW) * tileSize
        val srcW = minOf(tileSize, src.width - srcX)
        val srcH = minOf(tileSize, src.height - srcY)
        if (srcW <= 0 || srcH <= 0) continue
        canvas.drawBitmap(
            src,
            Rect(srcX, srcY, srcX + srcW, srcY + srcH),
            Rect(dstX, dstY, dstX + srcW, dstY + srcH),
            paint
        )
    }

    val croppedWidth = width.coerceAtMost(out.width)
    val croppedHeight = height.coerceAtMost(out.height)
    if (croppedWidth == out.width && croppedHeight == out.height) return out
    val cropped = Bitmap.createBitmap(out, 0, 0, croppedWidth, croppedHeight)
    if (cropped !== out) out.recycle()
    return cropped
}

private fun dlsitePlayMtShuffledTiles(seed: Int, length: Int): List<Int> {
    val random = DlsitePlayMtRandom(seed)
    val values = MutableList(length) { it }
    for (n in length - 1 downTo 0) {
        val index = floor(random.nextDouble() * (n + 1)).toInt()
        val tmp = values[n]
        values[n] = values[index]
        values[index] = tmp
    }
    return values
}

private class DlsitePlayMtRandom(seed: Int) {
    private val mt = IntArray(624)
    private var index = 624

    init {
        mt[0] = seed
        for (i in 1 until 624) {
            val prev = mt[i - 1]
            mt[i] = (1812433253L * (prev xor (prev ushr 30)) + i).toInt()
        }
    }

    fun nextDouble(): Double {
        val a = nextInt32() ushr 5
        val b = peekInt32() ushr 6
        return (a * 67108864.0 + b) / 9007199254740992.0
    }

    private fun nextInt32(): Int {
        if (index >= 624) twist()
        val value = temper(mt[index])
        index += 1
        return value
    }

    private fun peekInt32(): Int {
        if (index < 624) return temper(mt[index])

        val saved = mt.copyOf()
        val savedIndex = index
        twist()
        val value = temper(mt[index])
        saved.copyInto(mt)
        index = savedIndex
        return value
    }

    private fun temper(value: Int): Int {
        var y = value
        y = y xor (y ushr 11)
        y = y xor ((y shl 7) and -1658038656)
        y = y xor ((y shl 15) and -272236544)
        y = y xor (y ushr 18)
        return y
    }

    private fun twist() {
        for (i in 0 until 624) {
            val y = (mt[i] and Int.MIN_VALUE) or (mt[(i + 1) % 624] and Int.MAX_VALUE)
            mt[i] = mt[(i + 397) % 624] xor (y ushr 1)
            if ((y and 1) != 0) mt[i] = mt[i] xor -1727483681
        }
        index = 0
    }
}
