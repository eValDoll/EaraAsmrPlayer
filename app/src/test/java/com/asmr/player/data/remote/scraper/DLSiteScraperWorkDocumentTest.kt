package com.asmr.player.data.remote.scraper

import org.jsoup.Jsoup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DLSiteScraperWorkDocumentTest {
    @Test
    fun matchesDlsiteWorkDocument_rejectsRedirectToAnotherWork() {
        val doc = Jsoup.parse(
            "<h1 id='work_name'>Other work</h1>",
            "https://www.dlsite.com/maniax/work/=/product_id/RJ01718403.html?locale=zh_CN"
        )

        assertFalse(matchesDlsiteWorkDocument("RJ01706426", doc))
    }

    @Test
    fun matchesDlsiteWorkDocument_rejectsAnotherWorkInCanonicalUrl() {
        val doc = Jsoup.parse(
            """
            <link rel="canonical" href="/maniax/work/=/product_id/RJ01718403.html">
            <h1 id="work_name">Other work</h1>
            """.trimIndent(),
            "https://www.dlsite.com/maniax/work/=/product_id/RJ01706426.html"
        )

        assertFalse(matchesDlsiteWorkDocument("RJ01706426", doc))
    }

    @Test
    fun matchesDlsiteWorkDocument_rejectsConflictingOpenGraphUrl() {
        val doc = Jsoup.parse(
            """
            <link rel="canonical" href="/maniax/work/=/product_id/RJ01706426.html">
            <meta property="og:url" content="https://www.dlsite.com/maniax/work/=/product_id/RJ01718403.html">
            <h1 id="work_name">Other work</h1>
            """.trimIndent(),
            "https://www.dlsite.com/maniax/work/=/product_id/RJ01706426.html"
        )

        assertFalse(matchesDlsiteWorkDocument("RJ01706426", doc))
    }

    @Test
    fun matchesDlsiteWorkDocument_acceptsSameWorkAcrossLocalesAndStores() {
        val doc = Jsoup.parse(
            """
            <link rel="canonical" href="/home/announce/=/product_id/RJ01706426.html">
            <meta property="og:url" content="https://www.dlsite.com/maniax/work/=/product_id/RJ01706426.html?locale=ja_JP">
            <h1 id="work_name">Same work</h1>
            """.trimIndent(),
            "https://www.dlsite.com/home/work/=/product_id/RJ01706426.html?locale=zh_CN"
        )

        assertTrue(matchesDlsiteWorkDocument(" rj01706426 ", doc))
    }

    @Test
    fun matchesDlsiteWorkDocument_supportsOtherDlsiteWorkNumberPrefixes() {
        val doc = Jsoup.parse(
            "<meta property='og:url' content='/books/work/=/product_id/BJ02370869.html'>"
        )

        assertTrue(matchesDlsiteWorkDocument("BJ02370869", doc))
        assertFalse(matchesDlsiteWorkDocument("VJ01005620", doc))
    }

    @Test
    fun matchesDlsiteWorkDocument_keepsPagesWithoutValidIdentityDeclarations() {
        val doc = Jsoup.parse(
            """
            <link rel="canonical" href="not a valid url">
            <meta property="og:url" content="https://www.dlsite.com/?product_id=RJ01718403">
            <h1 id="work_name">Work</h1>
            <a href="/maniax/work/=/product_id/RJ01718403.html">Recommended work</a>
            """.trimIndent()
        )

        assertTrue(matchesDlsiteWorkDocument("RJ01706426", doc))
    }
}
