package com.asmr.player.data.remote.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DLSiteScraperUrlBuilderTest {
    @Test
    fun buildDlsiteSearchUrls_usesDedicatedUnsortedPresaleRoute() {
        val urls = buildDlsiteSearchUrls(
            keyword = "耳",
            page = 2,
            order = "trend",
            locale = "ja_JP",
            presaleOnly = true
        )

        assertEquals(1, urls.size)
        assertEquals(
            "https://www.dlsite.com/maniax/fsr/=/ana_flg/on/work_type%5B0%5D/SOU/" +
                "keyword/%E8%80%B3/page/2/?locale=ja_JP",
            urls.first()
        )
        assertTrue(urls.single().contains("ana_flg/on"))
        assertTrue(!urls.single().contains("/order/"))
        assertTrue(urls.single().endsWith("?locale=ja_JP"))
    }

    @Test
    fun buildDlsiteSearchUrls_omitsFirstPageSegmentForPresaleLandingPage() {
        val url = buildDlsiteSearchUrls(
            keyword = "",
            page = 1,
            order = "price_d",
            locale = "ja_JP",
            presaleOnly = true
        ).single()

        assertEquals(
            "https://www.dlsite.com/maniax/fsr/=/ana_flg/on/work_type%5B0%5D/SOU/?locale=ja_JP",
            url
        )
        assertTrue(!url.contains("/order/"))
        assertTrue(!url.contains("/page/1"))
    }

    @Test
    fun buildDlsiteSearchUrls_usesLocaleOnlyForPageLanguageInNormalSearch() {
        val urls = buildDlsiteSearchUrls(
            keyword = "耳",
            page = 2,
            order = "trend",
            locale = "ja_JP",
            presaleOnly = false
        )

        assertEquals(1, urls.size)
        assertEquals(
            "https://www.dlsite.com/maniax/fsr/=/work_category%5B0%5D/doujin/order/trend/" +
                "work_type%5B0%5D/SOU/keyword/%E8%80%B3/page/2/from/left_pain.work_type/?locale=ja_JP",
            urls.single()
        )
        assertTrue(urls.single().contains("/order/trend/"))
        assertTrue(urls.single().contains("/keyword/%E8%80%B3/page/2"))
        assertTrue(!urls.single().contains("/language/"))
        assertTrue(!urls.single().contains("without_order"))
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

        assertEquals(1, urls.size)
        assertTrue(urls.single().let {
            it.contains("age_category%5B0%5D/general/age_category%5B1%5D/r15")
        })
        assertTrue(urls.single().let {
            it.contains("options%5B0%5D/CHI/options%5B1%5D/CHI_HANS/options%5B2%5D/CHI_HANT")
        })
        assertTrue(urls.single().contains("work_type%5B0%5D/SOU"))
        assertTrue(!urls.single().contains("/language/"))
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
    fun buildDlsiteSearchUrls_usesChineseWorksFilterWhenRequested() {
        val urls = buildDlsiteSearchUrls(
            keyword = "",
            page = 1,
            order = "trend",
            locale = "ja_JP",
            chineseTranslatedOnly = true
        )

        assertEquals(1, urls.size)
        assertEquals(
            "https://www.dlsite.com/maniax/fsr/=/work_category%5B0%5D/doujin/order/trend/work_type_category%5B0%5D/audio/options%5B0%5D/CHI/options%5B1%5D/CHI_HANS/options%5B2%5D/CHI_HANT/?locale=zh_CN",
            urls.first()
        )
    }

    @Test
    fun buildDlsiteSearchUrls_changesOnlyLocaleQueryForNormalSearch() {
        val japaneseUrls = buildDlsiteSearchUrls(
            keyword = "",
            page = 1,
            order = "trend",
            locale = "ja_JP"
        )
        val chineseUrls = buildDlsiteSearchUrls(
            keyword = "",
            page = 1,
            order = "trend",
            locale = "zh_CN"
        )

        assertEquals(
            japaneseUrls.map { it.removeSuffix("?locale=ja_JP") },
            chineseUrls.map { it.removeSuffix("?locale=zh_CN") }
        )
        assertTrue(chineseUrls.none { it.contains("/language/") })
        assertTrue(chineseUrls.all { it.endsWith("?locale=zh_CN") })
    }

    @Test
    fun buildDlsiteSearchUrls_changesOnlyOrderSegmentWhenSorting() {
        val trendUrl = buildDlsiteSearchUrls(
            keyword = "",
            page = 1,
            order = "trend",
            locale = "zh_CN"
        ).single()
        val releaseUrl = buildDlsiteSearchUrls(
            keyword = "",
            page = 1,
            order = "release_d",
            locale = "zh_CN"
        ).single()

        assertEquals(
            trendUrl.replace("/order/trend/", "/order/release_d/"),
            releaseUrl
        )
        assertTrue(releaseUrl.endsWith("?locale=zh_CN"))
    }

    @Test
    fun buildDlsiteSearchUrls_keepsChineseWorksLocaleAndAppendsKeywordAndPage() {
        val urls = buildDlsiteSearchUrls(
            keyword = "耳",
            page = 2,
            order = "price_d",
            locale = "zh_TW",
            chineseTranslatedOnly = true
        )

        assertEquals(
            "https://www.dlsite.com/maniax/fsr/=/work_category%5B0%5D/doujin/order/price_d/work_type_category%5B0%5D/audio/options%5B0%5D/CHI/options%5B1%5D/CHI_HANS/options%5B2%5D/CHI_HANT/keyword/%E8%80%B3/page/2/?locale=zh_CN",
            urls.single()
        )
    }
}
