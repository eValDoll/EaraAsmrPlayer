package com.asmr.player.ui.player

import androidx.compose.ui.graphics.Color
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.ui.theme.neutralPaletteForMode

internal val PlayerBackdropBaseColor = neutralPaletteForMode(ThemeMode.Dark).background

internal fun playerSurfaceColors(base: AsmrColorScheme): AsmrColorScheme {
    val neutral = neutralPaletteForMode(ThemeMode.Dark)
    return base.copy(
        primary = Color.White,
        primarySoft = Color.White.copy(alpha = 0.12f),
        primaryStrong = Color.White,
        onPrimary = Color.Black,
        primaryContainer = Color.White.copy(alpha = 0.12f),
        onPrimaryContainer = Color.White,
        background = neutral.background,
        onBackground = Color.White,
        surface = neutral.surface,
        onSurface = Color.White,
        surfaceVariant = neutral.surfaceVariant,
        onSurfaceVariant = Color.White.copy(alpha = 0.78f),
        textPrimary = Color.White,
        textSecondary = Color.White.copy(alpha = 0.76f),
        textTertiary = Color.White.copy(alpha = 0.60f),
        accent = Color.White,
        isDark = true,
        mode = ThemeMode.Dark
    )
}
