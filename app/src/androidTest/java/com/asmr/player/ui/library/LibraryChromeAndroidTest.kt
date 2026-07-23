package com.asmr.player.ui.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.common.CollapsibleHeaderState
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryChromeAndroidTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryChrome_exposesActiveFilterState() {
        composeRule.setContent {
            AsmrPlayerTheme {
                LibraryChrome(
                    modifier = Modifier,
                    searchText = "",
                    onSearchTextChange = {},
                    onClearSearch = {},
                    currentSort = LibrarySort.AddedDesc,
                    sortMenuExpanded = false,
                    onSortMenuExpandedChange = {},
                    onSortLastPlayed = {},
                    onSortAdded = {},
                    onSortTitle = {},
                    onOpenFilterScreen = {},
                    filterActive = true,
                    rightPanelToggle = null,
                    materialColorScheme = MaterialTheme.colorScheme,
                    chromeState = remember { CollapsibleHeaderState() },
                    onMeasured = {}
                )
            }
        }

        composeRule.onNodeWithTag(LIBRARY_FILTER_BUTTON_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "筛选已启用")
        )
    }

    @Test
    fun libraryChrome_marksCurrentSortMenuItemSelected() {
        composeRule.setContent {
            AsmrPlayerTheme {
                LibraryChrome(
                    modifier = Modifier,
                    searchText = "",
                    onSearchTextChange = {},
                    onClearSearch = {},
                    currentSort = LibrarySort.TitleAsc,
                    sortMenuExpanded = true,
                    onSortMenuExpandedChange = {},
                    onSortLastPlayed = {},
                    onSortAdded = {},
                    onSortTitle = {},
                    onOpenFilterScreen = {},
                    filterActive = false,
                    rightPanelToggle = null,
                    materialColorScheme = MaterialTheme.colorScheme,
                    chromeState = remember { CollapsibleHeaderState() },
                    onMeasured = {}
                )
            }
        }

        composeRule.onNodeWithTag(LIBRARY_SORT_TITLE_ITEM_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        )
        composeRule.onNodeWithTag(LIBRARY_SORT_ADDED_ITEM_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
        )
    }
}
