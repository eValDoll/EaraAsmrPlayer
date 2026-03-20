package com.asmr.player.ui.player

import com.asmr.player.util.SubtitleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LyricItemKeyTest {
    @Test
    fun duplicateStartTimesStillProduceUniqueKeys() {
        val first = SubtitleEntry(startMs = 727760L, endMs = 729000L, text = "line one")
        val second = SubtitleEntry(startMs = 727760L, endMs = 730100L, text = "line two")

        assertNotEquals(lyricItemKey(0, first), lyricItemKey(1, second))
    }

    @Test
    fun sameEntryAndIndexProduceStableKey() {
        val entry = SubtitleEntry(startMs = 566780L, endMs = 568120L, text = "same line")

        assertEquals(lyricItemKey(3, entry), lyricItemKey(3, entry))
    }
}
