package com.asmr.player.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.common.CollapsibleHeaderState
import com.asmr.player.ui.common.interruptScrollableFlingOnPointerDown
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
                    selectedFilter = SearchFilterOption.Standard,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = { submitCount += 1 },
                    onOptionsChanged = {}
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
    fun searchToolbar_readOnlyFieldOpensAssistAndSearchIconSubmits() {
        var assistOpenCount by mutableIntStateOf(0)
        var submitCount by mutableIntStateOf(0)

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchToolbar(
                    keyword = "RJ123456",
                    onKeywordChange = {},
                    searchFieldReadOnly = true,
                    onSearchFieldClick = { assistOpenCount += 1 },
                    selectedFilter = SearchFilterOption.Standard,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = { submitCount += 1 },
                    onOptionsChanged = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_INPUT_TAG).performClick()
        composeRule.onNodeWithTag(SEARCH_SUBMIT_BUTTON_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(1, assistOpenCount)
            assertEquals(1, submitCount)
        }
    }

    @Test
    fun searchToolbar_usesHotKeywordPlaceholderWhenKeywordIsEmpty() {
        composeRule.setContent {
            AsmrPlayerTheme {
                SearchToolbar(
                    keyword = "",
                    onKeywordChange = {},
                    placeholder = "CV A",
                    selectedFilter = SearchFilterOption.Standard,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = {},
                    onOptionsChanged = {}
                )
            }
        }

        composeRule.onNodeWithText("CV A").assertExists()
    }

    @Test
    fun searchPending_disablesChromeAndShowsSearchSpinner() {
        composeRule.setContent {
            AsmrPlayerTheme {
                Column {
                    SearchToolbar(
                        keyword = "test",
                        onKeywordChange = {},
                        selectedFilter = SearchFilterOption.Standard,
                        selectedLocale = "ja_JP",
                        filterControlsLocked = true,
                        searchSubmitLocked = true,
                        showSearchSpinner = true,
                        onSearchSubmit = {},
                        onOptionsChanged = {}
                    )
                    SearchPaginationHeader(
                        page = 1,
                        canGoPrev = false,
                        canGoNext = false,
                        controlsLocked = true,
                        onFirstPage = {},
                        onPrev = {},
                        onNext = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_SORT_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_CLEAR_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_SUBMIT_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_SUBMIT_SPINNER_TAG, useUnmergedTree = true).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, SEARCH_SUBMIT_SPINNER_TAG)
        )
    }

    @Test
    fun filterMenu_hidesWorkFiltersForUnsupportedScope() {
        val filterOptions = SearchFilterOption.values()
        assertEquals(SearchFilterOption.Collected, filterOptions.first())
        assertEquals(SearchFilterOption.Presale, filterOptions[filterOptions.lastIndex - 1])
        assertEquals(SearchFilterOption.PurchasedOnly, filterOptions.last())

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchToolbar(
                    keyword = "",
                    onKeywordChange = {},
                    selectedFilter = SearchFilterOption.ChineseTranslated,
                    selectedLocale = "zh_CN",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = {},
                    onOptionsChanged = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(
            "${SEARCH_SCOPE_OPTION_TAG_PREFIX}_${SearchFilterOption.ChineseTranslated.name}"
        ).assertExists()
        composeRule.onNodeWithText("已购").assertExists()
        composeRule.onNodeWithText("预售").assertExists()
        composeRule.onNodeWithText("已收录").assertExists()
        composeRule.onNodeWithText("全部作品").assertExists()
        composeRule.onNodeWithTag(
            "${SEARCH_SCOPE_OPTION_TAG_PREFIX}_${SearchFilterOption.Standard.name}"
        ).assertExists()
        composeRule.onAllNodesWithTag(SEARCH_HAS_SUBTITLE_OPTION_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SEARCH_ALL_AGES_OPTION_TAG).assertCountEquals(0)
    }

    @Test
    fun sortMenu_appliesStandardSortImmediately() {
        var selectedOrder = SearchSortOption.Trend

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchToolbar(
                    keyword = "",
                    onKeywordChange = {},
                    selectedFilter = SearchFilterOption.Standard,
                    selectedOrder = selectedOrder,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = {},
                    onOptionsChanged = { selectedOrder = it.order }
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_SORT_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(
            "${SEARCH_SORT_OPTION_TAG_PREFIX}_${SearchSortOption.DLCount.name}"
        ).performClick()
        composeRule.runOnIdle {
            assertEquals(SearchSortOption.DLCount, selectedOrder)
        }
    }

    @Test
    fun collectedSortMenu_showsCollectedSortOptions() {
        var selectedSort = SearchCollectedSortOption.ReleaseNew

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchToolbar(
                    keyword = "",
                    onKeywordChange = {},
                    selectedFilter = SearchFilterOption.Collected,
                    selectedCollectedSort = selectedSort,
                    selectedLocale = "zh_CN",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = {},
                    onOptionsChanged = { selectedSort = it.collectedSort }
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_SORT_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(
            "${SEARCH_COLLECTED_SORT_OPTION_TAG_PREFIX}_${SearchCollectedSortOption.ReleaseNew.name}"
        ).assertExists()
        composeRule.onNodeWithText("最新收录").assertExists()
        composeRule.onNodeWithText("评分最高").assertExists()
        composeRule.onNodeWithTag("${SEARCH_COLLECTED_SORT_OPTION_TAG_PREFIX}_${SearchCollectedSortOption.ReleaseNew.name}").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        )
        composeRule.onNodeWithTag("${SEARCH_COLLECTED_SORT_OPTION_TAG_PREFIX}_${SearchCollectedSortOption.CollectedNew.name}").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
        )
        composeRule.onAllNodesWithText("时长最长").assertCountEquals(0)
        composeRule.onNodeWithText("最新收录").performClick()

        composeRule.runOnIdle {
            assertEquals(SearchCollectedSortOption.CollectedNew, selectedSort)
        }
    }

    @Test
    fun filterMenu_appliesSubtitleAndAllAgesForDirectSearch() {
        var hasSubtitle by mutableStateOf(false)
        var allAges by mutableStateOf(false)

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchToolbar(
                    keyword = "",
                    onKeywordChange = {},
                    selectedFilter = SearchFilterOption.Standard,
                    hasSubtitle = hasSubtitle,
                    allAges = allAges,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = {},
                    onOptionsChanged = {
                        hasSubtitle = it.hasSubtitle
                        allAges = it.allAges
                    }
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).performClick()
        composeRule.onNodeWithText("有字幕").assertExists()
        composeRule.onNodeWithText("全年龄").assertExists()
        composeRule.onAllNodesWithText("带字幕作品").assertCountEquals(0)
        composeRule.onAllNodesWithText("全年龄（含 R15）").assertCountEquals(0)
        composeRule.onNodeWithTag(SEARCH_HAS_SUBTITLE_OPTION_TAG).performClick()
        composeRule.onNodeWithTag(SEARCH_ALL_AGES_OPTION_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(true, hasSubtitle)
            assertEquals(true, allAges)
        }
    }

    @Test
    fun changingToUnsupportedScope_preservesHiddenWorkFilters() {
        var selectedFilter by mutableStateOf(SearchFilterOption.Standard)
        var hasSubtitle by mutableStateOf(true)
        var allAges by mutableStateOf(true)

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchToolbar(
                    keyword = "",
                    onKeywordChange = {},
                    selectedFilter = selectedFilter,
                    hasSubtitle = hasSubtitle,
                    allAges = allAges,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = {},
                    onOptionsChanged = {
                        selectedFilter = it.scope
                        hasSubtitle = it.hasSubtitle
                        allAges = it.allAges
                    }
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(
            "${SEARCH_SCOPE_OPTION_TAG_PREFIX}_${SearchFilterOption.ChineseTranslated.name}"
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(SearchFilterOption.ChineseTranslated, selectedFilter)
            assertEquals(true, hasSubtitle)
            assertEquals(true, allAges)
        }
        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).performClick()
        composeRule.onAllNodesWithTag(SEARCH_HAS_SUBTITLE_OPTION_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SEARCH_ALL_AGES_OPTION_TAG).assertCountEquals(0)
        composeRule.onNodeWithTag(
            "${SEARCH_SCOPE_OPTION_TAG_PREFIX}_${SearchFilterOption.Standard.name}"
        ).performClick()
        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(SEARCH_HAS_SUBTITLE_OPTION_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        )
        composeRule.onNodeWithTag(SEARCH_ALL_AGES_OPTION_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        )
    }

    @Test
    fun languageMenu_marksCurrentLocaleSelected() {
        composeRule.setContent {
            AsmrPlayerTheme {
                SearchToolbar(
                    keyword = "",
                    onKeywordChange = {},
                    selectedFilter = SearchFilterOption.Standard,
                    selectedLocale = "zh_CN",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = {},
                    onOptionsChanged = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag("${SEARCH_LANGUAGE_OPTION_TAG_PREFIX}_zh_CN").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        )
        composeRule.onNodeWithTag("${SEARCH_LANGUAGE_OPTION_TAG_PREFIX}_ja_JP").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Selected, false)
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
                        selectedFilter = SearchFilterOption.Standard,
                        selectedLocale = "ja_JP",
                        filterControlsLocked = true,
                        searchSubmitLocked = true,
                        showSearchSpinner = false,
                        onSearchSubmit = {},
                        onOptionsChanged = {}
                    )
                    SearchPaginationHeader(
                        page = 2,
                        canGoPrev = true,
                        canGoNext = true,
                        controlsLocked = true,
                        onFirstPage = {},
                        onPrev = {},
                        onNext = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_SORT_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_SUBMIT_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_PREV_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithTag(SEARCH_NEXT_BUTTON_TAG).assertIsNotEnabled()
    }

    @Test
    fun paginationBlankAreaTap_doesNotPassThroughToUnderlyingContent() {
        var underlayClicks = 0

        composeRule.setContent {
            AsmrPlayerTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 72.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { underlayClicks += 1 }
                    )
                    SearchPaginationHeader(
                        page = 2,
                        canGoPrev = true,
                        canGoNext = true,
                        controlsLocked = false,
                        onFirstPage = {},
                        onPrev = {},
                        onNext = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag(SEARCH_PAGINATION_TAG)
            .performTouchInput {
                down(center.copy(x = center.x, y = center.y))
                up()
            }

        composeRule.runOnIdle {
            assertEquals(0, underlayClicks)
        }
    }

    @Test
    fun paginationButtons_remainClickable() {
        var prevCount = 0
        var nextCount = 0

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchPaginationHeader(
                    page = 2,
                    canGoPrev = true,
                    canGoNext = true,
                    controlsLocked = false,
                    onFirstPage = {},
                    onPrev = { prevCount += 1 },
                    onNext = { nextCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_PREV_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag(SEARCH_NEXT_BUTTON_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(1, prevCount)
            assertEquals(1, nextCount)
        }
    }

    @Test
    fun clearButton_clearsKeywordWithoutSubmitting() {
        var submitCount by mutableIntStateOf(0)
        var latestKeyword = "RJ123456"

        composeRule.setContent {
            var keyword by remember { mutableStateOf("RJ123456") }
            latestKeyword = keyword

            AsmrPlayerTheme {
                SearchToolbar(
                    keyword = keyword,
                    onKeywordChange = { keyword = it },
                    selectedFilter = SearchFilterOption.Standard,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = { submitCount += 1 },
                    onOptionsChanged = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_CLEAR_BUTTON_TAG).performClick()
        composeRule.runOnIdle {
            assertEquals("", latestKeyword)
            assertEquals(0, submitCount)
        }
        composeRule.onAllNodesWithTag(SEARCH_CLEAR_BUTTON_TAG).assertCountEquals(0)
    }

    @Test
    fun clearButton_runsCustomClearActionWhenProvided() {
        var clearSearchCount by mutableIntStateOf(0)
        var valueChangeCount by mutableIntStateOf(0)
        var latestKeyword = "RJ123456"

        composeRule.setContent {
            var keyword by remember { mutableStateOf("RJ123456") }
            latestKeyword = keyword

            AsmrPlayerTheme {
                SearchToolbar(
                    keyword = keyword,
                    onKeywordChange = {
                        valueChangeCount += 1
                        keyword = it
                    },
                    selectedFilter = SearchFilterOption.Standard,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    onSearchSubmit = {},
                    onClearKeyword = {
                        keyword = ""
                        clearSearchCount += 1
                    },
                    onOptionsChanged = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_CLEAR_BUTTON_TAG).performClick()
        composeRule.runOnIdle {
            assertEquals("", latestKeyword)
            assertEquals(1, clearSearchCount)
            assertEquals(0, valueChangeCount)
        }
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
                    selectedFilter = SearchFilterOption.Standard,
                    selectedLocale = "ja_JP",
                    filterControlsLocked = false,
                    searchSubmitLocked = false,
                    showSearchSpinner = false,
                    showPagination = true,
                    page = 2,
                    canGoPrev = true,
                    canGoNext = true,
                    controlsLocked = false,
                    rightPanelToggle = null,
                    chromeState = chromeState,
                    onMeasured = { chromeState.updateHeight(it.height.toFloat()) },
                    onSearchSubmit = {},
                    onOptionsChanged = {},
                    onFirstPage = {},
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

    @Test
    fun collapsedSearchChrome_doesNotBlockUnderlyingItemTap() {
        composeRule.mainClock.autoAdvance = false
        lateinit var chromeState: CollapsibleHeaderState
        var itemClicks = 0

        composeRule.setContent {
            chromeState = remember { CollapsibleHeaderState() }

            AsmrPlayerTheme {
                Box(modifier = Modifier.interruptScrollableFlingOnPointerDown {}) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("search_top_item")
                            .clickable { itemClicks += 1 }
                    )
                    SearchChrome(
                        keyword = "RJ123456",
                        onKeywordChange = {},
                        selectedFilter = SearchFilterOption.Standard,
                        selectedLocale = "ja_JP",
                        filterControlsLocked = false,
                        searchSubmitLocked = false,
                        showSearchSpinner = false,
                        showPagination = true,
                        page = 2,
                        canGoPrev = true,
                        canGoNext = true,
                        controlsLocked = false,
                        rightPanelToggle = null,
                        chromeState = chromeState,
                        onMeasured = { chromeState.updateHeight(it.height.toFloat()) },
                        onSearchSubmit = {},
                        onOptionsChanged = {},
                        onFirstPage = {},
                        onPrev = {},
                        onNext = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle { chromeState.collapse() }
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("search_top_item").performTouchInput {
            down(center)
            up()
        }

        composeRule.runOnIdle { assertEquals(1, itemClicks) }
    }
}
