package com.asmr.player.data.remote.crawler

import org.junit.Assert.assertEquals
import org.junit.Test

class AsmrOneSiteSelectionTest {
    @Test
    fun selectedAsmrOneBackupSite_usesOnlyConfiguredDomain() {
        assertEquals(100, selectedAsmrOneBackupSite(100))
        assertEquals(200, selectedAsmrOneBackupSite(200))
        assertEquals(300, selectedAsmrOneBackupSite(300))
    }

    @Test
    fun selectedAsmrOneBackupSite_defaultsTo200ForUnexpectedValue() {
        assertEquals(200, selectedAsmrOneBackupSite(0))
    }
}
