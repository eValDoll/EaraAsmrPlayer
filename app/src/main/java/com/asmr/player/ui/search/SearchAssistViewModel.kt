package com.asmr.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.datastore.SearchCacheStore
import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.api.AsmrOneRecommendationItem
import com.asmr.player.data.remote.api.AsmrOneRecommendationResponse
import com.asmr.player.data.remote.api.normalizeRecommendationRjs
import com.asmr.player.data.repository.ListeningRecordRepository
import com.asmr.player.domain.model.Album
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
    private val listeningRecordRepository: ListeningRecordRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchAssistUiState())
    val uiState: StateFlow<SearchAssistUiState> = _uiState.asStateFlow()
    private var listenedRjs: List<String> = emptyList()
    private val recommendationSeenRjs = linkedSetOf<String>()

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
        if (_uiState.value.isLoadingRecommendations || listenedRjs.isEmpty()) return
        _uiState.update { it.copy(isLoadingRecommendations = true) }
        viewModelScope.launch {
            loadRecommendationBatch(retryWithoutSeenWhenEmpty = true)
        }
    }

    private suspend fun loadRecommendations() {
        if (!asmrOneAvailabilityApi.isBackendConfigured) {
            _uiState.update {
                it.copy(
                    isLoadingRecommendations = false,
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
        recommendationSeenRjs.clear()
        if (listenedRjs.isEmpty()) {
            _uiState.update {
                it.copy(
                    isLoadingRecommendations = false,
                    suggestions = it.suggestions.copy(recommendations = emptyList())
                )
            }
            return
        }
        loadRecommendationBatch(retryWithoutSeenWhenEmpty = false)
    }

    private suspend fun loadRecommendationBatch(retryWithoutSeenWhenEmpty: Boolean) {
        _uiState.update { it.copy(isLoadingRecommendations = true) }
        val exclusionPlan = planRecommendationExclusions(
            listenedRjs = listenedRjs,
            recommendationSeenRjs = recommendationSeenRjs.toList(),
            maxExcludes = MAX_RECOMMENDATION_EXCLUDES
        )
        if (exclusionPlan.resetRecommendationSeen) {
            recommendationSeenRjs.clear()
        }
        var response = requestRecommendations(exclusionPlan.excludeRjs)
        if (
            retryWithoutSeenWhenEmpty &&
            response != null &&
            response.items.orEmpty().isEmpty() &&
            recommendationSeenRjs.isNotEmpty()
        ) {
            recommendationSeenRjs.clear()
            response = requestRecommendations(listenedRjs)
        }
        if (response == null) {
            _uiState.update { it.copy(isLoadingRecommendations = false) }
            return
        }
        val items = response.items.orEmpty()
        items.flatMapTo(recommendationSeenRjs) { item -> item.recommendationExclusionRjs() }
        val recommendations = items
            .map { item -> item.toSearchAssistRecommendation() }
            .filter { recommendation -> recommendation.album.rjCode.isNotBlank() }
            .take(RECOMMENDATION_DISPLAY_LIMIT)
        _uiState.update {
            it.copy(
                isLoadingRecommendations = false,
                suggestions = it.suggestions.copy(recommendations = recommendations)
            )
        }
    }

    private suspend fun requestRecommendations(excludeRjs: List<String>): AsmrOneRecommendationResponse? =
        try {
            asmrOneAvailabilityApi.getRecommendations(
                seedRjs = listenedRjs.take(MAX_RECOMMENDATION_SEEDS),
                excludeRjs = excludeRjs,
                limit = RECOMMENDATION_DISPLAY_LIMIT
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

    private companion object {
        const val MAX_RECOMMENDATION_SEEDS = 20
        const val MAX_RECOMMENDATION_EXCLUDES = 200
        const val RECOMMENDATION_DISPLAY_LIMIT = 20
    }
}

data class SearchAssistUiState(
    val history: List<String> = emptyList(),
    val suggestions: SearchSuggestionsUiData = SearchSuggestionsUiData(),
    val isLoadingSuggestions: Boolean = true,
    val isLoadingRecommendations: Boolean = true
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
        .map { it.trim().uppercase() }
        .firstOrNull { RECOMMENDATION_RJ_REGEX.matches(it) }
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

internal data class RecommendationExclusionPlan(
    val excludeRjs: List<String>,
    val resetRecommendationSeen: Boolean
)

internal fun planRecommendationExclusions(
    listenedRjs: List<String>,
    recommendationSeenRjs: List<String>,
    maxExcludes: Int
): RecommendationExclusionPlan {
    val normalizedListened = normalizeRecommendationRjs(listenedRjs, maxExcludes)
    val listenedSet = normalizedListened.toSet()
    val normalizedSeen = normalizeRecommendationRjs(recommendationSeenRjs, Int.MAX_VALUE)
        .filterNot(listenedSet::contains)
    val shouldResetSeen = normalizedListened.size + normalizedSeen.size > maxExcludes
    return RecommendationExclusionPlan(
        excludeRjs = if (shouldResetSeen) normalizedListened else normalizedListened + normalizedSeen,
        resetRecommendationSeen = shouldResetSeen
    )
}

internal fun AsmrOneRecommendationItem.recommendationExclusionRjs(): List<String> =
    normalizeRecommendationRjs(
        values = buildList {
            add(rj)
            add(originalWorkno)
            addAll(matchedRjs.orEmpty())
        },
        limit = Int.MAX_VALUE
    )

private val RECOMMENDATION_RJ_REGEX = Regex("""RJ\d{6,}""")
