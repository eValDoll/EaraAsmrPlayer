package com.asmr.player.data.remote.scraper

import com.asmr.player.data.remote.dlsite.DlsiteProductInfoClient
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class DlsiteMetadataParserTest {
    @Test
    fun listReadsEachWorkFromTheVisibleTableAndUsesItsDiscountedYenPrice() {
        val doc = Jsoup.parse("""
            <html lang="zh-CN"><div id="search_result_list" class="loading_display"></div>
            <div id="search_result_list" class="loading_display_open"><table>
            <tr data-list_item_product_id="RJ123456"><td>
                <div class="work_thumb"><img :src="is_show ? '//img.dlsite.jp/work.jpg' : 'data:image/gif'"></div>
                <div class="work_name"><a href="/maniax/work/=/product_id/RJ123456.html">作品</a></div>
                <div class="maker_name"><a>社团</a><span class="author"><a>甲</a></span></div>
                <div class="work_price"><span class="work_price_base">8.50</span>
                    <div data-currency_price='{"USD":8.50,"JPY":1232}'></div></div>
                <div class="strike"><div data-currency_price='{"JPY":1760}'></div></div>
                <div class="search_tag"><a href="/genre/056/">治愈</a></div>
                <ul><li class="sales_date">发售日: 2026年10月7日</li>
                    <li class="work_dl">售出数: <span class="_dl_count_RJ123456">2,282</span></li>
                    <li class="work_rating"><div class="star_rating star_50">(27)</div></li></ul>
            </td></tr>
            <tr data-list_item_product_id="RJ789012"><td>
                <div class="work_name"><a href="/maniax/announce/=/product_id/RJ789012.html">预告</a></div>
                <div class="sales_date">预告开始日期: 2026年11月17日</div>
            </td></tr></table></div><div class="page_no"><strong>1</strong><a data-value="2">下一页</a><a data-value="89">最后一页</a></div>
            </html>
        """.trimIndent())

        val result = parseDlsiteSearchPage(doc, "zh_CN", 1)
        assertEquals(89, result.lastPage)
        assertEquals(listOf("RJ123456", "RJ789012"), result.items.map { it.rjCode })
        val (album, announce) = result.items
        assertEquals("作品", album.title)
        assertEquals("社团", album.circle)
        assertEquals("甲", album.cv)
        assertEquals("https://img.dlsite.jp/work.jpg", album.coverUrl)
        assertEquals(listOf("治愈"), album.tags)
        val tag = album.tagDetails.orEmpty().single()
        assertEquals("治愈", tag.names.orEmpty()["zh-CN"])
        assertEquals("dlsite:genre:056", tag.key)
        assertEquals(1232, album.priceJpy)
        assertEquals("2026-10-07", album.releaseDate)
        assertEquals(2282, album.dlCount)
        assertEquals(27, album.ratingCount)
        assertNull(album.ratingValue)
        assertEquals("", announce.releaseDate)
        assertEquals(0, announce.priceJpy)
        doc.selectFirst(".page_no")!!.html("""<a data-value="88">上一页</a><strong>89</strong>""")
        assertEquals(89, parseDlsiteSearchPage(doc, "zh_CN", 89).lastPage)
    }

    private fun workPage() = Jsoup.parse("""
        <html lang="en-US"><head>
        <script type="application/ld+json">{"@type":"WebSite"}</script>
        <script type="application/ld+json">{"@type":"Product","sku":"RJ123456",
            "offers":{"priceCurrency":"JPY","price":"1980"},
            "aggregateRating":{"ratingValue":"4.81","ratingCount":"1204"}}</script>
        <meta property="og:image" content="https://img.dlsite.jp/work.jpg">
        </head><body><h1 id="work_name">Work</h1><a href="/genre/999/">Trending</a>
        <a class="c-productInfo__makerName">Circle</a>
        <dl class="c-productInfoPc__list--outline">
            <dt class="c-productInfo__key">Release date</dt><dd class="c-productInfo__value">Oct/21/2026</dd>
            <dt class="c-productInfo__key">Voice Actors</dt><dd class="c-productInfo__value"><a>A</a> / <a>B</a></dd>
            <dt class="c-productInfo__key">Genre</dt><dd class="c-productInfo__value"><a href="/genre/056/">Healing</a></dd>
        </dl><dl><dt class="c-productStatsItemPc__term">Purchased :</dt><dd>7,675</dd></dl>
        </body></html>
    """.trimIndent())

    @Test
    fun detailUsesProductSchemaAndLimitsTagsToWorkMetadata() {
        val doc = workPage()
        assertTrue(hasDlsiteWorkMetadata(doc))
        val album = parseDlsiteAlbum("RJ123456", doc, "en_US")!!
        assertEquals(listOf("Healing"), album.tags)
        assertEquals("A, B", album.cv)
        assertEquals("Circle", album.circle)
        assertEquals("2026-10-21", album.releaseDate)
        assertEquals(1980, album.priceJpy)
        assertEquals(4.81, album.ratingValue!!, 0.0001)
        assertEquals(1204, album.ratingCount)
        assertEquals(7675, album.dlCount)
    }

    @Test
    fun detailReadsTraditionalChineseReleaseAndSalesLabels() {
        val doc = workPage()
        doc.selectFirst(".c-productInfo__key")!!.text("販賣日")
        doc.selectFirst(".c-productStatsItemPc__term")!!.text("銷售數量：")
        val album = parseDlsiteAlbum("RJ123456", doc, "zh_TW")!!
        assertEquals("2026-10-21", album.releaseDate)
        assertEquals(7675, album.dlCount)
    }

    @Test
    fun verifiedTranslationUsesItsOwnSaleAndRatingDataOnParentPage() {
        val json = """{"RJ789012":{"product_id":"RJ789012","translation_info":{"is_child":true,"parent_workno":"RJ123456"},
            "price":1320,"regist_date":"2026-11-01 00:00:00","rate_average_2dp":4.95,"rate_count":39,"dl_count":1410}}"""
        val translation = DlsiteProductInfoClient(OkHttpClient()).parseTranslationProduct("RJ789012", json)!!
        val album = parseDlsiteAlbum("RJ789012", workPage(), "en_US", translation)!!
        assertEquals("RJ789012", album.rjCode)
        assertEquals(listOf("Healing"), album.tags)
        assertEquals("2026-11-01", album.releaseDate)
        assertEquals(1320, album.priceJpy)
        assertEquals(4.95, album.ratingValue!!, 0.0001)
        assertEquals(39, album.ratingCount)
        assertEquals(1410, album.dlCount)
    }
}
