package com.asmr.player.ui.library

import com.asmr.player.util.isMissingLocalDocumentFailure

import com.asmr.player.data.remote.api.Artist
import com.asmr.player.data.remote.api.Circle
import com.asmr.player.data.remote.api.Tag
import com.asmr.player.data.remote.api.WorkDetailsResponse
import com.asmr.player.data.local.db.entities.AlbumEntity
import com.asmr.player.data.local.db.entities.TrackEntity
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.ui.nav.AlbumCoverHint
import com.asmr.player.data.remote.crawler.AlbumResourceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.FileNotFoundException

class AlbumDetailViewModelSupportTest {
    @Test
    fun savedJapaneseAsmrRjAlbum_reopensWithItsSourceBeforeGlobalPreference() {
        val album = Album(
            id = 7L,
            title = "已保存作品",
            path = "web://rj/RJ01707048",
            rjCode = "RJ01707048",
            tracks = listOf(
                Track(
                    albumId = 7L,
                    title = "本編",
                    path = "https://audio.example/stream.m3u8#eara-chapter=0,&file=本編.m4a"
                )
            )
        )

        assertEquals(
            AlbumResourceSource.JapaneseAsmr,
            resolveAlbumResourceSource(resolveAlbumDetailRj(null, album), null, AlbumResourceSource.AsmrOne, album)
        )
    }

    @Test
    fun downloadedJapaneseAsmrRjAlbum_recognizesFileAndDocumentTracks() {
        val tracks = listOf(
            Track(albumId = 7L, title = "本編", path = "/albums/RJ01707048/japaneseasmr.com/本編.m4a"),
            Track(albumId = 7L, title = "本編", path = "D:\\albums\\RJ01707048\\japaneseasmr.com\\本編.m4a"),
            Track(albumId = 7L, title = "本編", path = "content://library/document/audio", group = "japaneseasmr.com"),
            Track(albumId = 7L, title = "本編", path = "https://audio.example/本編.mp3", group = "japaneseasmr.com/本編")
        )

        tracks.forEach { track ->
            val album = Album(title = "已下载作品", path = "/albums/RJ01707048", rjCode = "RJ01707048", tracks = listOf(track))

            assertEquals(
                track.path,
                AlbumResourceSource.JapaneseAsmr,
                resolveAlbumResourceSource(resolveAlbumDetailRj(null, album), null, AlbumResourceSource.AsmrOne, album)
            )
        }
    }

    @Test
    fun localJapaneseAsmrSource_keepsExplicitSourceChoice() {
        val album = Album(
            title = "已保存作品",
            path = "web://rj/RJ01707048",
            tracks = listOf(Track(albumId = 7L, title = "本編", path = "https://audio.example/本編.mp3", group = "japaneseasmr.com"))
        )

        assertEquals(
            AlbumResourceSource.AsmrOne,
            resolveAlbumResourceSource("RJ01707048", AlbumResourceSource.AsmrOne, AlbumResourceSource.JapaneseAsmr, album)
        )
    }

    @Test
    fun importedAndAsmrOneAlbums_keepGlobalSourcePreference() {
        val tracks = listOf(
            Track(albumId = 7L, title = "本編", path = "/albums/RJ01707048/本編.m4a"),
            Track(albumId = 7L, title = "本編", path = "https://asmr.one/media/本編.mp3")
        )

        tracks.forEach { track ->
            val album = Album(title = "本地作品", path = "/albums/RJ01707048", tracks = listOf(track))
            assertEquals(AlbumResourceSource.AsmrOne, resolveAlbumResourceSource("RJ01707048", null, null, album))
            AlbumResourceSource.values().forEach { preferred ->
                assertEquals(preferred, resolveAlbumResourceSource("RJ01707048", null, preferred, album))
            }
        }
    }

