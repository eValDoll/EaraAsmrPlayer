package com.asmr.player.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigatorTest {
    @Test
    fun resolveAlbumDetailPopUpToRoute_keepsHotListeningAsDetailSource() {
        assertEquals(Routes.HotListening, resolveAlbumDetailPopUpToRoute(Routes.HotListening))
        assertEquals(Routes.Search, resolveAlbumDetailPopUpToRoute(Routes.Search))
        assertEquals(Routes.Library, resolveAlbumDetailPopUpToRoute(null))
    }
}
