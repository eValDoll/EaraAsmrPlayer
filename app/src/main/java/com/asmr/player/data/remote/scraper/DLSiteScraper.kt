package com.asmr.player.data.remote.scraper

import android.content.Context
import android.util.Log
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.auth.buildDlsiteCookieHeader
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.util.DlsiteWorkNo
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.Connection
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLEncoder
import kotlin.math.absoluteValue
import javax.inject.Inject
import javax.inject.Singleton


internal fun storeSegment(): String = String.format("%s%s", "ma", "niax")

internal const val DLSITE_DOMAIN = "https://www.dlsite.com/"

private fun appendLocaleQuery(url: String, locale: String?): String {
    val normalizedLocale = locale?.trim().takeIf { !it.isNullOrBlank() } ?: "ja_JP"
    val separator = if ('?' in url) '&' else '?'
    return "$url$separator" + "locale=${URLEncoder.encode(normalizedLocale, "UTF-8")}"
}

internal fun buildDlsiteSearchUrls(
    keyword: String,
    page: Int,
    order: String,
    locale: String? = null,
    presaleOnly: Boolean = false,
    chineseTranslatedOnly: Boolean = false,
    hasSubtitle: Boolean = false,
    allAges: Boolean = false
): List<String> {
    val normalizedKeyword = keyword.trim()
    val encodedKeyword = URLEncoder.encode(normalizedKeyword, "UTF-8")
    val normalizedOrder = order.trim().ifBlank { "trend" }
    val encodedOrder = URLEncoder.encode(normalizedOrder, "UTF-8")
    val safePage = page.coerceAtLeast(1)

    if (chineseTranslatedOnly) {
        val chineseWorksBase =
            "$DLSITE_DOMAIN${storeSegment()}/fsr/=/work_category%5B0%5D/doujin/order/$encodedOrder/" +
                "work_type_category%5B0%5D/audio/options%5B0%5D/CHI/options%5B1%5D/CHI_HANS/options%5B2%5D/CHI_HANT"
        val chineseWorks = buildString {
            append(chineseWorksBase)
            if (normalizedKeyword.isNotBlank()) {
                append("/keyword/")
                append(encodedKeyword)
            }
            if (safePage > 1) {
                append("/page/")
                append(safePage)
            }
            append('/')
        }
        return listOf(appendLocaleQuery(chineseWorks, "zh_CN"))
    }

    if (presaleOnly) {
        val presaleUrl = buildString {
            append(DLSITE_DOMAIN)
            append(storeSegment())
            append("/fsr/=/ana_flg/on/work_type%5B0%5D/SOU")
            if (normalizedKeyword.isNotBlank()) {
                append("/keyword/")
                append(encodedKeyword)
            }
            if (safePage > 1) {
                append("/page/")
                append(safePage)
            }
            append('/')
        }
        return listOf(appendLocaleQuery(presaleUrl, locale))
    }

    val ageFilterSegment = if (allAges) {
        "age_category%5B0%5D/general/age_category%5B1%5D/r15/"
    } else {
        ""
    }
    val subtitleFilterSegment = if (hasSubtitle) {
        "/options%5B0%5D/CHI/options%5B1%5D/CHI_HANS/options%5B2%5D/CHI_HANT"
    } else {
        ""
    }
    val searchUrl = buildString {
        append(DLSITE_DOMAIN)
        append(storeSegment())
        append("/fsr/=/")
        append(ageFilterSegment)
        append("work_category%5B0%5D/doujin/order/")
        append(encodedOrder)
        append("/work_type%5B0%5D/SOU")
        append(subtitleFilterSegment)
        if (normalizedKeyword.isNotBlank()) {
            append("/keyword/")
            append(encodedKeyword)
        }
        if (safePage > 1) {
            append("/page/")
            append(safePage)
        }
        append("/from/left_pain.work_type/")
    }
    return listOf(appendLocaleQuery(searchUrl, locale))
}

data class DlsiteWorkInfo(
    val album: Album,
    val galleryUrls: List<String>,
    val recommendations: DlsiteRecommendations
)

data class DlsiteWorkInitialDetail(
    val workInfo: DlsiteWorkInfo?,
    val trialTracks: List<Track> = emptyList()
)

data class DlsiteRecommendedWork(
    val rjCode: String,
    val title: String,
    val coverUrl: String,
    val ribbon: String? = null
)

