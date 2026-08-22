package com.asmr.player.ui.library

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDetailLandscapeLayoutTest {
    @Test
    fun landscapeTablet_usesArtworkTideLayout() {
        assertTrue(
            shouldUseAlbumDetailLandscapeLayout(
                compactWidth = false,
                screenWidthDp = 1280,
                screenHeightDp = 800
            )
        )
    }

    @Test
    fun portraitTablet_keepsExistingAlbumLayout() {
        assertFalse(
            shouldUseAlbumDetailLandscapeLayout(
                compactWidth = false,
                screenWidthDp = 800,
                screenHeightDp = 1280
            )
        )
    }

    @Test
    fun compactLandscapeDevice_keepsPhoneLayout() {
        assertFalse(
            shouldUseAlbumDetailLandscapeLayout(
                compactWidth = true,
                screenWidthDp = 740,
                screenHeightDp = 360
            )
        )
    }

    @Test
    fun landscapeArtwork_keepsInteractiveContentOutsideCoverBounds() {
        val artworkSize = 384.dp

        assertTrue(albumLandscapeHeaderStart(artworkSize) > albumLandscapeArtworkRight(artworkSize))
    }

    @Test
    fun landscapeCollapse_stopsBeforeContentScrollingAndReachesSeventyPercentScale() {
        val artworkSize = 384.dp
        val collapseDistance = albumLandscapeCollapseDistance(artworkSize)

        assertEquals(107.52f, collapseDistance.value, 0.01f)
        assertEquals(1f, albumLandscapeCoverScale(0f, collapseDistance.value), 0.001f)
        assertEquals(
            0.70f,
            albumLandscapeCoverScale(collapseDistance.value, collapseDistance.value),
            0.001f
        )
        assertEquals(
            1f,
            albumLandscapeCollapseProgress(collapseDistance.value, collapseDistance.value),
            0.001f
        )
    }

    @Test
    fun landscapeSurface_keepsCoveringScreenBottomAtMaximumCollapse() {
        val artworkSize = 384.dp
        val viewportHeight = 572.dp
        val collapseDistance = albumLandscapeCollapseDistance(artworkSize)

        assertEquals(
            viewportHeight.value + collapseDistance.value,
            albumLandscapeSurfaceHeight(viewportHeight, artworkSize).value,
            0.01f
        )
    }

    @Test
    fun landscapePlaybackProgress_isReadOnlyFractionClampedToTrackDuration() {
        assertEquals(0f, albumLandscapePlaybackProgress(20_000L, 0L), 0.001f)
        assertEquals(0.25f, albumLandscapePlaybackProgress(15_000L, 60_000L), 0.001f)
        assertEquals(1f, albumLandscapePlaybackProgress(70_000L, 60_000L), 0.001f)
    }
}
