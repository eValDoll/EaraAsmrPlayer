package com.asmr.player.ui.search

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JpAsmrSearchToolbarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun jpMenusShowSupportedOptionsInLightTheme() = verifyMenus(ThemeMode.Light)
    @Test fun jpMenusShowSupportedOptionsInDarkTheme() = verifyMenus(ThemeMode.Dark)

    private fun verifyMenus(mode: ThemeMode) {
        composeRule.setContent {
            AsmrPlayerTheme(mode = mode) {
                SearchToolbar(
                    keyword = "",
                    onKeywordChange = {},
                    selectedFilter = SearchFilterOption.JapaneseAsmr,
                    selectedCollectedSort = SearchCollectedSortOption.ReleaseNew,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = {},
                    onOptionsChanged = {}
                )
            }
        }
        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).performClick()
        composeRule.onNodeWithText("asmr.one").assertExists()
        composeRule.onAllNodesWithTag(SEARCH_HAS_SUBTITLE_OPTION_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(SEARCH_ALL_AGES_OPTION_TAG).assertExists()
        composeRule.onNodeWithTag("${SEARCH_SCOPE_OPTION_TAG_PREFIX}_${SearchFilterOption.JapaneseAsmr.name}").performClick()
        composeRule.onNodeWithTag(SEARCH_SORT_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag("${SEARCH_COLLECTED_SORT_OPTION_TAG_PREFIX}_${SearchCollectedSortOption.ReleaseNew.name}").assertExists()
        composeRule.onAllNodesWithText("最新收录").assertCountEquals(0)
        composeRule.onAllNodesWithText("评分最高").assertCountEquals(0)
        composeRule.onAllNodesWithText("作品语言").assertCountEquals(0)
    }
}
