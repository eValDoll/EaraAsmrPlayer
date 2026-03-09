package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {
    @Test
    fun mergeLyrics_sameContent_doesNotDuplicateLines() {
        val l1 = listOf(
            SubtitleEntry(0, 1000, "hello"),
            SubtitleEntry(1000, 2000, "world")
        )
        val l2 = listOf(
            SubtitleEntry(0, 1000, "hello"),
            SubtitleEntry(1000, 2000, "world")
        )

        val merged = SubtitleParser.mergeLyrics(l1, l2)
        assertEquals(listOf("hello", "world"), merged.map { it.text })
    }

    @Test
    fun mergeLyrics_differentContent_keepsTwoLines() {
        val l1 = listOf(SubtitleEntry(0, 1000, "hello"))
        val l2 = listOf(SubtitleEntry(0, 1000, "こんにちは"))

        val merged = SubtitleParser.mergeLyrics(l1, l2)
        assertEquals(1, merged.size)
        assertTrue(merged[0].text.contains("\n"))
    }

    @Test
    fun parseLrc_dedupesExactDuplicateTimestamps() {
        val content = """
            [00:00.00]a
            [00:00.00]a
            [00:05.00]b
        """.trimIndent()

        val parsed = SubtitleParser.parseText("lrc", content)
        assertEquals(2, parsed.size)
        assertEquals("a", parsed[0].text)
        assertEquals("b", parsed[1].text)
    }

    @Test
    fun parseVtt_dlsiteJsonWebvtt_convertsAndParses() {
        val json = """
            {
              "webvtt": [
                {"start_time":"00:00:01.000","end_time":"00:00:02.000","subtitles":["第一行"]},
                {"start_time":"00:00:02.000","end_time":"00:00:03.500","subtitles":["第二行","Second line"]}
              ]
            }
        """.trimIndent()

        val parsed = SubtitleParser.parseText("vtt", json)
        assertEquals(2, parsed.size)
        assertEquals("第一行", parsed[0].text)
        assertEquals("第二行\nSecond line", parsed[1].text)
        assertEquals(1000L, parsed[0].startMs)
        assertEquals(2000L, parsed[0].endMs)
        assertEquals(2000L, parsed[1].startMs)
        assertEquals(3500L, parsed[1].endMs)
    }
}
