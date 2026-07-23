package com.asmr.player.ui.search

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.domain.model.Album
import com.asmr.player.hotlistening.SearchSuggestionTerm
import com.asmr.player.ui.testWindowSizeClass
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchAssistScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inputIsFocusedAtTextEndWhenAssistPageOpens() {
        val initialKeyword = "RJ123456"

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchAssistContent(
                    windowSizeClass = testWindowSizeClass(),
                    initialRequest = SearchAssistSearchRequest(keyword = initialKeyword),
                    uiState = SearchAssistUiState(),
                    onSubmitSearch = {},
                    onClearHistory = {},
                    onRefreshRecommendations = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG).assertIsFocused()
        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.TextSelectionRange,
                TextRange(initialKeyword.length)
            )
        )
    }

    @Test
    fun longPressInput_doesNotClearFocusViaOutsideTapHandler() {
        composeRule.setContent {
            AsmrPlayerTheme {
                SearchAssistContent(
                    windowSizeClass = testWindowSizeClass(),
                    initialRequest = SearchAssistSearchRequest(keyword = "RJ123456"),
                    uiState = SearchAssistUiState(),
                    onSubmitSearch = {},
                    onClearHistory = {},
                    onRefreshRecommendations = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG)
            .performTouchInput {
                down(center)
                advanceEventTime(800)
                up()
            }

        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG).assertIsFocused()
    }

    @Test
    fun imeSearchAndSubmitButtonShareSubmitPath() {
        val submitted = mutableListOf<SearchAssistSearchRequest>()

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchAssistContent(
                    windowSizeClass = testWindowSizeClass(),
                    initialRequest = SearchAssistSearchRequest(),
                    uiState = SearchAssistUiState(),
                    onSubmitSearch = { submitted += it },
                    onClearHistory = {},
                    onRefreshRecommendations = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG).performTextInput("  rain  ")
        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG).performImeAction()
        composeRule.onNodeWithTag(SEARCH_ASSIST_SUBMIT_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("rain", "rain"), submitted.map { it.keyword })
        }
    }

    @Test
    fun historyCvAndTagChipsSubmitTheirText() {
        val submitted = mutableListOf<SearchAssistSearchRequest>()

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchAssistContent(
                    windowSizeClass = testWindowSizeClass(),
                    initialRequest = SearchAssistSearchRequest(),
                    uiState = SearchAssistUiState(
                        history = listOf("雨声"),
                        suggestions = SearchSuggestionsUiData(
                            hotCvs = listOf(SearchSuggestionTerm(value = "CV A", count = 12, rank = 1)),
                            hotTags = listOf(SearchSuggestionTerm(value = "耳语", count = 8, rank = 1))
                        ),
                        isLoadingSuggestions = false
                    ),
                    onSubmitSearch = { submitted += it },
                    onClearHistory = {},
                    onRefreshRecommendations = {}
                )
            }
        }

        composeRule.onAllNodesWithText("12").assertCountEquals(0)
        composeRule.onAllNodesWithText("8").assertCountEquals(0)
        composeRule.onNodeWithText("雨声").performClick()
        composeRule.onNodeWithText("CV A").performClick()
        composeRule.onNodeWithText("耳语").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("雨声", "CV A", "耳语"), submitted.map { it.keyword })
        }
    }

    @Test
    fun emptySubmitUsesCurrentHotKeywordPlaceholder() {
        val submitted = mutableListOf<SearchAssistSearchRequest>()

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchAssistContent(
                    windowSizeClass = testWindowSizeClass(),
                    initialRequest = SearchAssistSearchRequest(),
                    uiState = SearchAssistUiState(
                        suggestions = SearchSuggestionsUiData(
                            hotCvs = listOf(SearchSuggestionTerm(value = "CV A", count = 12, rank = 1))
                        ),
                        isLoadingSuggestions = false
                    ),
                    onSubmitSearch = { submitted += it },
                    onClearHistory = {},
                    onRefreshRecommendations = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_ASSIST_SUBMIT_TAG).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("CV A"), submitted.map { it.keyword })
        }
    }

    @Test
    fun currentFilterSelectionSubmitsInputWithoutHotKeywordFallback() {
        val submitted = mutableListOf<SearchAssistSearchRequest>()

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchAssistContent(
                    windowSizeClass = testWindowSizeClass(),
                    initialRequest = SearchAssistSearchRequest(),
                    uiState = SearchAssistUiState(
                        suggestions = SearchSuggestionsUiData(
                            hotCvs = listOf(SearchSuggestionTerm(value = "CV A", count = 12, rank = 1))
                        ),
                        isLoadingSuggestions = false
                    ),
                    onSubmitSearch = { submitted += it },
                    onClearHistory = {},
                    onRefreshRecommendations = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag("${SEARCH_SCOPE_OPTION_TAG_PREFIX}_${SearchFilterOption.Collected.name}")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, submitted.size)
            assertEquals("", submitted.single().keyword)
            assertEquals(SearchFilterOption.Collected, submitted.single().selectedFilter)
        }
    }

    @Test
    fun filterSelectionSubmitsTypedInputAndSelectedFilter() {
        val submitted = mutableListOf<SearchAssistSearchRequest>()

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchAssistContent(
                    windowSizeClass = testWindowSizeClass(),
                    initialRequest = SearchAssistSearchRequest(),
                    uiState = SearchAssistUiState(),
                    onSubmitSearch = { submitted += it },
                    onClearHistory = {},
                    onRefreshRecommendations = {}
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG).performTextInput("  rain  ")
        composeRule.onNodeWithTag(SEARCH_SCOPE_BUTTON_TAG).performClick()
        composeRule.onNodeWithTag("${SEARCH_SCOPE_OPTION_TAG_PREFIX}_${SearchFilterOption.ChineseTranslated.name}")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, submitted.size)
            assertEquals("rain", submitted.single().keyword)
            assertEquals(SearchFilterOption.ChineseTranslated, submitted.single().selectedFilter)
        }
    }

    @Test
    fun clearHistoryRecommendationCardAndRefreshUseSeparateCallbacks() {
        var clearHistoryCount = 0
        var refreshRecommendationsCount = 0
        val submitted = mutableListOf<SearchAssistSearchRequest>()
        val firstAlbum = Album(
            title = "Rain Work",
            path = "",
            workId = "RJ111111",
            rjCode = "RJ111111",
            circle = "Circle A",
            cv = "CV A"
        )
        val secondAlbum = Album(
            title = "Sleep Work",
            path = "",
            workId = "RJ222222",
            rjCode = "RJ222222",
            circle = "Circle B",
            cv = "CV B"
        )

        composeRule.setContent {
            AsmrPlayerTheme {
                SearchAssistContent(
                    windowSizeClass = testWindowSizeClass(),
                    initialRequest = SearchAssistSearchRequest(),
                    uiState = SearchAssistUiState(
                        history = listOf("雨声"),
                        suggestions = SearchSuggestionsUiData(
                            recommendations = listOf(
                                SearchAssistRecommendation(album = firstAlbum),
                                SearchAssistRecommendation(album = secondAlbum)
                            )
                        ),
                        isLoadingRecommendations = false
                    ),
                    onSubmitSearch = { submitted += it },
                    onClearHistory = { clearHistoryCount += 1 },
                    onRefreshRecommendations = { refreshRecommendationsCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag(SEARCH_ASSIST_HISTORY_CLEAR_TAG).performClick()
        composeRule.onNodeWithText("是否清空历史搜索记录？").assertExists()
        composeRule.runOnIdle {
            assertEquals(0, clearHistoryCount)
        }
        composeRule.onNodeWithText("清空").performClick()
        composeRule.onNodeWithText("猜你喜欢").assertExists()
        composeRule.onNodeWithText("CV A").assertExists()
        composeRule.onAllNodesWithText("Circle A").assertCountEquals(0)
        composeRule.onAllNodesWithTag(SEARCH_ASSIST_RECOMMENDATION_CARD_TAG).assertCountEquals(2)
        composeRule.onAllNodesWithText("RJ111111").assertCountEquals(0)
        composeRule.onAllNodesWithTag(SEARCH_ASSIST_RECOMMENDATION_CARD_TAG)[0]
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(SEARCH_ASSIST_RECOMMENDATION_REFRESH_TAG)
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, clearHistoryCount)
            assertEquals(1, refreshRecommendationsCount)
            assertEquals(listOf("RJ111111"), submitted.map { it.keyword })
        }
    }

    @Test
    fun recommendationWithoutTitleUsesGenericLabelInsteadOfRj() {
        composeRule.setContent {
            AsmrPlayerTheme {
                SearchAssistContent(
                    windowSizeClass = testWindowSizeClass(),
                    initialRequest = SearchAssistSearchRequest(),
                    uiState = SearchAssistUiState(
                        suggestions = SearchSuggestionsUiData(
                            recommendations = listOf(
                                SearchAssistRecommendation(
                                    album = Album(
                                        title = "",
                                        path = "",
                                        workId = "RJ333333",
                                        rjCode = "RJ333333",
                                        cv = "CV C"
                                    )
                                )
                            )
                        ),
                        isLoadingRecommendations = false
                    ),
                    onSubmitSearch = {},
                    onClearHistory = {},
                    onRefreshRecommendations = {}
                )
            }
        }

        composeRule.onNodeWithText("推荐作品").assertExists()
        composeRule.onAllNodesWithText("RJ333333").assertCountEquals(0)
    }
}
