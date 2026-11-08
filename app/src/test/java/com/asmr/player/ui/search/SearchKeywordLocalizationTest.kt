package com.asmr.player.ui.search

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.domain.model.AlbumTag
import com.asmr.player.hotlistening.SearchSuggestionTerm
import com.asmr.player.ui.createEaraComposeRule
import com.asmr.player.ui.testWindowSizeClass
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchKeywordLocalizationTest {
    @get:Rule val composeRule = createEaraComposeRule()

    private fun suggestion(value: String, english: String) = SearchSuggestionTerm(
        value = value,
        tagDetails = listOf(AlbumTag(
            key = "asmr-one:tag:$value", source = "asmr-one", originalName = value,
            names = mapOf("zh-CN" to value, "en" to english)
        ))
    )

    @Test fun quickSearchDisplaysAppLanguageAndKeepsCanonicalQuery() {
        val language = mutableStateOf("en")
        val submitted = mutableListOf<SearchAssistSearchRequest>()
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                setLocales(LocaleList.forLanguageTags(language.value))
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                AsmrPlayerTheme {
                    SearchAssistContent(
                        windowSizeClass = testWindowSizeClass(),
                        initialRequest = SearchAssistSearchRequest(),
                        uiState = SearchAssistUiState(
                            suggestions = SearchSuggestionsUiData(hotTags = listOf(suggestion("耳语", "Whispering"))),
                            isLoadingSuggestions = false
                        ),
                        onSubmitSearch = { submitted += it }, onClearHistory = {}, onRefreshRecommendations = {}
                    )
                }
            }
        }
        composeRule.onNode(hasText("Whispering") and hasAnyAncestor(hasTestTag(SEARCH_ASSIST_HOT_TAGS_SECTION_TAG))).performClick()
        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG).assertTextEquals("Whispering")
        composeRule.runOnIdle { assertEquals("耳语", submitted.single().keyword); language.value = "zh-CN" }
        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG).assertTextEquals("耳语")
        composeRule.runOnIdle { language.value = "en" }
        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG).assertTextEquals("Whispering")
        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG).performTextReplacement("耳语")
        composeRule.onNodeWithTag(SEARCH_ASSIST_INPUT_TAG).assertTextEquals("耳语")
        composeRule.onNodeWithTag(SEARCH_ASSIST_SUBMIT_TAG).performClick()
        composeRule.runOnIdle { assertEquals(listOf("耳语", "耳语"), submitted.map { it.keyword }) }
    }

    @Test fun carouselUsesCurrentLanguageAcrossRotationAndPausesWhenInactive() {
        val language = mutableStateOf("en")
        val active = mutableStateOf(true)
        val terms = buildSearchHotKeywordTerms(emptyList(), listOf(suggestion("耳语", "Whispering"), suggestion("掏耳", "Ear Cleaning")))
        var current: SearchHotKeywordCarouselItem? = null
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList.forLanguageTags(language.value)) }
            CompositionLocalProvider(LocalConfiguration provides config) {
                val labels = rememberLocalizedSearchKeywords(terms)
                val item = rememberSearchHotKeywordCarouselItem(terms, labels, showFallback = true, isActive = active.value)
                current = item
                Text(item.placeholder, Modifier.testTag("placeholder"))
            }
        }
        val names = mapOf("耳语" to "Whispering", "掏耳" to "Ear Cleaning")
        val first = composeRule.runOnIdle { current!!.keyword }
        composeRule.onNodeWithTag("placeholder").assertTextEquals(names.getValue(first!!))
        composeRule.mainClock.advanceTimeBy(5_100)
        val second = composeRule.runOnIdle { current!!.keyword }
        assertNotEquals(first, second)
        composeRule.onNodeWithTag("placeholder").assertTextEquals(names.getValue(second!!))
        composeRule.runOnIdle { language.value = "zh-CN"; active.value = false }
        composeRule.onNodeWithTag("placeholder").assertTextEquals(second)
        composeRule.mainClock.advanceTimeBy(10_100)
        composeRule.runOnIdle { assertEquals(second, current!!.keyword) }
    }

    @Test fun labelsOutsideCarouselLimitAndLateMetadataRemainLocalized() {
        val terms = mutableStateOf(buildSearchHotKeywordTerms(emptyList(), (1..30).map { SearchSuggestionTerm(value = "标签$it") }))
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply { setLocales(LocaleList.forLanguageTags("en")) }
            CompositionLocalProvider(LocalConfiguration provides config) {
                val labels = rememberLocalizedSearchKeywords(terms.value)
                Text(labels.getValue("标签30"), Modifier.testTag("keyword"))
            }
        }
        composeRule.onNodeWithTag("keyword").assertTextEquals("标签30")
        composeRule.runOnIdle {
            terms.value = buildSearchHotKeywordTerms(emptyList(), (1..30).map { suggestion("标签$it", "Tag $it") })
        }
        composeRule.onNodeWithTag("keyword").assertTextEquals("Tag 30")
    }
}
