package com.asmr.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSemanticSegmenterTest {
    @Test
    fun `standalone punctuation is merged into surrounding subtitle`() {
        val result = SubtitleSemanticSegmenter.reflow(
            listOf(
                GeneratedSubtitle(1_000L, 1_200L, "あ"),
                GeneratedSubtitle(1_200L, 1_350L, "。"),
                GeneratedSubtitle(1_500L, 3_000L, "今日は眠れないんですね"),
                GeneratedSubtitle(3_000L, 3_150L, "。")
            )
        )

        assertEquals(
            listOf(
                GeneratedSubtitle(1_000L, 3_150L, "あ。今日は眠れないんですね")
            ),
            result
        )
    }

    @Test
    fun `short fragments are merged into a readable Japanese sentence`() {
        val result = SubtitleSemanticSegmenter.reflow(
            listOf(
                GeneratedSubtitle(0L, 300L, "今日"),
                GeneratedSubtitle(300L, 500L, "は"),
                GeneratedSubtitle(500L, 1_100L, "ゆっくり"),
                GeneratedSubtitle(1_100L, 1_600L, "休んで"),
                GeneratedSubtitle(1_600L, 2_200L, "ください"),
                GeneratedSubtitle(2_200L, 2_400L, "ね"),
                GeneratedSubtitle(2_400L, 2_500L, "。")
            )
        )

        assertEquals(
            listOf(
                GeneratedSubtitle(0L, 2_500L, "今日はゆっくり休んでくださいね")
            ),
            result
        )
    }

    @Test
    fun `question and exclamation endings are preserved`() {
        val result = SubtitleSemanticSegmenter.reflow(
            listOf(
                GeneratedSubtitle(0L, 1_500L, "聞こえますか？"),
                GeneratedSubtitle(1_600L, 3_000L, "びっくりしました！")
            )
        )

        assertEquals(
            listOf(
                GeneratedSubtitle(0L, 1_500L, "聞こえますか？"),
                GeneratedSubtitle(1_600L, 3_000L, "びっくりしました！")
            ),
            result
        )
    }

    @Test
    fun `only question and exclamation punctuation can remain at subtitle end`() {
        val result = SubtitleSemanticSegmenter.reflow(
            listOf(
                GeneratedSubtitle(0L, 1_000L, "そのままで大丈夫ですよ…"),
                GeneratedSubtitle(1_200L, 2_200L, "起きていますか？")
            )
        )

        assertEquals(
            listOf(
                GeneratedSubtitle(0L, 1_000L, "そのままで大丈夫ですよ"),
                GeneratedSubtitle(1_200L, 2_200L, "起きていますか？")
            ),
            result
        )
    }

    @Test
    fun `long subtitle is split at semantic punctuation with monotonic timing`() {
        val result = SubtitleSemanticSegmenter.reflow(
            listOf(
                GeneratedSubtitle(
                    startMs = 0L,
                    endMs = 12_000L,
                    text = "今日は少し長めに耳元で話していきますね、ゆっくり呼吸を合わせてください、眠くなったらそのまま目を閉じて大丈夫ですよ。"
                )
            )
        )

        assertTrue(result.size > 1)
        assertEquals(0L, result.first().startMs)
        assertEquals(12_000L, result.last().endMs)
        result.zipWithNext { left, right ->
            assertTrue(left.endMs <= right.startMs)
        }
        assertTrue(result.all { it.text.isNotBlank() && it.endMs > it.startMs })
    }
}