data class DlsiteRecommendations(
    val circleWorks: List<DlsiteRecommendedWork> = emptyList(),
    val sameVoiceWorks: List<DlsiteRecommendedWork> = emptyList(),
    val alsoBoughtWorks: List<DlsiteRecommendedWork> = emptyList()
)

data class DlsiteSearchResult(
    val items: List<Album>,
    val canGoNext: Boolean
)

internal fun parseCvNames(doc: Document): List<String> {
    val outline = doc.selectFirst("#work_outline") ?: return emptyList()
    return outline.select("tr")
        .filter { row ->
            val th = row.selectFirst("th")?.text()?.trim().orEmpty()
            th.contains("声优") ||
                th.contains("声優") ||
                th.contains("聲優") ||
                th.contains("CV", ignoreCase = true)
        }
        .flatMap { row ->
            val anchors = row.select("td a")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }
            if (anchors.isNotEmpty()) {
                anchors
            } else {
                row.selectFirst("td")
                    ?.text()
                    .orEmpty()
                    .split("/", "／", ",", "，", "、")
                    .map { it.trim() }
            }
        }
        .filter { it.isNotBlank() }
        .distinct()
}

@Singleton
class DLSiteScraper @Inject constructor(
    @ApplicationContext context: Context
) {
    private val workBaseUrl = "$DLSITE_DOMAIN${storeSegment()}/work/=/product_id/"
    private val announceBaseUrl = "$DLSITE_DOMAIN${storeSegment()}/announce/=/product_id/"
    
    private val authStore = DlsiteAuthStore(context)
    private val gson = Gson()

    private fun normalizeUrl(src: String, baseUrl: String): String {
        val trimmed = src.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("//")) return "https:$trimmed"
        return runCatching { URI(baseUrl).resolve(trimmed).toString() }.getOrDefault(trimmed)
    }

    private fun cookieHeader(locale: String? = null): String {
        return buildDlsiteCookieHeader(authStore.getDlsiteCookie(), locale)
    }

    private fun acceptLanguageForLocale(locale: String?): String {
        val l = locale?.trim().orEmpty()
        return when {
            l.startsWith("zh_CN", ignoreCase = true) -> "zh-CN,zh;q=0.9,en;q=0.8,ja;q=0.7"
            l.startsWith("zh_TW", ignoreCase = true) -> "zh-TW,zh;q=0.9,en;q=0.8,ja;q=0.7"
            l.startsWith("ja", ignoreCase = true) -> "ja-JP,ja;q=0.9,en;q=0.8"
            else -> NetworkHeaders.ACCEPT_LANGUAGE
        }
    }

    private fun connect(url: String, locale: String? = null, acceptLanguage: String? = null): Connection {
        return Jsoup.connect(url)
            .userAgent(NetworkHeaders.USER_AGENT)
            .header("Accept-Language", acceptLanguage ?: acceptLanguageForLocale(locale))
            .header("Cookie", cookieHeader(locale))
            .ignoreHttpErrors(true)
            .timeout(10000)
    }

    private fun parseRecommendHtml(doc: Document): List<DlsiteRecommendedWork> {
        return DlsiteRecommendHtmlParser.parse(doc)
    }

    private suspend fun fetchWorkDocument(workId: String, locale: String? = null): Document? {
        val clean = workId.trim().uppercase()
        if (clean.isBlank()) return null
        val candidates = listOf(
            "$workBaseUrl$clean.html",
            "$announceBaseUrl$clean.html"
        )
        for (url in candidates) {
            val doc = runCatching { runInterruptible { connect(url, locale = locale).get() } }.getOrNull() ?: continue
            if (doc.selectFirst("#work_name") != null) return doc
        }
        return null
    }

    suspend fun getRecommendationsDetailV2(workId: String, locale: String? = null): DlsiteRecommendations =
        withContext(Dispatchers.IO) {
            val clean = workId.trim().uppercase()
            if (clean.isBlank()) return@withContext DlsiteRecommendations()

            // 1. 获取主页面以查找异步加载推荐的 data-href
            val mainDoc = runCatching { runInterruptible { connect("$workBaseUrl$clean.html", locale = locale).get() } }.getOrNull()

            // 2. 提取各推荐板块的 URL
            val circleWorksUrl = mainDoc?.selectFirst("div[data-type=maker_works], div.recommend_list[data-href*='maker_works']")?.attr("data-href")
            val sameVoiceWorksUrl = mainDoc?.selectFirst("div[data-type=same_voice_products], div.recommend_list[data-href*='same_voice_products']")?.attr("data-href")
            val alsoBoughtUrlFromDoc = mainDoc?.selectFirst("div[data-type=viewsales2], div.recommend_list[data-href*='viewsales2']")?.attr("data-href")

            // 3. 定义备选抓取逻辑
            suspend fun fetchViewsales2Fallback(productId: String): List<DlsiteRecommendedWork> {
                val id = productId.trim().uppercase()
                if (id.isBlank()) return emptyList()
                val candidates = listOf(
                    "$DLSITE_DOMAIN${storeSegment()}/load/recommend/v2/=/type/viewsales2/product_id/$id.html",
                    "$DLSITE_DOMAIN${storeSegment()}/load/recommend/v2/=/type/viewsales2/reject/RG68316/product_id/$id.html"
                )
                for (u in candidates) {
                    val parsed = runCatching { runInterruptible { connect(u, locale = locale).get() } }.getOrNull()?.let { parseRecommendHtml(it) }
                    if (!parsed.isNullOrEmpty()) return parsed
                }
                return emptyList()
            }

            // 4. 并行执行抓取任务
            coroutineScope {
                val circleDeferred = async {
                    circleWorksUrl?.let { url ->
                        runCatching { runInterruptible { connect(url, locale = locale).get() } }.getOrNull()?.let { parseRecommendHtml(it) }
                    } ?: emptyList()
                }
                val sameVoiceDeferred = async {
                    sameVoiceWorksUrl?.let { url ->
                        runCatching { runInterruptible { connect(url, locale = locale).get() } }.getOrNull()?.let { parseRecommendHtml(it) }
                    } ?: emptyList()
                }
                val alsoBoughtDeferred = async {
                    if (!alsoBoughtUrlFromDoc.isNullOrBlank()) {
                        runCatching { runInterruptible { connect(alsoBoughtUrlFromDoc, locale = locale).get() } }.getOrNull()?.let { parseRecommendHtml(it) } ?: emptyList()
                    } else {
                        fetchViewsales2Fallback(clean)
                    }
                }

                DlsiteRecommendations(
                    circleWorks = circleDeferred.await(),
                    sameVoiceWorks = sameVoiceDeferred.await(),
                    alsoBoughtWorks = alsoBoughtDeferred.await()
                )
            }
        }

    private suspend fun fetchChobitEmbedUrl(workId: String): String {
        val clean = workId.trim().uppercase()
        if (clean.isBlank()) return ""
        val ts = System.currentTimeMillis()
        val callback = "jQuery${(ts * 1103515245L).absoluteValue}_${ts}"
        val apiUrl = "https://chobit.cc/api/v1/dlsite/embed?callback=$callback&workno=$clean&_=$ts"
        val body = runCatching {
            runInterruptible {
                Jsoup.connect(apiUrl)
                    .userAgent(NetworkHeaders.USER_AGENT)
                    .header("Referer", NetworkHeaders.REFERER_DLSITE)
                    .header("Accept", "*/*")
                    .ignoreContentType(true)
                    .timeout(10000)
                    .execute()
                    .body()
            }
        }.getOrDefault("")
        if (body.isBlank()) return ""
        val jsonp = body.trim()
        val start = jsonp.indexOf('(')
        val end = jsonp.lastIndexOf(')')
        val json = if (start >= 0 && end > start) jsonp.substring(start + 1, end) else jsonp
        val obj = runCatching { gson.fromJson(json, Map::class.java) }.getOrNull() ?: return ""
        val works = obj["works"] as? List<*> ?: return ""
        val first = works.firstOrNull() as? Map<*, *> ?: return ""
        var embedUrl = (first["embed_url"] as? String).orEmpty().trim()
        if (embedUrl.isBlank()) return ""
        if (!embedUrl.startsWith("http")) {
            embedUrl = if (embedUrl.startsWith("//")) "https:$embedUrl" else "https://chobit.cc$embedUrl"
        }
        if (!embedUrl.contains("dlsite=1")) {
            embedUrl += if (embedUrl.contains("?")) "&dlsite=1" else "?dlsite=1"
        }
        return embedUrl
    }

    suspend fun search(
        keyword: String,
        page: Int = 1,
        order: String = "trend",
        locale: String? = null,
        presaleOnly: Boolean = false,
        chineseTranslatedOnly: Boolean = false,
        hasSubtitle: Boolean = false,
        allAges: Boolean = false
    ): DlsiteSearchResult = withContext(Dispatchers.IO) {
        fun parseItems(items: List<Element>, doc: Document): List<Album> {
            val results = mutableListOf<Album>()

            for (item in items) {
                val titleTag = item.selectFirst(".work_name a") ?: continue
                val title = titleTag.text().trim()
                val link = titleTag.attr("href")
                val workNo = DlsiteWorkNo.extractWorkNo(link)
                if (workNo.isBlank()) continue

                val circle = item.selectFirst(".maker_name a")?.text()?.trim() ?: ""
                val tags = item.select(".search_tag a").map { it.text().trim() }
                val cv = item.select(".author a")
                    .map { it.text().trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(", ")

                fun firstFromSrcset(srcset: String): String {
                    val first = srcset.split(",").firstOrNull().orEmpty().trim()
                    return first.split(Regex("\\s+")).firstOrNull().orEmpty().trim()
                }

                fun extractVueBoundImageSrc(expr: String): String {
                    val trimmed = expr.trim()
                    if (trimmed.isEmpty()) return ""
                    val direct = Regex("""['\"](//[^'\"]+|https?://[^'\"]+|/[^'\"]+)['\"]""")
                        .findAll(trimmed)
                        .map { it.groupValues.getOrNull(1).orEmpty().trim() }
                        .firstOrNull { candidate ->
                            candidate.isNotBlank() && !candidate.startsWith("data:", ignoreCase = true)
                        }
                    return direct.orEmpty()
                }

                fun pickBestImageUrl(img: Element): String {
                    val candidates = sequenceOf(
                        extractVueBoundImageSrc(img.attr(":src")),
                        img.attr("data-src"),
                        img.attr("data-original"),
                        img.attr("data-lazy"),
                        img.attr("data-lazy-src"),
                        img.attr("data-srcset").let { firstFromSrcset(it) },
                        img.attr("srcset").let { firstFromSrcset(it) },
                        img.attr("src")
                    )
                        .map { it.trim() }
                        .filter { it.isNotBlank() && !it.startsWith("data:", ignoreCase = true) }
                        .map { raw -> normalizeUrl(raw, doc.baseUri()) }
                        .toList()

                    return candidates.firstOrNull().orEmpty()
                }

                fun scoreUrl(url: String): Int {
                    val u = url.lowercase()
                    var s = 0
                    if (u.contains("/resize/images2/work/")) s += 30
                    if (u.contains("/modpub/images2/work/") || u.contains("/images2/work/")) s += 20
                    if (u.contains("img.dlsite.jp")) s += 10
                    if (u.contains("dlsite")) s += 5
                    if (u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") || u.endsWith(".webp")) s += 2
                    return s
                }

                val imgs = item.select("img").toList()
                val coverUrl = imgs.asSequence()
                    .map { pickBestImageUrl(it) }
                    .filter { it.isNotBlank() }
                    .sortedByDescending { scoreUrl(it) }
                    .firstOrNull()
                    .orEmpty()

                results.add(
                    Album(
                        title = title,
                        path = "",
                        workId = workNo,
                        rjCode = workNo,
                        circle = circle,
                        cv = cv,
                        tags = tags,
                        coverUrl = coverUrl
                    )
                )
            }

            return results
        }

        fun parseCanGoNext(doc: Document, itemCount: Int): Boolean {
            val infoScript = doc.select("script")
                .firstOrNull { it.data().contains("\"page_info\"") }
                ?.data()
                .orEmpty()
            val pageInfo = Regex("""\"page_info\":\{\"count\":(\d+),\"first_indice\":(\d+),\"last_indice\":(\d+)\}""")
                .find(infoScript)
            if (pageInfo != null) {
                val total = pageInfo.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
                val lastIndex = pageInfo.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                if (total > 0 && lastIndex > 0) {
                    return lastIndex < total
                }
            }
            return itemCount >= 30
        }

        fun parseDoc(doc: Document): DlsiteSearchResult {
            val container = doc.selectFirst("#search_result_list.loading_display_open")
                ?: doc.select("#search_result_list").lastOrNull()

            val candidates = listOf(
                container?.select("li")?.toList().orEmpty(),
                doc.select("#search_result_img_box li").toList(),
                container?.select("table tr")?.toList().orEmpty().filter { it.select("th").isEmpty() }
            )

            for (items in candidates) {
                if (items.isEmpty()) continue
                val parsed = parseItems(items, doc)
                if (parsed.isNotEmpty()) {
                    return DlsiteSearchResult(
                        items = parsed,
                        canGoNext = parseCanGoNext(doc, parsed.size)
                    )
                }
            }

            return DlsiteSearchResult(items = emptyList(), canGoNext = false)
        }

        val urls = buildDlsiteSearchUrls(
            keyword = keyword,
            page = page,
            order = order,
            locale = locale,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            hasSubtitle = hasSubtitle,
            allAges = allAges
        )
        var lastEmpty = DlsiteSearchResult(items = emptyList(), canGoNext = false)
        for (u in urls) {
            Log.d("DLSiteScraper", "Searching URL: $u")
            val doc = runInterruptible { connect(u, locale = locale).get() }
            val parsed = parseDoc(doc)
            if (parsed.items.isNotEmpty()) return@withContext parsed
            lastEmpty = parsed
        }
        lastEmpty
    }

    suspend fun getDetails(workId: String): Album? = withContext(Dispatchers.IO) {
        getWorkInfo(workId)?.album
    }

    suspend fun getDetails(workId: String, locale: String?): Album? = withContext(Dispatchers.IO) {
        getWorkInfo(workId, locale = locale)?.album
    }

    suspend fun getWorkInfo(
        workId: String,
        locale: String? = null,
        allowJapaneseCvFallback: Boolean = true
    ): DlsiteWorkInfo? = withContext(Dispatchers.IO) {
        val clean = workId.trim().uppercase()
        val doc = fetchWorkDocument(clean, locale = locale) ?: return@withContext null
        val parsed = parseWorkInfo(clean, doc) ?: return@withContext null
        if (allowJapaneseCvFallback) {
            withJapaneseCvFallback(clean, locale, parsed)
        } else {
            parsed
        }
    }

    private suspend fun withJapaneseCvFallback(
        workId: String,
        locale: String?,
        parsed: DlsiteWorkInfo
    ): DlsiteWorkInfo {
        if (parsed.album.cv.isNotBlank() || locale?.startsWith("ja", ignoreCase = true) == true) {
            return parsed
        }

        val japaneseCv = fetchWorkDocument(workId, locale = "ja_JP")
            ?.let { japaneseDoc -> parseCvNames(japaneseDoc).joinToString(", ") }
            .orEmpty()
        return if (japaneseCv.isBlank()) {
            parsed
        } else {
            parsed.copy(album = parsed.album.copy(cv = japaneseCv))
        }
    }

    suspend fun getInitialWorkDetail(workId: String, locale: String? = null): DlsiteWorkInitialDetail = withContext(Dispatchers.IO) {
        val clean = workId.trim().uppercase()
        if (clean.isBlank()) return@withContext DlsiteWorkInitialDetail(workInfo = null)
        val doc = fetchWorkDocument(clean, locale = locale) ?: return@withContext DlsiteWorkInitialDetail(workInfo = null)
        val workInfo = parseWorkInfo(clean, doc)
            ?.let { withJapaneseCvFallback(clean, locale, it) }
        DlsiteWorkInitialDetail(
            workInfo = workInfo,
            trialTracks = parseTracksFromWorkDocument(clean, locale, doc, allowFallback = true)
        )
    }

    private fun parseWorkInfo(workId: String, doc: Document): DlsiteWorkInfo? {
        val title = doc.selectFirst("#work_name")?.text()?.trim() ?: return null
        val circle = doc.selectFirst(".maker_name a")?.text()?.trim() ?: ""
        
        val cvNames = parseCvNames(doc)
        val outline = doc.selectFirst("#work_outline")
        var releaseDate = ""
        outline?.select("tr")?.forEach { row ->
            val th = row.selectFirst("th")?.text() ?: ""
            if (releaseDate.isBlank() && (th.contains("販売日") || th.contains("発売日") || th.contains("发售日") || th.contains("發售日"))) {
                val txt = row.selectFirst("td")?.text()?.trim().orEmpty()
                val m = Regex("""(\d{4})\D+(\d{1,2})\D+(\d{1,2})""").find(txt)
                if (m != null) {
                    val y = m.groupValues.getOrNull(1).orEmpty()
                    val mo = m.groupValues.getOrNull(2).orEmpty().padStart(2, '0')
                    val d = m.groupValues.getOrNull(3).orEmpty().padStart(2, '0')
                    if (y.isNotBlank()) releaseDate = "$y-$mo-$d"
                }
            }
        }

        val ratingValue = doc.selectFirst("[itemprop=aggregateRating] meta[itemprop=ratingValue]")?.attr("content")?.trim()?.toDoubleOrNull()
        val ratingCount = doc.selectFirst("[itemprop=aggregateRating] meta[itemprop=ratingCount]")?.attr("content")?.trim()?.toIntOrNull() ?: 0
        val priceJpy = doc.selectFirst("[itemprop=offers] meta[itemprop=price]")?.attr("content")?.trim()?.toIntOrNull() ?: 0

        fun pickInt(text: String): Int {
            val m = Regex("""([\d,]+)""").find(text)
            return m?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull() ?: 0
        }

        var dlCount = 0
        doc.select(".work_right_info_item dt").forEach { dt ->
            val label = dt.text().trim().replace("：", "")
            val dd = dt.nextElementSibling() ?: return@forEach
            val v = dd.text().trim()
            if (label in listOf("售出数", "販売数", "DL数", "売上数")) {
                dlCount = pickInt(v)
            }
        }

        val tags = doc.select(".main_genre a").map { it.text().trim() }
        val coverUrl = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        
        // Parse description/story
        val description = doc.selectFirst("#work_outline")?.html() ?: doc.selectFirst(".work_parts_area")?.html() ?: ""

        val galleryUrls = runCatching { extractGalleryUrls(doc, doc.baseUri()) }.getOrDefault(emptyList())
        val recommendations = runCatching { extractRecommendations(doc, doc.baseUri()) }.getOrDefault(DlsiteRecommendations())

        return DlsiteWorkInfo(
            album = Album(
                title = title,
                path = "",
                workId = workId,
                rjCode = workId,
                circle = circle,
                cv = cvNames.joinToString(", "),
                tags = tags,
                coverUrl = if (coverUrl.startsWith("http")) coverUrl else "https:$coverUrl",
                ratingValue = ratingValue,
                ratingCount = ratingCount,
                releaseDate = releaseDate,
                dlCount = dlCount,
                priceJpy = priceJpy,
                description = description
            ),
            galleryUrls = galleryUrls,
            recommendations = recommendations
        )
    }

    suspend fun getTracks(workId: String, locale: String? = null): List<Track> = withContext(Dispatchers.IO) {
        val doc = fetchWorkDocument(workId, locale = locale) ?: return@withContext emptyList()
        parseTracksFromWorkDocument(workId, locale, doc, allowFallback = true)
    }

    private suspend fun parseTracksFromWorkDocument(
        workId: String,
        locale: String?,
        doc: Document,
        allowFallback: Boolean
    ): List<Track> {
        fun parseTrackListFromDoc(doc: Document): List<Track> {
            val out = mutableListOf<Track>()
            doc.select(".track-list li").forEach { item ->
                val title = item.attr("data-title").ifEmpty { item.selectFirst(".name, .track_name")?.text()?.trim() } ?: "Unknown Trial"
                val src = item.attr("data-src").trim().removePrefix("`").removeSuffix("`").trim()
                val streamUrl = when {
                    src.isEmpty() -> ""
                    src.startsWith("//") -> "https:$src"
                    src.startsWith("/") -> "https://www.dlsite.com$src"
                    src.startsWith("http") -> src
                    else -> runCatching { URI(doc.baseUri()).resolve(src).toString() }.getOrDefault(src)
                }
                if (streamUrl.isNotBlank()) {
                    out.add(Track(albumId = 0, title = title, path = streamUrl, duration = 0.0))
                }
            }
            return out
        }

        fun parseVideoFromDoc(doc: Document): List<Track> {
            val sources = doc.select("video source[src]")
            if (sources.isEmpty()) return emptyList()
            var bestSrc = ""
            var bestRes = 0
            sources.forEach { source ->
                val src = source.attr("src").trim()
                if (src.isBlank()) return@forEach
                val resStr = source.attr("data-height").ifBlank { source.attr("data-res") }
                val res = Regex("""(\d+)""").find(resStr)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                if (res > bestRes || bestSrc.isBlank()) {
                    bestRes = res
                    bestSrc = src
                }
            }
            val fullSrc = when {
                bestSrc.startsWith("//") -> "https:$bestSrc"
                bestSrc.startsWith("http") -> bestSrc
                else -> runCatching { URI(doc.baseUri()).resolve(bestSrc).toString() }.getOrDefault(bestSrc)
            }
            if (fullSrc.isBlank()) return emptyList()
            return listOf(
                Track(
                    albumId = 0,
                    title = if (bestRes > 0) "Sample Movie (${bestRes}p)" else "Sample Movie",
                    path = fullSrc,
                    duration = 0.0
                )
            )
        }

        fun parseTrialDownload(doc: Document): List<Track> {
            val btn = doc.select("a[href*='trial_download']").firstOrNull() ?: return emptyList()
            val downloadUrl = btn.attr("href").trim()
            val fullUrl = when {
                downloadUrl.startsWith("//") -> "https:$downloadUrl"
                downloadUrl.startsWith("/") -> "https://www.dlsite.com$downloadUrl"
                else -> downloadUrl
            }.trim()
            if (fullUrl.isBlank()) return emptyList()
            return listOf(Track(albumId = 0, title = "试听下载 (ZIP)", path = fullUrl, duration = 0.0))
        }

        fun parseChobitFromHtml(html: String): String {
            val m = Regex("""https?://chobit\.cc/embed/[a-zA-Z0-9]+/[a-zA-Z0-9]+(?:\?[^"'<> ]+)?""").find(html)
                ?: Regex("""//chobit\.cc/embed/[a-zA-Z0-9]+/[a-zA-Z0-9]+(?:\?[^"'<> ]+)?""").find(html)
            if (m == null) return ""
            var u = m.value.trim()
            if (u.startsWith("//")) u = "https:$u"
            if (!u.contains("dlsite=1")) u += if (u.contains("?")) "&dlsite=1" else "?dlsite=1"
            return u
        }

        suspend fun internal(workId0: String, doc0: Document, allowFallback0: Boolean): List<Track> {
            val tracks = mutableListOf<Track>()

            tracks.addAll(parseTrackListFromDoc(doc0))
            if (tracks.isNotEmpty()) return tracks

            doc0.select(".work_parts_area audio source, .work_parts_area video source").forEach { source ->
                val src = source.attr("src").trim()
                if (src.isNotEmpty()) {
                    val fullUrl = if (src.startsWith("//")) "https:$src" else if (src.startsWith("/")) "https://www.dlsite.com$src" else src
                    tracks.add(Track(albumId = 0, title = "Sample Content", path = fullUrl, duration = 0.0))
                }
            }
            if (tracks.isNotEmpty()) return tracks

            var embedUrl = fetchChobitEmbedUrl(workId0)
            if (embedUrl.isBlank()) {
                embedUrl = parseChobitFromHtml(doc0.html())
            }
            if (embedUrl.isNotBlank()) {
                val acceptLanguage = acceptLanguageForLocale(locale)
                val embedDoc = runInterruptible {
                    Jsoup.connect(embedUrl)
                        .userAgent(NetworkHeaders.USER_AGENT)
                        .header("Referer", NetworkHeaders.REFERER_DLSITE)
                        .header("Accept-Language", acceptLanguage)
                        .ignoreHttpErrors(true)
                        .timeout(10000)
                        .get()
                }
                val t = parseTrackListFromDoc(embedDoc)
                if (t.isNotEmpty()) return t
                val v = parseVideoFromDoc(embedDoc)
                if (v.isNotEmpty()) return v
            }

            if (allowFallback0) {
                val jpLink = doc0.select("a.work_edition_linklist_item").firstOrNull { it.text().contains("日本語") }
                val href = jpLink?.attr("href").orEmpty()
                val jpId = DlsiteWorkNo.extractWorkNo(href)
                if (jpId.isNotBlank() && jpId != workId0.uppercase()) {
                    val jpDoc = fetchWorkDocument(jpId, locale = locale)
                    val fallback = if (jpDoc != null) internal(jpId, jpDoc, allowFallback0 = false) else emptyList()
                    if (fallback.isNotEmpty()) return fallback
                }
            }

            val dl = parseTrialDownload(doc0)
            if (dl.isNotEmpty()) return dl

            return emptyList()
        }

        return internal(workId, doc, allowFallback0 = allowFallback)
    }

    private fun extractGalleryUrls(doc: Document, baseUrl: String): List<String> {
        val selectors = listOf(
            ".work_parts_area img",
            "#work_visual img",
            ".work_slider img",
            ".work_sample img",
            ".work_sample_area img",
            ".slider_item img",
            ".gallery img"
        )
        val urls = linkedSetOf<String>()
        selectors.forEach selectorLoop@{ sel ->
            doc.select(sel).forEach imgLoop@{ img ->
                val attr = when {
                    img.hasAttr("data-src") && img.attr("data-src").isNotBlank() -> "data-src"
                    img.hasAttr("data-original") && img.attr("data-original").isNotBlank() -> "data-original"
                    else -> "src"
                }
                val raw = img.attr(attr)
                val u = img.absUrl(attr).takeIf { it.isNotBlank() } ?: normalizeUrl(raw, baseUrl)
                if (u.isBlank()) return@imgLoop
                val lower = u.lowercase()
                if (!lower.startsWith("http")) return@imgLoop
                if (lower.contains("sprite") || lower.contains("icon") || lower.contains("loading")) return@imgLoop
                urls.add(u)
            }
        }
        return urls.toList()
    }

    private fun extractRecommendations(doc: Document, baseUrl: String): DlsiteRecommendations {
        fun sanitizeTitle(s: String): String {
            return s.trim().replace(Regex("\\s+"), " ")
        }

        fun normalizeWorkId(workId: String): String {
            return workId.trim().uppercase()
        }

        fun parseWorks(container: Element?): List<DlsiteRecommendedWork> {
            if (container == null) return emptyList()
            val out = ArrayList<DlsiteRecommendedWork>()
            val anchors = container.select("a.work_thumb, a[id^=_link_], a[href*=/product_id/]")
            anchors.forEach { a ->
                val href = a.attr("href").ifBlank { a.absUrl("href") }
                val workNo = DlsiteWorkNo.extractWorkNo(href)
                if (workNo.isBlank()) return@forEach
                val img = a.selectFirst("img")
                val title = sanitizeTitle(img?.attr("alt").orEmpty().ifBlank { a.attr("title") }.ifBlank { a.text() })
                val cover = DlsiteRecommendHtmlParser.extractCoverUrl(a, baseUrl)
                val ribbon = a.selectFirst(".recommend_ribbon .ribbon, .recommend_ribbon span, .ribbon")?.text()?.trim()?.ifBlank { null }
                out.add(
                    DlsiteRecommendedWork(
                        rjCode = normalizeWorkId(workNo),
                        title = if (title.isBlank()) normalizeWorkId(workNo) else title,
                        coverUrl = cover,
                        ribbon = ribbon
                    )
                )
            }
            return out.distinctBy { it.rjCode }
        }

        fun findSectionContainer(vararg labels: String): Element? {
            val normalized = labels.map { it.trim() }.filter { it.isNotBlank() }
            if (normalized.isEmpty()) return null
            val candidates = normalized.flatMap { label ->
                doc.select("*:matchesOwn(${Regex.escape(label)})").toList()
            }
            for (hit in candidates) {
                var p: Element? = hit
                repeat(7) {
                    if (p == null) return@repeat
                    val has = p!!.select("a.work_thumb, a[id^=_link_], a[href*=/product_id/]").isNotEmpty()
                    if (has) return p
                    p = p!!.parent()
                }
            }
            return null
        }

        val circleContainer = findSectionContainer("サークル作品一覧", "社团作品一览", "社團作品一覽")
        val sameVoiceContainer = findSectionContainer("同一声優作品", "同一声优作品", "同一聲優作品")
        val alsoBoughtContainer = findSectionContainer(
            "この作品を買った人はこんな作品も買っています",
            "购买了此作品的人也购买了这些作品",
            "購買了此作品的人也購買了這些作品"
        )

        return DlsiteRecommendations(
            circleWorks = parseWorks(circleContainer),
            sameVoiceWorks = parseWorks(sameVoiceContainer),
            alsoBoughtWorks = parseWorks(alsoBoughtContainer)
        )
    }
}
