package com.asmr.player.ui.sidepanel

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentProgressPositionFormatTest {
    @Test
    fun formatRecentProgressPosition_underOneHour_formatsAsMmSs() {
        assertEquals("00:00", formatRecentProgressPosition(0L))
        assertEquals("00:00", formatRecentProgressPosition(-1L))
        assertEquals("00:05", formatRecentProgressPosition(5_000L))
        assertEquals("59:59", formatRecentProgressPosition((59L * 60L + 59L) * 1_000L))
    }

    @Test
    fun formatRecentProgressPosition_atOrOverOneHour_formatsAsHhMmSs() {
        assertEquals("01:00:00", formatRecentProgressPosition(60L * 60L * 1_000L))
        assertEquals("01:02:03", formatRecentProgressPosition((1L * 3600L + 2L * 60L + 3L) * 1_000L))
    }
}