    @Test fun dmmLocalAlbumReopensWithJapaneseAsmrAndKeepsExplicitSourceChoice() {
        val album = Album(title = "DMM 作品", path = "web://rj/UND353674", workId = "UND353674", rjCode = "UND353674")
        assertEquals("UND353674", resolveAlbumDetailRj(null, album))
        assertEquals("UND353674", resolveAlbumDetailRj("und353674", null))
        assertEquals(AlbumResourceSource.JapaneseAsmr, resolveAlbumResourceSource("UND353674", null, AlbumResourceSource.AsmrOne))
        assertEquals(AlbumResourceSource.AsmrOne, resolveAlbumResourceSource("UND353674", AlbumResourceSource.AsmrOne, AlbumResourceSource.JapaneseAsmr))
    }

    @Test fun numericLocalAlbumReopensWithJapaneseAsmrAndKeepsExplicitSourceChoice() {
        val album = Album(title = "数字作品", path = "web://rj/UN124393", workId = "UN124393", rjCode = "UN124393")
        assertEquals("UN124393", resolveAlbumDetailRj(null, album))
        assertEquals("UN124393", resolveAlbumDetailRj("un124393", null))
        assertEquals(AlbumResourceSource.JapaneseAsmr, resolveAlbumResourceSource("UN124393", null, AlbumResourceSource.AsmrOne))
        assertEquals(AlbumResourceSource.AsmrOne, resolveAlbumResourceSource("UN124393", AlbumResourceSource.AsmrOne, AlbumResourceSource.JapaneseAsmr))
        assertEquals(AlbumResourceSource.AsmrOne, resolveAlbumResourceSource("RJ124393", null, AlbumResourceSource.AsmrOne))
    }

    @Test
    fun shouldRemoveMissingLocalAlbum_removesOnlyConfirmedMissingPhysicalWork() {
        val album = AlbumEntity(
            id = 7L,
            title = "已删除作品",
            path = "content://library/tree/root/document/root%2Fwork",
            localPath = "content://library/tree/root/document/root%2Fwork",
        )
        val tracks = listOf(
            TrackEntity(id = 11L, albumId = 7L, title = "01", path = "content://library/track/01")
        )

        assertTrue(
            shouldRemoveMissingLocalAlbum(album, tracks) { LocalSourceAvailability.Missing }
        )
        assertFalse(
            shouldRemoveMissingLocalAlbum(album, tracks) { LocalSourceAvailability.Unknown }
        )
    }

    @Test
    fun shouldRemoveMissingLocalAlbum_preservesHybridOnlineWork() {
        val album = AlbumEntity(
            id = 7L,
            title = "混合作品",
            path = "content://library/tree/root/document/root%2Fwork",
            localPath = "content://library/tree/root/document/root%2Fwork",
        )
        val tracks = listOf(
            TrackEntity(id = 11L, albumId = 7L, title = "在线轨道", path = "https://example.com/01.mp3")
        )

        assertFalse(
            shouldRemoveMissingLocalAlbum(album, tracks) { LocalSourceAvailability.Missing }
        )
    }

    @Test
    fun isMissingLocalDocumentFailure_recognizesWrappedProviderFailure() {
        assertTrue(
            isMissingLocalDocumentFailure(
                IllegalArgumentException(
                    "Failed to determine child: java.io.FileNotFoundException: Missing file for root/work"
                )
            )
        )
        assertTrue(
            isMissingLocalDocumentFailure(
                IllegalStateException("provider failure", FileNotFoundException("deleted"))
            )
        )
        assertFalse(isMissingLocalDocumentFailure(SecurityException("permission denied")))
    }

    @Test
    fun albumDetailRequestKey_normalizesRjAndFallsBackToLocalId() {
        assertEquals("rj:RJ01554925", albumDetailRequestKey(42L, " rj01554925 "))
        assertEquals("id:42", albumDetailRequestKey(42L, null))
    }

