package com.asmr.player.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.asmr.player.data.settings.PlayerBackgroundMode
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.ui.theme.neutralPaletteForMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSurfaceColorsTest {

    @Test
    fun themeWithoutCover_usesBlueAndRedThemePrimaryInEveryGlobalMode() {
        ThemeMode.entries.forEach { mode ->
            primaryCases.forEach { primaryCase ->
                val base = testColorScheme(mode, primaryCase)

                val result = playerSurfaceColors(base, PlayerBackgroundMode.Theme, false)

                assertEquals("${primaryCase.name} in $mode", base.primary, result.background)
            }
        }
    }

    @Test
    fun themeWithoutCover_keepsTextReadableAndLocalModeFollowsPrimarySurface() {
        ThemeMode.entries.forEach { mode ->
            primaryCases.forEach { primaryCase ->
                val base = testColorScheme(mode, primaryCase)

                val result = playerSurfaceColors(base, PlayerBackgroundMode.Theme, false)

                val context = "${primaryCase.name} in $mode"
                assertEquals(context, primaryCase.foreground, result.onSurface)
                assertTrue(context, contrastRatio(result.onSurface, result.background) >= 4.5f)
                assertTrue(context, contrastRatio(result.textPrimary, result.background) >= 4.5f)
                assertEquals(context, primaryCase.surfaceMode, result.mode)
                assertEquals(context, primaryCase.surfaceMode.isDark, result.isDark)
            }
        }
    }

    @Test
    fun themeWithCover_preservesExistingThemeColors() {
        ThemeMode.entries.forEach { mode ->
            primaryCases.forEach { primaryCase ->
                val base = testColorScheme(mode, primaryCase)

                val result = playerSurfaceColors(base, PlayerBackgroundMode.Theme, true)

                assertEquals("${primaryCase.name} in $mode", base, result)
            }
        }
    }

    @Test
    fun solid_followsGlobalModeWithoutPureWhiteOrBlackRegardlessOfThemeHueAndCover() {
        ThemeMode.entries.forEach { mode ->
            val neutral = neutralPaletteForMode(mode)
            val expectedBackground = when (mode) {
                ThemeMode.Light -> Color(0xFFDEE0DE)
                ThemeMode.Dark -> Color(0xFF282A2C)
                ThemeMode.SoftDark -> Color(0xFF303337)
            }
            primaryCases.forEach { primaryCase ->
                val base = testColorScheme(mode, primaryCase)
                val withoutCover = playerSurfaceColors(base, PlayerBackgroundMode.Solid, false)
                val withCover = playerSurfaceColors(base, PlayerBackgroundMode.Solid, true)

                val context = "${primaryCase.name} in $mode"
                assertEquals(context, withoutCover, withCover)
                assertEquals(context, expectedBackground, withoutCover.background)
                assertTrue(
                    context,
                    if (mode.isDark) {
                        withoutCover.background.luminance() > neutral.background.luminance()
                    } else {
                        withoutCover.background.luminance() < neutral.background.luminance()
                    }
                )
                assertNotEquals(context, Color.White, withoutCover.background)
                assertNotEquals(context, Color.Black, withoutCover.background)
                assertEquals(context, mode, withoutCover.mode)
                assertEquals(context, mode.isDark, withoutCover.isDark)
                assertEquals(context, mode.isDark, withoutCover.background.luminance() < 0.5f)
            }
        }
    }

    @Test
    fun solid_usesReadableNeutralTextAndSurfacesWhilePreservingThemeAccents() {
        ThemeMode.entries.forEach { mode ->
            val neutral = neutralPaletteForMode(mode)
            primaryCases.forEach { primaryCase ->
                val base = testColorScheme(mode, primaryCase)
                val result = playerSurfaceColors(base, PlayerBackgroundMode.Solid, true)

                val context = "${primaryCase.name} in $mode"
                assertEquals(context, neutral.onBackground, result.onBackground)
                assertEquals(context, neutral.surface, result.surface)
                assertEquals(context, neutral.onSurface, result.onSurface)
                assertEquals(context, neutral.surfaceVariant, result.surfaceVariant)
                assertEquals(context, neutral.onSurfaceVariant, result.onSurfaceVariant)
                assertEquals(context, neutral.textPrimary, result.textPrimary)
                assertEquals(context, neutral.textSecondary, result.textSecondary)
                assertEquals(context, neutral.textTertiary, result.textTertiary)
                assertTrue(context, contrastRatio(result.textPrimary, result.background) >= 4.5f)
                assertTrue(context, contrastRatio(result.textSecondary, result.background) >= 4.5f)
                assertEquals(context, base.primary, result.primary)
                assertEquals(context, base.onPrimary, result.onPrimary)
                assertEquals(context, base.accent, result.accent)
                assertEquals(context, base.danger, result.danger)
            }
        }
    }

    @Test
    fun flowing_keepsDarkSurfaceAndWhiteControlsRegardlessOfCoverPreference() {
        ThemeMode.entries.forEach { mode ->
            primaryCases.forEach { primaryCase ->
                val base = testColorScheme(mode, primaryCase)

                val withoutCover = playerSurfaceColors(base, PlayerBackgroundMode.Flowing, false)
                val withCover = playerSurfaceColors(base, PlayerBackgroundMode.Flowing, true)

                val context = "${primaryCase.name} in $mode"
                assertEquals(context, withoutCover, withCover)
                assertEquals(context, Color(0xFF121212), withoutCover.background)
                assertEquals(context, Color.White, withoutCover.onSurface)
                assertEquals(context, Color.White, withoutCover.primary)
                assertEquals(context, ThemeMode.Dark, withoutCover.mode)
                assertTrue(context, withoutCover.isDark)
            }
        }
    }

    private fun contrastRatio(foreground: Color, background: Color): Float {
        val foregroundLuminance = foreground.compositeOver(background).luminance()
        val backgroundLuminance = background.luminance()
        return (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
            (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
    }

    private fun testColorScheme(mode: ThemeMode, primaryCase: PrimaryCase): AsmrColorScheme {
        val background = when (mode) {
            ThemeMode.Light -> Color(0xFFF5F7FA)
            ThemeMode.Dark -> Color(0xFF121212)
            ThemeMode.SoftDark -> Color(0xFF16181D)
        }
        val foreground = if (mode.isDark) Color(0xFFE6EDF3) else Color(0xFF1B1E23)
        return AsmrColorScheme(
            primary = primaryCase.primary,
            primarySoft = Color(0xFFD4E0F0),
            primaryStrong = primaryCase.primary,
            onPrimary = primaryCase.foreground,
            primaryContainer = Color(0xFFD4E0F0),
            onPrimaryContainer = Color(0xFF102033),
            background = background,
            onBackground = foreground,
            surface = background,
            onSurface = foreground,
            surfaceVariant = Color(0xFF687080),
            onSurfaceVariant = foreground,
            textPrimary = foreground,
            textSecondary = foreground.copy(alpha = 0.72f),
            textTertiary = foreground.copy(alpha = 0.52f),
            accent = primaryCase.primary,
            danger = Color(0xFFAB0000),
            isDark = mode.isDark,
            mode = mode
        )
    }

    private data class PrimaryCase(
        val name: String,
        val primary: Color,
        val foreground: Color,
        val surfaceMode: ThemeMode
    )

    private val primaryCases = listOf(
        PrimaryCase("deep blue", Color(0xFF0D47A1), Color.White, ThemeMode.Dark),
        PrimaryCase("deep red", Color(0xFF7B1A1A), Color.White, ThemeMode.Dark),
        PrimaryCase("bright blue", Color(0xFF29B6F6), Color.Black, ThemeMode.Light),
        PrimaryCase("bright red", Color(0xFFFF5252), Color.Black, ThemeMode.Light)
    )
}
