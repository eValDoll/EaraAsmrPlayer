package com.asmr.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.asmr.player.LocalizedRobolectricTest
import com.asmr.player.R
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.i18n.appString
import com.asmr.player.ui.asmrone.CloudSourceToggle
import com.asmr.player.ui.calendar.DashboardAccountRow
import com.asmr.player.ui.library.OnlineSaveDialog
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AsmrOneEntryUiTest : LocalizedRobolectricTest() {
    @get:Rule val compose = createEaraComposeRule()

    @Test fun headerSourceSwitchChangesSourceInOneTap() {
        compose.setContent {
            AsmrPlayerTheme {
                var cloud by remember { mutableStateOf(false) }
                CloudSourceToggle(cloud, { cloud = !cloud })
            }
        }
        val sourceToggle = compose.onNodeWithContentDescription(appString(R.string.cloud_switch_source))
        sourceToggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, appString(R.string.cloud_local)))
        compose.onNodeWithText("本地").assertDoesNotExist()
        sourceToggle.performClick()
        sourceToggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, appString(R.string.cloud_remote)))
        compose.onNodeWithText("ASMR.one").assertDoesNotExist()
        compose.onNodeWithText("本地").assertDoesNotExist()
        sourceToggle.performClick()
        sourceToggle.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, appString(R.string.cloud_local)))
    }

    @Test fun dashboardAccountEntriesShareActionsAndKeepDlsiteSummary() {
        var asmrClicks = 0
        var dlsiteClicks = 0
        compose.setContent {
            AsmrPlayerTheme {
                Column {
                    DashboardAccountRow("ASMR.one 账号", { asmrClicks++ }, false, AsmrTheme.colorScheme)
                    DashboardAccountRow("DLsite 账号", { dlsiteClicks++ }, true, AsmrTheme.colorScheme,
                        description = appString(R.string.ui_used_for_purchased_search_authentication))
                }
            }
        }
        compose.onNodeWithText("ASMR.one 账号").performClick()
        compose.onNodeWithText("DLsite 账号").performClick()
        assertEquals(1, asmrClicks)
        assertEquals(1, dlsiteClicks)
        compose.onNodeWithText(appString(R.string.ui_used_for_purchased_search_authentication)).assertIsDisplayed()
        compose.onNodeWithText(appString(R.string.ui_not_logged_in)).assertIsDisplayed()
        compose.onNodeWithText(appString(R.string.ui_logged_in)).assertIsDisplayed()
    }

    @Test fun localSaveSheetOnlyConfirmsSelectedFilesAndHasNoCloudEntry() {
        val files = listOf(
            AsmrOneTrackNodeResponse(title = "第一轨.mp3", type = "audio", mediaDownloadUrl = "https://example.test/one.mp3"),
            AsmrOneTrackNodeResponse(title = "第二轨.mp3", type = "audio", mediaDownloadUrl = "https://example.test/two.mp3"),
        )
        var saved: Set<String>? = null
        compose.setContent {
            AsmrPlayerTheme {
                OnlineSaveDialog(albumTitle = "作品", trackTree = files, onDismiss = {}, onConfirm = { saved = it })
            }
        }
        compose.onNodeWithTag("asmr_source_toggle").assertDoesNotExist()
        compose.onNodeWithContentDescription(appString(R.string.cloud_add_to_playlist)).assertDoesNotExist()
        compose.onNodeWithText(appString(R.string.ui_select_file_to_save)).assertIsDisplayed()
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onAllNodes(isToggleable())[0].assertIsOff()
        compose.onAllNodes(isToggleable())[1].assertIsOn()
        compose.onNodeWithText(appString(R.string.ui_save_to_local_library)).performClick()
        assertEquals(setOf("第二轨.mp3"), saved)
    }
}
