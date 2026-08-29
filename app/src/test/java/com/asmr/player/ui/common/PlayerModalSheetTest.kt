package com.asmr.player.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerModalSheetTest {
    @Test
    fun maxHeight_matchesQueueStyleInPortraitAndLandscape() {
        assertEquals(600f, playerModalSheetMaxHeightDp(screenHeightDp = 800), 0.001f)
        assertEquals(308.25f, playerModalSheetMaxHeightDp(screenHeightDp = 411), 0.001f)
    }

    @Test
    fun maxHeight_doesNotBecomeNegativeForInvalidConfiguration() {
        assertEquals(0f, playerModalSheetMaxHeightDp(screenHeightDp = -1), 0.001f)
    }
}
