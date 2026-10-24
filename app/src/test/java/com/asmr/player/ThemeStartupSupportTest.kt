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

        assertEquals("cover-hsl-readable:artwork:https://example.com/cover.jpg", source.dynamicThemeSourceKey())
    }

    @Test
    fun resolveBootstrapDynamicHueSeedArgb_usesPersistedSeedBeforePlaybackRestoreResolves() {
        for (sourceKey in listOf(
            "cover-hsl-readable:artwork:https://example.com/cover.jpg",
            "cover-hsl-readable:video:file:///sample.mp4"
        )) {
            val result = resolveBootstrapDynamicHueSeedArgb(
                dynamicHueEnabled = true,
                playbackRestoreResolved = false,
                currentSourceKey = null,
                persistedSourceKey = sourceKey,
                persistedSeedArgb = 0xFF336699.toInt()
            )

            assertEquals(sourceKey, 0xFF336699.toInt(), result)
        }
    }

    @Test
    fun resolveBootstrapDynamicHueSeedArgb_usesPersistedSeedWhenCurrentSourceMatches() {
        for (sourceKey in listOf(
            "cover-hsl-readable:artwork:https://example.com/cover.jpg",
            "cover-hsl-readable:video:file:///sample.mp4"
        )) {
            val result = resolveBootstrapDynamicHueSeedArgb(
                dynamicHueEnabled = true,
                playbackRestoreResolved = true,
                currentSourceKey = sourceKey,
                persistedSourceKey = sourceKey,
                persistedSeedArgb = 0xFF663399.toInt()
            )

            assertEquals(sourceKey, 0xFF663399.toInt(), result)
        }
    }

    @Test
    fun resolveBootstrapDynamicHueSeedArgb_rejectsPreviousExtractorBeforeAndAfterRestore() {
        for (source in listOf("artwork:https://example.com/cover.jpg", "video:file:///sample.mp4")) {
            for (previousPrefix in listOf("", "cover-hsl-v1:")) {
                for (restored in listOf(false, true)) {
                    assertNull(
                        "source=$source, previousPrefix=$previousPrefix, restored=$restored",
                        resolveBootstrapDynamicHueSeedArgb(
                            dynamicHueEnabled = true,
                            playbackRestoreResolved = restored,
                            currentSourceKey = if (restored) "cover-hsl-readable:$source" else null,
                            persistedSourceKey = "$previousPrefix$source",
                            persistedSeedArgb = 0xFF336699.toInt()
                        )
                    )
                }
            }
        }
    }

    @Test
    fun resolveBootstrapDynamicHueSeedArgb_ignoresPersistedSeedWhenCurrentSourceChanges() {
        for (restored in listOf(false, true)) {
            for (currentSourceKey in listOf(
                "cover-hsl-readable:artwork:https://example.com/other-cover.jpg",
                "cover-hsl-readable:video:file:///sample.mp4"
            )) {
                assertNull(
                    "source=$currentSourceKey, restored=$restored",
                    resolveBootstrapDynamicHueSeedArgb(
                        dynamicHueEnabled = true,
                        playbackRestoreResolved = restored,
                        currentSourceKey = currentSourceKey,
                        persistedSourceKey = "cover-hsl-readable:artwork:https://example.com/cover.jpg",
                        persistedSeedArgb = 0xFF336699.toInt()
                    )
                )
            }
        }
    }

    @Test
    fun dynamicThemeSourceKey_versionsVideoSourcesWithoutArtwork() {
        val source = ThemeMediaSource(
            artworkUri = null,
            videoUri = Uri.parse("file:///sample.mp4"),
            isVideo = true
        )

        assertEquals("cover-hsl-readable:video:file:///sample.mp4", source.dynamicThemeSourceKey())
    }

    @Test
    fun resolveBootstrapDynamicHueSeedArgb_ignoresPersistedSeedAfterRestoreWhenNoCurrentSource() {
        val result = resolveBootstrapDynamicHueSeedArgb(
            dynamicHueEnabled = true,
            playbackRestoreResolved = true,
            currentSourceKey = null,
            persistedSourceKey = "cover-hsl-readable:artwork:https://example.com/cover.jpg",
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
                persistedSourceKey = "cover-hsl-readable:artwork:https://example.com/cover.jpg",
                persistedSeedArgb = 0xFF336699.toInt()
            )
        )

        assertTrue(
            shouldClearPersistedDynamicHueSeed(
                dynamicHueEnabled = true,
                playbackRestoreResolved = true,
                currentSourceKey = null,
                persistedSourceKey = "cover-hsl-readable:artwork:https://example.com/cover.jpg",
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
                currentSourceKey = "cover-hsl-readable:artwork:https://example.com/cover.jpg",
                persistedSourceKey = "cover-hsl-readable:artwork:https://example.com/cover.jpg",
                persistedSeedArgb = 0xFF336699.toInt()
            )
        )
    }
}
