package com.asmr.player.ui.library

import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumDetailDlsiteInitialTargetTest {

    @Test
    fun resolveInitialDlsiteLoadTarget_prefersHansWhenAvailable() {
        val result = resolveInitialDlsiteLoadTarget(
            baseRj = "RJ000001",
            editions = listOf(
                DlsiteLanguageEdition(workno = "RJ000001", lang = "JPN", label = "jp", displayOrder = 3),
                DlsiteLanguageEdition(workno = "RJ000010", lang = "CHI_HANS", label = "zh-cn", displayOrder = 1),
                DlsiteLanguageEdition(workno = "RJ000011", lang = "CHI_HANT", label = "zh-tw", displayOrder = 2)
            ),
            chinesePreference = DlsiteChinesePreference.None
        )

        assertEquals("CHI_HANS", result.selectedLang)
        assertEquals("RJ000010", result.workno)
    }

    @Test
    fun resolveInitialDlsiteLoadTarget_fallsBackToHantWhenHansMissing() {
        val result = resolveInitialDlsiteLoadTarget(
            baseRj = "RJ000002",
            editions = listOf(
                DlsiteLanguageEdition(workno = "RJ000002", lang = "JPN", label = "jp", displayOrder = 2),
                DlsiteLanguageEdition(workno = "RJ000020", lang = "CHI_HANT", label = "zh-tw", displayOrder = 1)
            ),
            chinesePreference = DlsiteChinesePreference.None
        )

        assertEquals("CHI_HANT", result.selectedLang)
        assertEquals("RJ000020", result.workno)
    }

    @Test
    fun resolveInitialDlsiteLoadTarget_keepsJpnWhenNoChineseEditionExists() {
        val result = resolveInitialDlsiteLoadTarget(
            baseRj = "RJ000003",
            editions = listOf(
                DlsiteLanguageEdition(workno = "RJ000003", lang = "JPN", label = "jp", displayOrder = 1)
            ),
            chinesePreference = DlsiteChinesePreference.None
        )

        assertEquals("JPN", result.selectedLang)
        assertEquals("RJ000003", result.workno)
    }

    @Test
    fun resolveInitialDlsiteLoadTarget_preservesUserSelectionWhenRequested() {
        val result = resolveInitialDlsiteLoadTarget(
            baseRj = "RJ000004",
            editions = listOf(
                DlsiteLanguageEdition(workno = "RJ000004", lang = "JPN", label = "jp", displayOrder = 3),
                DlsiteLanguageEdition(workno = "RJ000040", lang = "CHI_HANS", label = "zh-cn", displayOrder = 1),
                DlsiteLanguageEdition(workno = "RJ000041", lang = "CHI_HANT", label = "zh-tw", displayOrder = 2)
            ),
            currentSelectedLang = "CHI_HANT",
            preserveCurrentSelection = true,
            chinesePreference = DlsiteChinesePreference.Hans
        )

        assertEquals("CHI_HANT", result.selectedLang)
        assertEquals("RJ000041", result.workno)
    }
}
