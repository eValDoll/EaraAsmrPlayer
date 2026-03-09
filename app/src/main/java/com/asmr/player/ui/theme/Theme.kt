package com.asmr.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DefaultBrandPrimaryLight = Color(0xFF0B3D2E)
val DefaultBrandPrimaryDark = Color(0xFF2E7D32)

@Immutable
data class AsmrColorScheme(
    val primary: Color,
    val primarySoft: Color,
    val primaryStrong: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val danger: Color,
    val isDark: Boolean
)

val LocalAsmrColorScheme = staticCompositionLocalOf {
    val mode = ThemeMode.Dark
    val neutral = neutralPaletteForMode(mode)
    val hue = deriveHuePalette(
        primary = DefaultBrandPrimaryDark,
        mode = mode,
        neutral = neutral,
        fallbackOnPrimary = Color.White
    )
    AsmrColorScheme(
        primary = hue.primaryStrong,
        primarySoft = hue.primarySoft,
        primaryStrong = hue.primaryStrong,
        onPrimary = hue.onPrimary,
        primaryContainer = hue.primarySoft,
        onPrimaryContainer = neutral.onSurface,
        background = neutral.background,
        onBackground = neutral.onBackground,
        surface = neutral.surface,
        onSurface = neutral.onSurface,
        surfaceVariant = neutral.surfaceVariant,
        onSurfaceVariant = neutral.onSurfaceVariant,
        textPrimary = neutral.textPrimary,
        textSecondary = neutral.textSecondary,
        textTertiary = neutral.textTertiary,
        accent = hue.primaryStrong,
        danger = Color(0xFFAB0000),
        isDark = mode.isDark
    )
}

@Composable
fun AsmrPlayerTheme(
    mode: ThemeMode = if (isSystemInDarkTheme()) ThemeMode.Dark else ThemeMode.Light,
    hue: HuePalette? = null,
    content: @Composable () -> Unit
) {
    val neutral = remember(mode) { neutralPaletteForMode(mode) }
    val resolvedHue = remember(hue, neutral, mode) {
        hue ?: deriveHuePalette(
            primary = if (mode.isDark) DefaultBrandPrimaryDark else DefaultBrandPrimaryLight,
            mode = mode,
            neutral = neutral,
            fallbackOnPrimary = if (mode.isDark) Color.White else Color.Black
        )
    }
    val materialColorScheme = remember(resolvedHue, neutral, mode) {
        materialColorSchemeFor(mode, neutral, resolvedHue)
    }
    val asmrColorScheme = remember(resolvedHue, neutral, mode) {
        AsmrColorScheme(
            primary = resolvedHue.primaryStrong,
            primarySoft = resolvedHue.primarySoft,
            primaryStrong = resolvedHue.primaryStrong,
            onPrimary = resolvedHue.onPrimary,
            primaryContainer = resolvedHue.primarySoft,
            onPrimaryContainer = neutral.onSurface,
            background = neutral.background,
            onBackground = neutral.onBackground,
            surface = neutral.surface,
            onSurface = neutral.onSurface,
            surfaceVariant = neutral.surfaceVariant,
            onSurfaceVariant = neutral.onSurfaceVariant,
            textPrimary = neutral.textPrimary,
            textSecondary = neutral.textSecondary,
            textTertiary = neutral.textTertiary,
            accent = resolvedHue.primaryStrong,
            danger = Color(0xFFAB0000),
            isDark = mode.isDark
        )
    }

    val shapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
    )

    CompositionLocalProvider(LocalAsmrColorScheme provides asmrColorScheme) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            shapes = shapes,
            typography = Typography,
            content = content
        )
    }
}

private fun materialColorSchemeFor(
    mode: ThemeMode,
    neutral: NeutralPalette,
    hue: HuePalette
): ColorScheme {
    return if (mode.isDark) {
        darkColorScheme(
            primary = hue.primaryStrong,
            onPrimary = hue.onPrimary,
            primaryContainer = hue.primarySoft,
            onPrimaryContainer = neutral.onSurface,
            secondary = hue.primary,
            onSecondary = hue.onPrimary,
            background = neutral.background,
            onBackground = neutral.onBackground,
            surface = neutral.surface,
            surfaceTint = Color.Transparent,
            onSurface = neutral.onSurface,
            surfaceVariant = neutral.surfaceVariant,
            onSurfaceVariant = neutral.onSurfaceVariant
        )
    } else {
        lightColorScheme(
            primary = hue.primaryStrong,
            onPrimary = hue.onPrimary,
            primaryContainer = hue.primarySoft,
            onPrimaryContainer = neutral.onSurface,
            secondary = hue.primary,
            onSecondary = hue.onPrimary,
            background = neutral.background,
            onBackground = neutral.onBackground,
            surface = neutral.surface,
            surfaceTint = Color.Transparent,
            onSurface = neutral.onSurface,
            surfaceVariant = neutral.surfaceVariant,
            onSurfaceVariant = neutral.onSurfaceVariant
        )
    }
}

internal fun neutralPaletteForMode(mode: ThemeMode): NeutralPalette {
    return when (mode) {
        ThemeMode.Light -> {
            val on = Color(0xFF1F2328)
            NeutralPalette(
                background = Color(0xFFF0F4F8),
                onBackground = on,
                surface = Color(0xFFFFFFFF),
                onSurface = on,
                surfaceVariant = Color(0xFFF0F5FA),
                onSurfaceVariant = Color(0xFF6E7681),
                textPrimary = on,
                textSecondary = on.copy(alpha = 0.72f),
                textTertiary = on.copy(alpha = 0.52f),
                divider = on.copy(alpha = 0.12f)
            )
        }
        ThemeMode.Dark -> {
            val on = Color(0xFFE6EDF3)
            NeutralPalette(
                background = Color(0xFF121212),
                onBackground = on,
                surface = Color(0xFF1E1E1E),
                onSurface = on,
                surfaceVariant = Color(0xFF2D323B),
                onSurfaceVariant = Color(0xFF8B949E),
                textPrimary = on,
                textSecondary = on.copy(alpha = 0.72f),
                textTertiary = on.copy(alpha = 0.52f),
                divider = on.copy(alpha = 0.14f)
            )
        }
        ThemeMode.SoftDark -> {
            val on = Color(0xFFE1E7EE)
            NeutralPalette(
                background = Color(0xFF16181D),
                onBackground = on,
                surface = Color(0xFF20242B),
                onSurface = on,
                surfaceVariant = Color(0xFF2A2F39),
                onSurfaceVariant = Color(0xFF9AA3AE),
                textPrimary = on,
                textSecondary = on.copy(alpha = 0.70f),
                textTertiary = on.copy(alpha = 0.50f),
                divider = on.copy(alpha = 0.12f)
            )
        }
    }
}

object AsmrTheme {
    val colorScheme: AsmrColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAsmrColorScheme.current
}
