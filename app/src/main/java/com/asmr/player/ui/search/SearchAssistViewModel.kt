package com.asmr.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.datastore.SearchCacheStore
import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.api.AsmrOneRecommendationCursorExpiredException
import com.asmr.player.data.remote.api.AsmrOneRecommendationItem
import com.asmr.player.data.remote.api.AsmrOneRecommendationResponse
import com.asmr.player.data.remote.api.normalizeRecommendationRjs
import com.asmr.player.data.repository.ListeningRecordRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.util.DlsiteWorkNo
import com.asmr.player.hotlistening.HotListeningApi
import com.asmr.player.hotlistening.SearchSuggestionTerm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
internal const val SEARCH_ASSIST_RESULT_COLLECTED_SORT_KEY = "searchCollectedSortName"
internal const val SEARCH_ASSIST_RESULT_LOCALE_KEY = "searchLocale"
internal const val SEARCH_ASSIST_RECOMMENDATION_DISPLAY_LIMIT = 10

data class SearchAssistSearchRequest(
    val keyword: String = "",
    val orderName: String = SearchSortOption.Trend.name,
    val purchasedOnly: Boolean = false,
    val presaleOnly: Boolean = false,
    val chineseTranslatedOnly: Boolean = false,
    val collectedOnly: Boolean = true,
    val collectedSortName: String = SearchCollectedSortOption.ReleaseNew.name,
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

    val selectedCollectedSort: SearchCollectedSortOption
        get() = SearchCollectedSortOption.fromName(collectedSortName)
}