    @Test
    fun asmrOneTracksCacheKey_isolatesSelectedEndpoint() {
        assertEquals("100:1580085", asmrOneTracksCacheKey(100, " 1580085 "))
        assertEquals("-1:1580085", asmrOneTracksCacheKey(-1, "1580085"))
        assertFalse(
            asmrOneTracksCacheKey(100, "1580085") ==
                asmrOneTracksCacheKey(-1, "1580085")
        )
    }

    @Test
    fun shouldReuseAlbumDetailModel_reusesOnlyCompletedMatchingPage() {
        assertTrue(
            shouldReuseAlbumDetailModel(
                force = false,
                hasCurrentModel = true,
                requestKey = "rj:RJ01554925",
                activeRequestKey = "rj:RJ01554925",
                completedRequestKey = "rj:RJ01554925"
            )
        )
        assertFalse(
            shouldReuseAlbumDetailModel(
                force = false,
                hasCurrentModel = true,
                requestKey = "rj:RJ01554925",
                activeRequestKey = "rj:RJ01554925",
                completedRequestKey = null
            )
        )
        assertFalse(
            shouldReuseAlbumDetailModel(
                force = true,
                hasCurrentModel = true,
                requestKey = "rj:RJ01554925",
                activeRequestKey = "rj:RJ01554925",
                completedRequestKey = "rj:RJ01554925"
            )
        )
    }

    @Test
    fun resolveAlbumDetailRj_fallsBackToImportedAlbumMetadataAndTitle() {
        assertEquals(
            "RJ123456",
            resolveAlbumDetailRj(
                routeRj = null,
                localAlbum = Album(
                    title = "外部导入 RJ123456",
                    path = "/storage/emulated/0/ASMR/作品"
                )
            )
        )
        assertEquals(
            "RJ654321",
            resolveAlbumDetailRj(
                routeRj = null,
                localAlbum = Album(
                    title = "作品",
                    path = "/storage/emulated/0/ASMR/作品",
                    workId = "RJ654321"
                )
            )
        )
        assertEquals(
            "",
            resolveAlbumDetailRj(
                routeRj = null,
                localAlbum = Album(title = "未绑定作品", path = "/storage/emulated/0/ASMR/作品")
            )
        )
    }

    @Test
    fun resolveAlbumDetailRj_supportsBooksAndProfessionalWorkNumbers() {
        assertEquals("BJ02370869", resolveAlbumDetailRj("bj02370869", null))
        assertEquals(
            "VJ01005620",
            resolveAlbumDetailRj(
                routeRj = null,
                localAlbum = Album(title = "导入作品 VJ01005620", path = "/storage/emulated/0/作品")
            )
        )
    }

    @Test
    fun asmrOneTrackRjCandidates_supportsAllKnownDlsitePrefixes() {
        assertEquals(
            listOf("BJ02370869", "VJ01005620", "RJ123456"),
            asmrOneTrackRjCandidates(
                baseRj = "BJ02370869",
                currentRj = "VJ01005620",
                dlsiteWorkno = "invalid",
                originalRj = "RJ123456",
                selectedLang = "JPN",
                preferInitialRj = true
            )
        )
    }

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
    fun withCurrentLocalCover_appliesManualCoverToAnOnlineEntryHint() {
        val hint = Album(
            title = "在线列表标题",
            path = "",
            cv = "在线CV",
            coverUrl = "https://example.com/list.jpg"
        )
        val local = Album(
            id = 42L,
            title = "本地标题",
            path = "/albums/RJ123456",
            coverPath = "/albums/RJ123456/cover.jpg",
            manualCoverPath = "/albums/RJ123456/cover.jpg"
        )

        val result = hint.withCurrentLocalCover(local)

        assertEquals(hint.title, result.title)
        assertEquals(hint.cv, result.cv)
        assertEquals(hint.coverUrl, result.coverUrl)
        assertEquals(local.manualCoverPath, result.coverPath)
        assertEquals(local.manualCoverPath, result.manualCoverPath)
    }

