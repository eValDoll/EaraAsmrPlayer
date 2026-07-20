package com.asmr.player.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningRecordRepositoryTest {

    @Test
    fun parseListeningArtistMeta_splitsCircleAndCv() {
        val meta = parseListeningArtistMeta("社团A / CVB")
        assertEquals("社团A", meta.circle)
        assertEquals("CVB", meta.cv)
    }

    @Test
    fun parseListeningArtistMeta_treatsSingleValueAsCvFallback() {
        val meta = parseListeningArtistMeta("CVB")
        assertEquals("", meta.circle)
        assertEquals("CVB", meta.cv)
    }
}
