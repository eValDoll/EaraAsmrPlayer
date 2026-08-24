package com.asmr.player.data.remote.crawler

import com.asmr.player.data.remote.api.AsmrOneLanguageEdition
import com.asmr.player.data.remote.api.AsmrOneTranslationInfo
import com.asmr.player.data.remote.api.WorkDetailsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsmrOneWorkMatcherTest {
    @Test
    fun matchesBjSourceId() {
        val work = WorkDetailsResponse(
            id = 100000062,
            source_id = "BJ02370869",
            title = "",
            circle = null,
            vas = null,
            tags = null,
            duration = 0,
            mainCoverUrl = "",
            dl_count = 0,
            price = 0
        )

        assertTrue(asmrOneWorkMatchesRj(work, "bj02370869"))
    }

    @Test
    fun matchesByOriginalWorkNo() {
        val work = WorkDetailsResponse(
            id = 1,
            source_id = "RJ000000",
            original_workno = "RJ392613",
            language_editions = null,
            title = "",
            circle = null,
            vas = null,
            tags = null,
            duration = 0,
            mainCoverUrl = "",
            dl_count = 0,
            price = 0
        )
        assertTrue(asmrOneWorkMatchesRj(work, "RJ392613"))
        assertFalse(asmrOneWorkMatchesRj(work, "RJ111111"))
    }

    @Test
    fun matchesByLanguageEditions() {
        val work = WorkDetailsResponse(
            id = 1,
            source_id = "RJ000000",
            original_workno = null,
            language_editions = listOf(
                AsmrOneLanguageEdition(lang = "CHI_HANS", label = "简中", workno = "RJ392613")
            ),
            title = "",
            circle = null,
            vas = null,
            tags = null,
            duration = 0,
            mainCoverUrl = "",
            dl_count = 0,
            price = 0
        )
        assertTrue(asmrOneWorkMatchesRj(work, "RJ392613"))
    }

    @Test
    fun selectionPrefersExactJapaneseSourceOverTranslatedAlias() {
        val translated = work(
            id = 1_569_012,
            sourceId = "RJ01569012",
            originalWorkno = "RJ01557280",
            currentLang = "CHI_HANS"
        )
        val original = work(
            id = 1_557_280,
            sourceId = "RJ01557280",
            originalWorkno = null,
            currentLang = null
        )

        assertEquals(
            1_557_280,
            selectAsmrOneWorkForRj(listOf(translated, original), "RJ01557280")?.id
        )
    }

    @Test
    fun selectionMapsDlsiteParentWorknoToSameLanguageAsmrOneResource() {
        val original = work(
            id = 1_557_280,
            sourceId = "RJ01557280",
            originalWorkno = null,
            currentLang = null
        )
        val translated = work(
            id = 1_569_012,
            sourceId = "RJ01569012",
            originalWorkno = "RJ01557280",
            currentLang = "CHI_HANS"
        )

        assertEquals(
            1_569_012,
            selectAsmrOneWorkForRj(listOf(original, translated), "RJ01569011")?.id
        )
    }

    private fun work(
        id: Int,
        sourceId: String,
        originalWorkno: String?,
        currentLang: String?
    ) = WorkDetailsResponse(
        id = id,
        source_id = sourceId,
        original_workno = originalWorkno,
        translation_info = AsmrOneTranslationInfo(
            lang = currentLang,
            is_original = originalWorkno.isNullOrBlank()
        ),
        language_editions = listOf(
            AsmrOneLanguageEdition(lang = "JPN", label = "日本語", workno = "RJ01557280"),
            AsmrOneLanguageEdition(lang = "CHI_HANS", label = "简体中文", workno = "RJ01569011")
        ),
        title = "",
        circle = null,
        vas = null,
        tags = null,
        duration = 0,
        mainCoverUrl = "",
        dl_count = 0,
        price = 0
    )
}

