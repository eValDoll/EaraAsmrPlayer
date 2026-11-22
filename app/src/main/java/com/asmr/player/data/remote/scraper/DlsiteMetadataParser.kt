package com.asmr.player.data.remote.scraper

import com.asmr.player.data.remote.dlsite.DlsiteTranslationProduct
import com.asmr.player.domain.model.Album
import com.asmr.player.util.DlsiteWorkNo
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private val releaseLabels = setOf("販売日", "発売日", "发售日", "發售日", "販賣日", "Release date")
private val salesLabels = setOf("售出数", "售出數", "銷售數量", "販売数", "DL数", "売上数", "Sales", "Downloads", "Purchased")
private val yearFirstDate = Regex("""(\d{4})\D+(\d{1,2})\D+(\d{1,2})""")
private val integerInText = Regex("""[\d,]+""")
private val quotedImageUrl = Regex("""['"](https?://[^'"]+|//[^'"]+)['"]""")

private fun parseReleaseDate(text: String): String {
    val date = yearFirstDate.find(text) ?: return parseEnglishReleaseDate(text).orEmpty()
    val (year, month, day) = date.destructured
    return "$year-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
}

private fun String.firstInteger(): Int =
    integerInText.find(this)?.value?.replace(",", "")?.toIntOrNull() ?: 0

internal fun parseDlsiteSearchPage(doc: Document, locale: String?, page: Int): DlsiteSearchResult {
    val items = doc.select("#search_result_list.loading_display_open tr[data-list_item_product_id]").mapNotNull { row ->
        val workNo = DlsiteWorkNo.normalizeWorkNo(row.attr("data-list_item_product_id"))
        val titleLink = row.selectFirst(".work_name a") ?: return@mapNotNull null
        if (workNo.isBlank()) return@mapNotNull null

        val tags = parseDlsiteTags(row.select(".search_tag a"))
        val image = row.selectFirst(".work_thumb img")
        val cover = image?.let { quotedImageUrl.find(it.attr(":src"))?.groupValues?.get(1) ?: it.absUrl("src") }.orEmpty()
        val releaseDate = if ("/announce/" in titleLink.attr("href")) {
            ""
        } else {
            parseReleaseDate(row.selectFirst(".sales_date")?.text().orEmpty())
        }
        Album(
            title = titleLink.text(),
            path = "",
            workId = workNo,
            rjCode = workNo,
            circle = row.selectFirst(".maker_name a")?.text().orEmpty(),
            cv = row.select(".author a").eachText().distinct().joinToString(", "),
            tags = tags.map { it.originalName },
            tagDetails = tags,
            metadataLocale = locale,
            coverUrl = if (cover.startsWith("//")) "https:$cover" else cover.takeUnless { it.startsWith("data:") }.orEmpty(),
            priceJpy = row.selectFirst(".work_price [data-currency_price]")?.attr("data-currency_price")
                ?.let(::parseJsonObject)?.get("JPY")?.asInt ?: 0,
            releaseDate = releaseDate,
            dlCount = row.selectFirst(".work_dl [class^=_dl_count_]")?.text().orEmpty().firstInteger(),
            ratingCount = row.selectFirst(".work_rating .star_rating")?.text().orEmpty().firstInteger()
        )
    }
    val pagination = doc.selectFirst(".page_no")
    val currentPage = pagination?.selectFirst("strong")?.text()?.toIntOrNull()
        ?: if (items.isEmpty()) 1 else page
    val lastLink = pagination?.select("a[data-value]")?.maxOfOrNull { it.attr("data-value").toIntOrNull() ?: 0 }
    return DlsiteSearchResult(items, maxOf(currentPage, lastLink ?: 0))
}

private fun Document.outlineValue(predicate: (String) -> Boolean): Element? =
    select(".c-productInfo__key, #work_outline th").firstOrNull { predicate(it.text()) }?.nextElementSibling()

internal fun parseCvNames(doc: Document): List<String> {
    val value = doc.outlineValue {
        it.contains("声优") || it.contains("声優") || it.contains("聲優") ||
            it.contains("CV", ignoreCase = true) || it.contains("Voice Actor", ignoreCase = true)
    } ?: return emptyList()
    return value.select("a").eachText().ifEmpty {
        value.text().split("/", "／", ",", "，", "、").map(String::trim).filter(String::isNotBlank)
    }.distinct()
}

private fun parseJsonObject(text: String): JsonObject? =
    runCatching { JsonParser.parseString(text) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject

private fun Document.productSchema(): JsonObject? =
    select("script[type=application/ld+json]").asSequence()
        .mapNotNull { parseJsonObject(it.data()) }
        .firstOrNull { it.get("@type")?.asString == "Product" }

internal fun parseDlsiteAlbum(
    workId: String,
    doc: Document,
    locale: String?,
    translation: DlsiteTranslationProduct? = null
): Album? {
    val title = doc.selectFirst("#work_name")?.text() ?: return null
    val tags = parseDlsiteTags(doc.select(".c-productInfo__value a[href*=/genre/], .main_genre a"))
    val date = doc.outlineValue { label -> releaseLabels.any { it.equals(label, ignoreCase = true) } }
    val product = doc.productSchema()
    val offer = product?.getAsJsonObject("offers")
    val rating = product?.getAsJsonObject("aggregateRating")
    val salesLabel = doc.select(".c-productStatsItemPc__term, .work_right_info_item dt").firstOrNull {
        it.text().trimEnd('：', ':', ' ') in salesLabels
    }
    return Album(
        title = title,
        path = "",
        workId = workId,
        rjCode = workId,
        circle = doc.selectFirst(".c-productInfo__makerName, .maker_name a")?.text().orEmpty(),
        cv = parseCvNames(doc).joinToString(", "),
        tags = tags.map { it.originalName },
        tagDetails = tags,
        metadataLocale = locale,
        coverUrl = doc.selectFirst("meta[property=og:image]")?.absUrl("content").orEmpty(),
        releaseDate = translation?.releaseDate ?: parseReleaseDate(date?.text().orEmpty()),
        priceJpy = translation?.priceJpy ?: offer?.takeIf { it.get("priceCurrency")?.asString == "JPY" }?.get("price")?.asString?.toIntOrNull()
            ?: doc.selectFirst("[itemprop=offers] meta[itemprop=price]")?.attr("content")?.toIntOrNull() ?: 0,
        ratingValue = if (translation != null) translation.ratingValue else
            rating?.get("ratingValue")?.asString?.toDoubleOrNull()
                ?: doc.selectFirst("[itemprop=aggregateRating] meta[itemprop=ratingValue]")?.attr("content")?.toDoubleOrNull(),
        ratingCount = translation?.ratingCount ?: rating?.get("ratingCount")?.asString?.toIntOrNull()
            ?: doc.selectFirst("[itemprop=aggregateRating] meta[itemprop=ratingCount]")?.attr("content")?.toIntOrNull() ?: 0,
        dlCount = translation?.dlCount ?: salesLabel?.nextElementSibling()?.text().orEmpty().firstInteger(),
        description = doc.selectFirst("#work_outline, .work_parts_area")?.html() ?: product?.get("description")?.asString.orEmpty()
    )
}
