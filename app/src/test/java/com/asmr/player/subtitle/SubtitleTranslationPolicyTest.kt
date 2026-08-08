package com.asmr.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTranslationPolicyTest {
    @Test
    fun fullTranslation_alwaysUsesOneRequestForNonEmptyContent() {
        assertEquals(0, fullTranslationRequestCount(totalSources = 0))
        assertEquals(1, fullTranslationRequestCount(totalSources = 1))
        assertEquals(1, fullTranslationRequestCount(totalSources = 10_000))
    }

    @Test
    fun generatedCaptionValidation_normalizesTimelineFromSemanticSource() {
        val longText = "あ".repeat(80)
        val source = GeneratedSubtitleSource(
            index = 0,
            startMs = 0L,
            endMs = 7_000L,
            text = longText
        )

        val result = validateGeneratedSubtitleCaptions(
            captions = listOf(
                GeneratedSubtitleCaption(
                    sourceIndices = listOf(0),
                    startMs = 0L,
                    endMs = 0L,
                    correctedJapanese = longText,
                    chineseText = "这是一条较长的单段字幕"
                )
            ),
            expectedSources = listOf(source)
        )

        assertEquals(7_000L, result.single().endMs)
    }
}
