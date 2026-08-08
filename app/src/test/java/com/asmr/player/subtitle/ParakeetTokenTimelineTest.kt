package com.asmr.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParakeetTokenTimelineTest {
    @Test
    fun `token timestamps become bounded millisecond segments`() {
        val result = buildRecognizerTokenTimeline(
            tokens = listOf(" ", "日本", "語", "?", "って"),
            timestampsSeconds = listOf(0f, 1.04f, 1.36f, 4.72f, 4.96f),
            segmentDurationMs = 5_500L
        )

        assertEquals(listOf("日本", "語", "?", "って"), result.map { it.text })
        assertEquals(1_040L, result[0].startMs)
        assertEquals(1_360L, result[0].endMs)
        assertEquals(1_360L, result[1].startMs)
        assertEquals(1_760L, result[1].endMs)
        assertEquals(4_720L, result[2].startMs)
        assertEquals(4_960L, result[2].endMs)
        assertEquals(5_360L, result[3].endMs)
    }

    @Test
    fun `reported token duration takes precedence when available`() {
        val result = buildRecognizerTokenTimeline(
            tokens = listOf("▁おやすみ"),
            timestampsSeconds = listOf(0.25f),
            durationsSeconds = listOf(0.12f),
            segmentDurationMs = 1_000L
        )

        assertEquals(
            listOf(SubtitleTranscriptionToken(250L, 370L, "おやすみ")),
            result
        )
    }

    @Test
    fun `mismatched token timestamps are rejected for safe fallback`() {
        val result = buildRecognizerTokenTimeline(
            tokens = listOf("今日", "は"),
            timestampsSeconds = listOf(0f),
            segmentDurationMs = 1_000L
        )

        assertTrue(result.isEmpty())
    }
}
