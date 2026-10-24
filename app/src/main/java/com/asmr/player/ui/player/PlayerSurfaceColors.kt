package com.asmr.player.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.asmr.player.data.settings.PlayerBackgroundMode
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.ui.theme.neutralPaletteForMode

internal fun playerSupportingTextColor(isDark: Boolean): Color =
    themedLyricTextColor(isDark)

internal fun playerSurfaceColors(
    base: AsmrColorScheme,
    backgroundMode: PlayerBackgroundMode,
    coverEnabled: Boolean
): AsmrColorScheme {
    if (backgroundMode == PlayerBackgroundMode.Solid) {
        val neutral = neutralPaletteForMode(base.mode)
        return base.copy(
            background = when (base.mode) {
                ThemeMode.Light -> Color(0xFFDEE0DE)
                ThemeMode.Dark -> Color(0xFF282A2C)
                ThemeMode.SoftDark -> Color(0xFF303337)
            },
            onBackground = neutral.onBackground,
            surface = neutral.surface,
            onSurface = neutral.onSurface,
            surfaceVariant = neutral.surfaceVariant,
            onSurfaceVariant = neutral.onSurfaceVariant,
            textPrimary = neutral.textPrimary,
            textSecondary = neutral.textSecondary,
            textTertiary = neutral.textTertiary
        )
    }
    if (backgroundMode == PlayerBackgroundMode.Theme && coverEnabled) return base

    val flowing = backgroundMode == PlayerBackgroundMode.Flowing
    val neutral = neutralPaletteForMode(ThemeMode.Dark)
    val background = if (flowing) neutral.background else base.primary
    val foreground = if (flowing) Color.White else base.onPrimary
    val isDark = foreground.luminance() > 0.5f
    return base.copy(
        primary = foreground,
        primarySoft = foreground.copy(alpha = 0.12f),
        primaryStrong = foreground,
        onPrimary = if (flowing) Color.Black else background,
        primaryContainer = foreground.copy(alpha = 0.12f),
        onPrimaryContainer = foreground,
        background = background,
        onBackground = foreground,
        surface = if (flowing) neutral.surface else background,
        onSurface = foreground,
        surfaceVariant = if (flowing) neutral.surfaceVariant else foreground.copy(alpha = 0.12f).compositeOver(background),
        onSurfaceVariant = foreground.copy(alpha = 0.78f),
        textPrimary = foreground,
        textSecondary = foreground.copy(alpha = 0.76f),
        textTertiary = foreground.copy(alpha = 0.60f),
        accent = foreground,
        isDark = isDark,
        mode = if (isDark) ThemeMode.Dark else ThemeMode.Light
    )
}
