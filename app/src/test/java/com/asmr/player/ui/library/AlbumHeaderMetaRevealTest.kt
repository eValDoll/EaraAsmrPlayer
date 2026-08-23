package com.asmr.player.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumHeaderMetaRevealTest {
    @Test
    fun shouldAnimateAlbumHeaderMetaReveal_keepsInitialMetaStable() {
        assertFalse(
            shouldAnimateAlbumHeaderMetaReveal(
                presentInitially = true,
                hasContent = true,
                animationsEnabled = true
            )
        )
    }

    @Test
    fun shouldAnimateAlbumHeaderMetaReveal_animatesLateMeta() {
        assertTrue(
            shouldAnimateAlbumHeaderMetaReveal(
                presentInitially = false,
                hasContent = true,
                animationsEnabled = true
            )
        )
    }

    @Test
    fun shouldAnimateAlbumHeaderMetaReveal_waitsUntilContentArrives() {
        assertFalse(
            shouldAnimateAlbumHeaderMetaReveal(
                presentInitially = false,
                hasContent = false,
                animationsEnabled = true
            )
        )
    }

    @Test
    fun shouldAnimateAlbumHeaderMetaReveal_respectsDisabledAnimations() {
        assertFalse(
            shouldAnimateAlbumHeaderMetaReveal(
                presentInitially = false,
                hasContent = true,
                animationsEnabled = false
            )
        )
    }
}
