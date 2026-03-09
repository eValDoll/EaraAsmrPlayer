package com.asmr.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.common.adjustHslForUi
import com.asmr.player.ui.common.computeCenterWeightedHintColorInt
import com.asmr.player.ui.common.pickBestColorInt
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class DominantColorExtractionTest {

    @Test
    fun dominantColor_prefersBackground_overWhiteText() {
        val background = 0xFF0D3B66.toInt()
        val text = 0xFFFFFFFF.toInt()
        val bitmap = makeCoverLikeBitmap(background, text, textSizePx = 92f, y = 270f)

        val palette = Palette.from(bitmap).generate()
        val hint = computeCenterWeightedHintColorInt(bitmap, 0.62f)
        val picked = pickBestColorInt(palette, fallbackColorInt = background, preferDarkBackground = true, hintColorInt = hint)

        assertTrue(colorDistanceLab(picked, background) < colorDistanceLab(picked, text))
    }

    @Test
    fun dominantColor_prefersBackground_overBrightColoredText() {
        val background = 0xFF1B1B1B.toInt()
        val text = 0xFFFFE45E.toInt()
        val bitmap = makeCoverLikeBitmap(background, text, textSizePx = 96f, y = 280f)

        val palette = Palette.from(bitmap).generate()
        val hint = computeCenterWeightedHintColorInt(bitmap, 0.62f)
        val picked = pickBestColorInt(palette, fallbackColorInt = background, preferDarkBackground = true, hintColorInt = hint)

        assertTrue(colorDistanceLab(picked, background) < colorDistanceLab(picked, text))
    }

    @Test
    fun dominantColor_excludesGray_andPrefersColoredElement() {
        val background = 0xFF2A2A2A.toInt()
        val text = 0xFFE53935.toInt()
        val bitmap = makeCoverLikeBitmap(background, text, textSizePx = 110f, y = 300f)

        val palette = Palette.from(bitmap).generate()
        val hint = computeCenterWeightedHintColorInt(bitmap, 0.62f)
        val picked = pickBestColorInt(palette, fallbackColorInt = 0xFF0B3D2E.toInt(), preferDarkBackground = true, hintColorInt = hint)

        assertTrue(colorDistanceLab(picked, text) < colorDistanceLab(picked, background))
    }

    @Test
    fun adjustedColor_doesNotInventHue_onGrayBackground() {
        val background = 0xFF2A2A2A.toInt()
        val text = 0xFFFFFFFF.toInt()
        val bitmap = makeCoverLikeBitmap(background, text, textSizePx = 88f, y = 260f)

        val palette = Palette.from(bitmap).generate()
        val hint = computeCenterWeightedHintColorInt(bitmap, 0.62f)
        val picked = pickBestColorInt(palette, fallbackColorInt = background, preferDarkBackground = true, hintColorInt = hint)

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(picked, hsl)
        adjustHslForUi(hsl, preferDarkBackground = true)
        val adjusted = ColorUtils.HSLToColor(hsl)

        assertTrue(colorDistanceLab(adjusted, background) < 18.0)
        assertTrue(hsl[1] <= 0.12f)
    }

    @Test
    fun adjustedColor_tamesBrightColor() {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(0xFFFFE45E.toInt(), hsl)
        adjustHslForUi(hsl, preferDarkBackground = true)
        assertTrue(hsl[2] <= 0.50f)
        assertTrue(hsl[1] <= 0.70f)
    }

    private fun makeCoverLikeBitmap(backgroundColor: Int, textColor: Int, textSizePx: Float, y: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(backgroundColor)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = textSizePx
        }
        canvas.drawText("ASMR", 48f, y, paint)
        return bitmap
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
