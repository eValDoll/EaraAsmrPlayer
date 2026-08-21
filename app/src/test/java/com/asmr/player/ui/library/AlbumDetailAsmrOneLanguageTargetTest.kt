package com.asmr.player.ui.library

import com.asmr.player.data.remote.api.AsmrOneOtherLanguageEditionInDb
import com.asmr.player.data.remote.api.AsmrOneTranslationInfo
import com.asmr.player.data.remote.api.WorkDetailsResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumDetailAsmrOneLanguageTargetTest {

    @Test
    fun collectedSearchResult_prefersExactCollectedRjBeforeDlsiteEditionRj() {
        val candidates = asmrOneTrackRjCandidates(
            baseRj = "RJ01612762",
            currentRj = "RJ01598807",
            dlsiteWorkno = "RJ01598807",
            originalRj = "RJ01579177",
            selectedLang = "CHI_HANT",
            preferInitialRj = true
        )

        assertEquals(listOf("RJ01612762", "RJ01598807"), candidates)
    }

    @Test
    fun nonJapaneseSelection_doesNotIncludeJapaneseFallbackCandidate() {
        val candidates = asmrOneTrackRjCandidates(
            baseRj = "RJ01583802",
            currentRj = "RJ01593950",
            dlsiteWorkno = "RJ01593950",
            originalRj = "RJ01583802",
            selectedLang = "CHI_HANS",
            preferInitialRj = false
        )

        assertEquals(listOf("RJ01593950"), candidates)
    }

    @Test
    fun translatedWorkId_matchesExactCollectedRjFromOriginalDetails() {
        val details = workDetails(
            id = 1_579_177,
            sourceId = "RJ01579177",
            otherEditions = listOf(
                AsmrOneOtherLanguageEditionInDb(
                    id = 1_612_762,
                    lang = "繁體中文",
                    source_id = "RJ01612762"
                )
            )
        )

        val workId = resolveAsmrOneTrackWorkId(
            resolvedWorkId = "1579177",
            resolvedDetails = details,
            selectedLang = "CHI_HANT",
            selectedRjs = listOf("RJ01612762", "RJ01598807")
        )

        assertEquals("1612762", workId)
    }

    @Test
    fun nonJapaneseSelection_neverFallsBackToOriginalWorkIdWhenEditionIsUnknown() {
        val workId = resolveAsmrOneTrackWorkId(
            resolvedWorkId = "1583802",
            resolvedDetails = null,
            selectedLang = "CHI_HANS",
            selectedRjs = listOf("RJ01593951", "RJ01593950")
        )

        assertNull(workId)
    }

    @Test
    fun japaneseSelection_usesResolvedOriginalWorkId() {
        val workId = resolveAsmrOneTrackWorkId(
            resolvedWorkId = "1583802",
            resolvedDetails = null,
            selectedLang = "JPN",
            selectedRjs = listOf("RJ01583802")
        )

        assertEquals("1583802", workId)
    }

    @Test
    fun dlsiteParentWorkno_usesSameLanguageAsmrOneResource() {
        val details = workDetails(
            id = 1_569_012,
            sourceId = "RJ01569012",
            translationInfo = AsmrOneTranslationInfo(
                lang = "CHI_HANS",
                is_original = false,
                parent_workno = "RJ01569011",
                original_workno = "RJ01557280"
            ),
            otherEditions = listOf(
                AsmrOneOtherLanguageEditionInDb(
                    id = 1_557_280,
                    lang = "日本語",
                    source_id = "RJ01557280",
                    is_original = true
                )
            )
        )

        val workId = resolveAsmrOneTrackWorkId(
            resolvedWorkId = "1569012",
            resolvedDetails = details,
            selectedLang = "CHI_HANS",
            selectedRjs = listOf("RJ01569011")
        )

        assertEquals("1569012", workId)
    }

    @Test
    fun japaneseSelectionFromTranslatedDetails_usesCollectedOriginalEdition() {
        val details = workDetails(
            id = 1_569_012,
            sourceId = "RJ01569012",
            translationInfo = AsmrOneTranslationInfo(lang = "CHI_HANS", is_original = false),
            otherEditions = listOf(
                AsmrOneOtherLanguageEditionInDb(
                    id = 1_557_280,
                    lang = "日本語",
                    source_id = "RJ01557280",
                    is_original = true
                )
            )
        )

        val workId = resolveAsmrOneTrackWorkId(
            resolvedWorkId = "1569012",
            resolvedDetails = details,
            selectedLang = "JPN",
            selectedRjs = listOf("RJ01557280")
        )

        assertEquals("1557280", workId)
    }

    private fun workDetails(
        id: Int,
        sourceId: String,
        translationInfo: AsmrOneTranslationInfo? = null,
        otherEditions: List<AsmrOneOtherLanguageEditionInDb>
    ): WorkDetailsResponse {
        return WorkDetailsResponse(
            id = id,
            source_id = sourceId,
            translation_info = translationInfo,
            title = "",
            circle = null,
            vas = null,
            tags = null,
            duration = 0,
            mainCoverUrl = "",
            dl_count = 0,
            price = 0,
            other_language_editions_in_db = otherEditions
        )
    }
}
