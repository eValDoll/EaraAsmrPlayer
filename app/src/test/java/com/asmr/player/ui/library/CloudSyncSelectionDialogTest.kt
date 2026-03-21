package com.asmr.player.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.data.remote.dlsite.DlsiteCloudSyncCandidate
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudSyncSelectionDialogTest {
    private val existsMatcher = SemanticsMatcher("exists") { true }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialog_rendersCompactHeaderAndFooterProgress() {
        composeRule.setContent {
            AsmrPlayerTheme {
                CloudSyncSelectionDialog(
                    state = CloudSyncSelectionDialogState(
                        albumTitle = "Album A",
                        candidates = listOf(
                            candidate("RJ000111", "Candidate One", "CV A"),
                            candidate("RJ000112", "Candidate Two", "CV B"),
                            candidate("RJ000113", "Candidate Three", "CV C"),
                            candidate("RJ000114", "Candidate Four", "CV D")
                        ),
                        currentPosition = 2,
                        totalCount = 5
                    ),
                    onSelect = {},
                    onCancel = {},
                    onIgnoreAll = {}
                )
            }
        }

        composeRule.onNodeWithTag(CLOUD_SYNC_SELECTION_DIALOG_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, CLOUD_SYNC_SELECTION_DIALOG_TAG)
        )
        composeRule.onNodeWithText("云同步候选：4 个疑似结果(点击对应作品以确认同步)").assert(existsMatcher)
        composeRule.onNodeWithText("Album A").assert(existsMatcher)
        composeRule.onNodeWithTag(CLOUD_SYNC_SELECTION_PROGRESS_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, CLOUD_SYNC_SELECTION_PROGRESS_TAG)
        )
        composeRule.onNodeWithText("待确认 2 / 5").assert(existsMatcher)
        composeRule.onNodeWithText("Candidate One").assert(existsMatcher)
        composeRule.onNodeWithText("CV A").assert(existsMatcher)
        composeRule.onNodeWithText("忽略全部").assert(existsMatcher)
        composeRule.onNodeWithText("取消当前").assert(existsMatcher)
        assertTrue(composeRule.onAllNodesWithText("RJ000111").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag(CLOUD_SYNC_SELECTION_LIST_TAG)
            .assertHeightIsEqualTo(CLOUD_SYNC_SELECTION_LIST_HEIGHT)
    }

    @Test
    fun dialog_rendersLongTitleCandidateWithoutCroppingListHeightContract() {
        composeRule.setContent {
            AsmrPlayerTheme {
                CloudSyncSelectionDialog(
                    state = CloudSyncSelectionDialogState(
                        albumTitle = "Album A",
                        candidates = listOf(
                            candidate(
                                "RJ000111",
                                "A very long candidate title that should still fit within two lines of text in the dialog row",
                                "CV A, CV B, CV C"
                            )
                        ),
                        currentPosition = 1,
                        totalCount = 1
                    ),
                    onSelect = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithText("A very long candidate title that should still fit within two lines of text in the dialog row")
            .assert(existsMatcher)
        composeRule.onNodeWithText("CV A").assert(existsMatcher)
        composeRule.onNodeWithTag(CLOUD_SYNC_SELECTION_LIST_TAG)
            .assertHeightIsEqualTo(CLOUD_SYNC_SELECTION_LIST_HEIGHT)
    }

    @Test
    fun dialog_hidesIgnoreAllOutsideBatchQueueMode() {
        composeRule.setContent {
            AsmrPlayerTheme {
                CloudSyncSelectionDialog(
                    state = CloudSyncSelectionDialogState(
                        albumTitle = "Album A",
                        candidates = listOf(candidate("RJ000111", "Candidate One", "CV A"))
                    ),
                    onSelect = {},
                    onCancel = {}
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithText("忽略全部").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("取消").assert(existsMatcher)
    }

    @Test
    fun dialog_allowsSwitchingToNextRequestAfterSelection() {
        composeRule.setContent {
            var state by mutableStateOf<CloudSyncSelectionDialogState?>(
                CloudSyncSelectionDialogState(
                    albumTitle = "Album A",
                    candidates = listOf(candidate("RJ000111", "Candidate One", "CV A")),
                    currentPosition = 1,
                    totalCount = 2
                )
            )

            AsmrPlayerTheme {
                state?.let {
                    CloudSyncSelectionDialog(
                        state = it,
                        onSelect = {
                            state = CloudSyncSelectionDialogState(
                                albumTitle = "Album B",
                                candidates = listOf(candidate("RJ000222", "Candidate Two", "CV B")),
                                currentPosition = 2,
                                totalCount = 2
                            )
                        },
                        onCancel = { state = null },
                        onIgnoreAll = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Candidate One").performClick()
        composeRule.onNodeWithText("Album B").assert(existsMatcher)
        composeRule.onNodeWithText("Candidate Two").assert(existsMatcher)
        composeRule.onNodeWithText("待确认 2 / 2").assert(existsMatcher)

        composeRule.onNodeWithText("取消当前").performClick()
        assertTrue(composeRule.onAllNodesWithTag(CLOUD_SYNC_SELECTION_DIALOG_TAG).fetchSemanticsNodes().isEmpty())
    }

    private fun candidate(workno: String, title: String, cv: String): DlsiteCloudSyncCandidate {
        return DlsiteCloudSyncCandidate(
            workno = workno,
            title = title,
            cv = cv,
            coverUrl = ""
        )
    }
}
