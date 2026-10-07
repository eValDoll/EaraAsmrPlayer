package com.asmr.player.util

import org.junit.Assert.*
import org.junit.Test

class AlbumWorkNoTest {
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
