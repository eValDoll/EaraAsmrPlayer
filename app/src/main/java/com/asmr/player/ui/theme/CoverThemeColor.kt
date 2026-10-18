package com.asmr.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private const val COLOR_BUCKET_COUNT = 4_096
private const val TARGET_SAMPLE_COUNT = 22_500
private const val MIN_VISIBLE_ALPHA = 125

internal fun coverThemeSeedColorFromBitmap(bitmap: Bitmap, fallbackColor: Color): Color {
    if (bitmap.isRecycled) return fallbackColor
    val width = bitmap.width
    val height = bitmap.height
    val pixelCount = width.toLong() * height
    if (pixelCount <= 0L || pixelCount > Int.MAX_VALUE) return fallbackColor

    val pixels = IntArray(pixelCount.toInt())
    runCatching { bitmap.getPixels(pixels, 0, width, 0, 0, width, height) }
        .getOrElse { return fallbackColor }
    return Color(coverThemeSeedColorFromPixels(pixels, fallbackColor.toArgb()))
}

/** 以椒盐的分箱与候选评分提取主题种子；文字对比度交给现有主题色板处理。 */
internal fun coverThemeSeedColorFromPixels(pixels: IntArray, fallbackArgb: Int): Int {
    if (pixels.isEmpty()) return fallbackArgb

    val populations = IntArray(COLOR_BUCKET_COUNT)
    val redSums = IntArray(COLOR_BUCKET_COUNT)
    val greenSums = IntArray(COLOR_BUCKET_COUNT)
    val blueSums = IntArray(COLOR_BUCKET_COUNT)
    val step = max(1, pixels.size / TARGET_SAMPLE_COUNT)
    for (index in pixels.indices step step) {
        val argb = pixels[index]
        if (argb ushr 24 >= MIN_VISIBLE_ALPHA) {
            val red = argb ushr 16 and 0xFF
            val green = argb ushr 8 and 0xFF
            val blue = argb and 0xFF
            val bucket = ((red shr 4) shl 8) or ((green shr 4) shl 4) or (blue shr 4)
            populations[bucket]++
            redSums[bucket] += red
            greenSums[bucket] += green
            blueSums[bucket] += blue
        }
    }

    val swatches = ArrayList<CoverSwatch>()
    var dominant: CoverSwatch? = null
    var representative: CoverSwatch? = null
    for (bucket in populations.indices) {
        val population = populations[bucket]
        if (population == 0) continue
        val swatch = coverSwatch(
            red = (redSums[bucket].toDouble() / population).roundToInt(),
            green = (greenSums[bucket].toDouble() / population).roundToInt(),
            blue = (blueSums[bucket].toDouble() / population).roundToInt(),
            population = population
        )
        swatches.add(swatch)
        if (population > (dominant?.population ?: 0)) dominant = swatch
        // 该参考色在反编译片段中控制流不完整，这里明确采用非极端颜色中人口最多的一项。
        if (!swatch.isExtreme && population > (representative?.population ?: 0)) {
            representative = swatch
        }
    }

    val dominantSwatch = dominant ?: return fallbackArgb
    val reference = representative ?: dominantSwatch
    var background = dominantSwatch
    if (background.isExtreme) {
        val minimumPopulation = dominantSwatch.population / 2.5
        var colorfulAlternative: CoverSwatch? = null
        for (swatch in swatches) {
            if (!swatch.isExtreme && swatch.population >= minimumPopulation &&
                swatch.saturation > (colorfulAlternative?.saturation ?: -1.0)
            ) {
                colorfulAlternative = swatch
            }
        }
        background = colorfulAlternative ?: background
    }

    val preferDark = relativeLuminance(background.argb) > 0.5
    val outerLightness = if (preferDark) 0.26 else 0.74
    val outerVibrant = bestSwatch(swatches, dominantSwatch.population, outerLightness, 1.0) {
        it.saturation >= 0.35 && if (preferDark) it.lightness <= 0.45 else it.lightness >= 0.55
    }
    val vibrant = bestSwatch(swatches, dominantSwatch.population, 0.50, 1.0) {
        it.lightness in 0.30..0.70 && it.saturation >= 0.35
    }
    val outerMuted = bestSwatch(swatches, dominantSwatch.population, outerLightness, 0.30) {
        it.saturation <= 0.40 && if (preferDark) it.lightness <= 0.45 else it.lightness >= 0.55
    }
    val muted = bestSwatch(swatches, dominantSwatch.population, 0.50, 0.30) {
        it.lightness in 0.30..0.70 && it.saturation <= 0.40
    }

    var selected = morePopulous(outerVibrant, vibrant) ?: morePopulous(muted, outerMuted) ?: reference
    if (selected !== reference && reference.saturation > 0.19 &&
        selected.population.toDouble() / reference.population < 0.01
    ) {
        selected = reference
    }

    val hue = when {
        selected.saturation >= 0.18 -> selected.hue
        reference.saturation >= 0.15 -> reference.hue
        background.saturation >= 0.10 -> background.hue
        // 黑白与灰阶封面保持中性，不凭空补出一个蓝色主题。
        else -> return reference.argb
    }
    val saturation = when {
        selected.saturation >= 0.30 -> selected.saturation
        reference.saturation >= 0.30 -> reference.saturation * 0.85
        background.saturation >= 0.15 -> background.saturation * 0.75
        else -> 0.30
    }.coerceIn(0.30, 0.60)
    return hslToArgb(hue, saturation, selected.lightness)
}

