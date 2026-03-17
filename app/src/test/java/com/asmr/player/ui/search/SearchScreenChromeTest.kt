package com.asmr.player.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.common.CollapsibleHeaderState
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenChromeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun searchToolbar_imeSearchAndIconShareTheSameSubmitPath() {
        var submitCount by mutableIntStateOf(0)

        composeRule.setContent {
            var keyword by remember { mutableStateOf("") }

            AsmrPlayerTheme {
                SearchToolbar(
                    keyword = keyword,
                    onKeywordChange = { keyword = it },
                    selectedOrder = SearchSortOption.Trend,
                    purchasedOnly = false,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = { submitCount += 1 },
                    onPurchasedOnlySelected = {},
                    onOrderSelected = {},
                    onLocaleSelected = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_INPUT_TAG).performTextInput("RJ123456")
        composeRule.onNodeWithTag(SEARCH_INPUT_TAG).performImeAction()
        composeRule.runOnIdle {
            assertEquals(1, submitCount)
        }

        composeRule.onNodeWithTag(SEARCH_SUBMIT_BUTTON_TAG).performClick()
        composeRule.runOnIdle {
            assertEquals(2, submitCount)
        }
    }

    @Test
    fun searchPending_disablesChromeAndShowsSearchSpinner() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Column {
                    SearchToolbar(
                        keyword = "test",
                        onKeywordChange = {},
                        selectedOrder = SearchSortOption.Trend,
                        purchasedOnly = false,
                        selectedLocale = "ja_JP",
                        filterControlsLocked = true,
                        searchSubmitLocked = true,
                        showSearchSpinner = true,
                        onSearchSubmit = {},
                        onPurchasedOnlySelected = {},
                        onOrderSelected = {},
                        onLocaleSelected = {}
                    )
                    SearchPaginationHeader(
                        page = 1,
                        canGoPrev = false,
                        canGoNext = false,
                        controlsLocked = true,
                        showPagingSpinner = false,
                        onPrev = {},
                        onNext = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_LANGUAGE_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_SUBMIT_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_SUBMIT_SPINNER_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SEARCH_SUBMIT_SPINNER_TAG)
        )
    }

    @Test
    fun pagePending_disablesChromeAndShowsPaginationSpinner() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Column {
                    SearchToolbar(
                        keyword = "test",
                        onKeywordChange = {},
                        selectedOrder = SearchSortOption.Trend,
                        purchasedOnly = false,
                        selectedLocale = "ja_JP",
                        filterControlsLocked = true,
                        searchSubmitLocked = true,
                        showSearchSpinner = false,
                        onSearchSubmit = {},
                        onPurchasedOnlySelected = {},
                        onOrderSelected = {},
                        onLocaleSelected = {}
                    )
                    SearchPaginationHeader(
                        page = 2,
                        canGoPrev = true,
                        canGoNext = true,
                        controlsLocked = true,
                        showPagingSpinner = true,
                        onPrev = {},
                        onNext = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_LANGUAGE_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_SUBMIT_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_PREV_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_NEXT_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_PAGINATION_SPINNER_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SEARCH_PAGINATION_SPINNER_TAG)
        )
    }

    @Test
    fun searchChrome_collapsesAndExpandsWhileKeepingControlsMounted() {
        composeRule.mainClock.autoAdvance = false
        lateinit var chromeState: CollapsibleHeaderState

        composeRule.setContent {
            chromeState = remember { CollapsibleHeaderState() }

            AsmrPlayerTheme {
                SearchChrome(
                    modifier = Modifier,
                    keyword = "RJ123456",
                    onKeywordChange = {},
                    selectedOrder = SearchSortOption.Trend,
                    purchasedOnly = false,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    showPagination = true,
                    page = 2,
                    canGoPrev = true,
                    canGoNext = true,
                    controlsLocked = false,
                    showPagingSpinner = false,
                    rightPanelToggle = null,
                    animatedOffsetPx = chromeState.offsetPx,
                    collapseFraction = chromeState.collapseFraction,
                    onMeasured = { chromeState.updateHeight(it.height.toFloat()) },
                    onSearchSubmit = {},
                    onPurchasedOnlySelected = {},
                    onOrderSelected = {},
                    onLocaleSelected = {},
                    onPrev = {},
                    onNext = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SEARCH_CHROME_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded")
        )

        composeRule.runOnIdle { chromeState.onScrollDelta(-1000f) }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SEARCH_CHROME_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "collapsed")
        )
        composeRule.onNodeWithTag(SEARCH_INPUT_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SEARCH_INPUT_TAG)
        )
        composeRule.onNodeWithTag(SEARCH_PREV_BUTTON_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SEARCH_PREV_BUTTON_TAG)
        )
        composeRule.onNodeWithTag(SEARCH_NEXT_BUTTON_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SEARCH_NEXT_BUTTON_TAG)
        )

        composeRule.runOnIdle { chromeState.expand() }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SEARCH_CHROME_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "expanded")
        )
    }
}
