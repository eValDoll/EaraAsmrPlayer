package com.asmr.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

internal fun deriveHuePalette(
    primary: Color,
    mode: ThemeMode,
    neutral: NeutralPalette,
    fallbackOnPrimary: Color
): HuePalette {
    val primaryArgb = primary.toArgb()
    val bgArgb = neutral.background.toArgb()

    val softStrength = when (mode) {
        ThemeMode.Light -> 0.12f
        ThemeMode.Dark -> 0.22f
        ThemeMode.SoftDark -> 0.18f
    }
    val strongBlend = when (mode) {
        ThemeMode.Light -> 0.88f
        ThemeMode.Dark -> 0.90f
        ThemeMode.SoftDark -> 0.88f
    }

    val primarySoft = Color(ColorUtils.blendARGB(bgArgb, primaryArgb, softStrength))
    val primaryStrong = run {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(primaryArgb, hsl)
        val saturationBoost = when (mode) {
            ThemeMode.Light -> 0.05f
            ThemeMode.Dark -> 0.06f
            ThemeMode.SoftDark -> 0.05f
        }
        val lightnessShift = when (mode) {
            ThemeMode.Light -> -0.06f
            ThemeMode.Dark -> 0.05f
            ThemeMode.SoftDark -> 0.04f
        }
        if (hsl[1] >= 0.12f) {
            hsl[1] = (hsl[1] + saturationBoost).coerceIn(0f, 1f)
        }
        hsl[2] = (hsl[2] + lightnessShift).coerceIn(0f, 1f)
        clampPrimaryHslForMode(hsl, mode)
        val target = Color(ColorUtils.HSLToColor(hsl))
        Color(ColorUtils.blendARGB(primaryArgb, target.toArgb(), strongBlend))
    }

    val onPrimary = bestOnPrimary(primaryStrong, fallbackOnPrimary)

    return HuePalette(
        primary = primary,
        primarySoft = primarySoft,
        primaryStrong = primaryStrong,
        onPrimary = onPrimary
    )
}

internal fun bestOnPrimary(primary: Color, fallback: Color): Color {
    val bg = primary.toArgb()
    val white = Color.White.toArgb()
    val black = Color.Black.toArgb()
    val contrastWhite = ColorUtils.calculateContrast(white, bg)
    val contrastBlack = ColorUtils.calculateContrast(black, bg)
    val pick = if (contrastWhite >= contrastBlack) Color.White else Color.Black
    val contrastPick = if (pick == Color.White) contrastWhite else contrastBlack
    if (contrastPick >= 4.5) return pick
    return fallback
}

internal fun clampPrimaryHslForMode(hsl: FloatArray, mode: ThemeMode) {
    val hue = ((hsl[0] % 360f) + 360f) % 360f
    val saturation = hsl[1].coerceIn(0f, 1f)
    val lightnessMin = when (mode) {
        ThemeMode.Light -> 0.18f
        ThemeMode.Dark -> when {
            hue in 38f..85f -> 0.58f
            hue in 85f..170f -> 0.54f
            hue in 170f..260f -> 0.48f
            else -> 0.52f
        }
        ThemeMode.SoftDark -> when {
            hue in 38f..85f -> 0.54f
            hue in 85f..170f -> 0.50f
            hue in 170f..260f -> 0.45f
            else -> 0.48f
        }
    }
    val lightnessMax = when (mode) {
        ThemeMode.Light -> when {
            hue in 38f..85f -> if (saturation >= 0.45f) 0.38f else 0.42f
            hue in 85f..170f -> if (saturation >= 0.45f) 0.40f else 0.44f
            hue in 170f..260f -> if (saturation >= 0.45f) 0.46f else 0.50f
            else -> if (saturation >= 0.45f) 0.42f else 0.46f
        }
        ThemeMode.Dark -> when {
            hue in 38f..85f -> 0.74f
            hue in 85f..170f -> 0.70f
            hue in 170f..260f -> 0.66f
            else -> 0.68f
        }
        ThemeMode.SoftDark -> when {
            hue in 38f..85f -> 0.70f
            hue in 85f..170f -> 0.66f
            hue in 170f..260f -> 0.62f
            else -> 0.64f
        }
    }
    val saturationMax = when (mode) {
        ThemeMode.Light -> 0.76f
        ThemeMode.Dark -> 0.78f
        ThemeMode.SoftDark -> 0.76f
    }
    hsl[1] = hsl[1].coerceIn(0f, saturationMax)
    hsl[2] = hsl[2].coerceIn(lightnessMin, lightnessMax)
}

