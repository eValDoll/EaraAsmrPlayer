package com.asmr.player.data.remote.scraper

import com.asmr.player.domain.model.AlbumTag
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
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
    val utc = TimeZone.getTimeZone("UTC")
    val parser = SimpleDateFormat("MMM d yyyy", Locale.ENGLISH).apply {
        isLenient = false
        timeZone = utc
    }
    val position = ParsePosition(0)
    val parsed = parser.parse(normalized, position)
        ?.takeIf { position.index == normalized.length } ?: return null
    return SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = utc }.format(parsed)
}
