package com.asmr.player.data.remote.scraper

import com.asmr.player.domain.model.mergeAlbumTags
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class DlsiteTagParserTest {
    @Test fun matchesGenresAcrossLanguagesByIdentity() {
        val zh = Jsoup.parse("""<html lang="zh-CN"><a href="/genre/=/genre/123/">掏耳</a><a href="/genre/456/">治愈</a></html>""")
        val en = Jsoup.parse("""<html lang="en-US"><a href="/genre/456/">Healing</a><a href="/genre/=/genre/123/">Ear cleaning</a></html>""")
        val tags = mergeAlbumTags(parseDlsiteTags(zh.select("a")), parseDlsiteTags(en.select("a")))
        assertEquals(2, tags.size)
        assertEquals("dlsite:genre:123", tags.first().key)
        assertEquals("Ear cleaning", tags.first().displayName("en"))
        assertEquals("掏耳", tags.first().displayName("zh"))
    }

    @Test fun unknownPageLanguageDoesNotClaimEnglishName() {
        val doc = Jsoup.parse("""<a href="/genre/123/">耳かき</a>""")
        val tag = parseDlsiteTags(doc.select("a")).single()
        assertTrue(tag.names.orEmpty().isEmpty())
        assertEquals("耳かき", tag.displayName("en"))
    }

    @Test fun englishOutlineKeepsDateAndVoiceActors() {
        assertEquals("2023-07-14", parseEnglishReleaseDate("Jul/14/2023"))
        assertEquals("2026-11-08", parseEnglishReleaseDate("Nov. 08, 2026"))
        assertEquals("2026-11-08", parseEnglishReleaseDate("November 8, 2026"))
        assertNull(parseEnglishReleaseDate("not a date"))
        assertNull(parseEnglishReleaseDate("Feb 29, 2023"))
        assertEquals("2024-02-29", parseEnglishReleaseDate("February 29, 2024"))
        val doc = Jsoup.parse("""<table id="work_outline"><tr><th>Voice Actor</th><td><a>Actor A</a><a>Actor B</a></td></tr></table>""")
        assertEquals(listOf("Actor A", "Actor B"), parseCvNames(doc))
    }

    @Test fun realDlsiteGenreLinksMatchAcrossLanguages() {
        // Minimal genre excerpts from DLsite RJ254481, sampled 2026-11-08.
        val link = "https://www.dlsite.com/maniax/fsr/=/genre/415/from/work.genre/ana_flg/all"
        val zh = Jsoup.parse("""<html lang="zh-CN"><div class="main_genre"><a href="$link">辣妹</a></div></html>""")
        val en = Jsoup.parse("""<html lang="en-US"><div class="main_genre"><a href="$link">Gal</a></div></html>""")
        val tag = mergeAlbumTags(parseDlsiteTags(zh.select(".main_genre a")), parseDlsiteTags(en.select(".main_genre a"))).single()
        assertEquals("dlsite:genre:415", tag.key)
        assertEquals("Gal", tag.displayName("en"))
        assertEquals("辣妹", tag.displayName("zh"))
    }
}
