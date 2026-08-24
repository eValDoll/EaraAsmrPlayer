package com.asmr.player.data.remote.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class DlsiteCookieUtilTest {
    @Test
    fun buildDlsiteCookieHeader_replacesStoredLocaleAndAdultCheckCookies() {
        val header = buildDlsiteCookieHeader(
            baseCookie = "login_token=abc; locale=zh_CN; adultchecked=0; preference=grid",
            locale = "ja_JP"
        )

        assertEquals(
            "login_token=abc; preference=grid; locale=ja_JP; adultchecked=1",
            header
        )
    }
}