    @Test
    fun mergeDetailHeaderAlbum_preservesOnlineMetadataWhileApplyingManualCover() {
        val hint = Album(
            title = "在线列表标题",
            path = "",
            cv = "在线CV",
            coverUrl = "https://example.com/list.jpg"
        )
        val local = Album(
            id = 42L,
            title = "本地标题",
            path = "/albums/RJ123456",
            coverPath = "/albums/RJ123456/cover.jpg",
            manualCoverPath = "/albums/RJ123456/cover.jpg"
        )

        val result = mergeDetailHeaderAlbum(
            currentDisplayAlbum = hint,
            localAlbum = local,
            fetchedDlsiteInfo = hint.copy(title = "抓取标题"),
            rjCode = "RJ123456",
            asmrOneWorkId = null,
            preserveHeaderAlbumMetadata = true
        )

        assertEquals(hint.title, result.title)
        assertEquals(hint.cv, result.cv)
        assertEquals(hint.coverUrl, result.coverUrl)
        assertEquals(local.manualCoverPath, result.coverPath)
        assertEquals(local.manualCoverPath, result.manualCoverPath)
    }

    @Test
    fun mergeDetailHeaderAlbum_restoresDefaultCoverWhenAHintHasACancelledSelection() {
        val staleHint = Album(
            title = "在线列表标题",
            path = "",
            coverPath = "/albums/manual.jpg",
            manualCoverPath = "/albums/manual.jpg",
            coverThumbPath = "/thumbs/manual_v2.jpg",
            coverUrl = "https://example.com/list.jpg"
        )

        listOf("/albums/cover.jpg", "").forEach { defaultCover ->
            val local = Album(
                id = 42L,
                title = "本地标题",
                path = "/albums/RJ123456",
                coverPath = defaultCover
            )
            listOf(false, true).forEach { preserveMetadata ->
                val result = mergeDetailHeaderAlbum(
                    currentDisplayAlbum = staleHint,
                    localAlbum = local,
                    fetchedDlsiteInfo = null,
                    rjCode = "RJ123456",
                    asmrOneWorkId = null,
                    preserveHeaderAlbumMetadata = preserveMetadata
                )

                assertEquals(defaultCover, result.coverPath)
                assertEquals("", result.manualCoverPath)
                assertEquals("", result.coverThumbPath)
                assertEquals(staleHint.coverUrl, result.coverUrl)
            }
        }
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
    fun mergeDetailHeaderAlbum_keepsResolvedMetadataWhenLateDlsiteDataIsPartial() {
        val resolved = Album(
            title = "ASMR 标题",
            path = "",
            rjCode = "RJ01491538",
            circle = "猫麦",
            cv = "大山チロル, 陽向葵ゅか, 柚木つばめ",
            tags = listOf("纯爱/甜蜜", "后宫", "环绕音"),
            hasAsmrOne = true
        )
        val partialDlsite = Album(
            title = "DLsite 标题",
            path = "",
            rjCode = "RJ01491538",
            coverUrl = "https://example.com/dlsite-cover.jpg"
        )

        val result = mergeDetailHeaderAlbum(
            currentDisplayAlbum = resolved,
            localAlbum = null,
            fetchedDlsiteInfo = partialDlsite,
            rjCode = "RJ01491538",
            asmrOneWorkId = "1491538",
            preserveHeaderAlbumMetadata = false
        )

        assertEquals("DLsite 标题", result.title)
        assertEquals("猫麦", result.circle)
        assertEquals("大山チロル, 陽向葵ゅか, 柚木つばめ", result.cv)
        assertEquals(listOf("纯爱/甜蜜", "后宫", "环绕音"), result.tags)
        assertTrue(result.hasAsmrOne)
    }

    @Test
    fun mergeAsmrOneHeaderAlbum_replacesPlaceholderWithResolvedMetadata() {
        val result = mergeAsmrOneHeaderAlbum(
            currentDisplayAlbum = Album(
                title = "专辑",
                path = "",
                rjCode = "RJ01522140"
            ),
            localAlbum = null,
            fetchedDlsiteInfo = null,
            resolvedAsmrOneDetails = WorkDetailsResponse(
                id = 1522140,
                source_id = "RJ01522140",
                title = "真实作品标题",
                circle = Circle("真实社团"),
                vas = listOf(Artist("声优A"), Artist("声优B")),
                tags = listOf(Tag("治愈"), Tag("耳语")),
                duration = 0,
                mainCoverUrl = "https://example.com/cover.jpg",
                dl_count = 123,
                price = 770
            ),
            rjCode = "RJ01522140",
            asmrOneWorkId = "1522140",
            preserveHeaderAlbumMetadata = false
        )

        assertEquals("真实作品标题", result.title)
        assertEquals("真实社团", result.circle)
        assertEquals("声优A, 声优B", result.cv)
        assertEquals(listOf("治愈", "耳语"), result.tags)
        assertEquals("https://example.com/cover.jpg", result.coverUrl)
        assertEquals("1522140", result.workId)
        assertTrue(result.hasAsmrOne)
    }

    @Test
    fun mergeAsmrOneHeaderAlbum_fillsMissingDlsiteMetadata() {
        val result = mergeAsmrOneHeaderAlbum(
            currentDisplayAlbum = Album(
                title = "专辑",
                path = "",
                rjCode = "RJ01491538"
            ),
            localAlbum = null,
            fetchedDlsiteInfo = Album(
                title = "专辑",
                path = "",
                rjCode = "RJ01491538",
                coverUrl = "https://example.com/dlsite-cover.jpg"
            ),
            resolvedAsmrOneDetails = WorkDetailsResponse(
                id = 1491538,
                source_id = "RJ01491538",
                title = "真实作品标题",
                circle = Circle("猫麦"),
                vas = listOf(Artist("大山チロル"), Artist("陽向葵ゅか"), Artist("柚木つばめ")),
                tags = listOf(Tag("纯爱/甜蜜"), Tag("后宫"), Tag("环绕音")),
                duration = 0,
                mainCoverUrl = "https://example.com/asmr-cover.jpg",
                dl_count = 456,
                price = 990
            ),
            rjCode = "RJ01491538",
            asmrOneWorkId = "1491538",
            preserveHeaderAlbumMetadata = false
        )

        assertEquals("真实作品标题", result.title)
        assertEquals("猫麦", result.circle)
        assertEquals("大山チロル, 陽向葵ゅか, 柚木つばめ", result.cv)
        assertEquals(listOf("纯爱/甜蜜", "后宫", "环绕音"), result.tags)
        assertEquals("https://example.com/dlsite-cover.jpg", result.coverUrl)
        assertTrue(result.hasAsmrOne)
    }

    @Test
    fun resolveStableAlbumHeroIdentity_upgradesPlaceholderWithoutReplacingRealTitle() {
        val placeholder = StableAlbumHeroIdentity(
            title = "专辑",
            rj = "RJ01522140",
            circle = ""
        )
        val resolved = StableAlbumHeroIdentity(
            title = "真实作品标题",
            rj = "RJ01522140",
            circle = "真实社团"
        )

        assertEquals(resolved, resolveStableAlbumHeroIdentity(placeholder, resolved))
        assertEquals(
            StableAlbumHeroIdentity(
                title = "列表已有标题",
                rj = "RJ01522140",
                circle = "列表社团"
            ),
            resolveStableAlbumHeroIdentity(
                stable = StableAlbumHeroIdentity(
                    title = "列表已有标题",
                    rj = "RJ01522140",
                    circle = "列表社团"
                ),
                current = resolved
            )
        )
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
            coverThumbPath = "",
            manualCoverPath = "/new/cover.jpg"
        )

        assertEquals("/new/cover.jpg", result.localAlbum?.coverPath)
        assertEquals("", result.localAlbum?.coverThumbPath)
        assertEquals("/new/cover.jpg", result.displayAlbum.coverPath)
        assertEquals("", result.displayAlbum.coverThumbPath)
        assertEquals("/new/cover.jpg", result.localAlbum?.manualCoverPath)
        assertEquals("/new/cover.jpg", result.displayAlbum.manualCoverPath)

        val restored = result.withUpdatedLocalCover(
            albumId = 42L,
            coverPath = "",
            coverThumbPath = "",
            manualCoverPath = ""
        )
        assertEquals("", restored.localAlbum?.manualCoverPath)
        assertEquals("", restored.localAlbum?.coverPath)
        assertEquals("", restored.displayAlbum.manualCoverPath)
        assertEquals("", restored.displayAlbum.coverPath)
    }

