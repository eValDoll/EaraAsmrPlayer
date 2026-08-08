package com.asmr.player.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumHeaderMetaRevealTest {
    @Test
    fun shouldExpandAlbumHeaderMetaReveal_keepsInitialMetaStable() {
        assertFalse(
            shouldExpandAlbumHeaderMetaReveal(
                presentInitially = true
            )
        )
    }

    @Test
    fun shouldExpandAlbumHeaderMetaReveal_expandsLateMeta() {
        assertTrue(
            shouldExpandAlbumHeaderMetaReveal(
                presentInitially = false
            )
        )
    }
}
