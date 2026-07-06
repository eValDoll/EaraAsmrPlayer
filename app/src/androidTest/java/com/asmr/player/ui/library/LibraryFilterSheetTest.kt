package com.asmr.player.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.data.local.db.dao.TagWithCount
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryFilterSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryFilterSheet_keepsDraftUntilApply() {
        var appliedSpec = LibraryQuerySpec()
        val tags = listOf(
            TagWithCount(
                id = 7L,
                name = "耳语",
                nameNormalized = "耳语",
                albumCount = 3,
                userAlbumCount = 1
            )
        )

        composeRule.setContent {
            var draftSpec by remember { mutableStateOf(appliedSpec) }
            AsmrPlayerTheme {
                LibraryFilterSheet(
                    modifier = Modifier,
                    appliedSpec = appliedSpec,
                    draftSpec = draftSpec,
                    tags = tags,
                    circles = listOf("社团A"),
                    cvs = listOf("CV A"),
                    presets = emptyList(),
                    onDraftSpecChange = { draftSpec = it },
                    onOpenTagManager = {},
                    onApply = { appliedSpec = draftSpec },
                    onSavePreset = {},
                    onDeletePreset = {},
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithText("#耳语").performClick()
        composeRule.runOnIdle {
            assertEquals(emptySet<Long>(), appliedSpec.includeTagIds)
        }

        composeRule.onNodeWithText("应用").performClick()
        composeRule.runOnIdle {
            assertEquals(setOf(7L), appliedSpec.includeTagIds)
        }
    }
}
