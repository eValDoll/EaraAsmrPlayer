package com.asmr.player.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingHomeLayoutModeTest {
    @Test
    fun fromStorageValue_defaultsToClassic() {
        assertEquals(NowPlayingHomeLayoutMode.Classic, NowPlayingHomeLayoutMode.fromStorageValue(null))
        assertEquals(NowPlayingHomeLayoutMode.Classic, NowPlayingHomeLayoutMode.fromStorageValue("unknown"))
    }

    @Test
    fun fromStorageValue_mapsStoredValues() {
        assertEquals(NowPlayingHomeLayoutMode.Classic, NowPlayingHomeLayoutMode.fromStorageValue("classic"))
        assertEquals(NowPlayingHomeLayoutMode.Expanded, NowPlayingHomeLayoutMode.fromStorageValue("expanded"))
    }
}
