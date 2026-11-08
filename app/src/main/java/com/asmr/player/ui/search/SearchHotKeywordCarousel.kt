package com.asmr.player.ui.search

import com.asmr.player.i18n.appString
import com.asmr.player.R

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import com.asmr.player.hotlistening.SearchSuggestionTerm
import kotlinx.coroutines.delay
import java.util.Locale

internal val DefaultSearchPlaceholder: String get() = appString(R.string.ui_search_albums_circles_voice_actors)
private const val SearchPlaceholderCarouselIntervalMs = 5_000L
private const val SearchHotKeywordCarouselLimit = 24

internal data class SearchHotKeywordTerm(
    val value: String,
    val tagDetails: List<com.asmr.player.domain.model.AlbumTag>? = emptyList()
)

internal data class SearchHotKeywordCarouselItem(
    val placeholder: String,
    val keyword: String?
)

internal fun buildSearchHotKeywordTerms(
    hotCvs: List<SearchSuggestionTerm>,
    hotTags: List<SearchSuggestionTerm>,
    limit: Int = Int.MAX_VALUE
): List<SearchHotKeywordTerm> {
    val cvTerms = hotCvs.toHotKeywordTerms()
    val tagTerms = hotTags.toHotKeywordTerms()
    val maxSize = maxOf(cvTerms.size, tagTerms.size)
    val merged = buildList {
        for (index in 0 until maxSize) {
            cvTerms.getOrNull(index)?.let(::add)
            tagTerms.getOrNull(index)?.let(::add)
        }
    }
    return merged
        .distinctBy { it.value.lowercase(Locale.ROOT) }
        .take(limit.coerceAtLeast(0))
}

@Composable
internal fun rememberLocalizedSearchKeywords(terms: List<SearchHotKeywordTerm>): Map<String, String> {
    val configuration = LocalConfiguration.current
    val language = configuration.locales[0]?.toLanguageTag().orEmpty()
    val locale = if (language.startsWith("zh", ignoreCase = true)) language else "en"
    return remember(terms, locale) {
        terms.associate { term ->
            term.value to SearchSuggestionTerm(value = term.value, tagDetails = term.tagDetails).localizedValue(locale)
        }
    }
}

@Composable
internal fun rememberSearchHotKeywordCarouselItem(
    terms: List<SearchHotKeywordTerm>,
    labels: Map<String, String>,
    showFallback: Boolean,
    isActive: Boolean = true,
): SearchHotKeywordCarouselItem {
    val keywords = remember(terms) {
        terms.map { term -> term.value.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(SearchHotKeywordCarouselLimit)
    }
    val signature = remember(keywords) {
        keywords.joinToString(separator = "\u001F")
    }
    var carouselState by remember(signature) {
        mutableStateOf(SearchHotKeywordCarouselState(queue = keywords.shuffledAvoidingFirst()))
    }

    LaunchedEffect(signature, isActive) {
        if (!isActive || keywords.size <= 1) return@LaunchedEffect
        while (true) {
            delay(SearchPlaceholderCarouselIntervalMs)
            carouselState = carouselState.next()
        }
    }

    val keyword = carouselState.current
    return when {
        keyword != null -> SearchHotKeywordCarouselItem(placeholder = labels[keyword] ?: keyword, keyword = keyword)
        showFallback -> SearchHotKeywordCarouselItem(placeholder = DefaultSearchPlaceholder, keyword = null)
        else -> SearchHotKeywordCarouselItem(placeholder = "", keyword = null)
    }
}

private fun List<SearchSuggestionTerm>.toHotKeywordTerms(): List<SearchHotKeywordTerm> {
    return mapNotNull { term ->
        val value = term.value.trim()
        if (value.isBlank()) {
            null
        } else {
            SearchHotKeywordTerm(value = value, tagDetails = term.tagDetails)
        }
    }
}

private data class SearchHotKeywordCarouselState(
    val queue: List<String>,
    val index: Int = 0
) {
    val current: String?
        get() = queue.getOrNull(index)

    fun next(): SearchHotKeywordCarouselState {
        if (queue.size <= 1) return this
        val nextIndex = index + 1
        if (nextIndex <= queue.lastIndex) {
            return copy(index = nextIndex)
        }
        return SearchHotKeywordCarouselState(
            queue = queue.shuffledAvoidingFirst(avoidFirst = current)
        )
    }
}

private fun List<String>.shuffledAvoidingFirst(avoidFirst: String? = null): List<String> {
    if (size <= 1) return this
    val shuffled = shuffled().toMutableList()
    val avoidIndex = avoidFirst?.let { avoided ->
        shuffled.indexOfFirst { it.equals(avoided, ignoreCase = true) }
    } ?: -1
    if (avoidIndex == 0) {
        val swapIndex = (1 until shuffled.size).random()
        val first = shuffled[0]
        shuffled[0] = shuffled[swapIndex]
        shuffled[swapIndex] = first
    }
    return shuffled
}
