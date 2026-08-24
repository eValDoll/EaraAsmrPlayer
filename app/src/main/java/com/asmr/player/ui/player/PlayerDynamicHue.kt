package com.asmr.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.media3.common.MediaItem
import com.asmr.player.toThemeMediaSource
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.HuePalette
import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.ui.theme.deriveHuePalette
import com.asmr.player.ui.theme.neutralPaletteForMode
import com.asmr.player.ui.theme.rememberDynamicHuePalette
import com.asmr.player.ui.theme.rememberDynamicHuePaletteFromVideoFrame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun rememberPlayerDynamicHuePalette(
    mediaItem: MediaItem?,
    colorScheme: AsmrColorScheme,
    transitionDurationMs: Int = 1000,
    cachedTransitionDurationMs: Int = 260
): State<HuePalette> {
    val themeMediaSource = remember(mediaItem) { mediaItem.toThemeMediaSource() }
    val mode = colorScheme.mode
    val neutral = remember(mode) { neutralPaletteForMode(mode) }
    val fallbackHue = remember(
        colorScheme.primaryStrong,
        colorScheme.onPrimary,
        mode,
        neutral
    ) {
        deriveHuePalette(
            primary = colorScheme.primaryStrong,
            mode = mode,
            neutral = neutral,
            fallbackOnPrimary = colorScheme.onPrimary
        )
    }

    return if (themeMediaSource.isVideo && themeMediaSource.artworkUri == null) {
        rememberDynamicHuePaletteFromVideoFrame(
            videoUri = themeMediaSource.videoUri,
            mode = mode,
            neutral = neutral,
            fallbackHue = fallbackHue,
            transitionDurationMs = transitionDurationMs,
            cachedTransitionDurationMs = cachedTransitionDurationMs
        )
    } else {
        rememberDynamicHuePalette(
            artworkModel = themeMediaSource.artworkUri,
            mode = mode,
            neutral = neutral,
            fallbackHue = fallbackHue,
            transitionDurationMs = transitionDurationMs,
            cachedTransitionDurationMs = cachedTransitionDurationMs
        )
    }
}

internal data class PlayerThemeColors(
    val coverAccentColor: Color,
    val accentColor: Color,
    val onAccentColor: Color,
    val backdropTintColor: Color,
    val videoBackdropColor: Color
)

@Composable
internal fun rememberPlayerThemeColors(
    mediaItem: MediaItem?,
    colorScheme: AsmrColorScheme,
    coverBackgroundEnabled: Boolean,
    artworkBackdropEnabled: Boolean = coverBackgroundEnabled,
    transitionDurationMs: Int = 1000,
    cachedTransitionDurationMs: Int = 260
): PlayerThemeColors {
    val dynamicHue by rememberPlayerDynamicHuePalette(
        mediaItem = mediaItem,
        colorScheme = colorScheme,
        transitionDurationMs = transitionDurationMs,
        cachedTransitionDurationMs = cachedTransitionDurationMs
    )
    return remember(dynamicHue, colorScheme, coverBackgroundEnabled, artworkBackdropEnabled) {
        resolvePlayerThemeColors(
            dynamicHue = dynamicHue,
            colorScheme = colorScheme,
            coverBackgroundEnabled = coverBackgroundEnabled,
            artworkBackdropEnabled = artworkBackdropEnabled
        )
    }
}

internal fun resolvePlayerThemeColors(
    dynamicHue: HuePalette,
    colorScheme: AsmrColorScheme,
    coverBackgroundEnabled: Boolean,
    artworkBackdropEnabled: Boolean = coverBackgroundEnabled
): PlayerThemeColors {
    val accentColor = if (coverBackgroundEnabled) {
        dynamicHue.primaryStrong
    } else {
        colorScheme.primary
    }
    val onAccentColor = if (coverBackgroundEnabled) {
        dynamicHue.onPrimary
    } else {
        colorScheme.onPrimary
    }
    val dynamicBackdropTintColor = derivePlayerBackdropTintColor(
        primary = dynamicHue.primary,
        primarySoft = dynamicHue.primarySoft,
        background = colorScheme.background,
        mode = colorScheme.mode
    )
    val backdropTintColor = if (artworkBackdropEnabled) {
        dynamicBackdropTintColor
    } else {
        colorScheme.background
    }
    val videoBackdropColor = if (coverBackgroundEnabled) {
        dynamicBackdropTintColor
    } else {
        derivePlayerBackdropTintColor(
            primary = colorScheme.primary,
            primarySoft = colorScheme.primarySoft,
            background = colorScheme.background,
            mode = colorScheme.mode
        )
    }
    return PlayerThemeColors(
        coverAccentColor = dynamicHue.primaryStrong,
        accentColor = accentColor,
        onAccentColor = onAccentColor,
        backdropTintColor = backdropTintColor,
        videoBackdropColor = videoBackdropColor
    )
}

