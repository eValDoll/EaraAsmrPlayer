package com.asmr.player.data.remote.scraper

import com.asmr.player.domain.model.AlbumTag
import org.jsoup.nodes.Element

private val genreIdentity = Regex("(?:genre|genre_id)(?:/=/|/|=)([0-9]+)")

internal fun parseDlsiteTags(links: Iterable<Element>): List<AlbumTag> =
    links.mapNotNull { link ->
        val name = link.text().trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
        val identity = genreIdentity.find(link.attr("href"))?.groupValues?.get(1)
        val documentLanguage = link.ownerDocument()?.selectFirst("html")?.attr("lang").orEmpty()
        // Only a declared page language can establish an official localized name.
        val language = documentLanguage.takeIf(String::isNotBlank)
        AlbumTag(
            key = "dlsite:genre:${identity ?: name}",
            source = "dlsite",
            originalName = name,
            names = language?.let { mapOf(it to name) }.orEmpty()
        )
    }.distinctBy(AlbumTag::key)

internal fun parseEnglishReleaseDate(value: String): String? {
    val date = Regex("[A-Za-z]+\\.?[ /]+\\d{1,2},?[ /]+\\d{4}").find(value)?.value ?: return null
    val normalized = date.replace(".", "").replace(",", "").replace("/", " ").replace(Regex("\\s+"), " ")
    return sequenceOf("MMM d uuuu", "MMMM d uuuu").mapNotNull { pattern ->
        runCatching {
            java.time.LocalDate.parse(normalized, java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH))
        }.getOrNull()
    }.firstOrNull()?.toString()
}
