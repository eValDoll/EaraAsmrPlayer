package com.asmr.player.ui.library

import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDetailDirectorySupportTest {

    @Test
    fun buildBreadcrumbSegments_preservesHierarchyOrder() {
        val result = buildBreadcrumbSegments("disc1/cd2/finale")

        assertEquals(listOf("disc1", "cd2", "finale"), result.map { it.label })
        assertEquals(
            listOf("disc1", "disc1/cd2", "disc1/cd2/finale"),
            result.map { it.path }
        )
    }

    @Test
    fun folderPathPrefixes_returnsEachParentPrefix() {
        assertEquals(
            listOf("disc1", "disc1/cd2", "disc1/cd2/finale"),
            folderPathPrefixes("disc1/cd2/finale")
        )
    }

    @Test
    fun buildLocalDirectoryBrowser_preservesCachedLocalSizeBytes() {
        val track = Track(
            albumId = 7L,
            title = "Track 1",
            path = "/album/disc1/track1.mp3",
            duration = 12.0
        )
        val album = Album(
            id = 7L,
            title = "Album",
            path = "/album",
            tracks = listOf(track)
        )
        val index = buildLocalTreeIndexFromLeaves(
            leaves = listOf(
                LocalTreeLeafCacheEntry(
                    relativePath = "disc1/track1.mp3",
                    absolutePath = track.path,
                    fileType = TreeFileType.Audio,
                    sizeBytes = 2_048L
                )
            ),
            tracks = album.tracks
        )

        val browser = buildLocalDirectoryBrowser(
            index = index,
            currentPath = "disc1",
            album = album,
            shouldShowSubtitleStamp = { false }
        )

        assertEquals(
            FileSizeSource.Local(path = track.path, sizeBytes = 2_048L),
            browser.files.single().sizeSource
        )
    }

    @Test
    fun flattenAsmrOneTracksForUi_matchesSubtitleFromOtherFolderUnderSameRoot() {
        val tree = listOf(
            AsmrOneTrackNodeResponse(
                title = "disc1",
                children = listOf(
                    AsmrOneTrackNodeResponse(
                        title = "01 Track A.mp3",
                        mediaDownloadUrl = "https://example.com/audio/01.mp3"
                    )
                )
            ),
            AsmrOneTrackNodeResponse(
                title = "lyrics",
                children = listOf(
                    AsmrOneTrackNodeResponse(
                        title = "01 Track A.lrc",
                        mediaDownloadUrl = "https://example.com/subs/01.lrc"
                    )
                )
            )
        )

        val leaves = flattenAsmrOneTracksForUi(tree)
        val target = leaves.single { it.title == "01 Track A" }

        assertTrue(target.subtitles.isNotEmpty())
        assertEquals("https://example.com/subs/01.lrc", target.subtitles.first().url)
    }

    @Test
    fun buildRemoteTreeIndex_matchesSubtitleFromOtherFolderUnderSameRoot() {
        val album = Album(
            id = 8L,
            title = "Album",
            path = "web://rj/RJ000001",
            rjCode = "RJ000001"
        )
        val tree = listOf(
            AsmrOneTrackNodeResponse(
                title = "disc1",
                children = listOf(
                    AsmrOneTrackNodeResponse(
                        title = "01 Track A.mp3",
                        mediaDownloadUrl = "https://example.com/audio/01.mp3"
                    )
                )
            ),
            AsmrOneTrackNodeResponse(
                title = "lyrics",
                children = listOf(
                    AsmrOneTrackNodeResponse(
                        title = "01 Track A.lrc",
                        mediaDownloadUrl = "https://example.com/subs/01.lrc"
                    )
                )
            )
        )

        val index = buildRemoteTreeIndex(tree, album)
        val browser = buildRemoteDirectoryBrowser(index, "disc1")
        val file = browser.files.single { it.title == "01 Track A" }

        assertTrue(file.subtitleSources.isNotEmpty())
        assertEquals("https://example.com/subs/01.lrc", file.subtitleSources.first().url)
    }
}
