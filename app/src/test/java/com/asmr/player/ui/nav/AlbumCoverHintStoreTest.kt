package com.asmr.player.ui.nav

import com.asmr.player.domain.model.Album
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumCoverHintStoreTest {
    @Test
    fun invalidateAlbum_removesIdAndEveryLocalAlbumAlias() {
        val album = Album(id = 7101, title = "旧作品", path = "/albums/work", rjCode = "RJ971001")
        AlbumCoverHintStore.recordLocalAlbum(album)
        AlbumCoverHintStore.recordLocalAlbum(album.copy(rjCode = "RJ971002"))
        AlbumCoverHintStore.record(album.id, null, "https://example.com/current.jpg")

        AlbumCoverHintStore.invalidateAlbum(album.id, emptyList())

        assertNull(AlbumCoverHintStore.peekHint(album.id, null))
        assertNull(AlbumCoverHintStore.peekHint(null, "RJ971001"))
        assertNull(AlbumCoverHintStore.peekHint(null, "RJ971002"))
    }

    @Test
    fun invalidateAlbum_removesNormalizedOldAndNewWorkNoHints() {
        AlbumCoverHintStore.record(null, "RJ972001", "https://example.com/old.jpg")
        AlbumCoverHintStore.record(null, "RJ972002", "https://example.com/new.jpg")

        AlbumCoverHintStore.invalidateAlbum(7102, listOf(" rj972001 ", "rj972002", ""))

        assertNull(AlbumCoverHintStore.peekHint(null, "RJ972001"))
        assertNull(AlbumCoverHintStore.peekHint(null, "RJ972002"))
    }

    @Test
    fun invalidateAlbum_keepsUnrelatedAlbumHints() {
        val changed = Album(id = 7103, title = "待修改作品", path = "/albums/changed", rjCode = "RJ973001")
        val unrelated = Album(id = 7104, title = "其他作品", path = "/albums/unrelated", rjCode = "RJ973002")
        AlbumCoverHintStore.recordLocalAlbum(changed)
        AlbumCoverHintStore.recordLocalAlbum(unrelated)
        AlbumCoverHintStore.record(null, "RJ973003", "https://example.com/unrelated.jpg")

        AlbumCoverHintStore.invalidateAlbum(changed.id, listOf(changed.rjCode, "RJ973004"))

        assertNull(AlbumCoverHintStore.peekHint(changed.id, changed.rjCode))
        assertEquals(unrelated, AlbumCoverHintStore.peekHint(unrelated.id, null)?.localAlbum)
        assertEquals(unrelated, AlbumCoverHintStore.peekHint(null, unrelated.rjCode)?.localAlbum)
        assertEquals("https://example.com/unrelated.jpg", AlbumCoverHintStore.peek(null, "RJ973003"))
    }

    @Test
    fun invalidateAlbum_withoutPersistedIdKeepsUnrelatedUnpersistedAlbumHints() {
        val album = Album(title = "在线作品", path = "", rjCode = "RJ974001")
        AlbumCoverHintStore.recordLocalAlbum(album)

        AlbumCoverHintStore.invalidateAlbum(0L, listOf("RJ974002"))

        assertEquals(album, AlbumCoverHintStore.peekHint(null, album.rjCode)?.localAlbum)
    }

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
