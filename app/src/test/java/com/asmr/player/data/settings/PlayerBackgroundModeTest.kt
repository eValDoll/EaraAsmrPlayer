package com.asmr.player.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerBackgroundModeTest {
    @Test
    fun fromStorageValue_defaultsToSolid() {
        assertEquals(PlayerBackgroundMode.Solid, PlayerBackgroundMode.fromStorageValue(null))
        assertEquals(PlayerBackgroundMode.Solid, PlayerBackgroundMode.fromStorageValue(""))
        assertEquals(PlayerBackgroundMode.Solid, PlayerBackgroundMode.fromStorageValue("unknown"))
    }

    @Test
    fun fromStorageValue_readsPersistedModeValues() {
        assertEquals(PlayerBackgroundMode.Solid, PlayerBackgroundMode.fromStorageValue("solid"))
        assertEquals(PlayerBackgroundMode.Flowing, PlayerBackgroundMode.fromStorageValue("flowing"))
    }
}
