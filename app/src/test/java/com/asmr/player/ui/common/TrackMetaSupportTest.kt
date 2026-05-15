package com.asmr.player.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackMetaSupportTest {
    @Test
    fun buildAudioMeta_splitsLeadingAndTrailingSegments() {
        val meta = buildAudioMeta(
            durationSeconds = 65.0,
            sizeText = "2.0 KB",
            prefixSegments = listOf("CV Alice")
        )

        assertEquals("CV Alice", meta.leadingText)
        assertEquals("01:05 · 2.0 KB", meta.trailingText)
    }

    @Test
    fun buildTrackMetaLine_includesDurationAndSize() {
        assertEquals("01:05 · 2.0 KB", buildTrackMetaLine(durationSeconds = 65.0, sizeText = "2.0 KB"))
    }

    @Test
    fun buildTrackMetaLine_skipsBlankSegments() {
        assertEquals("2.0 KB", buildTrackMetaLine(durationSeconds = 0.0, sizeText = "2.0 KB"))
        assertEquals("01:05", buildTrackMetaLine(durationSeconds = 65.0, sizeText = null))
    }

    @Test
    fun buildAudioMetaText_includesPrefixSegmentsWithDotSeparator() {
        assertEquals(
            "CV Alice · 01:05 · 2.0 KB",
            buildAudioMetaText(
                durationSeconds = 65.0,
                sizeText = "2.0 KB",
                prefixSegments = listOf("CV Alice")
            )
        )
    }
}
