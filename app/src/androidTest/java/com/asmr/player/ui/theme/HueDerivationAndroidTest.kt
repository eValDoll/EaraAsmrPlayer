package com.asmr.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class HueDerivationAndroidTest {

    @Test
    fun deriveHuePalette_doesNotInventHue_forGrayPrimary() {
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
        assertTrue(ColorUtils.calculateContrast(hue.primaryStrong.toArgb(), hue.primarySoft.toArgb()) >= 3.0)
        assertTrue(colorDistanceLab(hue.primarySoft.toArgb(), neutral.surface.toArgb()) > 3.0)
        assertTrue(bgHsl[1] <= 0.10f)
        assertTrue(colorDistanceLab(hue.background.toArgb(), neutral.background.toArgb()) < 10.0)
    }

    @Test
    fun deriveHuePalette_buildsMonetLikeContainers_forChromaticPrimary() {
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

