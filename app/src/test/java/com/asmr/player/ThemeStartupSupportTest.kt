package com.asmr.player

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeStartupSupportTest {

    @Test
    fun resolveStaticHueArgb_usesLightSlotForSystemTheme() {
        val result = resolveStaticHueArgb(
            themePref = "system",
            staticHueArgbLight = 0x00112233,
            staticHueArgbDark = 0x00445566
        )

        assertEquals(0x00112233, result)
    }

    @Test
    fun dynamicThemeSourceKey_prefersArtworkOverVideo() {
        val source = ThemeMediaSource(
            artworkUri = Uri.parse("https://example.com/cover.jpg"),
            videoUri = Uri.parse("file:///sample.mp4"),
            isVideo = true
        )

        assertEquals("artwork:https://example.com/cover.jpg", source.dynamicThemeSourceKey())
    }

    @Test
    fun resolveBootstrapDynamicHueSeedArgb_usesPersistedSeedBeforePlaybackRestoreResolves() {
        val result = resolveBootstrapDynamicHueSeedArgb(
            dynamicHueEnabled = true,
            playbackRestoreResolved = false,
            currentSourceKey = null,
            persistedSourceKey = "artwork:https://example.com/cover.jpg",
            persistedSeedArgb = 0xFF336699.toInt()
        )

        assertEquals(0xFF336699.toInt(), result)
    }

    @Test
    fun resolveBootstrapDynamicHueSeedArgb_usesPersistedSeedWhenCurrentSourceMatches() {
        val result = resolveBootstrapDynamicHueSeedArgb(
            dynamicHueEnabled = true,
            playbackRestoreResolved = true,
            currentSourceKey = "artwork:https://example.com/cover.jpg",
            persistedSourceKey = "artwork:https://example.com/cover.jpg",
            persistedSeedArgb = 0xFF663399.toInt()
        )

        assertEquals(0xFF663399.toInt(), result)
    }

    @Test
    fun resolveBootstrapDynamicHueSeedArgb_ignoresPersistedSeedAfterRestoreWhenNoCurrentSource() {
        val result = resolveBootstrapDynamicHueSeedArgb(
            dynamicHueEnabled = true,
            playbackRestoreResolved = true,
            currentSourceKey = null,
            persistedSourceKey = "artwork:https://example.com/cover.jpg",
            persistedSeedArgb = 0xFF663399.toInt()
        )

        assertNull(result)
    }

    @Test
    fun shouldClearPersistedDynamicHueSeed_onlyClearsAfterRestoreWhenNoSourceRemains() {
        assertFalse(
            shouldClearPersistedDynamicHueSeed(
                dynamicHueEnabled = true,
                playbackRestoreResolved = false,
                currentSourceKey = null,
                persistedSourceKey = "artwork:https://example.com/cover.jpg",
                persistedSeedArgb = 0xFF336699.toInt()
            )
        )

        assertTrue(
            shouldClearPersistedDynamicHueSeed(
                dynamicHueEnabled = true,
                playbackRestoreResolved = true,
                currentSourceKey = null,
                persistedSourceKey = "artwork:https://example.com/cover.jpg",
                persistedSeedArgb = 0xFF336699.toInt()
            )
        )
    }

    @Test
    fun shouldClearPersistedDynamicHueSeed_clearsImmediatelyWhenFeatureDisabled() {
        assertTrue(
            shouldClearPersistedDynamicHueSeed(
                dynamicHueEnabled = false,
                playbackRestoreResolved = false,
                currentSourceKey = "artwork:https://example.com/cover.jpg",
                persistedSourceKey = "artwork:https://example.com/cover.jpg",
                persistedSeedArgb = 0xFF336699.toInt()
            )
        )
    }
}
