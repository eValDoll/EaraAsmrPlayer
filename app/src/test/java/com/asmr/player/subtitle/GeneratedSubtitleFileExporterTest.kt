package com.asmr.player.subtitle

import com.asmr.player.data.local.db.entities.SubtitleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedSubtitleFileExporterTest {
    @Test
    fun `subtitle file uses audio base name`() {
        assertEquals("track 01.lrc", subtitleFileName("track 01.flac"))
        assertEquals("voice.part01.lrc", subtitleFileName("voice.part01.m4a"))
        assertEquals("voice.lrc", subtitleFileName("voice"))
    }

    @Test
    fun `renders utf8 chinese lrc with normalized timestamps`() {
        val rendered = renderChineseLrc(
            listOf(
                SubtitleEntity(
                    trackId = 1L,
                    startMs = 1_234L,
                    endMs = 65_678L,
                    text = "早上好",
                    japaneseText = "おはよう"
                ),
                SubtitleEntity(
                    trackId = 1L,
                    startMs = 3_600_000L,
                    endMs = 3_601_000L,
                    text = "同一句",
                    japaneseText = "同一句"
                )
            )
        )

        assertEquals(
            "[00:01.234]早上好\r\n" +
                "[60:00.000]同一句\r\n",
            rendered
        )
    }

    @Test
    fun `omits blank captions`() {
        val rendered = renderChineseLrc(
            listOf(
                SubtitleEntity(trackId = 1L, startMs = 0L, endMs = 100L, text = ""),
                SubtitleEntity(trackId = 1L, startMs = 100L, endMs = 200L, text = "字幕")
            )
        )

        assertEquals(
            "[00:00.100]字幕\r\n",
            rendered
        )
    }
}
