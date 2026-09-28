package com.asmr.player.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistMetadataTest {
    @Test
    fun audioRowsOnlyUseCvAndNeverTreatAnUnknownArtistAsCv() {
        assertEquals("声优甲、声优乙", formatStoredCv("社团名 / 声优甲, 声优乙"))
        assertEquals("声优甲、声优乙", formatStoredCv("社团名", "声优甲, 声优乙"))
        assertEquals("", formatStoredCv("社团名"))
        assertEquals("", formatStoredCv("社团名 / 旧声优", ""))
    }
    @Test
    fun normalizesLegacySeparatorsWithoutSplittingSpacesInsideNames() {
        assertEquals(
            "声优甲、声优乙、Alice Smith、声优丙",
            formatCvNames(" 声优甲, 声优乙，Alice Smith / 声优甲\n声优丙； | ")
        )
    }

    @Test
    fun queueAndFavoritesUseTheSameFormatWithoutRepeatingAlbumCv() {
        val expected = "社团名 / 声优甲、声优乙"
        assertEquals(expected, formatArtistMetadata(" 社团名 ", "声优甲, 声优乙"))
        assertEquals(expected, formatStoredArtist("社团名 / 声优甲, 声优乙"))
        assertEquals(expected, formatStoredArtist("社团名 / 声优甲, 声优乙", "声优甲、声优乙"))
        assertEquals(expected, formatStoredArtist("社团名", "声优甲、声优乙"))
        assertEquals("声优甲、声优乙", formatStoredArtist("声优甲, 声优乙", "声优甲、声优乙"))
    }

    @Test
    fun emptyFieldsDoNotLeaveSeparators() {
        assertEquals("", formatArtistMetadata(" ", " ,，、； "))
        assertEquals("社团名", formatArtistMetadata("社团名", ""))
        assertEquals("声优甲", formatStoredArtist("", "声优甲"))
    }
}
