package com.asmr.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class MainNavigationSupportTest {

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
    fun resolveCurrentPrimaryDestinationRoute_treatsSearchAssistAsSecondary() {
        assertEquals(null, resolveCurrentPrimaryDestinationRoute("search_assist"))
        assertEquals(null, resolveCurrentPrimaryDestinationRoute("search_assist?keyword={keyword}"))
    }

    @Test
    fun shouldScrollPrimaryRouteToTop_onlyWhenAlreadyAtPrimaryRoot() {
        assertEquals(true, shouldScrollPrimaryRouteToTop("playlists", "playlists", "playlists"))
        assertEquals(false, shouldScrollPrimaryRouteToTop("playlists", "playlists", null))
        assertEquals(false, shouldScrollPrimaryRouteToTop("groups", "playlists", "playlists"))
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
}
