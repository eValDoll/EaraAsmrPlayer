package com.asmr.player.ui.library

import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumDetailDlsiteInitialTargetTest {

    @Test
    fun resolveInitialDlsiteLoadTarget_keepsJapaneseEntryWhenChineseEditionsExist() {
        val result = resolveInitialDlsiteLoadTarget(
            entryRjCode = "RJ000001",
            editions = listOf(
                DlsiteLanguageEdition(workno = "RJ000001", lang = "JPN", label = "jp", displayOrder = 3),
                DlsiteLanguageEdition(workno = "RJ000010", lang = "CHI_HANS", label = "zh-cn", displayOrder = 1),
                DlsiteLanguageEdition(workno = "RJ000011", lang = "CHI_HANT", label = "zh-tw", displayOrder = 2)
            )
        )

        assertEquals("JPN", result.selectedLang)
        assertEquals("RJ000001", result.workno)
    }

    @Test
    fun resolveInitialDlsiteLoadTarget_keepsChineseEntryWhenJapaneseEditionExists() {
        val result = resolveInitialDlsiteLoadTarget(
            entryRjCode = "RJ01189945",
            editions = listOf(
                DlsiteLanguageEdition(workno = "RJ365382", lang = "JPN", label = "jp", displayOrder = 1),
                DlsiteLanguageEdition(workno = "RJ01189945", lang = "CHI_HANS", label = "zh-cn", displayOrder = 5)
            )
        )

        assertEquals("CHI_HANS", result.selectedLang)
        assertEquals("RJ01189945", result.workno)
    }

    @Test
    fun resolveInitialDlsiteLoadTarget_doesNotInventJapaneseForChineseOnlyEntry() {
        val result = resolveInitialDlsiteLoadTarget(
            entryRjCode = "RJ01189945",
            editions = listOf(
                DlsiteLanguageEdition(
                    workno = "RJ01189945",
                    lang = "CHI_HANS",
                    label = "zh-cn",
                    displayOrder = 5
                )
            )
        )

        assertEquals("CHI_HANS", result.selectedLang)
        assertEquals(listOf("CHI_HANS"), result.editions.map { it.lang })
    }

    @Test
    fun resolveInitialDlsiteLoadTarget_keepsJpnWhenNoChineseEditionExists() {
        val result = resolveInitialDlsiteLoadTarget(
            entryRjCode = "RJ000003",
            editions = listOf(
                DlsiteLanguageEdition(workno = "RJ000003", lang = "JPN", label = "jp", displayOrder = 1)
            )
        )

        assertEquals("JPN", result.selectedLang)
        assertEquals("RJ000003", result.workno)
    }

    @Test
    fun resolveInitialDlsiteLoadTarget_neverSubstitutesAnotherEdition() {
        val result = resolveInitialDlsiteLoadTarget(
            entryRjCode = "RJ000099",
            editions = listOf(
                DlsiteLanguageEdition(workno = "RJ000004", lang = "JPN", label = "jp", displayOrder = 3),
                DlsiteLanguageEdition(workno = "RJ000040", lang = "CHI_HANS", label = "zh-cn", displayOrder = 1),
                DlsiteLanguageEdition(workno = "RJ000041", lang = "CHI_HANT", label = "zh-tw", displayOrder = 2)
            )
        )

        assertEquals("JPN", result.selectedLang)
        assertEquals("RJ000099", result.workno)
    }

    @Test
    fun resolvedInitialLanguage_doesNotReloadOneWhenWorkNumberIsUnchanged() {
        assertFalse(
            shouldReloadAsmrOneForResolvedInitialTarget(
                currentRj = "RJ436159",
                resolvedWorkno = "rj436159"
            )
        )
        assertTrue(
            shouldReloadAsmrOneForResolvedInitialTarget(
                currentRj = "RJ436159",
                resolvedWorkno = "RJ000001"
            )
        )
    }
}