@HiltViewModel
class SearchAssistViewModel @Inject constructor(
    private val searchCacheStore: SearchCacheStore,
    private val hotListeningApi: HotListeningApi,
    private val asmrOneAvailabilityApi: AsmrOneAvailabilityApi,
    private val listeningRecordRepository: ListeningRecordRepository,
    private val recommendationSessionCache: SearchRecommendationSessionCache
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchAssistUiState())
    val uiState: StateFlow<SearchAssistUiState> = _uiState.asStateFlow()
    private var listenedRjs: List<String> = emptyList()
    private var recommendationCursor: String = ""

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { loadHistory() }
        viewModelScope.launch { loadSuggestionTerms() }
        viewModelScope.launch { loadRecommendations() }
    }

    fun submitSearch(request: SearchAssistSearchRequest, onSubmitted: (SearchAssistSearchRequest) -> Unit) {
        val normalized = request.keyword.trim()
        val normalizedRequest = request.copy(keyword = normalized)
        viewModelScope.launch {
            if (normalized.isNotBlank()) {
                searchCacheStore.addHistory(normalized)
                loadHistory()
            }
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

    private suspend fun loadSuggestionTerms() {
        if (!hotListeningApi.isBackendConfigured) {
            _uiState.update {
                it.copy(
                    isLoadingSuggestions = false,
                    suggestions = it.suggestions.copy(hotCvs = emptyList(), hotTags = emptyList())
                )
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
                    recommendations = it.suggestions.recommendations
                )
            )
        }
    }

    fun refreshRecommendations() {
        if (
            _uiState.value.isLoadingRecommendations ||
            listenedRjs.isEmpty() ||
            recommendationCursor.isBlank()
        ) {
            return
        }
        _uiState.update { it.copy(isLoadingRecommendations = true) }
        viewModelScope.launch {
            loadRecommendationBatch()
        }
    }

    private suspend fun loadRecommendations() {
        if (!asmrOneAvailabilityApi.isBackendConfigured) {
            _uiState.update {
                it.copy(
                    isLoadingRecommendations = false,
                    hasMoreRecommendations = false,
                    suggestions = it.suggestions.copy(recommendations = emptyList())
                )
            }
            return
        }
        _uiState.update { it.copy(isLoadingRecommendations = true) }
        listenedRjs = normalizeRecommendationRjs(
            values = try {
                listeningRecordRepository.recentRjs(MAX_RECOMMENDATION_EXCLUDES)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                emptyList()
            },
            limit = MAX_RECOMMENDATION_EXCLUDES
        )
        recommendationCursor = ""
        if (listenedRjs.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoadingRecommendations = false,
                    hasMoreRecommendations = false,
                    suggestions = it.suggestions.copy(recommendations = emptyList())
                )
            }
            return
        }
        recommendationSessionCache.read(
            seedRjs = listenedRjs.take(MAX_RECOMMENDATION_SEEDS),
            excludeRjs = listenedRjs
        )?.let { cachedResponse ->
            applyRecommendationResponse(cachedResponse)
            return
        }
        loadRecommendationBatch()
    }

    private suspend fun loadRecommendationBatch() {
        _uiState.update { it.copy(isLoadingRecommendations = true) }
        val response = requestRecommendationPageWithRecovery()
        if (response == null) {
            _uiState.update { it.copy(isLoadingRecommendations = false) }
            return
        }
        recommendationSessionCache.write(
            seedRjs = listenedRjs.take(MAX_RECOMMENDATION_SEEDS),
            excludeRjs = listenedRjs,
            response = response
        )
        applyRecommendationResponse(response)
    }

    private fun applyRecommendationResponse(response: AsmrOneRecommendationResponse) {
        val items = response.items.orEmpty()
        recommendationCursor = response.recommendationContinuationCursor()
        val recommendations = items
            .map { item -> item.toSearchAssistRecommendation() }
            .filter { recommendation -> recommendation.album.rjCode.isNotBlank() }
            .take(SEARCH_ASSIST_RECOMMENDATION_DISPLAY_LIMIT)
        _uiState.update {
            it.copy(
                isLoadingRecommendations = false,
                hasMoreRecommendations = recommendationCursor.isNotBlank(),
                suggestions = it.suggestions.copy(recommendations = recommendations)
            )
        }
    }

    private suspend fun requestRecommendationPageWithRecovery(): AsmrOneRecommendationResponse? =
        try {
            requestRecommendationPage()
        } catch (error: AsmrOneRecommendationCursorExpiredException) {
            recommendationCursor = ""
            try {
                requestRecommendationPage()
            } catch (retryError: CancellationException) {
                throw retryError
            } catch (_: Throwable) {
                null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

    private suspend fun requestRecommendationPage(): AsmrOneRecommendationResponse =
        if (recommendationCursor.isBlank()) {
            asmrOneAvailabilityApi.getRecommendations(
                seedRjs = listenedRjs.take(MAX_RECOMMENDATION_SEEDS),
                excludeRjs = listenedRjs,
                limit = SEARCH_ASSIST_RECOMMENDATION_DISPLAY_LIMIT
            )
        } else {
            asmrOneAvailabilityApi.continueRecommendations(
                cursor = recommendationCursor,
                limit = SEARCH_ASSIST_RECOMMENDATION_DISPLAY_LIMIT
            )
        }

    private companion object {
        const val MAX_RECOMMENDATION_SEEDS = 20
        const val MAX_RECOMMENDATION_EXCLUDES = 200
    }
}

data class SearchAssistUiState(
    val history: List<String> = emptyList(),
    val suggestions: SearchSuggestionsUiData = SearchSuggestionsUiData(),
    val isLoadingSuggestions: Boolean = true,
    val isLoadingRecommendations: Boolean = true,
    val hasMoreRecommendations: Boolean = false
)

data class SearchSuggestionsUiData(
    val hotCvs: List<SearchSuggestionTerm> = emptyList(),
    val hotTags: List<SearchSuggestionTerm> = emptyList(),
    val recommendations: List<SearchAssistRecommendation> = emptyList()
)

data class SearchAssistRecommendation(val album: Album)

internal fun AsmrOneRecommendationItem.toSearchAssistRecommendation(): SearchAssistRecommendation {
    val normalizedRj = buildList {
        add(rj)
        add(originalWorkno)
        addAll(matchedRjs.orEmpty())
    }
        .asSequence()
        .map { DlsiteWorkNo.normalizeWorkNo(it, minimumDigits = 6) }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    return SearchAssistRecommendation(
        album = Album(
            title = title.trim(),
            path = "",
            workId = normalizedRj,
            rjCode = normalizedRj,
            cv = cvs.orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString(" / "),
            coverUrl = mainCoverUrl.trim()
        )
    )
}

internal fun AsmrOneRecommendationResponse.recommendationContinuationCursor(): String =
    nextCursor.trim().takeIf { hasMore }.orEmpty()
