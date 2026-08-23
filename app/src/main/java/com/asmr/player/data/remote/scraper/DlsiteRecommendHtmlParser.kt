package com.asmr.player.data.remote.scraper

import com.asmr.player.util.DlsiteWorkNo
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

internal fun dlsiteOriginalCoverUrlForWorkNo(rawWorkNo: String): String {
    val workNo = DlsiteWorkNo.extractWorkNo(rawWorkNo)
    val prefix = workNo.take(2)
    val digits = workNo.drop(2)
    val number = digits.toLongOrNull() ?: return ""
    val category = when (prefix) {
        "RJ" -> "doujin"
        "BJ" -> "books"
        "VJ" -> "professional"
        else -> return ""
    }
    val group = ((number + 999L) / 1000L) * 1000L
    val folder = "$prefix${group.toString().padStart(digits.length, '0')}"
    return "https://img.dlsite.jp/modpub/images2/work/$category/$folder/${workNo}_img_main.jpg"
}

internal fun resolveRecommendedWorkCoverUrl(
    rawWorkNo: String,
    providedCoverUrl: String?
): String {
    return providedCoverUrl?.trim().orEmpty()
        .ifBlank { dlsiteOriginalCoverUrlForWorkNo(rawWorkNo) }
}

internal object DlsiteRecommendHtmlParser {
    private fun normalizeUrl(src: String, baseUrl: String): String {
        val trimmed = src.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("//")) return "https:$trimmed"
        return runCatching { URI(baseUrl).resolve(trimmed).toString() }.getOrDefault(trimmed)
    }

    private fun parseCandidates(raw: String): List<String> {
        val s = raw.trim()
        if (s.isBlank()) return emptyList()
        return Regex("""['"]([^'"]+)['"]""").findAll(s).map { it.groupValues[1].trim() }.filter { it.isNotBlank() }.toList()
    }

    private fun pickPreferred(candidates: List<String>): String {
        if (candidates.isEmpty()) return ""
        val normalized = candidates.map { it.trim() }.filter { it.isNotBlank() }
        if (normalized.isEmpty()) return ""
        val lower = normalized.map { it.lowercase() }
        val order = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")
        for (ext in order) {
            val idx = lower.indexOfFirst { it.contains(ext) }
            if (idx >= 0) return normalized[idx]
        }
        return normalized.first()
    }

    fun extractCoverUrl(root: Element?, baseUrl: String): String {
        if (root == null) return ""
        val img = root.selectFirst("img")
        val rawFromImg = when {
            img == null -> ""
            img.hasAttr("data-src") && img.attr("data-src").isNotBlank() -> img.attr("data-src")
            img.hasAttr("data-original") && img.attr("data-original").isNotBlank() -> img.attr("data-original")
            else -> img.attr("src")
        }
        val urlFromImg = normalizeUrl(rawFromImg, baseUrl)
        if (urlFromImg.isNotBlank()) return urlFromImg

        val imgWithFallback = root.selectFirst("img-with-fallback")
        val candidatesRaw = imgWithFallback?.attr(":candidates").orEmpty().ifBlank { imgWithFallback?.attr("candidates").orEmpty() }
        val picked = pickPreferred(parseCandidates(candidatesRaw))
        return normalizeUrl(picked, baseUrl)
    }

    fun parse(doc: Document): List<DlsiteRecommendedWork> {
        fun sanitizeTitle(s: String): String = s.trim().replace(Regex("\\s+"), " ")

        val out = ArrayList<DlsiteRecommendedWork>()
        val anchors = doc.select("a[href*=/product_id/], a[href*=product_id/], a.work_thumb, a[id^=_link_]")
        anchors.forEach { a ->
            val href = a.absUrl("href").ifBlank { a.attr("href") }
            val workNo = DlsiteWorkNo.extractWorkNo(href)
            if (workNo.isBlank()) return@forEach

            val container = a.closest("li, .work, .n_worklist_item, .recommend, .recommend_item") ?: a.parent()
            val cover = extractCoverUrl(container ?: a, doc.baseUri())
            val title = sanitizeTitle(
                container?.selectFirst(".work_name, .work_name a, .work_name span")?.text().orEmpty()
                    .ifBlank { a.selectFirst("img")?.attr("alt").orEmpty() }
                    .ifBlank { a.attr("title") }
                    .ifBlank { a.text() }
            ).ifBlank { workNo }
            val ribbon = container?.selectFirst(".recommend_ribbon .ribbon, .recommend_ribbon span, .work_discount, .ribbon")
                ?.text()
                ?.trim()
                ?.ifBlank { null }

            out.add(DlsiteRecommendedWork(rjCode = workNo, title = title, coverUrl = cover, ribbon = ribbon))
        }
        return out.distinctBy { it.rjCode }
    }
}