    @Test
    fun buildDisplayAlbum_keepsManualCoverWhenOnlineMetadataArrives() {
        val localAlbum = Album(
            id = 42L,
            title = "本地作品",
            path = "/albums/RJ123456",
            coverPath = "/albums/RJ123456/cover.jpg",
            manualCoverPath = "/albums/RJ123456/cover.jpg"
        )
        val result = buildDisplayAlbum(
            rjCode = "RJ123456",
            localAlbum = localAlbum,
            dlsiteInfo = Album(title = "在线作品", path = "", coverUrl = "https://example.com/online.jpg"),
            asmrOneWorkId = null
        )

        assertEquals(localAlbum.manualCoverPath, result.manualCoverPath)
        assertEquals(localAlbum.manualCoverPath, result.coverPath)
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

    @Test
    fun listenTogetherSummaryRj_staysOnBaseWorkWhenLanguageEditionChanges() {
        val model = albumDetailModel(
            baseRjCode = "RJ01588205",
            rjCode = "VJ01001234",
            listenerCount = 9
        )

        assertEquals("RJ01588205", model.listenTogetherSummaryRj())
    }

    @Test
    fun listenerCount_isPreservedWhenSameWorkModelIsRebuilt() {
        val previous = albumDetailModel(
            baseRjCode = "RJ01588205",
            rjCode = "RJ01588205",
            listenerCount = 9
        )
        val rebuilt = albumDetailModel(
            baseRjCode = "RJ01588205",
            rjCode = "VJ01001234",
            listenerCount = null
        )

        val result = rebuilt.withPreservedListenTogetherListenerCount(previous)

        assertEquals(9, result.listenTogetherRjListenerCount)
    }

    @Test
    fun listenerCount_isNotPreservedAcrossDifferentWorks() {
        val previous = albumDetailModel(
            baseRjCode = "RJ01588205",
            rjCode = "RJ01588205",
            listenerCount = 9
        )
        val rebuilt = albumDetailModel(
            baseRjCode = "RJ09999999",
            rjCode = "RJ09999999",
            listenerCount = null
        )

        val result = rebuilt.withPreservedListenTogetherListenerCount(previous)

        assertEquals(null, result.listenTogetherRjListenerCount)
    }
}

private fun albumDetailModel(
    baseRjCode: String,
    rjCode: String,
    listenerCount: Int?
): AlbumDetailModel {
    return AlbumDetailModel(
        baseRjCode = baseRjCode,
        rjCode = rjCode,
        listenTogetherRjListenerCount = listenerCount,
        displayAlbum = Album(title = "作品", path = "", rjCode = rjCode),
        localAlbum = null,
        dlsiteInfo = null,
        dlsiteGalleryUrls = emptyList(),
        dlsiteTrialTracks = emptyList(),
        dlsiteRecommendations = com.asmr.player.data.remote.scraper.DlsiteRecommendations(),
        dlsiteWorkno = rjCode,
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
}
