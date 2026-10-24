package com.asmr.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
class HueDerivationTest {

    @Test
    fun deriveHuePalette_keepsGraySeedCloseToNeutral() {
        val mode = ThemeMode.Dark
        val neutral = neutralPaletteForMode(mode)
        val primary = Color(0xFF2A2A2A)

        val hue = deriveHuePalette(
            primary = primary,
            mode = mode,
            neutral = neutral,
            fallbackOnPrimary = Color.White
        )

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(hue.primaryStrong.toArgb(), hsl)
        val bgHsl = FloatArray(3)
        ColorUtils.colorToHSL(hue.background.toArgb(), bgHsl)

        assertTrue(hsl[1] <= 0.12f)
        assertEquals(primary, hue.primary)
        assertTrue(colorDistanceLab(hue.primarySoft.toArgb(), neutral.surface.toArgb()) > 3.0)
        assertTrue(bgHsl[1] <= 0.10f)
        assertTrue(colorDistanceLab(hue.background.toArgb(), neutral.background.toArgb()) < 10.0)
    }

    @Test
    fun deriveHuePalette_createsReadableMonetLayersForChromaticSeed() {
        val mode = ThemeMode.Light
        val neutral = neutralPaletteForMode(mode)
        val primary = Color(0xFF1E88E5)

        val hue = deriveHuePalette(
            primary = primary,
            mode = mode,
            neutral = neutral,
            fallbackOnPrimary = Color.White
        )

        val strongContrast = ColorUtils.calculateContrast(hue.onPrimary.toArgb(), hue.primaryStrong.toArgb())
        val containerContrast = ColorUtils.calculateContrast(hue.onPrimaryContainer.toArgb(), hue.primarySoft.toArgb())
        val backgroundContrast = ColorUtils.calculateContrast(hue.onBackground.toArgb(), hue.background.toArgb())
        val surfaceContrast = ColorUtils.calculateContrast(hue.onSurface.toArgb(), hue.surface.toArgb())
        val surfaceVariantContrast = ColorUtils.calculateContrast(hue.onSurfaceVariant.toArgb(), hue.surfaceVariant.toArgb())

        assertTrue(strongContrast >= 4.5)
        assertTrue(containerContrast >= 4.5)
        assertTrue(backgroundContrast >= 4.5)
        assertTrue(surfaceContrast >= 4.5)
        assertTrue(surfaceVariantContrast >= 3.0)
        assertFalse(colorDistanceLab(hue.primarySoft.toArgb(), hue.primaryStrong.toArgb()) < 8.0)
        assertFalse(colorDistanceLab(hue.secondary.toArgb(), hue.primaryStrong.toArgb()) < 1.0)
        assertFalse(colorDistanceLab(hue.background.toArgb(), neutral.background.toArgb()) < 1.0)
        assertFalse(colorDistanceLab(hue.surfaceVariant.toArgb(), neutral.surfaceVariant.toArgb()) < 1.0)
    }

    @Test
    fun neutralArtworkKeepsNavigationAndButtonColorsReadableInEveryMode() {
        val artworkColors = intArrayOf(
            0xFF000000.toInt(), 0xFF010101.toInt(), 0xFF2A2A2A.toInt(),
            0xFF808080.toInt(), 0xFFFAFAFA.toInt(), 0xFFFFFFFF.toInt(),
            0xFF000001.toInt(), 0xFF020001.toInt(), 0xFF7C8084.toInt()
        )
        for (mode in ThemeMode.entries) {
            for (artworkColor in artworkColors) {
                val seed = coverThemeSeedColorFromPixels(IntArray(100) { artworkColor }, 0)
                // 同时覆盖新提取的种子及尚未重新提取的旧种子。
                for (primary in listOf(Color(seed), Color(artworkColor))) {
                    val hue = deriveHuePalette(primary, mode, neutralPaletteForMode(mode), Color.White)
                    val context = "$mode seed=${primary.toArgb().toUInt().toString(16)}"
                    assertEquals(context, primary, hue.primary)
                    assertContrast(context, hue.onPrimary, hue.primaryStrong, 4.5)
                    assertContrast(context, hue.onPrimaryContainer, hue.primarySoft, 4.5)
                    assertContrast(context, hue.onSecondary, hue.secondary, 4.5)
                    assertNavigationContrast(context, hue, mode)
                }
            }
        }
    }

    @Test
    fun darkChromaticArtworkKeepsItsColorAndReadableNavigationInEveryMode() {
        for (artworkColor in intArrayOf(0xFF401010.toInt(), 0xFF104010.toInt(), 0xFF101040.toInt())) {
            val seed = coverThemeSeedColorFromPixels(IntArray(100) { artworkColor }, 0)
            for (mode in ThemeMode.entries) {
                val hue = deriveHuePalette(Color(seed), mode, neutralPaletteForMode(mode), Color.White)
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(hue.primaryStrong.toArgb(), hsl)
                assertTrue("$mode seed=$seed", hsl[1] > 0.10f)
                assertNavigationContrast("$mode seed=$seed", hue, mode)
            }
        }
    }

    private fun assertNavigationContrast(context: String, hue: HuePalette, mode: ThemeMode) {
        val bar = hue.primarySoft.copy(alpha = if (mode.isDark) 0.05f else 0.08f)
            .compositeOver(hue.surface)
        for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val glow = hue.primaryStrong.copy(alpha = (if (mode.isDark) 0.22f else 0.18f) * progress)
                .compositeOver(bar)
            val chip = hue.primarySoft.copy(alpha = (if (mode.isDark) 0.52f else 0.95f) * progress)
                .compositeOver(glow)
            val icon = lerp(hue.textSecondary, hue.primaryStrong, progress)
            assertContrast("$context progress=$progress", icon, chip, 3.0)
        }
    }

    private fun assertContrast(context: String, foreground: Color, background: Color, minimum: Double) {
        val contrast = ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())
        assertTrue("$context contrast=$contrast expected>=$minimum", contrast >= minimum)
    }

    private fun colorDistanceLab(a: Int, b: Int): Double {
        val labA = DoubleArray(3)
        val labB = DoubleArray(3)
        ColorUtils.colorToLAB(a, labA)
        ColorUtils.colorToLAB(b, labB)
        val dl = labA[0] - labB[0]
        val da = labA[1] - labB[1]
        val db = labA[2] - labB[2]
        return sqrt(dl * dl + da * da + db * db)
    }
}
