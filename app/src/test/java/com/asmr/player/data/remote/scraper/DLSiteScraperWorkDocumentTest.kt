package com.asmr.player.data.remote.scraper

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun matchesDlsiteWorkDocument_acceptsVerifiedTranslationChildOnParentPage() {
        val doc = translationDocument()

        assertEquals("RJ01287594", dlsiteTranslationParentWorkNo(" rj01287595 ", doc))
        assertFalse(matchesDlsiteWorkDocument("RJ01287595", doc))
        assertTrue(matchesDlsiteWorkDocument("RJ01287595", doc, "RJ01287594"))
        assertFalse(matchesDlsiteWorkDocument("RJ01287595", doc, "RJ01718403"))
    }

    @Test
    fun matchesDlsiteWorkDocument_acceptsVerifiedChildInCanonicalAndOpenGraphUrls() {
        val doc = Jsoup.parse(
            """
            <link rel="canonical" href="/maniax/work/=/product_id/RJ01287595.html">
            <meta property="og:url" content="https://www.dlsite.com/maniax/work/=/product_id/RJ01287594.html/?translation=RJ01287595">
            <h1 id="work_name">Translation</h1>
            """.trimIndent(),
            "https://www.dlsite.com/maniax/work/=/product_id/RJ01287594.html/?translation=RJ01287595"
        )

        assertTrue(matchesDlsiteWorkDocument("RJ01287595", doc, "RJ01287594"))
    }

    @Test
    fun matchesDlsiteWorkDocument_rejectsParentWhenAnotherTranslationIsSelected() {
        val doc = translationDocument()

        assertNull(dlsiteTranslationParentWorkNo("RJ01287594", doc))
        assertFalse(matchesDlsiteWorkDocument("RJ01287594", doc))
        assertFalse(matchesDlsiteWorkDocument("RJ01287594", doc, "RJ01287594"))
    }

    @Test
    fun matchesDlsiteWorkDocument_rejectsUnrelatedCanonicalOnVerifiedTranslationPage() {
        val doc = translationDocument()
        doc.selectFirst("link[rel=canonical]")!!.attr(
            "href",
            "https://www.dlsite.com/maniax/work/=/product_id/RJ01718403.html"
        )

        assertFalse(matchesDlsiteWorkDocument("RJ01287595", doc, "RJ01287594"))
    }

    @Test
    fun matchesDlsiteWorkDocument_rejectsUnrelatedOpenGraphOnVerifiedTranslationPage() {
        val doc = translationDocument()
        doc.selectFirst("meta[property=og:url]")!!.attr(
            "content",
            "https://www.dlsite.com/maniax/work/=/product_id/RJ01718403.html/?translation=RJ01287595"
        )

        assertFalse(matchesDlsiteWorkDocument("RJ01287595", doc, "RJ01287594"))
    }

    @Test
    fun matchesDlsiteWorkDocument_rejectsEchoedTranslationQueryWithoutVerifiedParentRelationship() {
        val doc = translationDocument(parentWorkNo = "RJ01718403")

        assertEquals("RJ01718403", dlsiteTranslationParentWorkNo("RJ01287595", doc))
        assertFalse(matchesDlsiteWorkDocument("RJ01287595", doc))
        assertFalse(matchesDlsiteWorkDocument("RJ01287595", doc, "RJ01287594"))
    }

    @Test
    fun matchesDlsiteWorkDocument_decodesTranslationParameterOnce() {
        val doc = translationDocument(query = "locale=zh_CN&%74ranslation=%52%4A01287595")

        assertEquals("RJ01287594", dlsiteTranslationParentWorkNo("RJ01287595", doc))
        assertTrue(matchesDlsiteWorkDocument("RJ01287595", doc, "RJ01287594"))
    }

    @Test
    fun matchesDlsiteWorkDocument_rejectsAmbiguousOrInvalidTranslationParameters() {
        listOf(
            "translation=RJ01287595&translation=RJ01287595",
            "translation=RJ01287595&translation=RJ01718403",
            "translation=RJ01287595&%74ranslation=RJ01287595",
            "translation=",
            "translation",
            "translation=invalid",
            "translation=RJ01287595%26translation%3DRJ01718403",
            "translation=%2552J01287595",
            "translation=%ZZ"
        ).forEach { query ->
            val doc = translationDocument(query = query)

            assertNull(query, dlsiteTranslationParentWorkNo("RJ01287595", doc))
            assertFalse(query, matchesDlsiteWorkDocument("RJ01287595", doc, "RJ01287594"))
        }
    }

    @Test
    fun matchesDlsiteWorkDocument_rejectsInvalidTranslationEvenWhenProductPathMatches() {
        val doc = translationDocument(parentWorkNo = "RJ01287595", query = "translation=invalid")

        assertFalse(matchesDlsiteWorkDocument("RJ01287595", doc))
    }

    @Test
    fun matchesDlsiteWorkDocument_doesNotTreatUnrelatedQueryParametersAsTranslationSelection() {
        listOf(
            "utm_content=RJ01287595",
            "translation_id=RJ01287595",
            "Translation=RJ01287595",
            "redirect=translation%3DRJ01287595",
            "locale=zh_CN#translation=RJ01287595"
        ).forEach { query ->
            val doc = translationDocument(query = query)

            assertNull(query, dlsiteTranslationParentWorkNo("RJ01287595", doc))
            assertFalse(query, matchesDlsiteWorkDocument("RJ01287595", doc, "RJ01287594"))
        }
    }

    @Test
    fun matchesDlsiteWorkDocument_doesNotUseAlternateOrRecommendedTranslationLinksAsIdentity() {
        val doc = Jsoup.parse(
            """
            <link rel="alternate" hreflang="zh-CN" href="/maniax/work/=/product_id/RJ01287594.html/?translation=RJ01287595">
            <h1 id="work_name">Parent</h1>
            <a href="/maniax/work/=/product_id/RJ01287595.html">Recommended translation</a>
            """.trimIndent(),
            "https://www.dlsite.com/maniax/work/=/product_id/RJ01287594.html"
        )

        assertNull(dlsiteTranslationParentWorkNo("RJ01287595", doc))
        assertFalse(matchesDlsiteWorkDocument("RJ01287595", doc, "RJ01287594"))
    }

    private fun translationDocument(
        parentWorkNo: String = "RJ01287594",
        query: String = "translation=RJ01287595"
    ) = Jsoup.parse(
        """
        <link rel="canonical" href="https://www.dlsite.com/maniax/work/=/product_id/$parentWorkNo.html">
        <meta property="og:url" content="https://www.dlsite.com/maniax/work/=/product_id/$parentWorkNo.html">
        <h1 id="work_name">Translation</h1>
        """.trimIndent(),
        "https://www.dlsite.com/maniax/work/=/product_id/$parentWorkNo.html/?$query"
    )
}
