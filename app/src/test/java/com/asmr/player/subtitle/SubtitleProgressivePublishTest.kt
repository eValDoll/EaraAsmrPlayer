package com.asmr.player.subtitle

import com.asmr.player.data.local.db.entities.SubtitleFallbackCaptionEntity
import com.asmr.player.data.local.db.entities.SubtitleTranslationSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleProgressivePublishTest {
    @Test
    fun generatedTranslationLayout_usesOneSourceIndexPerSemanticCaption() {
        val captions = listOf(
            GeneratedSubtitle(0L, 2_500L, "今日はゆっくり休んでくださいね"),
            GeneratedSubtitle(2_700L, 4_000L, "目を閉じても大丈夫ですよ")
        )

        val layout = buildGeneratedTranslationLayout("item", captions)

        assertEquals(listOf(0, 1), layout.sources.map { it.sourceIndex })
        assertEquals(captions.map { it.text }, layout.sources.map { it.text })
        assertEquals(listOf(0, 1), layout.fallbackCaptions.map { it.firstSourceIndex })
        assertEquals(listOf(0, 1), layout.fallbackCaptions.map { it.lastSourceIndex })
    }

    @Test
    fun fallbackBoundary_isSplitAtTheConfirmedSourceWithoutOverlap() {
        val sources = (0..4).map { index ->
            SubtitleTranslationSourceEntity(
                itemId = "item",
                sourceIndex = index,
                startMs = index * 1_000L,
                endMs = (index + 1) * 1_000L,
                text = "日文$index"
            )
        }
        val fallback = listOf(
            SubtitleFallbackCaptionEntity("item", 0, 0, 2, 0L, 3_000L, "日文012"),
            SubtitleFallbackCaptionEntity("item", 1, 3, 4, 3_000L, 5_000L, "日文34")
        )

        val suffix = rebuildGeneratedFallbackSuffix(
            trackId = 7L,
            confirmedSourceCount = 2,
            sources = sources,
            fallback = fallback
        )

        assertEquals(listOf(2_000L, 3_000L), suffix.map { it.startMs })
        assertEquals(listOf(3_000L, 5_000L), suffix.map { it.endMs })
        assertTrue(suffix.zipWithNext().all { (left, right) -> left.endMs <= right.startMs })
    }
}
