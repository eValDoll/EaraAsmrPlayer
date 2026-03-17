package com.asmr.player.ui.common

import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DominantColorKeyAndroidTest {

    @Test
    fun imageCenterWeightedKey_changesWithBackgroundPolarity() {
        val lightKey = centerWeightedDominantColorCacheKey(
            baseKey = "content://album/artwork",
            centerRegionRatio = 0.62f,
            defaultColor = Color.White
        )
        val darkKey = centerWeightedDominantColorCacheKey(
            baseKey = "content://album/artwork",
            centerRegionRatio = 0.62f,
            defaultColor = Color.Black
        )

        assertNotEquals(lightKey, darkKey)
        assertTrue(lightKey.contains("bg=light"))
        assertTrue(darkKey.contains("bg=dark"))
    }

    @Test
    fun videoCenterWeightedKey_changesWithBackgroundPolarity() {
        val lightKey = centerWeightedVideoFrameDominantColorCacheKey(
            baseKey = "content://album/video",
            centerRegionRatio = 0.62f,
            defaultColor = Color(0xFFF0F4F8)
        )
        val darkKey = centerWeightedVideoFrameDominantColorCacheKey(
            baseKey = "content://album/video",
            centerRegionRatio = 0.62f,
            defaultColor = Color(0xFF121212)
        )

        assertNotEquals(lightKey, darkKey)
        assertTrue(lightKey.contains("bg=light"))
        assertTrue(darkKey.contains("bg=dark"))
    }
}
