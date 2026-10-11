package com.asmr.player.util

import org.junit.Assert.*
import org.junit.Test

class AlbumWorkNoTest {
    @Test fun dmmIdentitiesRemainSeparateFromNumericAndDlsiteWorks() {
        assertEquals("UND353674", AlbumWorkNo.normalizeWorkNo(" und353674 ", minimumDigits = 6))
        assertEquals("UND353674", AlbumWorkNo.extractWorkNo("web://rj/UND353674"))
        assertTrue(AlbumWorkNo.isJapaneseAsmrOnlyWork("UND353674"))
        assertTrue(AlbumWorkNo.isJapaneseAsmrOnlyWork("UN353674"))
        assertFalse(AlbumWorkNo.isJapaneseAsmrOnlyWork("RJ353674"))
        assertEquals("UN353674", AlbumWorkNo.normalizeWorkNo("un353674"))
        assertEquals("RJ353674", AlbumWorkNo.normalizeWorkNo("rj353674"))
        assertEquals("", AlbumWorkNo.normalizeWorkNo("UND353674", minimumDigits = 7))
        assertEquals("", DlsiteWorkNo.normalizeWorkNo("UND353674"))
        assertEquals("", DlsiteWorkNo.extractWorkNo("UND353674"))
        for (invalid in listOf("d_353674", "UND123", "UND353674ABC", "AUND353674")) {
            assertEquals(invalid, "", AlbumWorkNo.normalizeWorkNo(invalid))
            assertEquals(invalid, "", AlbumWorkNo.extractWorkNo(invalid))
        }
    }

    @Test fun numericIdentitiesRemainSeparateFromDlsite() {
        assertEquals("UN124393", AlbumWorkNo.normalizeWorkNo("un124393"))
        assertEquals("UN124393", AlbumWorkNo.extractWorkNo("web://rj/UN124393"))
        assertEquals("RJ124393", AlbumWorkNo.normalizeWorkNo("RJ124393"))
        assertEquals("", DlsiteWorkNo.normalizeWorkNo("UN124393"))
        assertEquals("", DlsiteWorkNo.extractWorkNo("UN124393"))
        for (invalid in listOf("124393", "UN123", "UN124393ABC", "AUN124393")) {
            assertEquals(invalid, "", AlbumWorkNo.normalizeWorkNo(invalid))
            assertEquals(invalid, "", AlbumWorkNo.extractWorkNo(invalid))
        }
    }
}
