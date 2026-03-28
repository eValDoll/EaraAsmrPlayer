package com.asmr.player.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackgroundEffectVisibilityTest {
    @Test
    fun appPages_followGlobalEffectToggle() {
        assertFalse(
            shouldRenderBackgroundEffect(
                surface = BackgroundEffectSurface.AppPage,
                effectEnabled = false,
                coverBackgroundEnabled = false
            )
        )
        assertTrue(
            shouldRenderBackgroundEffect(
                surface = BackgroundEffectSurface.AppPage,
                effectEnabled = true,
                coverBackgroundEnabled = true
            )
        )
    }

    @Test
    fun nowPlayingAndLyrics_hideEffectWhenCoverBackgroundIsEnabled() {
        assertFalse(
            shouldRenderBackgroundEffect(
                surface = BackgroundEffectSurface.NowPlayingPlayer,
                effectEnabled = true,
                coverBackgroundEnabled = true
            )
        )
        assertTrue(
            shouldRenderBackgroundEffect(
                surface = BackgroundEffectSurface.NowPlayingPlayer,
                effectEnabled = true,
                coverBackgroundEnabled = false
            )
        )
        assertFalse(
            shouldRenderBackgroundEffect(
                surface = BackgroundEffectSurface.NowPlayingLyrics,
                effectEnabled = true,
                coverBackgroundEnabled = true
            )
        )
        assertTrue(
            shouldRenderBackgroundEffect(
                surface = BackgroundEffectSurface.NowPlayingLyrics,
                effectEnabled = true,
                coverBackgroundEnabled = false
            )
        )
    }
}
