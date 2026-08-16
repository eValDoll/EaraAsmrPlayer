package com.asmr.player.ui.library

import com.asmr.player.domain.model.Album
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumStatsFormattingTest {
    @Test
    fun onlineStats_keepThreeSlotsWhenValuesAreMissing() {
        val album = Album(
            id = 1L,
            title = "测试作品",
            path = "/tmp/album",
            ratingValue = 4.9,
            ratingCount = 12,
            priceJpy = 0,
            releaseDate = "",
        )

        val stats = album.formatAlbumStats(
            includeDownloadCount = false,
            usePlaceholders = true,
        )

        assertEquals("★4.9(12)  ¥—", stats.leading)
        assertEquals("—", stats.date)
    }

    @Test
    fun localStats_remainEmptyWhenNoValuesAreAvailable() {
        val album = Album(
            id = 1L,
            title = "测试作品",
            path = "/tmp/album",
        )

        assertEquals(
            AlbumStatsText(leading = "", date = ""),
            album.formatAlbumStats(
                includeDownloadCount = true,
                usePlaceholders = false,
            ),
        )
    }
}
