package com.asmr.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        assertTrue(hsl[1] <= 0.12f)
        assertTrue(colorDistanceLab(hue.primaryStrong.toArgb(), primary.toArgb()) < 18.0)
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

