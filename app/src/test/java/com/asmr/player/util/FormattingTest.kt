package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test
    fun `formatTrackSeconds returns empty for missing or non-positive seconds`() {
        assertEquals("", Formatting.formatTrackSeconds(null))
        assertEquals("", Formatting.formatTrackSeconds(0.0))
        assertEquals("", Formatting.formatTrackSeconds(-5.0))
    }

    @Test
    fun `formatTrackSeconds formats minute based values`() {
        assertEquals("03:05", Formatting.formatTrackSeconds(185.0))
    }

    @Test
    fun `formatTrackSeconds formats hour based values`() {
        assertEquals("01:01:01", Formatting.formatTrackSeconds(3661.0))
    }
}
