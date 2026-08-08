package com.asmr.player.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TreeFileTypeResolverTest {
    @Test
    fun treeFileTypeForName_videoExtensions() {
        assertEquals(TreeFileType.Video, treeFileTypeForName("a.mp4"))
        assertEquals(TreeFileType.Video, treeFileTypeForName("a.mkv"))
        assertEquals(TreeFileType.Video, treeFileTypeForName("a.webm"))
        assertEquals(TreeFileType.Video, treeFileTypeForName("a.mov"))
        assertEquals(TreeFileType.Video, treeFileTypeForName("a.m4v"))
    }

    @Test
    fun treeFileTypeForName_keepsLeadingHashInLocalFileName() {
        assertEquals(TreeFileType.Audio, treeFileTypeForName("#2,track.wav"))
        assertEquals(TreeFileType.Audio, treeFileTypeForName("album/#1.track.flac"))
    }

    @Test
    fun treeFileTypeForName_stripsUrlQueryAndFragment() {
        assertEquals(TreeFileType.Audio, treeFileTypeForName("https://example.com/track.wav?token=1"))
        assertEquals(TreeFileType.Audio, treeFileTypeForName("https://example.com/track.wav#t=1"))
    }

    @Test
    fun treeFileTypeForNode_prefersUrlWhenRecognized() {
        assertEquals(
            TreeFileType.Text,
            treeFileTypeForNode(title = "unknown", url = "https://example.com/readme.txt?x=1")
        )
        assertEquals(
            TreeFileType.Subtitle,
            treeFileTypeForNode(title = "unknown", url = "https://example.com/sub.vtt#t=1")
        )
    }

    @Test
    fun treeFileTypeForNode_fallbackToAudioWhenUrlPresentButUnknown() {
        assertEquals(
            TreeFileType.Audio,
            treeFileTypeForNode(title = "unknown", url = "https://example.com/resource")
        )
    }
}