internal fun derivePlayerBackdropTintColor(
    primary: Color,
    primarySoft: Color,
    background: Color,
    mode: ThemeMode
): Color {
    val seedMixed = blendSrgbColors(
        start = primarySoft,
        end = primary,
        fraction = when (mode) {
            ThemeMode.Light -> 0.52f
            ThemeMode.Dark -> 0.72f
            ThemeMode.SoftDark -> 0.64f
        }
    )
    val mixed = blendSrgbColors(
        start = background,
        end = seedMixed,
        fraction = when (mode) {
            ThemeMode.Light -> 0.86f
            ThemeMode.Dark -> 0.88f
            ThemeMode.SoftDark -> 0.86f
        }
    )
    val hsl = mixed.toHsl()

    when (mode) {
        ThemeMode.Light -> {
            hsl[1] = hsl[1].coerceIn(0.22f, 0.56f)
            hsl[2] = hsl[2].coerceIn(0.66f, 0.78f)
        }
        ThemeMode.Dark -> {
            hsl[1] = hsl[1].coerceIn(0.24f, 0.58f)
            hsl[2] = hsl[2].coerceIn(0.42f, 0.56f)
        }
        ThemeMode.SoftDark -> {
            hsl[1] = hsl[1].coerceIn(0.22f, 0.52f)
            hsl[2] = hsl[2].coerceIn(0.46f, 0.60f)
        }
    }

    return hslToColor(hsl)
}

private fun blendSrgbColors(
    start: Color,
    end: Color,
    fraction: Float
): Color {
    val t = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * t,
        green = start.green + (end.green - start.green) * t,
        blue = start.blue + (end.blue - start.blue) * t,
        alpha = start.alpha + (end.alpha - start.alpha) * t
    )
}

private fun Color.toHsl(): FloatArray {
    val maxComponent = max(red, max(green, blue))
    val minComponent = min(red, min(green, blue))
    val lightness = (maxComponent + minComponent) / 2f
    if (abs(maxComponent - minComponent) < 0.00001f) {
        return floatArrayOf(0f, 0f, lightness)
    }

    val delta = maxComponent - minComponent
    val saturation = if (lightness > 0.5f) {
        delta / (2f - maxComponent - minComponent)
    } else {
        delta / (maxComponent + minComponent)
    }
    val hue = when (maxComponent) {
        red -> ((green - blue) / delta + if (green < blue) 6f else 0f) * 60f
        green -> ((blue - red) / delta + 2f) * 60f
        else -> ((red - green) / delta + 4f) * 60f
    }
    return floatArrayOf(hue, saturation, lightness)
}

private fun hslToColor(hsl: FloatArray): Color {
    val hue = ((hsl[0] % 360f) + 360f) % 360f
    val saturation = hsl[1].coerceIn(0f, 1f)
    val lightness = hsl[2].coerceIn(0f, 1f)
    if (saturation <= 0f) {
        return Color(lightness, lightness, lightness)
    }

    val q = if (lightness < 0.5f) {
        lightness * (1f + saturation)
    } else {
        lightness + saturation - lightness * saturation
    }
    val p = 2f * lightness - q
    val hk = hue / 360f
    return Color(
        red = hueToRgb(p, q, hk + 1f / 3f),
        green = hueToRgb(p, q, hk),
        blue = hueToRgb(p, q, hk - 1f / 3f)
    )
}

private fun hueToRgb(
    p: Float,
    q: Float,
    hue: Float
): Float {
    val t = when {
        hue < 0f -> hue + 1f
        hue > 1f -> hue - 1f
        else -> hue
    }
    return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 1f / 2f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
    }.coerceIn(0f, 1f)
}
