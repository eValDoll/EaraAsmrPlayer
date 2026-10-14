package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {
    @Test
    fun replacesEveryInvalidPathCharacterWithoutChangingUnicode() {
        assertEquals("声優_音声_________", sanitizeFolderName(" 声優/音声\\:*?\"<>|/ "))
    }

    @Test
    fun blankNamesUseTheExistingFallback() {
        assertEquals("item", sanitizeFolderName(" \t\n "))
    }

    @Test
    fun preservesExistingFolderNamesAndExtensions() {
        assertEquals("01. ささやき (中文).flac", sanitizeFolderName(" 01. ささやき (中文).flac "))
        assertEquals("Disc 1", sanitizeFolderName("Disc 1"))
    }
}
