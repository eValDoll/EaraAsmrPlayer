package com.asmr.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.datastore.SearchCacheStore
import com.asmr.player.domain.model.Album
import com.asmr.player.hotlistening.HotListeningApi
import com.asmr.player.hotlistening.HotListeningItem
import com.asmr.player.hotlistening.SearchSuggestionTerm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal const val SEARCH_ASSIST_RESULT_KEY = "searchKeyword"
internal const val SEARCH_ASSIST_RESULT_SIGNAL_KEY = "searchKeywordSignal"
internal const val SEARCH_ASSIST_RESULT_ORDER_KEY = "searchOrderName"
internal const val SEARCH_ASSIST_RESULT_PURCHASED_ONLY_KEY = "searchPurchasedOnly"
internal const val SEARCH_ASSIST_RESULT_PRESALE_ONLY_KEY = "searchPresaleOnly"
internal const val SEARCH_ASSIST_RESULT_CHINESE_TRANSLATED_ONLY_KEY = "searchChineseTranslatedOnly"
internal const val SEARCH_ASSIST_RESULT_COLLECTED_ONLY_KEY = "searchCollectedOnly"
internal const val SEARCH_ASSIST_RESULT_LOCALE_KEY = "searchLocale"

data class SearchAssistSearchRequest(
    val keyword: String = "",
    val orderName: String = SearchSortOption.Trend.name,
    val purchasedOnly: Boolean = false,
    val presaleOnly: Boolean = false,
    val chineseTranslatedOnly: Boolean = false,
    val collectedOnly: Boolean = true,
    val locale: String = "ja_JP"
) {
    val selectedOrder: SearchSortOption
        get() = SearchSortOption.values().firstOrNull { it.name == orderName } ?: SearchSortOption.Trend

    val selectedFilter: SearchFilterOption
        get() = SearchFilterOption.fromState(
            order = selectedOrder,
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly
        )
}

@HiltViewModel
class SearchAssistViewModel @Inject constructor(
    private val searchCacheStore: SearchCacheStore,
    private val hotListeningApi: HotListeningApi
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchAssistUiState())
    val uiState: StateFlow<SearchAssistUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loadHistory()
            loadSuggestions()
        }
    }

    fun submitSearch(request: SearchAssistSearchRequest, onSubmitted: (SearchAssistSearchRequest) -> Unit) {
        val normalized = request.keyword.trim()
        if (normalized.isBlank()) return
        val normalizedRequest = request.copy(keyword = normalized)
        viewModelScope.launch {
            searchCacheStore.addHistory(normalized)
            loadHistory()
            onSubmitted(normalizedRequest)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            searchCacheStore.clearHistory()
            _uiState.update { it.copy(history = emptyList()) }
        }
    }

    private suspend fun loadHistory() {
        val history = runCatching { searchCacheStore.readHistory() }.getOrDefault(emptyList())
        _uiState.update { it.copy(history = history) }
    }

    private suspend fun loadSuggestions() {
        if (!hotListeningApi.isBackendConfigured) {
            _uiState.update {
                it.copy(isLoadingSuggestions = false, suggestions = SearchSuggestionsUiData())
            }
            return
        }
        _uiState.update { it.copy(isLoadingSuggestions = true) }
        val suggestions = runCatching { hotListeningApi.getSearchSuggestions() }.getOrNull()
        _uiState.update {
            it.copy(
                isLoadingSuggestions = false,
                suggestions = SearchSuggestionsUiData(
                    hotCvs = suggestions?.hotCvs.orEmpty()
                        .filter { term -> term.value.isNotBlank() },
                    hotTags = suggestions?.hotTags.orEmpty()
                        .filter { term -> term.value.isNotBlank() },
                    hotWorks = suggestions?.hotWorks.orEmpty()
                        .filter { item -> item.rj.isNotBlank() || item.title.isNotBlank() }
                        .take(10)
                        .map { item -> item.toHotWork() }
                )
            )
        }
    }
}

data class SearchAssistUiState(
    val history: List<String> = emptyList(),
    val suggestions: SearchSuggestionsUiData = SearchSuggestionsUiData(),
    val isLoadingSuggestions: Boolean = true
)

data class SearchSuggestionsUiData(
    val hotCvs: List<SearchSuggestionTerm> = emptyList(),
    val hotTags: List<SearchSuggestionTerm> = emptyList(),
    val hotWorks: List<SearchAssistHotWork> = emptyList()
)

data class SearchAssistHotWork(
    val album: Album
)

internal fun HotListeningItem.toHotWork(): SearchAssistHotWork {
    val normalizedRj = rj.trim().uppercase()
    return SearchAssistHotWork(
        album = Album(
            title = title.trim().ifBlank { "热门作品" },
            path = "",
            workId = normalizedRj,
            rjCode = normalizedRj,
            circle = circle.trim(),
            cv = cv.trim(),
            tags = tagList,
            coverUrl = coverUrl.trim()
        )
    )
}
