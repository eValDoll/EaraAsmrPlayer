package com.asmr.player.data.remote.download

import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.ui.library.flattenAsmrOneLeafDownloads
import com.asmr.player.util.ChapterMediaReference
import org.junit.Assert.*
import org.junit.Test

class JapaneseAsmrHlsDownloadRequestTest {
    private fun chapter(name: String, start: Long, end: Long?, download: String?) =
        ChapterMediaReference("https://audio.example/work.m3u8", start, end, download, name)

    private fun node(reference: ChapterMediaReference) = AsmrOneTrackNodeResponse(
        title = reference.fileName, type = "audio", streamUrl = reference.encode(),
        mediaDownloadUrl = reference.downloadUrl,
    )

    @Test fun missingFileLinkStillEnqueuesTheChapterWithItsBoundaries() {
        val first = chapter("01_T0.m4a", 0, 239_000L, null)
        val last = chapter("02_T1.m4a", 239_000L, null, "https://buzzheavier.com/expired")
        val tree = listOf(AsmrOneTrackNodeResponse(title = "japaneseasmr.com", type = "folder",
            children = listOf(node(first), node(last))))
        val downloads = flattenAsmrOneLeafDownloads(tree)
        assertEquals(2, downloads.size)
        assertEquals("japaneseasmr.com/01_T0.m4a", downloads[0].relativePath)
        assertEquals(first, ChapterMediaReference.parse(downloads[0].url))
        assertEquals(last, ChapterMediaReference.parse(downloads[1].url))
    }

    @Test fun ordinaryDownloadsKeepTheirOriginalUrl() {
        val direct = "https://files.example/track.flac"
        val downloads = flattenAsmrOneLeafDownloads(listOf(
            AsmrOneTrackNodeResponse(title = "track.flac", type = "audio", mediaDownloadUrl = direct),
        ))
        assertEquals(direct, downloads.single().url)
    }

    @Test fun fileLinkIsPreservedEvenWhenOnlyTheApiFieldContainsIt() {
        val reference = chapter("track.m4a", 0, null, null)
        val leaf = node(reference).copy(mediaDownloadUrl = "https://buzzheavier.com/file")
        assertEquals("https://buzzheavier.com/file", ChapterMediaReference.parse(leaf.downloadUrl!!)!!.downloadUrl)
    }

    @Test fun onlineAudioChoiceIsPersistedOnlyAfterConfirmation() {
        val reference = chapter("track.m4a", 10_000L, null, null)
        assertFalse(ChapterMediaReference.parse(reference.encode())!!.useHlsDownload)
        val confirmed = reference.copy(useHlsDownload = true)
        assertEquals(confirmed, ChapterMediaReference.parse(confirmed.encode()))
    }
}
