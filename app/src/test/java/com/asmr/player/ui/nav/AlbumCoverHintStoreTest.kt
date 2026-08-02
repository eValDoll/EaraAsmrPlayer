package com.asmr.player.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumCoverHintStoreTest {
    @Test
    fun albumFromCoverHint_keepsResolvedListMetadataForDetailHeader() {
        val hint = AlbumCoverHint(
            title = "作品",
            rjCode = "RJ123456",
            circle = "社团",
            cv = "Alice, Bob",
            tags = listOf("耳语", "安眠"),
            coverUrl = "https://example.com/cover.jpg",
            ratingValue = 4.8,
            ratingCount = 42,
            releaseDate = "2026-06-19",
            dlCount = 1200,
            priceJpy = 990,
            hasAsmrOne = true,
            description = "简介",
            hasResolvedDlsiteInfo = true,
            localAlbum = null
        )

        val album = albumFromCoverHint("", hint)

        assertEquals("RJ123456", album.rjCode)
        assertEquals("Alice, Bob", album.cv)
        assertEquals(listOf("耳语", "安眠"), album.tags)
        assertEquals(4.8, album.ratingValue ?: 0.0, 0.0)
        assertEquals(42, album.ratingCount)
        assertEquals("2026-06-19", album.releaseDate)
        assertTrue(album.hasAsmrOne)
    }
}
