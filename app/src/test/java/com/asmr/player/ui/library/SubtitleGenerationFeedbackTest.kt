package com.asmr.player.ui.library

import com.asmr.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleGenerationFeedbackTest {
    @Test
    fun onlineAudioRequiresDownloadEvenWhenSavedInLibrary() {
        val file = audioFile("https://example.com/track.mp3", local = false)
        assertEquals(null, subtitleGenerationTrackForFile(file, emptySet()))
        assertEquals("批量翻译仅支持本地音频，请先下载音频后重试", subtitleGenerationSelectionRequirementMessage(listOf(file)))
    }

    @Test
    fun localUnsupportedFormatAndUnindexedAudioHaveDifferentRemedies() {
        assertEquals(
            "批量翻译仅支持 MP3/WAV 格式，请选择对应的本地音频",
            subtitleGenerationSelectionRequirementMessage(listOf(audioFile("/album/track.flac")))
        )
        assertEquals(
            "未找到可翻译的本地音轨，请重新扫描本地库后重试",
            subtitleGenerationSelectionRequirementMessage(listOf(audioFile("/album/track.wav").copy(track = null)))
        )
    }

    @Test
    fun emptySelectionAndNonAudioSelectionExplainWhatToSelect() {
        assertEquals("请先选择要翻译的本地 MP3/WAV 音频", subtitleGenerationSelectionRequirementMessage(emptyList()))
        assertEquals(
            "没有可翻译的音频，请选择本地 MP3/WAV 文件",
            subtitleGenerationSelectionRequirementMessage(listOf(audioFile("/album/cover.jpg").copy(fileType = TreeFileType.Image)))
        )
    }

    @Test
    fun directoryReasonIncludesNestedAudioAndRespectsCurrentDirectory() {
        val tracks = listOf(
            Track(id = 1L, albumId = 7L, title = "online", path = "https://example.com/online.mp3"),
            Track(id = 2L, albumId = 7L, title = "local", path = "/album/local/track.flac")
        )
        val index = buildLocalTreeIndexFromLeaves(
            leaves = listOf(
                LocalTreeLeafCacheEntry("online/disc/track.mp3", tracks[0].path, TreeFileType.Audio),
                LocalTreeLeafCacheEntry("local/track.flac", tracks[1].path, TreeFileType.Audio),
                LocalTreeLeafCacheEntry("booklet/cover.jpg", "/album/booklet/cover.jpg", TreeFileType.Image)
            ),
            tracks = tracks
        )
        assertTrue(collectSubtitleGenerationTracks(index, "", emptySet()).isEmpty())
        assertEquals("批量翻译仅支持本地音频，请先下载音频后重试", subtitleGenerationDirectoryRequirementMessage(index, "online"))
        assertEquals("批量翻译仅支持 MP3/WAV 格式，请选择对应的本地音频", subtitleGenerationDirectoryRequirementMessage(index, "local"))
        assertEquals("没有可翻译的音频，请选择本地 MP3/WAV 文件", subtitleGenerationDirectoryRequirementMessage(index, "booklet"))
    }

    private fun audioFile(path: String, local: Boolean = true): DirectoryFileItem = DirectoryFileItem(
        path = path.substringAfterLast('/'),
        title = "音频",
        fileType = TreeFileType.Audio,
        isPlayable = true,
        isOnline = !local,
        absolutePath = path,
        sizeSource = if (local) FileSizeSource.Local(path) else FileSizeSource.Remote(path),
        track = Track(id = 1L, albumId = 7L, title = "音频", path = path)
    )
}
