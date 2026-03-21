package com.asmr.player.data.remote.dlsite

import com.asmr.player.domain.model.Album
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteCloudSyncSupportTest {
    private val leftBracket = "\u3010"
    private val rightBracket = "\u3011"

    @Test
    fun sanitizeDlsiteCloudSyncKeyword_removesSingleBracketBlock() {
        assertEquals(
            "title rest",
            sanitizeDlsiteCloudSyncKeyword("title ${leftBracket}tag${rightBracket} rest")
        )
    }

    @Test
    fun sanitizeDlsiteCloudSyncKeyword_removesMultipleBlocksAndCollapsesWhitespace() {
        assertEquals(
            "alpha beta gamma",
            sanitizeDlsiteCloudSyncKeyword(
                " alpha ${leftBracket}first${rightBracket} beta  ${leftBracket}second${rightBracket}   gamma "
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
    fun buildDlsiteCloudSyncAttempts_prefersHansThenHantThenJpn() {
        val attempts = buildDlsiteCloudSyncAttempts(
            baseWorkno = "RJ000001",
            editions = listOf(
                DlsiteLanguageEdition(workno = "RJ000003", lang = "JPN", label = "jp", displayOrder = 3),
                DlsiteLanguageEdition(workno = "RJ000001", lang = "CHI_HANS", label = "zh-cn", displayOrder = 1),
                DlsiteLanguageEdition(workno = "RJ000002", lang = "CHI_HANT", label = "zh-tw", displayOrder = 2)
            )
        )

        assertEquals(
            listOf(
                DlsiteCloudSyncAttempt("RJ000001", CLOUD_SYNC_LOCALE_ZH_CN),
                DlsiteCloudSyncAttempt("RJ000002", CLOUD_SYNC_LOCALE_ZH_TW),
                DlsiteCloudSyncAttempt("RJ000003", CLOUD_SYNC_LOCALE_JA_JP)
            ),
            attempts
        )
    }

    @Test
    fun buildDlsiteCloudSyncAttempts_skipsMissingLanguages() {
        val attempts = buildDlsiteCloudSyncAttempts(
            baseWorkno = "RJ000001",
            editions = listOf(
                DlsiteLanguageEdition(workno = "RJ000002", lang = "CHI_HANT", label = "zh-tw", displayOrder = 1),
                DlsiteLanguageEdition(workno = "RJ000003", lang = "JPN", label = "jp", displayOrder = 2)
            )
        )

        assertEquals(
            listOf(
                DlsiteCloudSyncAttempt("RJ000002", CLOUD_SYNC_LOCALE_ZH_TW),
                DlsiteCloudSyncAttempt("RJ000003", CLOUD_SYNC_LOCALE_JA_JP)
            ),
            attempts
        )
    }

    @Test
    fun buildDlsiteCloudSyncAttempts_fallsBackToBaseWorknoWhenEditionsEmpty() {
        val attempts = buildDlsiteCloudSyncAttempts(baseWorkno = "RJ000001", editions = emptyList())

        assertEquals(
            listOf(
                DlsiteCloudSyncAttempt("RJ000001", CLOUD_SYNC_LOCALE_ZH_CN),
                DlsiteCloudSyncAttempt("RJ000001", CLOUD_SYNC_LOCALE_ZH_TW),
                DlsiteCloudSyncAttempt("RJ000001", CLOUD_SYNC_LOCALE_JA_JP)
            ),
            attempts
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
            "alpha ${leftBracket}first${rightBracket} beta ${leftBracket}second${rightBracket} gamma"
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
            "${leftBracket}tag1${rightBracket} ${leftBracket}tag2${rightBracket}"
        ) { _, _ ->
            called = true
            emptyList()
        }

        assertEquals(DlsiteCloudSyncSearchResult.NotFound, result)
        assertFalse(called)
    }

    @Test
    fun fetchDlsiteDetailsWithLocaleFallback_fallsBackToBaseWorknoWhenLanguageEditionsFail() = runBlocking {
        val attempts = mutableListOf<DlsiteCloudSyncAttempt>()

        val result = fetchDlsiteDetailsWithLocaleFallback(
            baseWorkno = "RJ000777",
            fetchLanguageEditions = { error("boom") },
            fetchDetails = { workno, locale ->
                attempts += DlsiteCloudSyncAttempt(workno, locale)
                if (workno == "RJ000777" && locale == CLOUD_SYNC_LOCALE_ZH_TW) albumOf(workno, "Traditional") else null
            }
        )

        assertEquals(
            listOf(
                DlsiteCloudSyncAttempt("RJ000777", CLOUD_SYNC_LOCALE_ZH_CN),
                DlsiteCloudSyncAttempt("RJ000777", CLOUD_SYNC_LOCALE_ZH_TW)
            ),
            attempts
        )
        assertEquals("RJ000777", result?.workno)
        assertEquals(CLOUD_SYNC_LOCALE_ZH_TW, result?.locale)
        assertEquals("Traditional", result?.details?.title)
    }

    @Test
    fun resolveDlsiteCloudSync_fallsBackToKeywordSearchWhenKnownWorknoFails() = runBlocking {
        val detailAttempts = mutableListOf<DlsiteCloudSyncAttempt>()

        val result = resolveDlsiteCloudSync(
            keyword = "album title",
            baseWorkno = "RJ000100",
            search = { _, locale ->
                when (locale) {
                    CLOUD_SYNC_LOCALE_ZH_CN -> listOf(albumOf("RJ000200"))
                    else -> emptyList()
                }
            },
            fetchLanguageEditions = { emptyList() },
            fetchDetails = { workno, locale ->
                detailAttempts += DlsiteCloudSyncAttempt(workno, locale)
                if (workno == "RJ000200" && locale == CLOUD_SYNC_LOCALE_ZH_CN) albumOf(workno, "Resolved") else null
            }
        )

        assertTrue(result is DlsiteCloudSyncResolveResult.Success)
        result as DlsiteCloudSyncResolveResult.Success
        assertEquals("RJ000200", result.workno)
        assertEquals(CLOUD_SYNC_LOCALE_ZH_CN, result.locale)
        assertEquals("Resolved", result.details.title)
        assertTrue(detailAttempts.take(3).all { it.workno == "RJ000100" })
    }

    @Test
    fun resolveSelectedDlsiteCloudSync_usesLocaleFallbackForChosenCandidate() = runBlocking {
        val detailAttempts = mutableListOf<DlsiteCloudSyncAttempt>()

        val result = resolveSelectedDlsiteCloudSync(
            workno = "RJ000555",
            fetchLanguageEditions = { emptyList() },
            fetchDetails = { workno, locale ->
                detailAttempts += DlsiteCloudSyncAttempt(workno, locale)
                if (workno == "RJ000555" && locale == CLOUD_SYNC_LOCALE_ZH_TW) {
                    albumOf(workno, title = "Chosen")
                } else {
                    null
                }
            }
        )

        assertTrue(result is DlsiteCloudSyncResolveResult.Success)
        result as DlsiteCloudSyncResolveResult.Success
        assertEquals("RJ000555", result.workno)
        assertEquals(CLOUD_SYNC_LOCALE_ZH_TW, result.locale)
        assertEquals("Chosen", result.details.title)
        assertEquals(
            listOf(
                DlsiteCloudSyncAttempt("RJ000555", CLOUD_SYNC_LOCALE_ZH_CN),
                DlsiteCloudSyncAttempt("RJ000555", CLOUD_SYNC_LOCALE_ZH_TW)
            ),
            detailAttempts
        )
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
