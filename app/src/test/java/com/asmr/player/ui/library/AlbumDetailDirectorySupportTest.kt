package com.asmr.player.ui.library

import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
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

    @Test
    fun buildDlsiteTrialDownloadTree_keepsOnlyPlayableMediaWithStableNames() {
        val tree = buildDlsiteTrialDownloadTree(
            listOf(
                Track(albumId = 0L, title = "试听音频", path = "https://example.com/trial/audio/sample.mp3"),
                Track(albumId = 0L, title = "试看视频", path = "https://example.com/trial/video/preview.mp4"),
                Track(albumId = 0L, title = "无扩展资源", path = "https://example.com/trial/audio/stream")
            )
        )

        val paths = flattenAsmrOneLeafDownloads(tree).map { it.relativePath }

        assertEquals(
            listOf(
                "01_试听音频.mp3",
                "02_试看视频.mp4",
                "03_无扩展资源.mp3"
            ),
            paths
        )
    }

    @Test
    fun filterDownloadableMediaTree_keepsImagesAndRemovesSubtitles() {
        val filtered = filterDownloadableMediaTree(
            listOf(
                AsmrOneTrackNodeResponse(title = "audio.mp3", mediaDownloadUrl = "https://example.com/audio.mp3"),
                AsmrOneTrackNodeResponse(title = "cover.jpg", mediaDownloadUrl = "https://example.com/cover.jpg"),
                AsmrOneTrackNodeResponse(title = "sub.srt", mediaDownloadUrl = "https://example.com/sub.srt")
            )
        )

        val leafPaths = flattenAsmrOneLeafDownloads(filtered).map { it.relativePath }

        assertEquals(listOf("audio.mp3", "cover.jpg"), leafPaths)
        assertTrue(leafPaths.contains("cover.jpg"))
        assertFalse(leafPaths.contains("sub.srt"))
    }

    @Test
    fun flattenOnlineSaveLeaves_includesResourceFilesButSkipsSubtitles() {
        val leaves = flattenOnlineSaveLeaves(
            listOf(
                AsmrOneTrackNodeResponse(title = "audio.mp3", mediaDownloadUrl = "https://example.com/audio.mp3"),
                AsmrOneTrackNodeResponse(title = "cover.jpg", mediaDownloadUrl = "https://example.com/cover.jpg"),
                AsmrOneTrackNodeResponse(title = "readme.txt", mediaDownloadUrl = "https://example.com/readme.txt"),
                AsmrOneTrackNodeResponse(title = "subtitle.vtt", mediaDownloadUrl = "https://example.com/subtitle.vtt")
            )
        )

        val paths = leaves.map { it.relativePath }

        assertEquals(listOf("audio.mp3", "cover.jpg", "readme.txt"), paths)
        assertEquals(TreeFileType.Audio, leaves.first { it.relativePath == "audio.mp3" }.fileType)
        assertEquals(TreeFileType.Image, leaves.first { it.relativePath == "cover.jpg" }.fileType)
        assertEquals(TreeFileType.Text, leaves.first { it.relativePath == "readme.txt" }.fileType)
        assertFalse(paths.contains("subtitle.vtt"))
    }

    @Test
    fun directoryFileTypeLabel_distinguishesLocalAndOnlineAudio() {
        val local = DirectoryFileItem(
            path = "track.mp3",
            title = "track",
            fileType = TreeFileType.Audio,
            isPlayable = true,
            isOnline = false
        )
        val online = local.copy(isOnline = true)

        assertEquals("本地音频", directoryFileTypeLabel(local))
        assertEquals("在线音频", directoryFileTypeLabel(online))
        assertEquals(
            "图片",
            directoryFileTypeLabel(
                local.copy(fileType = TreeFileType.Image, isOnline = true)
            )
        )
    }

    @Test
    fun buildLocalDirectoryBrowser_marksLogicalSavedAudioAsOnline() {
        val onlineTrack = Track(
            albumId = 9L,
            title = "online",
            path = "https://example.com/online.mp3"
        )
        val localTrack = Track(
            albumId = 9L,
            title = "local",
            path = "/album/local.mp3"
        )
        val index = buildLocalTreeIndexFromLeaves(
            leaves = listOf(
                LocalTreeLeafCacheEntry(
                    relativePath = "online.mp3",
                    absolutePath = "https://example.com/online.mp3",
                    fileType = TreeFileType.Audio
                ),
                LocalTreeLeafCacheEntry(
                    relativePath = "local.mp3",
                    absolutePath = "/album/local.mp3",
                    fileType = TreeFileType.Audio
                )
            ),
            tracks = listOf(onlineTrack, localTrack)
        )

        val browser = buildLocalDirectoryBrowser(
            index = index,
            currentPath = "",
            album = Album(id = 9L, title = "album", path = "/album"),
            shouldShowSubtitleStamp = { false }
        )

        assertEquals("在线音频", directoryFileTypeLabel(browser.files.single { it.title == "online" }))
        assertEquals("本地音频", directoryFileTypeLabel(browser.files.single { it.title == "local" }))
    }

    @Test
    fun buildLocalTreeIndexFromLeaves_keepsSameNameFilesFromDifferentDirectories() {
        val tracks = listOf(
            Track(albumId = 9L, title = "same", path = "/album/one/same.mp3", group = "one"),
            Track(albumId = 9L, title = "same", path = "/album/体验版/same.mp3", group = "体验版")
        )
        val leaves = listOf(
            LocalTreeLeafCacheEntry(
                relativePath = "one/same.mp3",
                absolutePath = "/album/one/same.mp3",
                fileType = TreeFileType.Audio,
                sizeBytes = 100L
            ),
            LocalTreeLeafCacheEntry(
                relativePath = "体验版/same.mp3",
                absolutePath = "/album/体验版/same.mp3",
                fileType = TreeFileType.Audio,
                sizeBytes = 120L
            )
        )

        val index = buildLocalTreeIndexFromLeaves(leaves = leaves, tracks = tracks)
        val flattened = flattenLocalTreeIndex(index, expanded = setOf("one", "体验版"))

        val paths = flattened.entries.filterIsInstance<LocalTreeUiEntry.File>().map { it.path }.sorted()
        assertEquals(listOf("one/same.mp3", "体验版/same.mp3"), paths)
    }
}
