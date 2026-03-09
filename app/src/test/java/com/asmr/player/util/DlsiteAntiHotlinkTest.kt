package com.asmr.player.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteAntiHotlinkTest {
    @Test
    fun headersForImageUrl_dlsiteHosts_returnsHeaders() {
        assertTrue(DlsiteAntiHotlink.headersForImageUrl("https://www.dlsite.com/").isNotEmpty())
        assertTrue(DlsiteAntiHotlink.headersForImageUrl("https://img.dlsite.jp/modpub/images2/work/doujin/RJ123/RJ123456_img_main.jpg").isNotEmpty())
        assertTrue(DlsiteAntiHotlink.headersForImageUrl("https://byteair.volces.com/aaa/bbb.jpg").isNotEmpty())
        assertTrue(DlsiteAntiHotlink.headersForImageUrl("https://cdn.example.com/modpub/images2/work/doujin/RJ123/RJ123456_img_main.jpg").isNotEmpty())
    }

    @Test
    fun headersForImageUrl_nonDlsiteHost_returnsEmpty() {
        assertTrue(DlsiteAntiHotlink.headersForImageUrl("https://example.com/a.jpg").isEmpty())
        assertTrue(DlsiteAntiHotlink.headersForImageUrl("not a url").isEmpty())
    }

    @Test
    fun isDlsiteImageHost_matchesExpected() {
        assertTrue(DlsiteAntiHotlink.isDlsiteImageHost("www.dlsite.com"))
        assertTrue(DlsiteAntiHotlink.isDlsiteImageHost("img.dlsite.jp"))
        assertTrue(DlsiteAntiHotlink.isDlsiteImageHost("byteair.volces.com"))
        assertFalse(DlsiteAntiHotlink.isDlsiteImageHost("example.com"))
    }
}
