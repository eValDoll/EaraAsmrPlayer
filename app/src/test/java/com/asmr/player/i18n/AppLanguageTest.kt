package com.asmr.player.i18n

import com.asmr.player.LocalizedRobolectricTest
import com.asmr.player.LocalizedTestApplication
import com.asmr.player.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@Config(application = LocalizedTestApplication::class, sdk = [32])
class AppLanguageTest : LocalizedRobolectricTest() {
    @After fun restoreSystemLanguage() { AppLanguage.select("") }

    @Test fun explicitLanguageRefreshesResourcesAndMetadataTogether() {
        AppLanguage.select("en")
        assertEquals("Settings", appString(R.string.ui_settings))
        assertEquals("en_US", AppLanguage.metadataLocale())
        AppLanguage.select("zh-CN")
        assertEquals("设置", appString(R.string.ui_settings))
        assertEquals("zh_CN", AppLanguage.metadataLocale())
    }

    @Test fun unsupportedSystemLanguageFallsBackToEnglish() {
        AppLanguage.select("")
        RuntimeEnvironment.setQualifiers("+fr")
        AppLanguage.refresh()
        assertEquals("Settings", appString(R.string.ui_settings))
        assertEquals("en_US", AppLanguage.metadataLocale())
    }
}
