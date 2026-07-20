package com.asmr.player.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThinScrollbarTest {
    @Test
    fun thumbHeightClampsMinimumToTinyTrackHeight() {
        assertEquals(
            2f,
            resolveThinScrollbarThumbHeight(
                trackHeight = 2f,
                thumbFraction = 0.4f,
                minThumbLengthPx = 80f
            )
        )
    }

    @Test
    fun thumbHeightUsesMinimumWhenTrackCanFitIt() {
        assertEquals(
            80f,
            resolveThinScrollbarThumbHeight(
                trackHeight = 200f,
                thumbFraction = 0.2f,
                minThumbLengthPx = 80f
            )
        )
    }

    @Test
    fun thumbHeightSkipsInvalidTrack() {
        assertNull(
            resolveThinScrollbarThumbHeight(
                trackHeight = 0f,
                thumbFraction = 0.4f,
                minThumbLengthPx = 80f
            )
        )
    }
}
