package com.asmr.player.ui.library

import com.asmr.player.domain.model.Album
import com.asmr.player.ui.nav.AlbumCoverHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AlbumDetailViewModelSupportTest {
    @Test
    fun buildDisplayAlbum_keepsFallbackCvWhenDlsiteInfoHasNoCv() {
        val localAlbum = Album(
            title = "作品",
            path = "",
            cv = "かの仔",
            workId = "RJ01572724",
            rjCode = "RJ01572724"
        )

        val result = buildDisplayAlbum(
            rjCode = "RJ01572724",
            localAlbum = localAlbum,
            dlsiteInfo = localAlbum.copy(cv = ""),
            asmrOneWorkId = null,
            fallbackCv = "かの仔",
            fallbackCoverUrl = ""
        )

        assertEquals("かの仔", result.cv)
        assertTrue(result.rjCode.isNotBlank())
    }
    @Test
    fun mergeDetailHeaderAlbum_preservesListMetadataWhenRequested() {
        val listAlbum = Album(
            title = "列表标题",
            path = "",
            rjCode = "RJ123456",
            cv = "列表CV A, 列表CV B",
            tags = listOf("列表标签"),
            coverUrl = "https://example.com/list.jpg"
        )
        val fetched = listAlbum.copy(
            title = "抓取标题",
            cv = "抓取CV",
            tags = listOf("抓取标签"),
            coverUrl = "https://example.com/fetched.jpg"
        )

        val result = mergeDetailHeaderAlbum(
            currentDisplayAlbum = listAlbum,
            localAlbum = null,
            fetchedDlsiteInfo = fetched,
            rjCode = "RJ123456",
            asmrOneWorkId = "789",
            preserveHeaderAlbumMetadata = true
        )

        assertEquals("列表标题", result.title)
        assertEquals("列表CV A, 列表CV B", result.cv)
        assertEquals(listOf("列表标签"), result.tags)
        assertEquals("https://example.com/list.jpg", result.coverUrl)
        assertEquals("789", result.workId)
    }

    @Test
    fun mergeDetailHeaderAlbum_allowsFetchedMetadataWhenNotPreserved() {
        val current = Album(
            title = "RJ123456",
            path = "",
            rjCode = "RJ123456",
            cv = "",
            tags = emptyList()
        )
        val fetched = current.copy(
            title = "抓取标题",
            cv = "抓取CV",
            tags = listOf("抓取标签")
        )

        val result = mergeDetailHeaderAlbum(
            currentDisplayAlbum = current,
            localAlbum = null,
            fetchedDlsiteInfo = fetched,
            rjCode = "RJ123456",
            asmrOneWorkId = null,
            preserveHeaderAlbumMetadata = false
        )

        assertEquals("抓取标题", result.title)
        assertEquals("抓取CV", result.cv)
        assertEquals(listOf("抓取标签"), result.tags)
    }

    @Test
    fun shouldPreserveHeaderAlbumMetadata_requiresResolvedDlsiteHint() {
        val partialHint = AlbumCoverHint(
            title = "列表标题",
            rjCode = "RJ123456",
            circle = "社团",
            cv = "CV",
            tags = listOf("标签"),
            coverUrl = "https://example.com/list.jpg",
            ratingValue = null,
            ratingCount = 0,
            releaseDate = "",
            dlCount = 0,
            priceJpy = 0,
            hasAsmrOne = false,
            description = "",
            hasResolvedDlsiteInfo = false,
            localAlbum = null
        )
        val resolvedHint = partialHint.copy(hasResolvedDlsiteInfo = true)

        assertFalse(shouldPreserveHeaderAlbumMetadata(partialHint))
        assertTrue(shouldPreserveHeaderAlbumMetadata(resolvedHint))
    }

    @Test
    fun withUpdatedLocalCover_updatesDisplayAlbumWhenLocalAlbumMatches() {
        val localAlbum = Album(
            id = 42L,
            title = "本地作品",
            path = "/local/RJ123456",
            coverPath = "/old/cover.jpg",
            coverThumbPath = "/old/thumb.jpg"
        )
        val displayAlbum = Album(
            id = 0L,
            title = "展示作品",
            path = "web://rj/RJ123456",
            coverPath = "",
            coverThumbPath = ""
        )
        val model = AlbumDetailModel(
            baseRjCode = "RJ123456",
            rjCode = "RJ123456",
            listenTogetherRjListenerCount = null,
            displayAlbum = displayAlbum,
            localAlbum = localAlbum,
            dlsiteInfo = null,
            dlsiteGalleryUrls = emptyList(),
            dlsiteTrialTracks = emptyList(),
            dlsiteRecommendations = com.asmr.player.data.remote.scraper.DlsiteRecommendations(),
            dlsiteWorkno = "",
            dlsitePlayWorkno = "",
            dlsiteEditions = emptyList(),
            dlsiteSelectedLang = "",
            hasResolvedInitialDlsiteTarget = false,
            hasLoadedInitialDlsiteContent = false,
            hasResolvedAsmrOneContent = false,
            hasResolvedDlsitePlayContent = false,
            preserveHeaderAlbumMetadata = false,
            isDlsiteLanguageUserSelected = false,
            asmrOneWorkId = null,
            asmrOneSite = null,
            asmrOneTree = emptyList(),
            dlsitePlayTree = emptyList(),
            isLoadingDlsite = false,
            isLoadingDlsiteTrial = false,
            isLoadingAsmrOne = false,
            isLoadingDlsitePlay = false
        )

        val result = model.withUpdatedLocalCover(
            albumId = 42L,
            coverPath = "/new/cover.jpg",
            coverThumbPath = ""
        )

        assertEquals("/new/cover.jpg", result.localAlbum?.coverPath)
        assertEquals("", result.localAlbum?.coverThumbPath)
        assertEquals("/new/cover.jpg", result.displayAlbum.coverPath)
        assertEquals("", result.displayAlbum.coverThumbPath)
    }

    @Test
    fun stableAlbumHeroCoverSource_updatesWhenLocalCoverPathChanges() {
        val current = resolveStableAlbumHeroCoverSource(
            stable = "https://example.com/old.jpg",
            currentLocal = "/albums/RJ123456/new-cover.jpg",
            current = "/albums/RJ123456/new-cover.jpg"
        )

        assertEquals("/albums/RJ123456/new-cover.jpg", current)
    }
}
