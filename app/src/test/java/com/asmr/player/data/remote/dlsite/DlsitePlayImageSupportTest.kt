package com.asmr.player.data.remote.dlsite

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DlsitePlayImageSupportTest {

    @Test
    fun parseDlsitePlayCryptFlag_supportsBooleanNumberAndStringValues() {
        assertTrue(parseDlsitePlayCryptFlag(true))
        assertTrue(parseDlsitePlayCryptFlag(1))
        assertTrue(parseDlsitePlayCryptFlag("1"))
        assertTrue(parseDlsitePlayCryptFlag("true"))
        assertTrue(parseDlsitePlayCryptFlag(" yes "))

        assertFalse(parseDlsitePlayCryptFlag(false))
        assertFalse(parseDlsitePlayCryptFlag(0))
        assertFalse(parseDlsitePlayCryptFlag("0"))
        assertFalse(parseDlsitePlayCryptFlag("false"))
        assertFalse(parseDlsitePlayCryptFlag(""))
        assertFalse(parseDlsitePlayCryptFlag(null))
    }

    @Test
    fun parseDlsitePlayImageSeed_returnsExpectedHexSlice() {
        assertEquals(0, parseDlsitePlayImageSeed("000000000000.png"))
        assertEquals(0xabc1234, parseDlsitePlayImageSeed("00000abc1234.jpg"))
        assertNull(parseDlsitePlayImageSeed("short"))
        assertNull(parseDlsitePlayImageSeed("00000zzzzzzz.jpg"))
    }

    @Test
    fun descrambleDlsitePlayBitmap_matchesReferenceTileOrderAndCropsPadding() {
        val scrambled = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFF000000.toInt())
            setPixels(IntArray(128 * 128) { 0xFFFF0000.toInt() }, 0, 128, 0, 128, 128, 128)
            setPixels(IntArray(128 * 128) { 0xFF00FF00.toInt() }, 0, 128, 128, 0, 128, 128)
            setPixels(IntArray(128 * 128) { 0xFF0000FF.toInt() }, 0, 128, 128, 128, 128, 128)
        }

        val descrambled = descrambleDlsitePlayBitmap(scrambled, seed = 0, width = 200, height = 160)

        assertEquals(200, descrambled.width)
        assertEquals(160, descrambled.height)
        assertEquals(0xFF000000.toInt(), descrambled.getPixel(0, 0))
        assertEquals(0xFF0000FF.toInt(), descrambled.getPixel(0, 129))
        assertEquals(0xFFFF0000.toInt(), descrambled.getPixel(129, 0))
        assertEquals(0xFF00FF00.toInt(), descrambled.getPixel(129, 129))

        descrambled.recycle()
        scrambled.recycle()
    }
}
