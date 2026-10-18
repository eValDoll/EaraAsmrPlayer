package com.asmr.player.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverThemeColorTest {

    @Test
    fun emptyOrLowAlphaArtworkUsesTheProvidedFallback() {
        val fallback = argb(23, 45, 67)

        assertEquals(fallback, coverThemeSeedColorFromPixels(intArrayOf(), fallback))
        assertEquals(
            fallback,
            coverThemeSeedColorFromPixels(
                intArrayOf(argb(240, 20, 20, 0), argb(20, 240, 20, 124)),
                fallback
            )
        )
        assertGreen(coverThemeSeedColorFromPixels(intArrayOf(argb(40, 180, 60, 125)), fallback))
    }

    @Test
    fun transparentPixelsCannotOutvoteAnOpaqueCoverColor() {
        val green = argb(40, 180, 60)
        val opaqueOnly = coverThemeSeedColorFromPixels(IntArray(100) { green }, fallback)
        val withTransparentPixels = coverThemeSeedColorFromPixels(
            IntArray(10_000) { argb(230, 20, 230, 124) } + IntArray(100) { green },
            fallback
        )

        assertEquals(opaqueOnly, withTransparentPixels)
    }

    @Test
    fun aSubstantialCoverColorSurvivesMostlyBlackOrWhiteArtwork() {
        for (background in intArrayOf(argb(0, 0, 0), argb(255, 255, 255))) {
            val result = coverThemeSeedColorFromPixels(
                IntArray(700) { background } + IntArray(300) { argb(40, 180, 60) },
                fallback
            )

            assertGreen(result)
        }
    }

    @Test
    fun aTinyVibrantAccentCannotReplaceTheRepresentativeCoverColor() {
        val result = coverThemeSeedColorFromPixels(
            IntArray(19_900) { argb(164, 118, 104) } + IntArray(100) { argb(0, 90, 255) },
            fallback
        )

        assertTrue(red(result) > green(result) + 20)
        assertTrue(red(result) > blue(result) + 20)
    }

    @Test
    fun comparableVibrantCandidatesFollowTheLargerColorRegion() {
        val cyan = argb(40, 180, 180)
        val magenta = argb(180, 40, 180)
        val mostlyCyan = coverThemeSeedColorFromPixels(
            IntArray(800) { cyan } + IntArray(200) { magenta },
            fallback
        )
        val mostlyMagenta = coverThemeSeedColorFromPixels(
            IntArray(200) { cyan } + IntArray(800) { magenta },
            fallback
        )

        assertTrue(green(mostlyCyan) > red(mostlyCyan) + 40)
        assertTrue(blue(mostlyCyan) > red(mostlyCyan) + 40)
        assertTrue(red(mostlyMagenta) > green(mostlyMagenta) + 40)
        assertTrue(blue(mostlyMagenta) > green(mostlyMagenta) + 40)
    }

    @Test
    fun aVisibleMidtoneCanBeChosenOverTheDarkestColorMass() {
        val result = coverThemeSeedColorFromPixels(
            IntArray(800) { argb(0, 0, 80) } + IntArray(200) { argb(220, 40, 40) },
            fallback
        )

        assertTrue(red(result) > green(result) + 40)
        assertTrue(red(result) > blue(result) + 40)
    }

    @Test
    fun grayscaleArtworkKeepsANeutralSeed() {
        for (gray in intArrayOf(0, 48, 128, 208, 255)) {
            val result = coverThemeSeedColorFromPixels(IntArray(100) { argb(gray, gray, gray) }, fallback)

            assertEquals(red(result), green(result))
            assertEquals(green(result), blue(result))
        }
    }

    @Test
    fun validArtworkDoesNotDependOnTheGlobalFallbackColor() {
        val pixels = IntArray(100) { argb(170, 90, 40) }

        assertEquals(
            coverThemeSeedColorFromPixels(pixels, argb(0, 80, 255)),
            coverThemeSeedColorFromPixels(pixels, argb(240, 20, 120))
        )
    }

    private fun assertGreen(color: Int) {
        assertTrue(green(color) > red(color) + 30)
        assertTrue(green(color) > blue(color) + 30)
    }

    private fun argb(red: Int, green: Int, blue: Int, alpha: Int = 255): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    private fun red(color: Int): Int = color ushr 16 and 255

    private fun green(color: Int): Int = color ushr 8 and 255

    private fun blue(color: Int): Int = color and 255

    private val fallback = argb(20, 70, 180)
}
