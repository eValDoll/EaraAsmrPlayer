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

    val onPrimary = bestOnPrimary(primary, fallbackOnPrimary)

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
    val lightnessMin = when (mode) {
        ThemeMode.Light -> 0.12f
        ThemeMode.Dark -> 0.18f
        ThemeMode.SoftDark -> 0.20f
    }
    val lightnessMax = when (mode) {
        ThemeMode.Light -> 0.62f
        ThemeMode.Dark -> 0.70f
        ThemeMode.SoftDark -> 0.66f
    }
    val saturationMax = when (mode) {
        ThemeMode.Light -> 0.82f
        ThemeMode.Dark -> 0.80f
        ThemeMode.SoftDark -> 0.78f
    }
    hsl[1] = hsl[1].coerceIn(0f, saturationMax)
    hsl[2] = hsl[2].coerceIn(lightnessMin, lightnessMax)
}

