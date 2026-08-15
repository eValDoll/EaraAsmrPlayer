package com.asmr.player.subtitle

import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleScriptTextCodecTest {

    @Test
    fun decode_utf8WithoutBom() {
        val text = "こんにちは、おやすみなさい"
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertEquals(text, SubtitleScriptTextCodec.decode(bytes))
    }

    @Test
    fun decode_utf8WithBom() {
        val text = "こんにちは"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(Charsets.UTF_8)
        assertEquals(text, SubtitleScriptTextCodec.decode(bytes))
    }

    @Test
    fun decode_shiftJis() {
        val text = "こんにちは、おやすみなさい"
        val bytes = text.toByteArray(Charset.forName("Shift_JIS"))
        assertEquals(text, SubtitleScriptTextCodec.decode(bytes))
    }

    @Test
    fun decode_utf16leWithBom() {
        val text = "あいうえお、おやすみ"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, SubtitleScriptTextCodec.decode(bytes))
    }

    @Test
    fun decode_utf16beWithBom() {
        val text = "あいうえお、おやすみ"
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            text.toByteArray(Charsets.UTF_16BE)
        assertEquals(text, SubtitleScriptTextCodec.decode(bytes))
    }

    @Test
    fun decode_emptyReturnsEmpty() {
        assertEquals("", SubtitleScriptTextCodec.decode(ByteArray(0)))
    }
}
