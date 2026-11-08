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
                availableWidth = 1280.dp,
                availableHeight = 800.dp
            )
        )
    }

    @Test
    fun portraitTablet_keepsExistingAlbumLayout() {
        assertFalse(
            shouldUseAlbumDetailLandscapeLayout(
                availableWidth = 800.dp,
                availableHeight = 1280.dp
            )
        )
    }

    @Test
    fun compactLandscapeDevice_keepsPhoneLayout() {
        assertFalse(
            shouldUseAlbumDetailLandscapeLayout(
                availableWidth = 740.dp,
                availableHeight = 360.dp
            )
        )
    }

    @Test
    fun splitWindowRequiresRoomForArtworkAndReadableHeaderActions() {
        assertFalse(shouldUseAlbumDetailLandscapeLayout(670.dp, 600.dp, 1.3f))
        assertFalse(shouldUseAlbumDetailLandscapeLayout(512.dp, 600.dp, 1.3f))
        assertFalse(shouldUseAlbumDetailLandscapeLayout(840.dp, 640.dp, 1.3f))
        // 1024dp 全屏扣除 88dp 左侧导航后仍沿用原双栏。
        assertTrue(shouldUseAlbumDetailLandscapeLayout(936.dp, 640.dp, 1.3f))
        assertTrue(shouldUseAlbumDetailLandscapeLayout(840.dp, 640.dp, 1f))
    }

    @Test
    fun landscapeArtwork_keepsInteractiveContentOutsideCoverBounds() {
        val artworkSize = 384.dp

        assertEquals(464.dp, albumLandscapeHeaderStart(artworkSize))
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
    fun landscapeLeftPaneViewport_growsWithVisibleSurfaceDuringCollapse() {
        val surfaceHeightPx = 680
        val collapseMaxPx = 108f

        assertEquals(
            572,
            albumLandscapePaneViewportHeightPx(surfaceHeightPx, 0f, collapseMaxPx)
        )
        assertEquals(
            626,
            albumLandscapePaneViewportHeightPx(surfaceHeightPx, 54f, collapseMaxPx)
        )
        assertEquals(
            680,
            albumLandscapePaneViewportHeightPx(surfaceHeightPx, collapseMaxPx, collapseMaxPx)
        )
    }

    @Test
    fun landscapeSpectrum_startsHigherAndFollowsCollapsePartially() {
        assertEquals(57f, albumLandscapeSpectrumOffsetY(300.dp).value, 0.01f)
        assertEquals(-68.88f, albumLandscapeSpectrumTranslationY(84f), 0.01f)
    }

    @Test
    fun landscapeProgressPulse_slowsAsItApproachesCurrentPlaybackPosition() {
        assertEquals(0f, albumLandscapePulseSweepFraction(0f), 0.001f)
        assertEquals(0.75f, albumLandscapePulseSweepFraction(0.5f), 0.001f)
        assertEquals(1f, albumLandscapePulseSweepFraction(1f), 0.001f)

        val earlyDistance = albumLandscapePulseSweepFraction(0.25f) -
            albumLandscapePulseSweepFraction(0f)
        val lateDistance = albumLandscapePulseSweepFraction(1f) -
            albumLandscapePulseSweepFraction(0.75f)
        assertTrue(earlyDistance > lateDistance)
    }

    @Test
    fun landscapeCoverShadow_startsAfterImageFadeAndNeverOutrunsIt() {
        assertEquals(0f, albumLandscapeCoverShadowAlpha(0f), 0.001f)
        assertEquals(0f, albumLandscapeCoverShadowAlpha(0.16f), 0.001f)

        val earlyShadowAlpha = albumLandscapeCoverShadowAlpha(0.20f)
        assertTrue(earlyShadowAlpha > 0f)
        assertTrue(earlyShadowAlpha < 0.20f)
        assertEquals(1f, albumLandscapeCoverShadowAlpha(1f), 0.001f)
    }

    @Test
    fun landscapeHeaderLift_releasesTheSameAmountOfScrollableSpace() {
        assertEquals(172f, albumLandscapeDirectoryTop(220.dp, 52.dp).value, 0.001f)
        assertEquals(0f, albumLandscapeDirectoryTop(40.dp, 52.dp).value, 0.001f)
    }

    @Test
    fun landscapeProgressPulse_onlyRunsDuringActivePlayback() {
        assertFalse(albumLandscapePulseEnabled(isPlaying = false, progress = 0.4f))
        assertFalse(albumLandscapePulseEnabled(isPlaying = true, progress = 0f))
        assertTrue(albumLandscapePulseEnabled(isPlaying = true, progress = 0.4f))
    }

    @Test
    fun landscapePlaybackProgress_isReadOnlyFractionClampedToTrackDuration() {
        assertEquals(0f, albumLandscapePlaybackProgress(20_000L, 0L), 0.001f)
        assertEquals(0.25f, albumLandscapePlaybackProgress(15_000L, 60_000L), 0.001f)
        assertEquals(1f, albumLandscapePlaybackProgress(70_000L, 60_000L), 0.001f)
    }
}
