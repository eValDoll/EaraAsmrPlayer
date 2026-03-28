package com.asmr.player

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
}
