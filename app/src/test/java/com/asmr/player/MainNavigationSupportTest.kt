package com.asmr.player

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.ui.nav.bottomChromeNavItems
import com.asmr.player.ui.nav.isPrimaryRoute
import com.asmr.player.ui.nav.resolvePrimaryRoute
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainNavigationSupportTest {

    @Test
    fun resolveMainRequestedOrientation_locksOnlyFullscreenPlayerToLandscape() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolveMainRequestedOrientation(
                isPhone = true,
                nowPlayingVisible = true,
                videoFullscreen = true,
                portraitExitPending = false
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolveMainRequestedOrientation(
                isPhone = false,
                nowPlayingVisible = true,
                videoFullscreen = true,
                portraitExitPending = false
            )
        )
    }

    @Test
    fun resolveMainRequestedOrientation_preservesNormalPlayerAndPagePolicies() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER,
            resolveMainRequestedOrientation(
                isPhone = true,
                nowPlayingVisible = true,
                videoFullscreen = false,
                portraitExitPending = false
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            resolveMainRequestedOrientation(
                isPhone = true,
                nowPlayingVisible = false,
                videoFullscreen = true,
                portraitExitPending = false
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR,
            resolveMainRequestedOrientation(
                isPhone = false,
                nowPlayingVisible = false,
                videoFullscreen = false,
                portraitExitPending = false
            )
        )
    }

    @Test
    fun resolveMainRequestedOrientation_rotatesPhoneBeforeRevealingUnderlyingPage() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            resolveMainRequestedOrientation(
                isPhone = true,
                nowPlayingVisible = true,
                videoFullscreen = false,
                portraitExitPending = true
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR,
            resolveMainRequestedOrientation(
                isPhone = false,
                nowPlayingVisible = true,
                videoFullscreen = false,
                portraitExitPending = true
            )
        )
    }

    @Test
    fun shouldKeepVideoOutputEnabled_keepsDecoderWarmWhilePlaybackUiOwnsVideo() {
        assertEquals(
            true,
            shouldKeepVideoOutputEnabled(
                currentItemIsVideo = true,
                miniPlayerEnabled = true,
                nowPlayingVisible = false
            )
        )
        assertEquals(
            true,
            shouldKeepVideoOutputEnabled(
                currentItemIsVideo = true,
                miniPlayerEnabled = false,
                nowPlayingVisible = true
            )
        )
        assertEquals(
            false,
            shouldKeepVideoOutputEnabled(
                currentItemIsVideo = true,
                miniPlayerEnabled = false,
                nowPlayingVisible = false
            )
        )
        assertEquals(
            false,
            shouldKeepVideoOutputEnabled(
                currentItemIsVideo = false,
                miniPlayerEnabled = true,
                nowPlayingVisible = false
            )
        )
    }

    @Test
    fun computePrimaryNavSelectionProgresses_blendsCurrentAndNeighborPages() {
        val result = computePrimaryNavSelectionProgresses(
            pagerRoutes = listOf("library", "search", "downloads"),
            currentPage = 1,
            currentPageOffsetFraction = 0.25f,
            fallbackRoute = "library"
        )

        assertEquals(0.75f, result.getValue("search"))
        assertEquals(0.25f, result.getValue("downloads"))
        assertEquals(false, result.containsKey("library"))
    }

    @Test
    fun computePrimaryNavSelectionProgresses_locksToPendingRouteDuringProgrammaticJump() {
        val result = computePrimaryNavSelectionProgresses(
            pagerRoutes = listOf("library", "search", "favorites", "downloads"),
            currentPage = 1,
            currentPageOffsetFraction = 0.4f,
            fallbackRoute = "library",
            lockedRoute = "downloads"
        )

        assertEquals(mapOf("downloads" to 1f), result)
    }

    @Test
    fun resolvePrimaryNavVisualRoute_prefersPendingRouteWhenItIsPrimary() {
        assertEquals(
            "downloads",
            resolvePrimaryNavVisualRoute(
                activeRoute = "search",
                pendingRoute = "downloads",
                pagerRoutes = listOf("library", "search", "downloads")
            )
        )
        assertEquals(
            "search",
            resolvePrimaryNavVisualRoute(
                activeRoute = "search",
                pendingRoute = "album_detail/1",
                pagerRoutes = listOf("library", "search", "downloads")
            )
        )
    }

    @Test
    fun resolvePrimaryPagerApproachPage_onlySkipsLongProgrammaticJumps() {
        assertEquals(null, resolvePrimaryPagerApproachPage(currentPage = 1, targetPage = 2))
        assertEquals(2, resolvePrimaryPagerApproachPage(currentPage = 0, targetPage = 3))
        assertEquals(1, resolvePrimaryPagerApproachPage(currentPage = 4, targetPage = 0))
    }

    @Test
    fun shouldSyncPrimaryPagerToRoute_onlyWhenSettledPageDiffersFromTarget() {
        assertEquals(false, shouldSyncPrimaryPagerToRoute(targetPage = -1, settledPage = 0))
        assertEquals(false, shouldSyncPrimaryPagerToRoute(targetPage = 2, settledPage = 2))
        assertEquals(true, shouldSyncPrimaryPagerToRoute(targetPage = 2, settledPage = 1))
    }

    @Test
    fun shouldClearPendingPrimaryNavigationRoute_waitsUntilPagerSettlesOnPendingPage() {
        assertEquals(
            false,
            shouldClearPendingPrimaryNavigationRoute(
                currentRoute = "search",
                pendingRoute = "search",
                navigationInProgress = false,
                pendingPage = 1,
                settledPage = 0
            )
        )
        assertEquals(
            false,
            shouldClearPendingPrimaryNavigationRoute(
                currentRoute = "search",
                pendingRoute = "search",
                navigationInProgress = true,
                pendingPage = 1,
                settledPage = 1
            )
        )
        assertEquals(
            true,
            shouldClearPendingPrimaryNavigationRoute(
                currentRoute = "search",
                pendingRoute = "search",
                navigationInProgress = false,
                pendingPage = 1,
                settledPage = 1
            )
        )
    }

    @Test
    fun resolvePrimaryPagerBeyondBoundsPageCount_keepsOnlyAdjacentPageComposed() {
        assertEquals(0, resolvePrimaryPagerBeyondBoundsPageCount(0))
        assertEquals(0, resolvePrimaryPagerBeyondBoundsPageCount(1))
        assertEquals(1, resolvePrimaryPagerBeyondBoundsPageCount(2))
        assertEquals(1, resolvePrimaryPagerBeyondBoundsPageCount(8))
    }

    @Test
    fun resolveCurrentPrimaryDestinationRoute_handlesFavoritesSystemPlaylist() {
        assertEquals(
            "playlist_system/favorites",
            resolveCurrentPrimaryDestinationRoute(
                currentRoute = "playlist_system/{type}",
                playlistSystemType = "favorites"
            )
        )
        assertEquals("settings", resolveCurrentPrimaryDestinationRoute("settings"))
        assertEquals(null, resolveCurrentPrimaryDestinationRoute("playlist_system/{type}", "recent"))
    }

    @Test
    fun bottomChromeNavItems_useListeningCalendarAsPrimaryEntry() {
        val items = bottomChromeNavItems()
        val routes = items.map { it.route }

        assertEquals("ASMR 看板", items[items.lastIndex - 1].label)
        assertEquals("listening_calendar", items[items.lastIndex - 1].route)
        assertEquals("设置", items.last().label)
        assertEquals("settings", items.last().route)
        assertEquals(true, routes.contains("listening_calendar"))
        assertEquals(false, routes.contains("dlsite_login"))
    }

    @Test
    fun primaryRouteResolution_treatsCalendarAsPrimaryAndDlsiteLoginAsSecondary() {
        assertEquals(true, isPrimaryRoute("listening_calendar"))
        assertEquals(false, isPrimaryRoute("dlsite_login"))
        assertEquals("listening_calendar", resolvePrimaryRoute("listening_calendar", "library"))
        assertEquals("library", resolvePrimaryRoute("dlsite_login", "library"))
        assertEquals("listening_calendar", resolveCurrentPrimaryDestinationRoute("listening_calendar"))
        assertEquals(null, resolveCurrentPrimaryDestinationRoute("dlsite_login"))
    }

    @Test
    fun resolveCurrentPrimaryDestinationRoute_treatsSearchAssistAsSecondary() {
        assertEquals(null, resolveCurrentPrimaryDestinationRoute("search_assist"))
        assertEquals(null, resolveCurrentPrimaryDestinationRoute("search_assist?keyword={keyword}"))
    }

    @Test
    fun shouldScrollPrimaryRouteToTop_whenRequestedRouteIsCurrentPrimaryRoute() {
        assertEquals(true, shouldScrollPrimaryRouteToTop("playlists", "playlists", "playlists"))
        assertEquals(true, shouldScrollPrimaryRouteToTop("playlists", "playlists", null))
        assertEquals(false, shouldScrollPrimaryRouteToTop("groups", "playlists", "playlists"))
    }

    @Test
    fun shouldTriggerPrimaryRouteScrollToTop_whenVisualRouteIsAlreadySelected() {
        assertEquals(
            true,
            shouldTriggerPrimaryRouteScrollToTop(
                requestedRoute = "hot",
                visualPrimaryRoute = "hot",
                activePrimaryRoute = "library",
                currentPrimaryRoute = "library"
            )
        )
        assertEquals(
            false,
            shouldTriggerPrimaryRouteScrollToTop(
                requestedRoute = "groups",
                visualPrimaryRoute = "hot",
                activePrimaryRoute = "library",
                currentPrimaryRoute = "library"
            )
        )
    }

    @Test
    fun shouldHideStatusBarForImmersivePage_hidesAlbumDetailRoutes() {
        assertEquals(true, shouldHideStatusBarForImmersivePage("album_detail/{albumId}?rjCode={rjCode}", false))
        assertEquals(true, shouldHideStatusBarForImmersivePage("album_detail_rj/{rj}?initialTab={initialTab}", false))
        assertEquals(true, shouldHideStatusBarForImmersivePage("album_detail_online/{rj}", false))
    }

    @Test
    fun shouldHideStatusBarForImmersivePage_hidesNowPlayingAndLyricsOverlay() {
        assertEquals(true, shouldHideStatusBarForImmersivePage("library", true))
        assertEquals(true, shouldHideStatusBarForImmersivePage(null, true))
    }

    @Test
    fun shouldHideStatusBarForImmersivePage_keepsRegularRoutesVisible() {
        assertEquals(false, shouldHideStatusBarForImmersivePage("library", false))
        assertEquals(false, shouldHideStatusBarForImmersivePage("search", false))
        assertEquals(false, shouldHideStatusBarForImmersivePage("settings", false))
        assertEquals(false, shouldHideStatusBarForImmersivePage(null, false))
    }

    @Test
    fun isAlbumDetailStackTransition_onlyMatchesDetailToDetailNavigation() {
        assertEquals(
            true,
            isAlbumDetailStackTransition(
                "album_detail_rj/{rj}?initialTab={initialTab}",
                "album_detail_rj/{rj}?initialTab={initialTab}"
            )
        )
        assertEquals(
            false,
            isAlbumDetailStackTransition("library", "album_detail_rj/{rj}?initialTab={initialTab}")
        )
        assertEquals(
            false,
            isAlbumDetailStackTransition("album_detail_online/{rj}", "library")
        )
    }

    @Test
    fun toThemeMediaSource_prefersArtworkForVideoWhenAvailable() {
        val item = MediaItem.Builder()
            .setUri("file:///sample.mp4")
            .setMimeType("video/mp4")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtworkUri(Uri.parse("https://example.com/cover.jpg"))
                    .setExtras(Bundle().apply { putBoolean("is_video", true) })
                    .build()
            )
            .build()

        val result = item.toThemeMediaSource()

        assertEquals(Uri.parse("https://example.com/cover.jpg"), result.artworkUri)
        assertEquals(Uri.parse("file:///sample.mp4"), result.videoUri)
        assertEquals(true, result.isVideo)
    }

    @Test
    fun toThemeMediaSource_filtersPlaceholderArtworkFromThemeSource() {
        val item = MediaItem.Builder()
            .setUri("file:///sample.mp3")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtworkUri(Uri.parse("android.resource://com.asmr.player/drawable/ic_placeholder"))
                    .build()
            )
            .build()

        val result = item.toThemeMediaSource()

        assertEquals(null, result.artworkUri)
        assertEquals(false, result.isVideo)
    }

    @Test
    fun isVideoPlaybackItem_distinguishesAudioAndVideoSources() {
        val audio = MediaItem.Builder()
            .setUri("file:///sample.flac")
            .setMimeType("audio/flac")
            .build()
        val video = MediaItem.Builder()
            .setUri("file:///sample.mp4")
            .setMimeType("video/mp4")
            .build()
        val playlistVideo = PlaylistItemEntity(
            playlistId = 1L,
            mediaId = "video",
            title = "视频",
            uri = "file:///sample.mkv"
        )

        assertEquals(false, audio.isVideoPlaybackItem())
        assertEquals(true, video.isVideoPlaybackItem())
        assertEquals(true, playlistVideo.isVideoPlaybackItem())
    }
}
