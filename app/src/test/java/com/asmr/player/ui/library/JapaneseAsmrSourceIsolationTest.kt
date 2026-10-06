package com.asmr.player.ui.library

import com.asmr.player.domain.model.Track
import com.asmr.player.util.ChapterMediaReference
import org.junit.Assert.*
import org.junit.Test

class JapaneseAsmrSourceIsolationTest {
    @Test fun sameNamedFilesFromOtherSourcesDoNotDisableJapaneseAsmrActions() {
        val remote = RemoteSelectionFileRef("japaneseasmr.com/01_導入.m4a", "https://ts.buzzheavier.com/d/file")
        val otherSource = LocalSelectionFileRef("01_導入.m4a", "/album/01_導入.m4a",
            Track(albumId = 1, title = "01_導入", path = "/album/01_導入.m4a"))
        assertTrue(resolveExistingRemoteSelectionPaths(listOf(remote), listOf(otherSource), true).isEmpty())
        val ownSource = otherSource.copy(relativePath = remote.relativePath, absolutePath = "/album/${remote.relativePath}",
            track = otherSource.track!!.copy(path = "/album/${remote.relativePath}", group = "japaneseasmr.com"))
        assertEquals(setOf(remote.relativePath), resolveExistingRemoteSelectionPaths(listOf(remote), listOf(ownSource), false))
    }

    @Test fun savedChaptersMatchTheirOwnDownloadAndKeepOtherChaptersSelectable() {
        val reference = ChapterMediaReference("https://audio.example/work.m3u8", 0, 106000,
            "https://ts.buzzheavier.com/d/first", "01_導入.m4a")
        val saved = LocalSelectionFileRef("japaneseasmr.com/01_導入.m4a", reference.encode(),
            Track(albumId = 1, title = "01_導入", path = reference.encode(), group = "japaneseasmr.com"))
        val remotes = listOf(
            RemoteSelectionFileRef("japaneseasmr.com/01_導入.m4a", reference.downloadUrl!!),
            RemoteSelectionFileRef("japaneseasmr.com/02_本編.m4a", "https://ts.buzzheavier.com/d/second"))
        assertEquals(setOf(remotes[0].relativePath), resolveExistingRemoteSelectionPaths(remotes, listOf(saved), true))
        assertTrue(resolveExistingRemoteSelectionPaths(remotes, listOf(saved), false).isEmpty())
    }
}
