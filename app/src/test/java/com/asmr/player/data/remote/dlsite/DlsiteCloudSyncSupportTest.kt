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
            sanitizeDlsiteCloudSyncKeyword(" alpha ${leftBracket}first${rightBracket} beta  ${leftBracket}second${rightBracket}   gamma ")
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
                DlsiteLanguageEdition(workno = "RJ000003", lang = "JPN", label = "日本語", displayOrder = 3),
                DlsiteLanguageEdition(workno = "RJ000001", lang = "CHI_HANS", label = "简中", displayOrder = 1),
                DlsiteLanguageEdition(workno = "RJ000002", lang = "CHI_HANT", label = "繁中", displayOrder = 2)
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
                DlsiteLanguageEdition(workno = "RJ000002", lang = "CHI_HANT", label = "繁中", displayOrder = 1),
                DlsiteLanguageEdition(workno = "RJ000003", lang = "JPN", label = "日本語", displayOrder = 2)
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
    fun searchDlsiteWorknoWithLocaleFallback_returnsAmbiguousWhenPreferredLocaleHasMultipleResults() = runBlocking {
        val result = searchDlsiteWorknoWithLocaleFallback("test") { _, locale ->
            when (locale) {
                CLOUD_SYNC_LOCALE_ZH_CN -> listOf(albumOf("RJ000111"), albumOf("RJ000112"))
                else -> emptyList()
            }
        }

        assertEquals(
            DlsiteCloudSyncSearchResult.Ambiguous(locale = CLOUD_SYNC_LOCALE_ZH_CN, count = 2),
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

        val result = searchDlsiteWorknoWithLocaleFallback("${leftBracket}tag1${rightBracket} ${leftBracket}tag2${rightBracket}") { _, _ ->
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
                if (workno == "RJ000777" && locale == CLOUD_SYNC_LOCALE_ZH_TW) albumOf(workno, "繁中详情") else null
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
        assertEquals("繁中详情", result?.details?.title)
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
                if (workno == "RJ000200" && locale == CLOUD_SYNC_LOCALE_ZH_CN) albumOf(workno, "简中命中") else null
            }
        )

        assertTrue(result is DlsiteCloudSyncResolveResult.Success)
        result as DlsiteCloudSyncResolveResult.Success
        assertEquals("RJ000200", result.workno)
        assertEquals(CLOUD_SYNC_LOCALE_ZH_CN, result.locale)
        assertEquals("简中命中", result.details.title)
        assertTrue(detailAttempts.take(3).all { it.workno == "RJ000100" })
    }

    private fun albumOf(rjCode: String, title: String = rjCode): Album {
        return Album(
            title = title,
            path = "",
            workId = rjCode,
            rjCode = rjCode
        )
    }
}
