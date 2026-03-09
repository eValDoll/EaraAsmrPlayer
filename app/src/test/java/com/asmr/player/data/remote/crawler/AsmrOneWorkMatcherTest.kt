package com.asmr.player.data.remote.crawler

import com.asmr.player.data.remote.api.AsmrOneLanguageEdition
import com.asmr.player.data.remote.api.WorkDetailsResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsmrOneWorkMatcherTest {
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
}

