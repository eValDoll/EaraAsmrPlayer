package com.asmr.player.ui.library

import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.local.db.entities.OnlineSavedResourceEntity
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlbumDetailDirectorySupportTest {

    @Test
    fun combineLocalTreeCacheStamp_changesWhenDatabaseTracksArrive() {
        val withoutTracks = combineLocalTreeCacheStamp(albumPathsStamp = 42L, tracks = emptyList())
        val withTracks = combineLocalTreeCacheStamp(
            albumPathsStamp = 42L,
            tracks = listOf(
                Track(albumId = 7L, title = "01", path = "/album/mp3/01.mp3")
            )
        )

        assertNotEquals(withoutTracks, withTracks)
    }

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
    fun directorySelectedItemPosition_joinsAdjacentSelectedRows() {
        assertEquals(
            DirectoryFolderPosition.First,
            directorySelectedItemPosition(
                selected = true,
                previousSelected = false,
                nextSelected = true,
            )
        )
        assertEquals(
            DirectoryFolderPosition.Middle,
            directorySelectedItemPosition(
                selected = true,
                previousSelected = true,
                nextSelected = true,
            )
        )
        assertEquals(
            DirectoryFolderPosition.Last,
            directorySelectedItemPosition(
                selected = true,
                previousSelected = true,
                nextSelected = false,
            )
        )
    }

    @Test
    fun localTreeDeletionPaths_rejectTraversalAndMatchDirectoryDescendants() {
        assertEquals("disc1/booklet/cover.jpg", normalizeLocalTreeRelativePath("/disc1\\booklet/cover.jpg"))
        assertEquals(null, normalizeLocalTreeRelativePath("disc1/../cover.jpg"))
        assertTrue(
            localTreePathMatchesTarget(
                candidatePath = "disc1/booklet/cover.jpg",
                targetPath = "disc1/booklet",
                targetIsDirectory = true,
            )
        )
        assertFalse(
            localTreePathMatchesTarget(
                candidatePath = "disc1/booklet-old/cover.jpg",
                targetPath = "disc1/booklet",
                targetIsDirectory = true,
            )
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
    fun buildLocalDirectoryBrowser_exposesFolderDeletionMetadata() {
        val localTrack = Track(
            id = 11L,
            albumId = 7L,
            title = "Track 1",
            path = "/album/disc1/track1.mp3",
        )
        val index = buildLocalTreeIndexFromLeaves(
            leaves = listOf(
                LocalTreeLeafCacheEntry(
                    relativePath = "disc1/track1.mp3",
                    absolutePath = localTrack.path,
                    fileType = TreeFileType.Audio,
                ),
                LocalTreeLeafCacheEntry(
                    relativePath = "disc1/cover.jpg",
                    absolutePath = "/album/disc1/cover.jpg",
                    fileType = TreeFileType.Image,
                ),
            ),
            tracks = listOf(localTrack),
        )

        val folder = buildLocalDirectoryBrowser(
            index = index,
            currentPath = "",
            album = Album(id = 7L, title = "Album", path = "/album"),
            shouldShowSubtitleStamp = { false },
        ).folders.single()

        assertEquals(listOf(11L), folder.descendantTrackIds)
        assertTrue(folder.hasLocalContent)
    }

    @Test
    fun collectSubtitleGenerationTracks_includesMp3AndWavRecursively() {
        val tracks = listOf(
            Track(id = 1L, albumId = 7L, title = "root mp3", path = "/album/root.mp3"),
            Track(id = 2L, albumId = 7L, title = "nested wav", path = "/album/disc/nested.wav"),
            Track(id = 3L, albumId = 7L, title = "nested flac", path = "/album/disc/nested.flac"),
            Track(id = 4L, albumId = 7L, title = "online mp3", path = "https://example.com/online.mp3")
        )
        val index = buildLocalTreeIndexFromLeaves(
            leaves = listOf(
                LocalTreeLeafCacheEntry("root.mp3", tracks[0].path, TreeFileType.Audio),
                LocalTreeLeafCacheEntry("disc/nested.wav", tracks[1].path, TreeFileType.Audio),
                LocalTreeLeafCacheEntry("disc/nested.flac", tracks[2].path, TreeFileType.Audio),
                LocalTreeLeafCacheEntry("disc/online.mp3", tracks[3].path, TreeFileType.Audio)
            ),
            tracks = tracks
        )

        val rootResult = collectSubtitleGenerationTracks(
            index = index,
            currentPath = "",
            unavailableTrackIds = emptySet()
        )
        val nestedResult = collectSubtitleGenerationTracks(
            index = index,
            currentPath = "disc",
            unavailableTrackIds = setOf(2L)
        )

        assertEquals(listOf(2L, 1L), rootResult.map { it.id })
        assertTrue(nestedResult.isEmpty())
    }

    @Test
    fun subtitleGenerationTrackForFile_rejectsUnsupportedAndExistingSubtitles() {
        val track = Track(
            id = 9L,
            albumId = 7L,
            title = "track",
            path = "/album/track.mp3"
        )
        val file = DirectoryFileItem(
            path = "track.mp3",
            title = "track",
            fileType = TreeFileType.Audio,
            isPlayable = true,
            sizeSource = FileSizeSource.Local(track.path),
            absolutePath = track.path,
            track = track
        )

        assertEquals(track, subtitleGenerationTrackForFile(file, emptySet()))
        assertEquals(null, subtitleGenerationTrackForFile(file, setOf(track.id)))
        assertEquals(
            null,
            subtitleGenerationTrackForFile(file.copy(path = "track.flac"), emptySet())
        )
    }

    @Test
    fun subtitleTranslationTrackForFile_requiresLocalSubtitles() {
        val track = Track(
            id = 10L,
            albumId = 7L,
            title = "track",
            path = "/album/track.mp3"
        )
        val file = DirectoryFileItem(
            path = "track.mp3",
            title = "track",
            fileType = TreeFileType.Audio,
            isPlayable = true,
            sizeSource = FileSizeSource.Local(track.path),
            absolutePath = track.path,
            track = track
        )

        assertEquals(track, subtitleTranslationTrackForFile(file, setOf(track.id)))
        assertEquals(null, subtitleTranslationTrackForFile(file, emptySet()))
        assertEquals(
            null,
            subtitleTranslationTrackForFile(
                file.copy(isOnline = true, sizeSource = FileSizeSource.Remote("https://example.com/track.mp3")),
                setOf(track.id)
            )
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
    fun collectRemoteTreeImageFiles_includesImagesFromEveryDirectory() {
        val album = Album(
            id = 8L,
            title = "Album",
            path = "web://rj/RJ000001",
            rjCode = "RJ000001"
        )
        val tree = listOf(
            AsmrOneTrackNodeResponse(
                title = "booklet",
                children = listOf(
                    AsmrOneTrackNodeResponse(
                        title = "page02.png",
                        mediaDownloadUrl = "https://example.com/booklet/page02.png"
                    ),
                    AsmrOneTrackNodeResponse(
                        title = "page01.webp",
                        mediaDownloadUrl = "https://example.com/booklet/page01.webp"
                    ),
                    AsmrOneTrackNodeResponse(
                        title = "track.mp3",
                        mediaDownloadUrl = "https://example.com/booklet/track.mp3"
                    )
                )
            ),
            AsmrOneTrackNodeResponse(
                title = "cover.jpg",
                mediaDownloadUrl = "https://example.com/cover.jpg"
            )
        )

        val images = collectRemoteTreeImageFiles(buildRemoteTreeIndex(tree, album))

        assertEquals(
            listOf("booklet/page01.webp", "booklet/page02.png", "cover.jpg"),
            images.map { it.path }
        )
        assertTrue(images.all { it.fileType == TreeFileType.Image && it.isOnline })
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
    fun flattenAsmrOneLeafDownloads_preservesDlsitePlayImageDescrambleMetadata() {
        val leaf = flattenAsmrOneLeafDownloads(
            listOf(
                AsmrOneTrackNodeResponse(
                    title = "01.パッケージ.jpg",
                    mediaDownloadUrl = "https://play.dlsite.com/optimized/00000abc1234.jpg",
                    dlsitePlayImageCrypt = true,
                    dlsitePlayImageWidth = 1200,
                    dlsitePlayImageHeight = 900,
                    dlsitePlayOptimizedName = "00000abc1234.jpg"
                )
            )
        ).single()

        assertEquals(0xabc1234, leaf.dlsitePlayImageSeed)
        assertEquals(1200, leaf.dlsitePlayImageWidth)
        assertEquals(900, leaf.dlsitePlayImageHeight)
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
    fun downloadableOnlineAudioTrack_onlyReturnsSavedOnlineAudio() {
        val onlineTrack = Track(
            albumId = 9L,
            title = "online",
            path = "https://example.com/online.mp3"
        )
        val baseFile = DirectoryFileItem(
            path = "disc/online.mp3",
            title = "online",
            fileType = TreeFileType.Audio,
            isPlayable = true,
            isOnline = true,
            track = onlineTrack
        )

        assertEquals(onlineTrack, downloadableOnlineAudioTrack(baseFile))
        assertEquals(null, downloadableOnlineAudioTrack(baseFile.copy(isOnline = false)))
        assertEquals(
            null,
            downloadableOnlineAudioTrack(
                baseFile.copy(track = onlineTrack.copy(path = "/album/online.mp3"))
            )
        )
        assertEquals(null, downloadableOnlineAudioTrack(baseFile.copy(fileType = TreeFileType.Video)))
    }

    @Test
    fun resolveExistingRemoteSelectionPaths_distinguishesDownloadedAndSavedFiles() {
        val remoteFiles = listOf(
            RemoteSelectionFileRef("disc/online.mp3", "https://example.com/online.mp3?token=new"),
            RemoteSelectionFileRef("disc/local.mp3", "https://example.com/local.mp3")
        )
        val localFiles = listOf(
            LocalSelectionFileRef(
                relativePath = "disc/online.mp3",
                absolutePath = "https://example.com/online.mp3?token=old",
                track = Track(
                    albumId = 9L,
                    title = "online",
                    path = "https://example.com/online.mp3?token=old",
                    group = "disc"
                )
            ),
            LocalSelectionFileRef(
                relativePath = "disc/local.mp3",
                absolutePath = "/album/disc/local.mp3",
                track = Track(
                    albumId = 9L,
                    title = "local",
                    path = "/album/disc/local.mp3",
                    group = "disc"
                )
            )
        )

        assertEquals(
            setOf("disc/local.mp3"),
            resolveExistingRemoteSelectionPaths(
                remoteFiles = remoteFiles,
                localFiles = localFiles,
                includeOnlineFiles = false
            )
        )
        assertEquals(
            setOf("disc/online.mp3", "disc/local.mp3"),
            resolveExistingRemoteSelectionPaths(
                remoteFiles = remoteFiles,
                localFiles = localFiles,
                includeOnlineFiles = true
            )
        )
    }

    @Test
    fun resolveExistingRemoteSelectionPaths_consumesImportedFallbackMatchesOnce() {
        val remoteFiles = listOf(
            RemoteSelectionFileRef("disc1/same.mp3", "https://example.com/disc1/same.mp3"),
            RemoteSelectionFileRef("disc2/same.mp3", "https://example.com/disc2/same.mp3")
        )
        val localFiles = listOf(
            LocalSelectionFileRef(
                relativePath = "external/same.mp3",
                absolutePath = "/import/external/same.mp3",
                track = Track(
                    albumId = 9L,
                    title = "same",
                    path = "/import/external/same.mp3",
                    group = "external"
                )
            )
        )

        assertEquals(
            setOf("disc1/same.mp3"),
            resolveExistingRemoteSelectionPaths(
                remoteFiles = remoteFiles,
                localFiles = localFiles,
                includeOnlineFiles = false
            )
        )
    }

    @Test
    fun resolveExistingRemoteSelectionPaths_matchesWavInsteadOfEarlierMp3WithSameTitle() {
        val remoteFiles = listOf(
            RemoteSelectionFileRef("remote/same.mp3", "https://example.com/remote/same.mp3"),
            RemoteSelectionFileRef("remote/same.wav", "https://example.com/remote/same.wav")
        )
        val localFiles = listOf(
            LocalSelectionFileRef(
                relativePath = "imported/same.wav",
                absolutePath = "/import/imported/same.wav",
                track = Track(
                    albumId = 9L,
                    title = "same",
                    path = "/import/imported/same.wav",
                    group = "imported"
                )
            )
        )

        assertEquals(
            setOf("remote/same.wav"),
            resolveExistingRemoteSelectionPaths(
                remoteFiles = remoteFiles,
                localFiles = localFiles,
                includeOnlineFiles = false
            )
        )
    }

    @Test
    fun resolveExistingRemoteSelectionPaths_doesNotMatchTextToAudioWithSameTitle() {
        val remoteFiles = listOf(
            RemoteSelectionFileRef("remote/same.txt", "https://example.com/remote/same.txt"),
            RemoteSelectionFileRef("remote/same.wav", "https://example.com/remote/same.wav")
        )
        val localFiles = listOf(
            LocalSelectionFileRef(
                relativePath = "imported/same.wav",
                absolutePath = "/import/imported/same.wav",
                track = Track(
                    albumId = 9L,
                    title = "same",
                    path = "/import/imported/same.wav",
                    group = "imported"
                )
            )
        )

        assertEquals(
            setOf("remote/same.wav"),
            resolveExistingRemoteSelectionPaths(
                remoteFiles = remoteFiles,
                localFiles = localFiles,
                includeOnlineFiles = false
            )
        )
    }

    @Test
    fun resolveExistingRemoteSelectionPaths_prioritizesLaterExactPathOverEarlierFileNameFallback() {
        val remoteFiles = listOf(
            RemoteSelectionFileRef("disc1/same.mp3", "https://example.com/disc1/same.mp3"),
            RemoteSelectionFileRef("disc2/same.mp3", "https://example.com/disc2/same.mp3")
        )
        val localFiles = listOf(
            LocalSelectionFileRef(
                relativePath = "disc2/same.mp3",
                absolutePath = "/import/disc2/same.mp3",
                track = Track(
                    albumId = 9L,
                    title = "same",
                    path = "/import/disc2/same.mp3",
                    group = "disc2"
                )
            )
        )

        assertEquals(
            setOf("disc2/same.mp3"),
            resolveExistingRemoteSelectionPaths(
                remoteFiles = remoteFiles,
                localFiles = localFiles,
                includeOnlineFiles = false
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
    fun onlineSavedResourceTreeLeaf_keepsLogicalImageWithoutLocalCoverAction() {
        val leaf = onlineSavedResourceTreeLeaf(
            OnlineSavedResourceEntity(
                albumId = 9L,
                relativePath = "booklet/images/scene.jpg",
                url = "https://example.com/scene.jpg",
                fileType = TreeFileType.Image.name
            )
        ) ?: error("resource should be retained")
        val index = buildLocalTreeIndexFromLeaves(leaves = listOf(leaf), tracks = emptyList())
        val browser = buildLocalDirectoryBrowser(
            index = index,
            currentPath = "booklet/images",
            album = Album(id = 9L, title = "album", path = "web://rj/RJ000009"),
            shouldShowSubtitleStamp = { false }
        )

        val file = browser.files.single()
        assertEquals(FileSizeSource.Remote("https://example.com/scene.jpg"), file.sizeSource)
        assertFalse(canSetDirectoryImageAsLocalCover(file))
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
