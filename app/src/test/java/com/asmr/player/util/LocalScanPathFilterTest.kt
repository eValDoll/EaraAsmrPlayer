package com.asmr.player.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalScanPathFilterTest {
    @Test
    fun trashAndHiddenDirectories_areExcludedFromLibraryScanning() {
        assertFalse(isScannableLocalDirectoryName(".fmtrashed-1720000000000"))
        assertFalse(isScannableLocalDirectoryName(".Trash"))
        assertFalse(isScannableLocalDirectoryName("\$RECYCLE.BIN"))
        assertFalse(isScannableLocalStorageEntry("voice/.fmtrashed-old/deleted.mp3", isDirectory = false))
    }

    @Test
    fun regularAlbumDirectories_areScannable() {
        assertTrue(isScannableLocalDirectoryName("RJ12345678"))
        assertTrue(isScannableLocalStorageEntry("RJ12345678/voice/track01.mp3", isDirectory = false))
    }
}
