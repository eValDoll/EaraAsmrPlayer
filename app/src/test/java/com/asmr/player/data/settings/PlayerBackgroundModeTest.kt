package com.asmr.player.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBackgroundModeTest {
    @Test
    fun fromStorageValue_defaultsToTheme() {
        assertEquals(PlayerBackgroundMode.Theme, PlayerBackgroundMode.fromStorageValue(null))
        assertEquals(PlayerBackgroundMode.Theme, PlayerBackgroundMode.fromStorageValue(""))
        assertEquals(PlayerBackgroundMode.Theme, PlayerBackgroundMode.fromStorageValue("unknown"))
    }

    @Test
    fun fromStorageValue_preservesExistingThemeAndFlowingPreferences() {
        assertEquals(PlayerBackgroundMode.Theme, PlayerBackgroundMode.fromStorageValue("solid"))
        assertEquals(PlayerBackgroundMode.Flowing, PlayerBackgroundMode.fromStorageValue("flowing"))
    }

    @Test
    fun storageValues_keepNeutralSeparateAndRoundTripAllModes() {
        assertEquals("solid", PlayerBackgroundMode.Theme.storageValue)
        assertEquals("neutral", PlayerBackgroundMode.Solid.storageValue)
        assertEquals(PlayerBackgroundMode.Solid, PlayerBackgroundMode.fromStorageValue("neutral"))
        PlayerBackgroundMode.entries.forEach { mode ->
            assertEquals(mode, PlayerBackgroundMode.fromStorageValue(mode.storageValue))
        }
    }
}
