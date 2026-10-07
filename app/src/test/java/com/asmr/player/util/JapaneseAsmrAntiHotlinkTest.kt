package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseAsmrAntiHotlinkTest {
    @Test
    fun coverHostsUseTheSiteReferer() {
        assertEquals(JapaneseAsmrAntiHotlink.REFERER,
            JapaneseAsmrAntiHotlink.headersForImageUrl("https://pic.weeabo0.xyz/RJ01728295_img_main.jpg")["Referer"])
        assertEquals(JapaneseAsmrAntiHotlink.REFERER,
            JapaneseAsmrAntiHotlink.headersForImageUrl("https://japaneseasmr.com/wp-content/uploads/cover.jpg")["Referer"])
    }

    @Test
    fun unrelatedAndLookalikeHostsKeepTheirOwnHeaders() {
        for (url in listOf("not a url", "/covers/local.jpg", "https://pic.weeabo0.xyz.example.com/a.jpg",
            "https://example.com/japaneseasmr.com/a.jpg", "https://img.dlsite.jp/a.jpg")) {
            assertTrue(JapaneseAsmrAntiHotlink.headersForImageUrl(url).isEmpty())
        }
    }
}
