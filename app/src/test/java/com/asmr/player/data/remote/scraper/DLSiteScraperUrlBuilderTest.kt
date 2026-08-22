package com.asmr.player.data.remote.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DLSiteScraperUrlBuilderTest {
    @Test
    fun buildDlsiteSearchUrls_includesPresaleFlagForPresaleOption() {
        val urls = buildDlsiteSearchUrls(
            keyword = "耳",
            page = 2,
            order = "trend",
            locale = "ja_JP",
            presaleOnly = true
        )

        assertEquals(2, urls.size)
        assertEquals(
            "https://www.dlsite.com/maniax/fsr/=/ana_flg/on/order/trend/work_type_category%5B0%5D/audio/keyword/%E8%80%B3/page/2",
            urls.first()
        )
        assertTrue(urls.all { it.contains("ana_flg/on") })
    }

    @Test
    fun buildDlsiteSearchUrls_usesExistingModernAndLegacyTemplatesForNormalSearch() {
        val urls = buildDlsiteSearchUrls(
            keyword = "耳",
            page = 2,
            order = "trend",
            locale = "ja_JP",
            presaleOnly = false
        )

        assertEquals(2, urls.size)
        assertTrue(urls.first().contains("order%5B0%5D/trend"))
        assertTrue(urls.first().contains("/keyword/%E8%80%B3/page/2"))
        assertTrue(urls.last().contains("/without_order/1/order/trend"))
        assertTrue(urls.none { it.contains("ana_flg/on") })
        assertTrue(urls.none { it.contains("age_category") })
        assertTrue(urls.none { it.contains("options%5B0%5D/CHI") })
    }

    @Test
    fun buildDlsiteSearchUrls_addsSubtitleAndGeneralPlusR15FiltersForNormalSearch() {
        val urls = buildDlsiteSearchUrls(
            keyword = "耳",
            page = 2,
            order = "trend",
            locale = "zh_CN",
            hasSubtitle = true,
            allAges = true
        )

        assertEquals(2, urls.size)
        assertTrue(urls.all {
            it.contains("age_category%5B0%5D/general/age_category%5B1%5D/r15")
        })
        assertTrue(urls.all {
            it.contains("options%5B0%5D/CHI/options%5B1%5D/CHI_HANS/options%5B2%5D/CHI_HANT")
        })
        assertTrue(urls.all { it.contains("work_type_category%5B0%5D/audio") })
        assertTrue(urls.none { it.contains("/JPN") })
    }

    @Test
    fun buildDlsiteSearchUrls_keepsSubtitleAndAgeFiltersIndependent() {
        val subtitleUrls = buildDlsiteSearchUrls(
            keyword = "",
            page = 1,
            order = "release_d",
            locale = "ja_JP",
            hasSubtitle = true
        )
        val allAgesUrls = buildDlsiteSearchUrls(
            keyword = "",
            page = 1,
            order = "release_d",
            locale = "ja_JP",
            allAges = true
        )

        assertTrue(subtitleUrls.all { it.contains("options%5B0%5D/CHI") })
        assertTrue(subtitleUrls.none { it.contains("age_category") })
        assertTrue(allAgesUrls.all { it.contains("age_category%5B0%5D/general") })
        assertTrue(allAgesUrls.none { it.contains("options%5B0%5D/CHI") })
    }

    @Test
    fun buildDlsiteSearchUrls_usesChineseTranslationListingWhenRequested() {
        val urls = buildDlsiteSearchUrls(
            keyword = "耳",
            page = 2,
            order = "price_d",
            locale = "zh_CN",
            chineseTranslatedOnly = true
        )

        assertEquals(1, urls.size)
        assertEquals(
            "https://www.dlsite.com/maniax/works/translation?langs%5B0%5D=CHI_HANS&work_type%5B0%5D=SOU&order=price_d&keyword=%E8%80%B3&page=2",
            urls.first()
        )
    }
}
