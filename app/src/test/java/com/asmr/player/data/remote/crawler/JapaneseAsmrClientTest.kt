package com.asmr.player.data.remote.crawler

import com.asmr.player.util.ChapterMediaReference
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class JapaneseAsmrClientTest {
    private val base = "https://japaneseasmr.com/150867/"
    private val page = """
        <p id="work_title_jp">作品 [RJ01707048]</p>
        <div class="cleanPlayer"><video><source src="https://audio.example/RJ01707048.m3u8"></video>
        <table><tr><td><a data-track-title="導入" data-value="0">導入</a></td></tr>
        <tr><td><a data-track-title="本編" data-value="106">本編</a></td></tr>
        <tr><td><a data-track-title="終章" data-value="1018">終章</a></td></tr></table></div>
        <div class="audio_main"><audio src="https://audio.example/RJ01707048.m3u8"></audio></div>
    """.trimIndent()

    @Test fun chaptersUseNextBoundaryAndKeepLastOpenEndedWithoutDuplicatePlayers() {
        val document = Jsoup.parse(page, base)
        val downloads = parseJapaneseAsmrDownloads(Jsoup.parse("""
            <a href="https://ts.buzzheavier.com/d/a">RJ01707048 - 01_導入.m4a</a>
            <a href="https://ts.buzzheavier.com/d/b">RJ01707048 - 02_本編.m4a</a>
            <a href="https://ts.buzzheavier.com/d/c">RJ01707048 - 03_終章.m4a</a>
            <a href="https://bad.example/installer.exe">RJ01707048.exe</a>
            <a href="https://bad.example/other.m4a">RJ999999.m4a</a>
        """.trimIndent(), base), "RJ01707048")
        assertEquals(3, downloads.size)
        val root = parseJapaneseAsmrTree(document, "RJ01707048", downloads).single()
        assertEquals("japaneseasmr.com", root.title)
        val leaves = root.children.orEmpty()
        assertEquals(3, leaves.size)
        assertEquals(listOf(106.0, 912.0, null), leaves.map { it.duration })
        val references = leaves.map { ChapterMediaReference.parse(it.playbackUrl!!)!! }
        assertEquals(listOf(0L, 106000L, 1018000L), references.map { it.startMs })
        assertEquals(listOf(106000L, 1018000L, null), references.map { it.endMs })
        assertEquals(downloads.map { it.url }, leaves.map { it.downloadUrl })
        assertEquals(3, leaves.map { it.playbackUrl }.distinct().size)
        assertTrue(japaneseAsmrPageMatches(document, "RJ01707048"))
        assertFalse(japaneseAsmrPageMatches(document, "RJ0170704"))
    }

    @Test fun missingDownloadsStillPlayButNeverDownloadTheHlsManifest() {
        val leaves = parseJapaneseAsmrTree(Jsoup.parse(page, base), "RJ01707048", emptyList()).single().children!!
        assertTrue(leaves.all { it.playbackUrl != null && it.downloadUrl == null })
    }

    @Test fun invalidTimesAreIgnoredAndLeadingContentIsPreserved() {
        val html = page.replace("data-value=\"0\"", "data-value=\"NaN\"")
            .replace("data-value=\"1018\"", "data-value=\"-1\"")
        val leaves = parseJapaneseAsmrTree(Jsoup.parse(html, base), "RJ01707048", emptyList()).single().children!!
        assertEquals(2, leaves.size)
        assertEquals(0L, ChapterMediaReference.parse(leaves[0].playbackUrl!!)!!.startMs)
        assertEquals(106000L, ChapterMediaReference.parse(leaves[1].playbackUrl!!)!!.startMs)
    }
}
