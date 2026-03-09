package com.asmr.player.data.remote.scraper

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteRecommendHtmlParserTest {
    @Test
    fun parse_imgWithFallbackCandidates_picksJpgAndNormalizesScheme() {
        val html = """
            <div class="recommend_work_item">
              <a id="_link_RJ01499589" href="https://www.dlsite.com/maniax/work/=/product_id/RJ01499589.html" title="t">
                <img-with-fallback :candidates="['//img.dlsite.jp/resize/images2/work/doujin/RJ01500000/RJ01499589_img_main_240x240.webp','//img.dlsite.jp/resize/images2/work/doujin/RJ01500000/RJ01499589_img_main_240x240.jpg']"></img-with-fallback>
              </a>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://www.dlsite.com/maniax/load/recommend/detail/v2/=/type/maker_works/")
        val works = DlsiteRecommendHtmlParser.parse(doc)

        assertEquals(1, works.size)
        assertEquals("RJ01499589", works.first().rjCode)
        assertTrue(works.first().coverUrl.startsWith("https://"))
        assertTrue(works.first().coverUrl.endsWith(".jpg"))
    }

    @Test
    fun extractCoverUrl_imgSrc_normalizesScheme() {
        val html = """
            <a href="/maniax/work/=/product_id/RJ01234567.html">
              <img src="//img.dlsite.jp/resize/images2/work/doujin/RJ01300000/RJ01234567_img_main_240x240.webp" />
            </a>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://www.dlsite.com/maniax/load/recommend/v2/=/type/viewsales2/product_id/RJ00000000.html")
        val a = doc.selectFirst("a")!!
        val url = DlsiteRecommendHtmlParser.extractCoverUrl(a, doc.baseUri())
        assertTrue(url.startsWith("https://"))
        assertTrue(url.endsWith(".webp"))
    }
}

