package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleMatchSupportTest {
    @Test
    fun matchBest_matchesSameBasenameFromDifferentFolderUnderRoot() {
        val candidates = listOfNotNull(
            SubtitleMatchSupport.inferCandidate("lyrics/01 Track A.lrc", "lyrics/01 Track A.lrc")
        )

        val matched = SubtitleMatchSupport.matchBest("disc1/01 Track A", candidates)

        assertEquals("lyrics/01 Track A.lrc", matched?.relativePath)
    }

    @Test
    fun matchBest_prefersNearestDirectoryWhenMultipleFoldersHaveSameSubtitleName() {
        val candidates = listOfNotNull(
            SubtitleMatchSupport.inferCandidate("lyrics/01 Track A.lrc", "lyrics/01 Track A.lrc"),
            SubtitleMatchSupport.inferCandidate("disc1/subs/01 Track A.lrc", "disc1/subs/01 Track A.lrc")
        )

        val matched = SubtitleMatchSupport.matchBest("disc1/audio/01 Track A", candidates)

        assertEquals("disc1/subs/01 Track A.lrc", matched?.relativePath)
    }

    @Test
    fun matchBest_prefersDefaultLanguageBeforeZhAndEn() {
        val candidates = listOfNotNull(
            SubtitleMatchSupport.inferCandidate("lyrics/01 Track A.en.lrc", "lyrics/01 Track A.en.lrc"),
            SubtitleMatchSupport.inferCandidate("lyrics/01 Track A.zh.lrc", "lyrics/01 Track A.zh.lrc"),
            SubtitleMatchSupport.inferCandidate("lyrics/01 Track A.lrc", "lyrics/01 Track A.lrc")
        )

        val matched = SubtitleMatchSupport.matchBest("disc1/01 Track A", candidates)

        assertEquals("lyrics/01 Track A.lrc", matched?.relativePath)
    }

    @Test
    fun matchBest_supportsSubtitleNameThatIncludesMediaExtension() {
        val candidates = listOfNotNull(
            SubtitleMatchSupport.inferCandidate("lyrics/01 Track A.mp3.zh.lrc", "lyrics/01 Track A.mp3.zh.lrc")
        )

        val matched = SubtitleMatchSupport.matchBest("disc1/01 Track A", candidates)

        assertEquals("lyrics/01 Track A.mp3.zh.lrc", matched?.relativePath)
    }

    @Test
    fun matchBest_returnsNullWhenBasenameDoesNotMatch() {
        val candidates = listOfNotNull(
            SubtitleMatchSupport.inferCandidate("lyrics/02 Track B.lrc", "lyrics/02 Track B.lrc")
        )

        val matched = SubtitleMatchSupport.matchBest("disc1/01 Track A", candidates)

        assertNull(matched)
    }
}
