package com.asmr.player.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDynamicHueTest {

    @Test
    fun playerThemeColors_splitBackdropAndAccent_whenCoverBackgroundEnabled() {
        val dynamicHue = com.asmr.player.ui.theme.HuePalette(
            primary = Color(0xFF3A6EA5),
            primarySoft = Color(0xFFD7E3F4),
            primaryStrong = Color(0xFF24538B),
            onPrimary = Color.White,
            secondary = Color(0xFF5A7AA3),
            onPrimaryContainer = Color(0xFF102033),
            onSecondary = Color.White,
            background = Color(0xFFF6F8FB),
            onBackground = Color(0xFF1B1E23),
            surface = Color.White,
            onSurface = Color(0xFF1B1E23),
            surfaceVariant = Color(0xFFE9EEF5),
            onSurfaceVariant = Color(0xFF5E6773),
            textPrimary = Color(0xFF1B1E23),
            textSecondary = Color(0xFF5E6773),
            textTertiary = Color(0xFF818A96)
        )
        val colorScheme = testColorScheme()

        val result = resolvePlayerThemeColors(
            dynamicHue = dynamicHue,
            colorScheme = colorScheme,
            coverBackgroundEnabled = true
        )

        assertEquals(dynamicHue.primaryStrong, result.coverAccentColor)
        assertEquals(dynamicHue.primaryStrong, result.accentColor)
        assertEquals(dynamicHue.onPrimary, result.onAccentColor)
        assertEquals(
            derivePlayerBackdropTintColor(
                primary = dynamicHue.primary,
                primarySoft = dynamicHue.primarySoft,
                background = colorScheme.background,
                mode = colorScheme.mode
            ),
            result.backdropTintColor
        )
        assertEquals(result.backdropTintColor, result.videoBackdropColor)
    }

    @Test
    fun playerThemeColors_fallsBackToThemeAccentAndTintedVideoBackdrop_whenCoverBackgroundDisabled() {
        val dynamicHue = com.asmr.player.ui.theme.HuePalette(
            primary = Color(0xFF3A6EA5),
            primarySoft = Color(0xFFD7E3F4),
            primaryStrong = Color(0xFF24538B),
            onPrimary = Color.White,
            secondary = Color(0xFF5A7AA3),
            onPrimaryContainer = Color(0xFF102033),
            onSecondary = Color.White,
            background = Color(0xFFF6F8FB),
            onBackground = Color(0xFF1B1E23),
            surface = Color.White,
            onSurface = Color(0xFF1B1E23),
            surfaceVariant = Color(0xFFE9EEF5),
            onSurfaceVariant = Color(0xFF5E6773),
            textPrimary = Color(0xFF1B1E23),
            textSecondary = Color(0xFF5E6773),
            textTertiary = Color(0xFF818A96)
        )
        val colorScheme = testColorScheme()

        val result = resolvePlayerThemeColors(
            dynamicHue = dynamicHue,
            colorScheme = colorScheme,
            coverBackgroundEnabled = false
        )

        assertEquals(dynamicHue.primaryStrong, result.coverAccentColor)
        assertEquals(colorScheme.primary, result.accentColor)
        assertEquals(colorScheme.onPrimary, result.onAccentColor)
        assertEquals(colorScheme.background, result.backdropTintColor)
        assertEquals(
            derivePlayerBackdropTintColor(
                primary = colorScheme.primary,
                primarySoft = colorScheme.primarySoft,
                background = colorScheme.background,
                mode = colorScheme.mode
            ),
            result.videoBackdropColor
        )
    }

    @Test
    fun playerThemeColors_keepsDynamicAccentForVideoWhenArtworkBackdropDisabled() {
        val dynamicHue = com.asmr.player.ui.theme.HuePalette(
            primary = Color(0xFFB8576A),
            primarySoft = Color(0xFFF6D4DB),
            primaryStrong = Color(0xFF8D3044),
            onPrimary = Color.White,
            secondary = Color(0xFFB07182),
            onPrimaryContainer = Color(0xFF351019),
            onSecondary = Color.White,
            background = Color(0xFFFFF7F8),
            onBackground = Color(0xFF24191B),
            surface = Color.White,
            onSurface = Color(0xFF24191B),
            surfaceVariant = Color(0xFFF3E4E7),
            onSurfaceVariant = Color(0xFF6C5960),
            textPrimary = Color(0xFF24191B),
            textSecondary = Color(0xFF6C5960),
            textTertiary = Color(0xFF917D84)
        )
        val colorScheme = testColorScheme()

        val result = resolvePlayerThemeColors(
            dynamicHue = dynamicHue,
            colorScheme = colorScheme,
            coverBackgroundEnabled = true,
            artworkBackdropEnabled = false
        )

        assertEquals(dynamicHue.primaryStrong, result.coverAccentColor)
        assertEquals(dynamicHue.primaryStrong, result.accentColor)
        assertEquals(dynamicHue.onPrimary, result.onAccentColor)
        assertEquals(colorScheme.background, result.backdropTintColor)
        assertEquals(
            derivePlayerBackdropTintColor(
                primary = dynamicHue.primary,
                primarySoft = dynamicHue.primarySoft,
                background = colorScheme.background,
                mode = colorScheme.mode
            ),
            result.videoBackdropColor
        )
    }

    @Test
    fun derivePlayerBackdropTintColor_liftsDarkModeBackdropAwayFromMud() {
        val background = Color(0xFF16181C)
        val primarySoft = Color(0xFF59616A)

        val result = derivePlayerBackdropTintColor(
            primary = Color(0xFF9CB7D3),
            primarySoft = primarySoft,
            background = background,
            mode = ThemeMode.Dark
        )

        assertTrue(result.luminance() > background.luminance())
        assertTrue(result.luminance() > 0.14f)
    }

    @Test
    fun derivePlayerBackdropTintColor_reinsInLightModeBackdropWhiteness() {
        val background = Color(0xFFF5F7FA)
        val primarySoft = Color(0xFFDCEBFA)

        val result = derivePlayerBackdropTintColor(
            primary = Color(0xFF7CA4D6),
            primarySoft = primarySoft,
            background = background,
            mode = ThemeMode.Light
        )

        assertTrue(result.luminance() < background.luminance())
        assertTrue(result.luminance() < 0.74f)
    }

    @Test
    fun resolvePlayerThemeColors_usesDominantColorForBackdrop_whenProvided() {
        val dynamicHue = sampleDynamicHuePalette()
        val colorScheme = testColorScheme()
        val dominant = Color(0xFFC0392B)

        val result = resolvePlayerThemeColors(
            dynamicHue = dynamicHue,
            colorScheme = colorScheme,
            coverBackgroundEnabled = true,
            artworkBackdropEnabled = true,
            backdropDominantColor = dominant
        )

        assertEquals(
            deriveBackdropTintFromDominant(dominant, colorScheme.background, colorScheme.mode),
            result.backdropTintColor
        )
        assertEquals(result.backdropTintColor, result.videoBackdropColor)
    }

    @Test
    fun resolvePlayerThemeColors_fallsBackToMonetBackdrop_whenDominantIsGray() {
        val dynamicHue = sampleDynamicHuePalette()
        val colorScheme = testColorScheme()

        val result = resolvePlayerThemeColors(
            dynamicHue = dynamicHue,
            colorScheme = colorScheme,
            coverBackgroundEnabled = true,
            artworkBackdropEnabled = true,
            backdropDominantColor = Color(0xFF808080)
        )

        assertEquals(
            derivePlayerBackdropTintColor(
                primary = dynamicHue.primary,
                primarySoft = dynamicHue.primarySoft,
                background = colorScheme.background,
                mode = colorScheme.mode
            ),
            result.backdropTintColor
        )
    }

    private fun sampleDynamicHuePalette(): com.asmr.player.ui.theme.HuePalette {
        return com.asmr.player.ui.theme.HuePalette(
            primary = Color(0xFF3A6EA5),
            primarySoft = Color(0xFFD7E3F4),
            primaryStrong = Color(0xFF24538B),
            onPrimary = Color.White,
            secondary = Color(0xFF5A7AA3),
            onPrimaryContainer = Color(0xFF102033),
            onSecondary = Color.White,
            background = Color(0xFFF6F8FB),
            onBackground = Color(0xFF1B1E23),
            surface = Color.White,
            onSurface = Color(0xFF1B1E23),
            surfaceVariant = Color(0xFFE9EEF5),
            onSurfaceVariant = Color(0xFF5E6773),
            textPrimary = Color(0xFF1B1E23),
            textSecondary = Color(0xFF5E6773),
            textTertiary = Color(0xFF818A96)
        )
    }

    private fun testColorScheme(): AsmrColorScheme {
        return AsmrColorScheme(
            primary = Color(0xFF2B5D8E),
            primarySoft = Color(0xFFD4E0F0),
            primaryStrong = Color(0xFF1E4A76),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD4E0F0),
            onPrimaryContainer = Color(0xFF102033),
            background = Color(0xFFF5F7FA),
            onBackground = Color(0xFF1B1E23),
            surface = Color.White,
            onSurface = Color(0xFF1B1E23),
            surfaceVariant = Color(0xFFE8EDF3),
            onSurfaceVariant = Color(0xFF5E6773),
            textPrimary = Color(0xFF1B1E23),
            textSecondary = Color(0xFF5E6773),
            textTertiary = Color(0xFF818A96),
            accent = Color(0xFF1E4A76),
            danger = Color(0xFFAB0000),
            isDark = false,
            mode = ThemeMode.Light
        )
    }
}