private data class CoverSwatch(
    val argb: Int,
    val hue: Double,
    val saturation: Double,
    val lightness: Double,
    val population: Int
) {
    val isExtreme: Boolean get() = lightness < 0.08 || lightness > 0.90
}

private fun coverSwatch(red: Int, green: Int, blue: Int, population: Int): CoverSwatch {
    val r = red / 255.0
    val g = green / 255.0
    val b = blue / 255.0
    val maximum = max(r, max(g, b))
    val minimum = min(r, min(g, b))
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2.0
    val saturation = if (delta == 0.0) 0.0 else delta / (1.0 - abs(2.0 * lightness - 1.0))
    val hue = when {
        delta == 0.0 -> 0.0
        maximum == r -> ((g - b) / delta + if (g < b) 6.0 else 0.0) * 60.0
        maximum == g -> ((b - r) / delta + 2.0) * 60.0
        else -> ((r - g) / delta + 4.0) * 60.0
    }
    return CoverSwatch(
        argb = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue,
        hue = hue,
        saturation = saturation,
        lightness = lightness,
        population = population
    )
}

private inline fun bestSwatch(
    swatches: List<CoverSwatch>,
    maximumPopulation: Int,
    targetLightness: Double,
    targetSaturation: Double,
    accepts: (CoverSwatch) -> Boolean
): CoverSwatch? {
    var best: CoverSwatch? = null
    var bestScore = -1.0
    for (swatch in swatches) {
        if (!accepts(swatch)) continue
        val score = (1.0 - abs(swatch.lightness - targetLightness)) * 0.52 +
            (1.0 - abs(swatch.saturation - targetSaturation)) * 0.24 +
            swatch.population.toDouble() / maximumPopulation * 0.24
        if (score > bestScore) {
            best = swatch
            bestScore = score
        }
    }
    return best
}

private fun morePopulous(first: CoverSwatch?, second: CoverSwatch?): CoverSwatch? = when {
    first == null -> second
    second == null -> first
    second.population > first.population -> second
    else -> first
}

private fun relativeLuminance(argb: Int): Double =
    linearChannel(argb ushr 16 and 0xFF) * 0.2126 +
        linearChannel(argb ushr 8 and 0xFF) * 0.7152 +
        linearChannel(argb and 0xFF) * 0.0722

private fun linearChannel(channel: Int): Double {
    val value = channel / 255.0
    return if (value <= 0.03928) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
}

private fun hslToArgb(hue: Double, saturation: Double, lightness: Double): Int {
    val chroma = (1.0 - abs(2.0 * lightness - 1.0)) * saturation
    val secondary = chroma * (1.0 - abs(hue / 60.0 % 2.0 - 1.0))
    val offset = lightness - chroma / 2.0
    val red: Double
    val green: Double
    val blue: Double
    when {
        hue < 60.0 -> {
            red = chroma
            green = secondary
            blue = 0.0
        }
        hue < 120.0 -> {
            red = secondary
            green = chroma
            blue = 0.0
        }
        hue < 180.0 -> {
            red = 0.0
            green = chroma
            blue = secondary
        }
        hue < 240.0 -> {
            red = 0.0
            green = secondary
            blue = chroma
        }
        hue < 300.0 -> {
            red = secondary
            green = 0.0
            blue = chroma
        }
        else -> {
            red = chroma
            green = 0.0
            blue = secondary
        }
    }
    return (0xFF shl 24) or
        (((red + offset) * 255.0).roundToInt().coerceIn(0, 255) shl 16) or
        (((green + offset) * 255.0).roundToInt().coerceIn(0, 255) shl 8) or
        ((blue + offset) * 255.0).roundToInt().coerceIn(0, 255)
}
