package com.asmr.player.ui.player

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class FlowingArtworkRendererAndroidTest {
    @Test
    fun uniformArtworkStaysUniformWithoutHistoryBrightnessDrift() {
        val artwork = solidBitmap(40, 40, Color.RED)
        val target = emptyBitmap(96, 128)
        val renderer = FlowingArtworkRenderer()
        try {
            repeat(8) { frame ->
                renderer.render(artwork, target, frame * 1_000_000_000L)
                assertTrue(target.pixels().all { it == Color.rgb(155, 0, 0) })
            }
        } finally {
            renderer.close()
            artwork.recycle()
            target.recycle()
        }
    }

    @Test
    fun flowingLayersChangeMulticolorArtworkOverTime() {
        val artwork = multicolorBitmap()
        val target = emptyBitmap(112, 164)
        val renderer = FlowingArtworkRenderer()
        try {
            renderer.render(artwork, target, 0L)
            val firstFrame = target.pixels()
            renderer.render(artwork, target, 17_000_000_000L)
            val secondFrame = target.pixels()
            val changedPixels = firstFrame.indices.count { firstFrame[it] != secondFrame[it] }
            assertTrue("Rotation should move the cover's color regions", changedPixels > firstFrame.size / 2)
            assertTrue("The background should retain multiple cover colors", secondFrame.toSet().size > 100)
        } finally {
            renderer.close()
            artwork.recycle()
            target.recycle()
        }
    }

    @Test
    fun resetRemovesPreviousFrameInfluence() {
        val artwork = multicolorBitmap()
        val target = emptyBitmap(96, 128)
        val expected = emptyBitmap(96, 128)
        val renderer = FlowingArtworkRenderer()
        val freshRenderer = FlowingArtworkRenderer()
        try {
            renderer.render(artwork, target, 0L)
            renderer.render(artwork, target, 17_000_000_000L)
            val blendedFrame = target.pixels()
            renderer.reset()
            renderer.render(artwork, target, 17_000_000_000L)
            freshRenderer.render(artwork, expected, 17_000_000_000L)
            assertArrayEquals(expected.pixels(), target.pixels())
            assertFalse(blendedFrame.contentEquals(target.pixels()))
        } finally {
            renderer.close()
            freshRenderer.close()
            artwork.recycle()
            target.recycle()
            expected.recycle()
        }
    }

    @Test
    fun changedArtworkAndResizedTargetDoNotReuseOldPixels() {
        val artwork = solidBitmap(32, 32, Color.RED)
        val firstTarget = emptyBitmap(96, 128)
        val resizedTarget = emptyBitmap(128, 96)
        val renderer = FlowingArtworkRenderer()
        try {
            renderer.render(artwork, firstTarget, 0L)
            artwork.eraseColor(Color.BLUE)
            renderer.render(artwork, firstTarget, 2_000_000_000L)
            assertTrue(firstTarget.pixels().all { it == Color.rgb(0, 0, 155) })
            renderer.render(artwork, resizedTarget, 3_000_000_000L)
            assertTrue(resizedTarget.pixels().all { it == Color.rgb(0, 0, 155) })
        } finally {
            renderer.close()
            artwork.recycle()
            firstTarget.recycle()
            resizedTarget.recycle()
        }
    }

    @Test
    fun extremeAspectRatiosAndTransparentArtworkAlwaysFillAnOpaqueCanvas() {
        val renderer = FlowingArtworkRenderer()
        val target = emptyBitmap(80, 144)
        try {
            for ((width, height) in listOf(1 to 512, 512 to 1, 1 to 1)) {
                val artwork = solidBitmap(width, height, Color.CYAN)
                try {
                    renderer.render(artwork, target, 23_000_000_000L)
                    assertTrue(target.pixels().all { it == Color.rgb(0, 155, 155) })
                } finally {
                    artwork.recycle()
                }
            }
            val transparent = solidBitmap(24, 24, Color.TRANSPARENT)
            try {
                renderer.render(transparent, target, 23_000_000_000L)
                assertTrue(target.pixels().all { Color.alpha(it) == 255 })
            } finally {
                transparent.recycle()
            }
        } finally {
            renderer.close()
            target.recycle()
        }
    }

    @Test
    fun renderingAndClosingPreserveCallerOwnedBitmaps() {
        val artwork = multicolorBitmap()
        val originalPixels = artwork.pixels()
        val target = emptyBitmap(96, 128)
        val renderer = FlowingArtworkRenderer()
        renderer.render(artwork, target, 1_000_000_000L)
        renderer.close()
        renderer.close()
        try {
            assertFalse(artwork.isRecycled)
            assertFalse(target.isRecycled)
            assertArrayEquals(originalPixels, artwork.pixels())
        } finally {
            artwork.recycle()
            target.recycle()
        }
    }

    private fun multicolorBitmap(): Bitmap = emptyBitmap(96, 96).apply {
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val content = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            colors[(if (x >= width / 2) 1 else 0) + (if (y >= height / 2) 2 else 0)]
        }
        setPixels(content, 0, width, 0, 0, width, height)
    }

    private fun solidBitmap(width: Int, height: Int, color: Int): Bitmap =
        emptyBitmap(width, height).apply { eraseColor(color) }

    private fun emptyBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private fun Bitmap.pixels(): IntArray = IntArray(width * height).also {
        getPixels(it, 0, width, 0, 0, width, height)
    }
}
