package com.asmr.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSegmentNormalizerTest {
    @Test
    fun `normalization trims clamps sorts and removes duplicate segments`() {
        val duplicate = GeneratedSubtitle(startMs = 500L, endMs = 900L, text = " 第二句 ")

        val result = SubtitleSegmentNormalizer.normalize(
            segments = listOf(
                duplicate,
                GeneratedSubtitle(startMs = -20L, endMs = 300L, text = "第一句"),
                duplicate,
                GeneratedSubtitle(startMs = 1_950L, endMs = 2_500L, text = "最后一句"),
                GeneratedSubtitle(startMs = 1_000L, endMs = 1_100L, text = "  ")
            ),
            totalDurationMs = 2_000L
        )

        assertEquals(
            listOf(
                GeneratedSubtitle(startMs = 0L, endMs = 300L, text = "第一句"),
                GeneratedSubtitle(startMs = 500L, endMs = 900L, text = "第二句"),
                GeneratedSubtitle(startMs = 1_950L, endMs = 2_000L, text = "最后一句")
            ),
            result
        )
    }
}
