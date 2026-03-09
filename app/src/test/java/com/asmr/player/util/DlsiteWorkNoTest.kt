package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DlsiteWorkNoTest {
    @Test
    fun extractRjCode_plain() {
        assertEquals("RJ01522140", DlsiteWorkNo.extractRjCode("RJ01522140"))
    }

    @Test
    fun extractRjCode_fromUrl() {
        assertEquals(
            "RJ01522140",
            DlsiteWorkNo.extractRjCode("https://play.dlsite.com/work/RJ01522140/tree")
        )
    }

    @Test
    fun extractRjCode_caseInsensitive() {
        assertEquals("RJ123", DlsiteWorkNo.extractRjCode("rj123"))
    }

    @Test
    fun extractRjCode_blank() {
        assertEquals("", DlsiteWorkNo.extractRjCode(""))
    }

    @Test
    fun normalizeCandidates_dedupAndUppercase() {
        val out = DlsiteWorkNo.normalizeCandidates(listOf(" rj1 ", "RJ1", "", "rj2"))
        assertEquals(listOf("RJ1", "RJ2"), out)
    }
}
