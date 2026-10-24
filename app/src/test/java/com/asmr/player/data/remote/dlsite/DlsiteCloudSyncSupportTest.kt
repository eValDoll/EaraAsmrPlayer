package com.asmr.player.data.remote.dlsite

import com.asmr.player.domain.model.Album
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteCloudSyncSupportTest {
    @Test
    fun sanitizeDlsiteCloudSyncKeyword_removesSingleBracketBlock() {
        assertEquals(
            "title rest",
            sanitizeDlsiteCloudSyncKeyword("title 【tag】 rest")
        )
    }

    @Test
    fun sanitizeDlsiteCloudSyncKeyword_removesMultipleBlocksAndCollapsesWhitespace() {
        assertEquals(
            "alpha beta gamma",
            sanitizeDlsiteCloudSyncKeyword(
                " alpha 【first】 beta  【second】   gamma "
            )
        )
    }

    @Test
    fun sanitizeDlsiteCloudSyncKeyword_keepsOriginalWhenNoBracketBlock() {
        assertEquals(
            "plain title",
            sanitizeDlsiteCloudSyncKeyword(" plain title ")
        )
    }

    @Test
    fun searchDlsiteWorknoWithLocaleFallback_returnsTraditionalWhenSimplifiedEmpty() = runBlocking {
        val keywords = mutableListOf<String>()

        val result = searchDlsiteWorknoWithLocaleFallback("test") { keyword, locale ->
            keywords += keyword
            when (locale) {
                CLOUD_SYNC_LOCALE_ZH_CN -> emptyList()
                CLOUD_SYNC_LOCALE_ZH_TW -> listOf(albumOf("RJ000222"))
                else -> emptyList()
            }
        }

        assertEquals(
            DlsiteCloudSyncSearchResult.Unique(workno = "RJ000222", locale = CLOUD_SYNC_LOCALE_ZH_TW),
            result
        )
        assertEquals(listOf("test", "test"), keywords)
    }

    @Test
    fun searchDlsiteWorknoWithLocaleFallback_returnsJapaneseWhenFirstTwoMiss() = runBlocking {
        val result = searchDlsiteWorknoWithLocaleFallback("test") { _, locale ->
            when (locale) {
                CLOUD_SYNC_LOCALE_ZH_CN -> error("network")
                CLOUD_SYNC_LOCALE_ZH_TW -> emptyList()
                else -> listOf(albumOf("RJ000333"))
            }
        }

        assertEquals(
            DlsiteCloudSyncSearchResult.Unique(workno = "RJ000333", locale = CLOUD_SYNC_LOCALE_JA_JP),
            result
        )
    }

    @Test
    fun searchDlsiteWorknoWithLocaleFallback_returnsAmbiguousCandidatesWhenPreferredLocaleHasMultipleResults() = runBlocking {
        val result = searchDlsiteWorknoWithLocaleFallback("test") { _, locale ->
            when (locale) {
                CLOUD_SYNC_LOCALE_ZH_CN -> listOf(
                    albumOf("RJ000111", title = "Candidate One", cv = "CV A", coverUrl = "https://example.com/1.jpg"),
                    albumOf("RJ000112", title = "Candidate Two", cv = "CV B", coverUrl = "https://example.com/2.jpg")
                )
                else -> emptyList()
            }
        }

        assertEquals(
            DlsiteCloudSyncSearchResult.Ambiguous(
                locale = CLOUD_SYNC_LOCALE_ZH_CN,
                candidates = listOf(
                    DlsiteCloudSyncCandidate(
                        workno = "RJ000111",
                        title = "Candidate One",
                        cv = "CV A",
                        coverUrl = "https://example.com/1.jpg"
                    ),
                    DlsiteCloudSyncCandidate(
                        workno = "RJ000112",
                        title = "Candidate Two",
                        cv = "CV B",
                        coverUrl = "https://example.com/2.jpg"
                    )
                )
            ),
            result
        )
    }

    @Test
    fun searchDlsiteWorknoWithLocaleFallback_removesMultipleBracketBlocksBeforeSearch() = runBlocking {
        val keywords = mutableListOf<String>()

        val result = searchDlsiteWorknoWithLocaleFallback(
            "alpha 【first】 beta 【second】 gamma"
        ) { keyword, locale ->
            keywords += "$locale:$keyword"
            when (locale) {
                CLOUD_SYNC_LOCALE_ZH_CN -> listOf(albumOf("RJ000210"))
                else -> emptyList()
            }
        }

        assertEquals(
            DlsiteCloudSyncSearchResult.Unique(workno = "RJ000210", locale = CLOUD_SYNC_LOCALE_ZH_CN),
            result
        )
        assertEquals(listOf("$CLOUD_SYNC_LOCALE_ZH_CN:alpha beta gamma"), keywords)
    }

    @Test
    fun searchDlsiteWorknoWithLocaleFallback_returnsNotFoundWithoutCallingSearchWhenSanitizedKeywordBlank() = runBlocking {
        var called = false

        val result = searchDlsiteWorknoWithLocaleFallback(
            "【tag1】 【tag2】"
        ) { _, _ ->
            called = true
            emptyList()
        }

        assertEquals(DlsiteCloudSyncSearchResult.NotFound, result)
        assertFalse(called)
    }

    @Test
    fun searchDlsiteWorknoWithLocaleFallback_ignoresNumericAndCustomWorkIds() = runBlocking {
        val result = searchDlsiteWorknoWithLocaleFallback("test") { _, _ ->
            listOf(
                albumOf("").copy(workId = "1706426"),
                albumOf("").copy(workId = "custom-id"),
                albumOf("").copy(workId = "folder-RJ01706426")
            )
        }

        assertEquals(DlsiteCloudSyncSearchResult.NotFound, result)
    }

    @Test
    fun searchDlsiteWorknoWithLocaleFallback_usesValidWorkIdWhenRjCodeMissing() = runBlocking {
        val result = searchDlsiteWorknoWithLocaleFallback("test") { _, _ ->
            listOf(albumOf("").copy(workId = " rj01706426 "))
        }

        assertEquals(
            DlsiteCloudSyncSearchResult.Unique("RJ01706426", CLOUD_SYNC_LOCALE_ZH_CN),
            result
        )
    }

    @Test
    fun resolveDlsiteCloudSync_keepsExistingRjInsteadOfSearchingAnotherEdition() = runBlocking {
        var searchCalled = false
        val detailAttempts = mutableListOf<Pair<String, String>>()

        val result = resolveDlsiteCloudSync(
            keyword = "album title",
            baseWorkno = "RJ01706426",
            search = { _, _ ->
                searchCalled = true
                listOf(albumOf("RJ01718403"))
            },
            fetchDetails = { workno, locale ->
                detailAttempts += workno to locale
                albumOf(workno, "Resolved")
            }
        )

        assertTrue(result is DlsiteCloudSyncResolveResult.Success)
        result as DlsiteCloudSyncResolveResult.Success
        assertEquals("RJ01706426", result.workno)
        assertEquals("RJ01706426", result.details.rjCode)
        assertEquals(listOf("RJ01706426" to CLOUD_SYNC_LOCALE_ZH_CN), detailAttempts)
        assertFalse(searchCalled)
    }

    @Test
    fun resolveDlsiteCloudSync_doesNotSearchByTitleWhenKnownWorknoFails() = runBlocking {
        var searchCalled = false
        val detailAttempts = mutableListOf<Pair<String, String>>()

        val result = resolveDlsiteCloudSync(
            keyword = "album title",
            baseWorkno = "RJ01706426",
            search = { _, _ ->
                searchCalled = true
                listOf(albumOf("RJ01718403"))
            },
            fetchDetails = { workno, locale ->
                detailAttempts += workno to locale
                null
            }
        )

        assertEquals(DlsiteCloudSyncResolveResult.NotFound, result)
        assertFalse(searchCalled)
        assertEquals(
            listOf(
                "RJ01706426" to CLOUD_SYNC_LOCALE_ZH_CN,
                "RJ01706426" to CLOUD_SYNC_LOCALE_ZH_TW,
                "RJ01706426" to CLOUD_SYNC_LOCALE_JA_JP
            ),
            detailAttempts
        )
    }

    @Test
    fun resolveDlsiteCloudSync_usesSameNormalizedWorknoAcrossLocaleFallback() = runBlocking {
        val detailAttempts = mutableListOf<Pair<String, String>>()

        val result = resolveDlsiteCloudSync(
            keyword = "album title",
            baseWorkno = " rj01706426 ",
            search = { _, _ -> error("Known work number must not trigger search") },
            fetchDetails = { workno, locale ->
                detailAttempts += workno to locale
                when (locale) {
                    CLOUD_SYNC_LOCALE_ZH_CN -> error("network")
                    CLOUD_SYNC_LOCALE_ZH_TW -> null
                    else -> albumOf(workno, "Japanese")
                }
            }
        )

        assertTrue(result is DlsiteCloudSyncResolveResult.Success)
        result as DlsiteCloudSyncResolveResult.Success
        assertEquals("RJ01706426", result.workno)
        assertEquals(CLOUD_SYNC_LOCALE_JA_JP, result.locale)
        assertEquals("Japanese", result.details.title)
        assertEquals(
            listOf(
                "RJ01706426" to CLOUD_SYNC_LOCALE_ZH_CN,
                "RJ01706426" to CLOUD_SYNC_LOCALE_ZH_TW,
                "RJ01706426" to CLOUD_SYNC_LOCALE_JA_JP
            ),
            detailAttempts
        )
    }

    @Test
    fun resolveDlsiteCloudSync_searchesTitleWhenNoValidWorknoExists() = runBlocking {
        for (baseWorkno in listOf("", "1706426", "custom-id", "folder-RJ01706426")) {
            val searchedKeywords = mutableListOf<String>()
            val detailAttempts = mutableListOf<Pair<String, String>>()

            val result = resolveDlsiteCloudSync(
                keyword = "album 【tag】 title",
                baseWorkno = baseWorkno,
                search = { keyword, _ ->
                    searchedKeywords += keyword
                    listOf(albumOf("RJ01706426"))
                },
                fetchDetails = { workno, locale ->
                    detailAttempts += workno to locale
                    albumOf(workno, "Resolved")
                }
            )

            assertTrue(result is DlsiteCloudSyncResolveResult.Success)
            result as DlsiteCloudSyncResolveResult.Success
            assertEquals("RJ01706426", result.workno)
            assertEquals("Resolved", result.details.title)
            assertEquals(listOf("album title"), searchedKeywords)
            assertEquals(listOf("RJ01706426" to CLOUD_SYNC_LOCALE_ZH_CN), detailAttempts)
        }
    }

    @Test
    fun resolveDlsiteCloudSync_returnsAmbiguousWithoutFetchingUnselectedCandidate() = runBlocking {
        var fetchCalled = false
        val candidates = listOf(albumOf("RJ01706426"), albumOf("RJ01718403"))

        val result = resolveDlsiteCloudSync(
            keyword = "album title",
            baseWorkno = "",
            search = { _, _ -> candidates },
            fetchDetails = { _, _ ->
                fetchCalled = true
                null
            }
        )

        assertEquals(
            DlsiteCloudSyncResolveResult.Ambiguous(
                locale = CLOUD_SYNC_LOCALE_ZH_CN,
                candidates = listOf(
                    DlsiteCloudSyncCandidate("RJ01706426", "RJ01706426", "", ""),
                    DlsiteCloudSyncCandidate("RJ01718403", "RJ01718403", "", "")
                )
            ),
            result
        )
        assertFalse(fetchCalled)
    }

    @Test
    fun resolveDlsiteCloudSync_returnsNotFoundWhenTitleSearchHasNoCandidates() = runBlocking {
        var fetchCalled = false

        val result = resolveDlsiteCloudSync(
            keyword = "album title",
            baseWorkno = "",
            search = { _, _ -> emptyList() },
            fetchDetails = { _, _ ->
                fetchCalled = true
                null
            }
        )

        assertEquals(DlsiteCloudSyncResolveResult.NotFound, result)
        assertFalse(fetchCalled)
    }

    @Test
    fun resolveDlsiteCloudSync_doesNotRepeatTitleSearchWhenCandidateDetailsMissing() = runBlocking {
        var searchCalls = 0

        val result = resolveDlsiteCloudSync(
            keyword = "album title",
            baseWorkno = "",
            search = { _, _ ->
                searchCalls++
                listOf(albumOf("RJ01706426"))
            },
            fetchDetails = { _, _ -> null }
        )

        assertEquals(DlsiteCloudSyncResolveResult.NotFound, result)
        assertEquals(1, searchCalls)
    }

    @Test
    fun resolveSelectedDlsiteCloudSync_usesSameWorknoForChosenCandidateAcrossLocales() = runBlocking {
        val detailAttempts = mutableListOf<Pair<String, String>>()

        val result = resolveSelectedDlsiteCloudSync(
            workno = " rj01718403 ",
            fetchDetails = { workno, locale ->
                detailAttempts += workno to locale
                if (locale == CLOUD_SYNC_LOCALE_ZH_TW) albumOf(workno, "Chosen") else null
            }
        )

        assertTrue(result is DlsiteCloudSyncResolveResult.Success)
        result as DlsiteCloudSyncResolveResult.Success
        assertEquals("RJ01718403", result.workno)
        assertEquals(CLOUD_SYNC_LOCALE_ZH_TW, result.locale)
        assertEquals("Chosen", result.details.title)
        assertEquals(
            listOf(
                "RJ01718403" to CLOUD_SYNC_LOCALE_ZH_CN,
                "RJ01718403" to CLOUD_SYNC_LOCALE_ZH_TW
            ),
            detailAttempts
        )
    }

    @Test
    fun resolveSelectedDlsiteCloudSync_rejectsInvalidCandidateWithoutFetching() = runBlocking {
        for (workno in listOf("", "1706426", "custom-id", "folder-RJ01706426")) {
            var fetchCalled = false

            val result = resolveSelectedDlsiteCloudSync(workno) { _, _ ->
                fetchCalled = true
                null
            }

            assertEquals(DlsiteCloudSyncResolveResult.NotFound, result)
            assertFalse(fetchCalled)
        }
    }

    @Test
    fun resolveSelectedDlsiteCloudSync_rejectsDifferentRjCodeInDetails() = runBlocking {
        val result = resolveSelectedDlsiteCloudSync("RJ01706426") { _, _ ->
            albumOf("RJ01718403").copy(workId = "RJ01706426")
        }

        assertEquals(DlsiteCloudSyncResolveResult.NotFound, result)
    }

    @Test
    fun resolveSelectedDlsiteCloudSync_rejectsDifferentWorkIdInDetails() = runBlocking {
        val result = resolveSelectedDlsiteCloudSync("RJ01706426") { _, _ ->
            albumOf("RJ01706426").copy(workId = "RJ01718403")
        }

        assertEquals(DlsiteCloudSyncResolveResult.NotFound, result)
    }

    @Test
    fun resolveSelectedDlsiteCloudSync_continuesSameNumberAfterMismatchedDetails() = runBlocking {
        val detailAttempts = mutableListOf<Pair<String, String>>()

        val result = resolveSelectedDlsiteCloudSync("RJ01706426") { workno, locale ->
            detailAttempts += workno to locale
            if (locale == CLOUD_SYNC_LOCALE_ZH_CN) albumOf("RJ01718403") else albumOf(workno)
        }

        assertTrue(result is DlsiteCloudSyncResolveResult.Success)
        result as DlsiteCloudSyncResolveResult.Success
        assertEquals("RJ01706426", result.workno)
        assertEquals(CLOUD_SYNC_LOCALE_ZH_TW, result.locale)
        assertEquals(
            listOf(
                "RJ01706426" to CLOUD_SYNC_LOCALE_ZH_CN,
                "RJ01706426" to CLOUD_SYNC_LOCALE_ZH_TW
            ),
            detailAttempts
        )
    }

    @Test
    fun resolveCloudSyncRjCode_preservesOriginalMatchingRjCode() {
        assertEquals(
            " rj01706426 ",
            resolveCloudSyncRjCode(" rj01706426 ", "custom-id", "RJ01706426")
        )
    }

    @Test
    fun resolveCloudSyncRjCode_prefersExistingRjCodeOverWorkId() {
        assertEquals(
            "RJ01706426",
            resolveCloudSyncRjCode("RJ01706426", "RJ01718403", "RJ01706426")
        )
    }

    @Test
    fun resolveCloudSyncRjCode_fillsMissingRjCodeFromMatchingResolvedWorkno() {
        for (existingWorkId in listOf("", "1706426", "custom-id", " rj01706426 ")) {
            assertEquals(
                "RJ01706426",
                resolveCloudSyncRjCode("", existingWorkId, " rj01706426 ")
            )
        }
    }

    @Test
    fun resolveCloudSyncRjCode_fillsInvalidRjCodeAfterTitleSearch() {
        assertEquals("RJ01706426", resolveCloudSyncRjCode("unknown", "custom-id", "RJ01706426"))
    }

    @Test
    fun resolveCloudSyncRjCode_rejectsChangingExistingRjCodeToAnotherEdition() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveCloudSyncRjCode("RJ01706426", "custom-id", "RJ01718403")
        }
    }

    @Test
    fun resolveCloudSyncRjCode_rejectsStaleResultAfterExistingWorkIdChanges() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveCloudSyncRjCode("", "RJ01718403", "RJ01706426")
        }
    }

    @Test
    fun resolveCloudSyncRjCode_rejectsInvalidResolvedWorkno() {
        for (resolvedWorkno in listOf("", "1706426", "custom-id")) {
            assertThrows(IllegalArgumentException::class.java) {
                resolveCloudSyncRjCode("", "", resolvedWorkno)
            }
        }
    }

    @Test
    fun resolveCloudSyncWorkId_replacesAnySupportedDlsiteWorkNumber() {
        assertEquals("BJ02370869", resolveCloudSyncWorkId("VJ01005620", "bj02370869"))
        assertEquals("BJ02370869", resolveCloudSyncWorkId("RJ123456", "BJ02370869"))
        assertEquals("custom-id", resolveCloudSyncWorkId("custom-id", "BJ02370869"))
    }

    private fun albumOf(
        rjCode: String,
        title: String = rjCode,
        cv: String = "",
        coverUrl: String = ""
    ): Album {
        return Album(
            title = title,
            path = "",
            cv = cv,
            coverUrl = coverUrl,
            workId = rjCode,
            rjCode = rjCode
        )
    }
}
