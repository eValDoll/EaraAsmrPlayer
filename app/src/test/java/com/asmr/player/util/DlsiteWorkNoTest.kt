package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DlsiteWorkNoTest {
    @Test
    fun extractWorkNo_supportsKnownPrefixes() {
        assertEquals("RJ01522140", DlsiteWorkNo.extractWorkNo("RJ01522140"))
        assertEquals("BJ02370869", DlsiteWorkNo.extractWorkNo("BJ02370869"))
        assertEquals("VJ01005620", DlsiteWorkNo.extractWorkNo("VJ01005620"))
    }

    @Test
    fun extractWorkNo_fromUrl() {
        assertEquals(
            "BJ02370869",
            DlsiteWorkNo.extractWorkNo("https://www.dlsite.com/books/work/=/product_id/BJ02370869.html")
        )
    }

    @Test
    fun extractWorkNo_normalizesCaseAndOptionalSpace() {
        assertEquals("RJ123", DlsiteWorkNo.extractWorkNo("rj123"))
        assertEquals("BJ02370869", DlsiteWorkNo.extractWorkNo("bj 02370869"))
    }

    @Test
    fun extractWorkNo_blank() {
        assertEquals("", DlsiteWorkNo.extractWorkNo(""))
    }

    @Test
    fun extractWorkNo_ignoresUnknownOrEmbeddedCodes() {
        assertEquals("", DlsiteWorkNo.extractWorkNo("MRJ123456"))
        assertEquals("", DlsiteWorkNo.extractWorkNo("RJ123456ABC"))
        assertEquals("", DlsiteWorkNo.extractWorkNo("AJ123456"))
    }

    @Test
    fun normalizeWorkNo_requiresEntireInputAndMinimumDigitCount() {
        assertEquals("VJ01005620", DlsiteWorkNo.normalizeWorkNo(" vj01005620 ", minimumDigits = 6))
        assertEquals("", DlsiteWorkNo.normalizeWorkNo("作品 BJ02370869", minimumDigits = 6))
        assertEquals("", DlsiteWorkNo.normalizeWorkNo("BJ123", minimumDigits = 6))
        assertEquals("", DlsiteWorkNo.extractWorkNo("BJ123", minimumDigits = 6))
        assertEquals(
            "RJ123456",
            DlsiteWorkNo.extractWorkNo("忽略 BJ123，使用 RJ123456", minimumDigits = 6)
        )
    }

    @Test
    fun normalizeCandidates_dedupAndUppercase() {
        val out = DlsiteWorkNo.normalizeCandidates(listOf(" rj1 ", "RJ1", "", "bj2", "invalid"))
        assertEquals(listOf("RJ1", "BJ2"), out)
    }
}
