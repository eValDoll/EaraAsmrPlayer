package com.asmr.player.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.asmr.player.hotlistening.SearchSuggestionTerm
import kotlinx.coroutines.delay

internal const val DefaultSearchPlaceholder = "搜索专辑、社团、CV..."
private const val SearchPlaceholderCarouselIntervalMs = 5_000L
private const val SearchHotKeywordCarouselLimit = 24

internal data class SearchHotKeywordTerm(
    val value: String
)

internal data class SearchHotKeywordCarouselItem(
    val placeholder: String,
    val keyword: String?
)

internal fun buildSearchHotKeywordTerms(
    hotCvs: List<SearchSuggestionTerm>,
    hotTags: List<SearchSuggestionTerm>,
    limit: Int = SearchHotKeywordCarouselLimit
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
        .distinctBy { it.value.lowercase() }
        .take(limit.coerceAtLeast(0))
}

@Composable
internal fun rememberSearchHotKeywordCarouselItem(
    terms: List<SearchHotKeywordTerm>,
    showFallback: Boolean
): SearchHotKeywordCarouselItem {
    val keywords = remember(terms) {
        terms.map { term -> term.value.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }
    val signature = remember(keywords) {
        keywords.joinToString(separator = "\u001F")
    }
    var carouselState by remember(signature) {
        mutableStateOf(SearchHotKeywordCarouselState(queue = keywords.shuffledAvoidingFirst()))
    }

    LaunchedEffect(signature) {
        if (keywords.size <= 1) return@LaunchedEffect
        while (true) {
            delay(SearchPlaceholderCarouselIntervalMs)
            carouselState = carouselState.next()
        }
    }

    val keyword = carouselState.current
    return when {
        keyword != null -> SearchHotKeywordCarouselItem(placeholder = keyword, keyword = keyword)
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
            SearchHotKeywordTerm(value = value)
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
