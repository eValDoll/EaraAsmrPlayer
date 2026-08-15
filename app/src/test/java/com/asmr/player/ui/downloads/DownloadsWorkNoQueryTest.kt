package com.asmr.player.ui.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsWorkNoQueryTest {
    @Test
    fun normalizesSupportedWorkNumbersAndLegacyDigitOnlyInput() {
        assertEquals("BJ02370869", normalizeDownloadWorkNoQuery(" bj02370869 "))
        assertEquals("VJ01005620", normalizeDownloadWorkNoQuery("VJ 01005620"))
        assertEquals("RJ123456", normalizeDownloadWorkNoQuery("123456"))
    }

    @Test
    fun extractsWorkNumberFromDlsiteUrl() {
        assertEquals(
            "BJ02370869",
            normalizeDownloadWorkNoQuery("https://www.dlsite.com/books/work/=/product_id/BJ02370869.html")
        )
    }
}
