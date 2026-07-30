package com.asmr.player.ui.search

import android.os.SystemClock
import android.util.Log
import com.asmr.player.BuildConfig
import com.asmr.player.data.local.datastore.LastSearchStateV1
import com.asmr.player.data.local.datastore.SearchCacheStore
import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.api.AsmrOneCollectedSearchItem
import com.asmr.player.data.remote.dlsite.DlsitePlayLibraryClient
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.hotlistening.HotListeningApi
import com.asmr.player.util.AppErrorMessageFormatter
import com.asmr.player.util.MessageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.HttpStatusException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

enum class SearchPendingRequestKind {
    Search,
    Page
}

data class SearchPendingRequest(
    val kind: SearchPendingRequestKind,
    val targetPage: Int
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val dlsiteScraper: DLSiteScraper,
    private val dlsitePlayLibraryClient: DlsitePlayLibraryClient,
    private val asmrOneAvailabilityApi: AsmrOneAvailabilityApi,
    private val settingsRepository: SettingsRepository,
    private val searchCacheStore: SearchCacheStore,
    private val hotListeningApi: HotListeningApi,
    val messageManager: MessageManager
) : ViewModel() {
    private val pageSize = 30
    private var currentOrder: SearchSortOption = SearchSortOption.Trend
    private var currentCollectedSort: SearchCollectedSortOption = SearchCollectedSortOption.ReleaseNew
    private var purchasedOnly: Boolean = false
    private var presaleOnly: Boolean = false
    private var chineseTranslatedOnly: Boolean = false
    private var collectedOnly: Boolean = true
    private var enrichJob: Job? = null
    private var asmrOneJob: Job? = null
    private var cacheWriteJob: Job? = null
    private val dlsiteDetailCache = BoundedLruCache<String, Album>(maxEntries = 180)
    private val enrichDispatcher = Dispatchers.IO
    private val asmrOneAvailabilityCache =
        BoundedLruCache<String, CachedAsmrOneAvailability>(maxEntries = 1_000)
    private val bootstrapped = AtomicBoolean(false)
    private var searchResultRevision: Long = 0L

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState = _uiState.asStateFlow()
    private val _hotKeywordTerms = MutableStateFlow<List<SearchHotKeywordTerm>>(emptyList())
    internal val hotKeywordTerms: StateFlow<List<SearchHotKeywordTerm>> = _hotKeywordTerms.asStateFlow()
    private val _showHotKeywordFallback = MutableStateFlow(false)
    internal val showHotKeywordFallback: StateFlow<Boolean> = _showHotKeywordFallback.asStateFlow()

    val viewMode: StateFlow<Int> = settingsRepository.searchViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    private var currentLocale: String? = "ja_JP"
    private var lastRequestedKeyword: String = ""

    init {
        refreshHotKeywordTerms()
    }

    private fun refreshHotKeywordTerms() {
        if (!hotListeningApi.isBackendConfigured) {
            _showHotKeywordFallback.value = true
            return
        }
        viewModelScope.launch {
            val suggestions = runCatching { hotListeningApi.getSearchSuggestions() }.getOrNull()
            val terms = buildSearchHotKeywordTerms(
                hotCvs = suggestions?.hotCvs.orEmpty(),
                hotTags = suggestions?.hotTags.orEmpty()
            )
            _hotKeywordTerms.value = terms
            _showHotKeywordFallback.value = terms.isEmpty()
        }
    }

    fun setViewMode(mode: Int) {
        viewModelScope.launch {
            settingsRepository.setSearchViewMode(mode)
        }
    }

    fun bootstrap(
        initialKeyword: String,
        initialPurchasedOnly: Boolean,
        initialLocale: String?,
        initialCollectedOnly: Boolean = true,
        initialCollectedSort: SearchCollectedSortOption = SearchCollectedSortOption.ReleaseNew
    ) {
        if (!bootstrapped.compareAndSet(false, true)) return
        viewModelScope.launch {
            val cached = runCatching { searchCacheStore.readLast() }.getOrNull()
            if (cached != null) {
                applyCachedState(cached)
                lastRequestedKeyword = cached.keyword
            } else {
                purchasedOnly = initialPurchasedOnly
                presaleOnly = false
                chineseTranslatedOnly = false
                collectedOnly = initialCollectedOnly
                currentCollectedSort = initialCollectedSort
                currentLocale = initialLocale
                lastRequestedKeyword = initialKeyword.trim()
                requestPage(lastRequestedKeyword, 1, SearchPendingRequestKind.Search)
            }
        }
    }

    fun stopBackgroundLoading() {
        cancelBackgroundJobs()
        _uiState.update { state ->
            val cur = state as? SearchUiState.Success ?: return@update state
            cur.copy(
                isEnriching = false,
                enrichingRjCodes = emptySet(),
                isAsmrOneChecking = false,
                asmrOneChecked = 0,
                asmrOneTotal = 0
            )
        }
    }

    fun search(keyword: String): Boolean {
        return search(
            keyword = keyword,
            order = currentOrder,
            collectedSort = currentCollectedSort,
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly,
            locale = currentLocale
        )
    }

    fun search(
        keyword: String,
        order: SearchSortOption,
        collectedSort: SearchCollectedSortOption,
        purchasedOnly: Boolean,
        presaleOnly: Boolean,
        chineseTranslatedOnly: Boolean,
        collectedOnly: Boolean,
        locale: String?
    ): Boolean {
        if (_uiState.value is SearchUiState.Loading) return false
        val current = _uiState.value as? SearchUiState.Success
        if (current?.isBusy == true) return false
        val nextFilters = normalizeSearchFilters(
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly
        )
        if (nextFilters.purchasedOnly && !dlsitePlayLibraryClient.hasStoredCredentials()) {
            messageManager.showWarning("请先登录 DLsite 后再使用\"已购\"搜索")
            return false
        }
        val normalizedKeyword = keyword.trim()
        Log.d("SearchViewModel", "Search requested: keyword=$normalizedKeyword")
        currentOrder = order
        currentCollectedSort = collectedSort
        this.purchasedOnly = nextFilters.purchasedOnly
        this.presaleOnly = nextFilters.presaleOnly
        this.chineseTranslatedOnly = nextFilters.chineseTranslatedOnly
        this.collectedOnly = nextFilters.collectedOnly
        currentLocale = locale
        lastRequestedKeyword = normalizedKeyword
        requestPage(normalizedKeyword, 1, SearchPendingRequestKind.Search)
        return true
    }

    fun setOrder(order: SearchSortOption) {
        updateSearchOptions(order = order)
    }

    fun setPurchasedOnly(enabled: Boolean) {
        updateSearchOptions(purchasedOnly = enabled)
    }

    fun setPresaleOnly(enabled: Boolean) {
        updateSearchOptions(presaleOnly = enabled)
    }

    fun setLocale(locale: String?) {
        updateSearchOptions(locale = locale)
    }

    fun updateSearchOptions(
        order: SearchSortOption = currentOrder,
        collectedSort: SearchCollectedSortOption = currentCollectedSort,
        purchasedOnly: Boolean = this.purchasedOnly,
        presaleOnly: Boolean = this.presaleOnly,
        chineseTranslatedOnly: Boolean = this.chineseTranslatedOnly,
        collectedOnly: Boolean = this.collectedOnly,
        locale: String? = currentLocale
    ): Boolean {
        val nextFilters = normalizeSearchFilters(
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly
        )
        val current = _uiState.value as? SearchUiState.Success ?: return false
        if (current.isBusy) return false
        if (nextFilters.purchasedOnly && !dlsitePlayLibraryClient.hasStoredCredentials()) {
            messageManager.showWarning("请先登录 DLsite 后再使用\"已购\"搜索")
            return false
        }
        if (
            currentOrder == order &&
            currentCollectedSort == collectedSort &&
            this.purchasedOnly == nextFilters.purchasedOnly &&
            this.presaleOnly == nextFilters.presaleOnly &&
            this.chineseTranslatedOnly == nextFilters.chineseTranslatedOnly &&
            this.collectedOnly == nextFilters.collectedOnly &&
            currentLocale == locale
        ) return true
        currentOrder = order
        currentCollectedSort = collectedSort
        this.purchasedOnly = nextFilters.purchasedOnly
        this.presaleOnly = nextFilters.presaleOnly
        this.chineseTranslatedOnly = nextFilters.chineseTranslatedOnly
        this.collectedOnly = nextFilters.collectedOnly
        currentLocale = locale
        requestPage(current.keyword, 1, SearchPendingRequestKind.Search)
        return true
    }

    fun nextPage() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (!current.canGoNext || current.isBusy) return
        requestPage(current.keyword, current.page + 1, SearchPendingRequestKind.Page)
    }

    fun prevPage() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (!current.canGoPrev || current.isBusy) return
        requestPage(current.keyword, current.page - 1, SearchPendingRequestKind.Page)
    }

    fun firstPage() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (!current.canGoPrev || current.isBusy) return
        requestPage(current.keyword, 1, SearchPendingRequestKind.Page)
    }

    fun refreshPage() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (current.isBusy) return
        requestPage(current.keyword, current.page, SearchPendingRequestKind.Page)
    }

    private fun requestPage(
        keyword: String,
        targetPage: Int,
        requestKind: SearchPendingRequestKind
    ) {
        val normalizedKeyword = keyword.trim()
        val page = targetPage.coerceAtLeast(1)
        lastRequestedKeyword = normalizedKeyword

        viewModelScope.launch {
            val previousSuccess = _uiState.value as? SearchUiState.Success
            cancelBackgroundJobs()

            _uiState.value = previousSuccess?.copy(
                pendingRequest = SearchPendingRequest(kind = requestKind, targetPage = page),
                isEnriching = false,
                enrichingRjCodes = emptySet(),
                isAsmrOneChecking = false,
                asmrOneChecked = 0,
                asmrOneTotal = 0
            ) ?: SearchUiState.Loading

            try {
                val pageResult = fetchPage(
                    keyword = normalizedKeyword,
                    page = page,
                    order = currentOrder,
                    collectedSort = currentCollectedSort,
                    purchasedOnly = purchasedOnly,
                    presaleOnly = presaleOnly,
                    chineseTranslatedOnly = chineseTranslatedOnly,
                    collectedOnly = collectedOnly
                )
                val resultRevision = ++searchResultRevision
                _uiState.value = SearchUiState.Success(
                    results = pageResult.items,
                    keyword = normalizedKeyword,
                    page = page,
                    order = currentOrder,
                    collectedSort = currentCollectedSort,
                    purchasedOnly = purchasedOnly,
                    presaleOnly = presaleOnly,
                    chineseTranslatedOnly = chineseTranslatedOnly,
                    collectedOnly = collectedOnly,
                    locale = currentLocale,
                    canGoPrev = page > 1,
                    canGoNext = pageResult.canGoNext,
                    pendingRequest = null,
                    visitedPages = buildVisitedPages(previousSuccess, requestKind, page),
                    isEnriching = false,
                    enrichingRjCodes = emptySet(),
                    enrichedDetailRjCodes = pageResult.resolvedDetailRjCodes,
                    isAsmrOneChecking = false,
                    asmrOneChecked = 0,
                    asmrOneTotal = 0,
                    resultRevision = resultRevision
                )
                if (!purchasedOnly && !collectedOnly && pageResult.items.isNotEmpty()) {
                    startEnrichDlsiteDetails(
                        keyword = normalizedKeyword,
                        page = page,
                        baseItems = pageResult.items,
                        resultRevision = resultRevision,
                    )
                    startMarkAsmrOneAvailability(
                        keyword = normalizedKeyword,
                        page = page,
                        baseItems = pageResult.items,
                        resultRevision = resultRevision,
                    )
                } else {
                    val cur = _uiState.value as? SearchUiState.Success
                    if (cur != null && cur.keyword == normalizedKeyword && cur.page == page) {
                        _uiState.value = cur.copy(
                            isAsmrOneChecking = false,
                            asmrOneChecked = 0,
                            asmrOneTotal = 0
                        )
                    }
                }
                scheduleCacheWrite()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SearchViewModel", "Search paging failed", e)
                val msg = if (e is IllegalStateException) {
                    AppErrorMessageFormatter.sanitize(e.message.orEmpty(), fallback = "搜索失败，请稍后重试")
                } else {
                    toUserMessage(e)
                }
                messageManager.showError(msg)
                if (previousSuccess != null) {
                    currentOrder = previousSuccess.order
                    currentCollectedSort = previousSuccess.collectedSort
                    purchasedOnly = previousSuccess.purchasedOnly
                    presaleOnly = previousSuccess.presaleOnly
                    chineseTranslatedOnly = previousSuccess.chineseTranslatedOnly
                    collectedOnly = previousSuccess.collectedOnly
                    currentLocale = previousSuccess.locale
                    _uiState.value = previousSuccess.copy(
                        pendingRequest = null,
                        isEnriching = false,
                        enrichingRjCodes = emptySet(),
                        isAsmrOneChecking = false,
                        asmrOneChecked = 0,
                        asmrOneTotal = 0
                    )
                } else {
                    _uiState.value = SearchUiState.Error(msg)
                }
            }
        }
    }

    private fun buildVisitedPages(
        previousSuccess: SearchUiState.Success?,
        requestKind: SearchPendingRequestKind,
        page: Int
    ): List<Int> {
        return when (requestKind) {
            SearchPendingRequestKind.Search -> listOf(1)
            SearchPendingRequestKind.Page -> {
                (previousSuccess?.visitedPages.orEmpty() + page)
                    .filter { it > 0 }
                    .distinct()
                    .sorted()
                    .ifEmpty { listOf(page) }
            }
        }
    }

    private fun cancelBackgroundJobs() {
        enrichJob?.cancel()
        asmrOneJob?.cancel()
    }

    private suspend fun fetchPage(
        keyword: String,
        page: Int,
        order: SearchSortOption,
        collectedSort: SearchCollectedSortOption,
        purchasedOnly: Boolean,
        presaleOnly: Boolean,
        chineseTranslatedOnly: Boolean,
        collectedOnly: Boolean
    ): SearchPageResult {
        if (purchasedOnly) {
            val resp = dlsitePlayLibraryClient.searchPurchased(keyword, page, pageSize)
            return SearchPageResult(items = resp.items, canGoNext = resp.canGoNext)
        }
        val keywordWithBlockedTerms = appendBlockedKeywordsForOnlineSearch(
            keyword = keyword,
            blockedKeywords = settingsRepository.searchBlockedKeywords.first()
        )
        if (collectedOnly) {
            val offset = (page.coerceAtLeast(1) - 1) * pageSize
            val resp = asmrOneAvailabilityApi.search(keywordWithBlockedTerms, pageSize, offset, collectedSort.backendSort)
            val items = withContext(Dispatchers.Default) {
                resp.items.orEmpty().map { it.toCollectedAlbum() }
            }
            val total = resp.total.coerceAtLeast(0)
            val responseOffset = resp.offset.coerceAtLeast(offset)
            return SearchPageResult(
                items = items,
                canGoNext = responseOffset + items.size < total,
                resolvedDetailRjCodes = items
                    .mapNotNull { it.rjCode.ifBlank { it.workId }.trim().uppercase().takeIf(String::isNotBlank) }
                    .toSet()
            )
        }
        val normalizedKeyword = keyword.trim()
        val normalizedRj = normalizedKeyword.uppercase()
        if (
            keywordWithBlockedTerms == normalizedKeyword &&
            !presaleOnly &&
            !chineseTranslatedOnly &&
            page == 1 &&
            Regex("""RJ\d{6,}""").matches(normalizedRj)
        ) {
            val preferred = currentLocale
            val info = when {
                !preferred.isNullOrBlank() -> {
                    runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = preferred) }.getOrNull()
                        ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = "zh_CN") }.getOrNull()
                        ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = "ja_JP") }.getOrNull()
                        ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj) }.getOrNull()
                }

                else -> {
                    runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = "zh_CN") }.getOrNull()
                        ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = "ja_JP") }.getOrNull()
                        ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj) }.getOrNull()
                }
            }
            if (info != null) {
                val album = info.album.copy(workId = normalizedRj, rjCode = normalizedRj)
                return SearchPageResult(
                    items = listOf(album),
                    canGoNext = false,
                    resolvedDetailRjCodes = setOf(normalizedRj)
                )
            }
        }
        val result = dlsiteScraper.search(
            keyword = keywordWithBlockedTerms,
            page = page,
            order = order.dlsiteOrder,
            locale = currentLocale,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly
        )
        return SearchPageResult(items = result.items, canGoNext = result.canGoNext)
    }

    private fun startEnrichDlsiteDetails(
        keyword: String,
        page: Int,
        baseItems: List<Album>,
        resultRevision: Long,
    ) {
        enrichJob?.cancel()
        enrichJob = viewModelScope.launch {
            val enrichTargets = baseItems
                .mapNotNull { it.rjCode.ifBlank { it.workId }.trim().uppercase().takeIf(String::isNotBlank) }
                .distinct()
                .toSet()
            if (enrichTargets.isEmpty()) return@launch
            var started = false
            _uiState.update { state ->
                val current = state as? SearchUiState.Success ?: return@update state
                if (
                    current.keyword != keyword ||
                    current.page != page ||
                    current.resultRevision != resultRevision ||
                    current.purchasedOnly ||
                    current.collectedOnly
                ) {
                    return@update state
                }
                started = true
                current.copy(
                    isEnriching = true,
                    enrichingRjCodes = enrichTargets,
                )
            }
            if (!started) return@launch

            coroutineScope {
                val sem = Semaphore(3)
                val deferreds = baseItems.mapIndexedNotNull { index, base ->
                    val rj = base.rjCode.ifBlank { base.workId }.trim().uppercase()
                    if (rj.isBlank() || rj !in enrichTargets) return@mapIndexedNotNull null
                    async(enrichDispatcher) {
                        sem.withPermit {
                            val cached = dlsiteDetailCache[rj]
                            val detail = cached ?: try {
                                dlsiteScraper.getWorkInfo(rj)?.album
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Throwable) {
                                null
                            }
                            if (detail != null) dlsiteDetailCache[rj] = detail
                            Triple(index, rj, detail)
                        }
                    }
                }
                deferreds.chunked(5).forEach { batch ->
                    val results = batch.mapNotNull { deferred ->
                        try {
                            deferred.await()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    if (results.isEmpty()) return@forEach
                    var hasResolvedDetail = false
                    val completedRjs = LinkedHashSet<String>(results.size)
                    val enrichedRjs = LinkedHashSet<String>()
                    _uiState.update { state ->
                        val cur = state as? SearchUiState.Success ?: return@update state
                        if (
                            cur.keyword != keyword ||
                            cur.page != page ||
                            cur.resultRevision != resultRevision ||
                            cur.purchasedOnly ||
                            cur.collectedOnly
                        ) {
                            return@update state
                        }
                        val list = cur.results.toMutableList()
                        var resultsChanged = false
                        results.forEach { (idx, rj, detail) ->
                            completedRjs += rj
                            if (detail != null && idx in list.indices) {
                                val merged = mergeSearchAlbumDetail(list[idx], detail)
                                if (merged != list[idx]) {
                                    list[idx] = merged
                                    resultsChanged = true
                                }
                                enrichedRjs += rj
                                hasResolvedDetail = true
                            }
                        }
                        cur.copy(
                            results = if (resultsChanged) list else cur.results,
                            enrichingRjCodes = cur.enrichingRjCodes - completedRjs,
                            enrichedDetailRjCodes = cur.enrichedDetailRjCodes + enrichedRjs,
                        )
                    }
                    if (hasResolvedDetail) scheduleCacheWrite()
                }
            }

            var completed = false
            _uiState.update { state ->
                val current = state as? SearchUiState.Success ?: return@update state
                if (
                    current.keyword != keyword ||
                    current.page != page ||
                    current.resultRevision != resultRevision ||
                    current.purchasedOnly ||
                    current.collectedOnly
                ) {
                    return@update state
                }
                completed = true
                current.copy(
                    isEnriching = false,
                    enrichingRjCodes = emptySet()
                )
            }
            if (completed) {
                scheduleCacheWrite()
            }
        }
    }

    fun retry() {
        when (val cur = _uiState.value) {
            is SearchUiState.Success -> {
                if (cur.isBusy) return
                requestPage(cur.keyword, cur.page, SearchPendingRequestKind.Page)
            }

            is SearchUiState.Error -> requestPage(lastRequestedKeyword, 1, SearchPendingRequestKind.Search)
            is SearchUiState.Loading -> return
            else -> requestPage(lastRequestedKeyword, 1, SearchPendingRequestKind.Search)
        }
    }

    private fun applyCachedState(cached: LastSearchStateV1) {
        val order = SearchSortOption.values()
            .firstOrNull { it.name == cached.orderName }
            ?: SearchSortOption.Trend
        val collectedSort = SearchCollectedSortOption.fromName(cached.collectedSortName)
        val page = cached.page.coerceAtLeast(1)
        val filters = normalizeSearchFilters(
            purchasedOnly = cached.purchasedOnly,
            presaleOnly = cached.presaleOnly,
            chineseTranslatedOnly = cached.chineseTranslatedOnly,
            collectedOnly = cached.collectedOnly
        )
        currentOrder = order
        currentCollectedSort = collectedSort
        purchasedOnly = filters.purchasedOnly
        presaleOnly = filters.presaleOnly
        chineseTranslatedOnly = filters.chineseTranslatedOnly
        collectedOnly = filters.collectedOnly
        currentLocale = cached.locale
        _uiState.value = SearchUiState.Success(
            results = cached.results,
            keyword = cached.keyword,
            page = page,
            order = order,
            collectedSort = collectedSort,
            purchasedOnly = filters.purchasedOnly,
            presaleOnly = filters.presaleOnly,
            chineseTranslatedOnly = filters.chineseTranslatedOnly,
            collectedOnly = filters.collectedOnly,
            locale = cached.locale,
            canGoPrev = page > 1,
            canGoNext = cached.canGoNext,
            pendingRequest = null,
            visitedPages = listOf(page),
            isEnriching = false,
            enrichingRjCodes = emptySet(),
            enrichedDetailRjCodes = if (filters.collectedOnly && !filters.purchasedOnly) {
                cached.results
                    .mapNotNull { it.rjCode.ifBlank { it.workId }.trim().uppercase().takeIf(String::isNotBlank) }
                    .toSet()
            } else {
                emptySet()
            },
            isAsmrOneChecking = false,
            asmrOneChecked = 0,
            asmrOneTotal = 0,
            resultRevision = searchResultRevision
        )
    }

    private fun scheduleCacheWrite() {
        val cur = _uiState.value as? SearchUiState.Success ?: return
        if (cur.isBusy) return
        cacheWriteJob?.cancel()
        cacheWriteJob = viewModelScope.launch(Dispatchers.IO) {
            delay(700)
            val latest = _uiState.value as? SearchUiState.Success ?: return@launch
            if (latest.isBusy) return@launch
            runCatching {
                searchCacheStore.writeLast(
                    LastSearchStateV1(
                        savedAtMs = System.currentTimeMillis(),
                        keyword = latest.keyword,
                        orderName = latest.order.name,
                        collectedSortName = latest.collectedSort.name,
                        purchasedOnly = latest.purchasedOnly,
                        presaleOnly = latest.presaleOnly,
                        chineseTranslatedOnly = latest.chineseTranslatedOnly,
                        collectedOnly = latest.collectedOnly,
                        locale = latest.locale,
                        page = latest.page,
                        canGoNext = latest.canGoNext,
                        results = latest.results
                    )
                )
            }
        }
    }

    private fun toUserMessage(e: Throwable): String {
        val raw = e.message.orEmpty()
        if (raw.contains("请先登录")) return "请先登录后再使用\"已购\"搜索"
        return when (e) {
            is SocketTimeoutException -> "连接超时，请稍后重试"
            is IOException -> "网络连接失败，请检查网络后重试"
            is HttpException -> {
                val code = e.code()
                when {
                    code == 401 -> "登录已过期，请重新登录"
                    code == 403 -> "访问受限，请稍后再试"
                    code in 500..599 -> "服务器开小差了，请稍后重试"
                    else -> "请求失败，请稍后重试"
                }
            }

            is HttpStatusException -> {
                when (e.statusCode) {
                    403, 429 -> "访问受限或触发风控，请稍后再试"
                    in 500..599 -> "服务器开小差了，请稍后重试"
                    else -> "请求失败，请稍后重试"
                }
            }

            else -> "搜索失败，请稍后重试"
        }
    }

    private fun AsmrOneCollectedSearchItem.toCollectedAlbum(): Album {
        val normalizedRj = rj.trim().uppercase()
            .ifBlank { originalWorkno.trim().uppercase() }
        return Album(
            title = title.trim().ifBlank { normalizedRj.ifBlank { "已收录作品" } },
            path = "",
            workId = normalizedRj,
            rjCode = normalizedRj,
            circle = circle.trim(),
            cv = cvs.orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", "),
            tags = tags.orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct(),
            coverUrl = mainCoverUrl.trim(),
            releaseDate = releaseDate.trim(),
            ratingValue = rateAverage2dp?.takeIf { it > 0.0 },
            ratingCount = (rateCount ?: reviewCount ?: 0).coerceAtLeast(0),
            dlCount = 0,
            priceJpy = price ?: 0,
            hasAsmrOne = true
        )
    }

    private fun extractRjCode(a: Album): String? {
        val r1 = a.rjCode.trim().uppercase()
        val m1 = RJ_CODE_REGEX.find(r1)?.value
        if (!m1.isNullOrBlank()) return m1
        val r2 = a.workId.trim().uppercase()
        val m2 = RJ_CODE_REGEX.find(r2)?.value
        if (!m2.isNullOrBlank()) return m2
        return null
    }

    private fun startMarkAsmrOneAvailability(
        keyword: String,
        page: Int,
        baseItems: List<Album>,
        resultRevision: Long,
    ) {
        asmrOneJob?.cancel()
        asmrOneJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = SystemClock.elapsedRealtime()
            val cur0 = _uiState.value as? SearchUiState.Success ?: return@launch
            if (
                cur0.keyword != keyword ||
                cur0.page != page ||
                cur0.resultRevision != resultRevision ||
                cur0.purchasedOnly ||
                cur0.collectedOnly
            ) return@launch

            val indexByRj = linkedMapOf<String, MutableList<Int>>()
            baseItems.forEachIndexed { idx, a ->
                val rj = extractRjCode(a) ?: return@forEachIndexed
                val list = indexByRj.getOrPut(rj) { mutableListOf() }
                list.add(idx)
            }
            if (indexByRj.isEmpty()) return@launch
            val total = indexByRj.size
            _uiState.update { state ->
                val cur = state as? SearchUiState.Success ?: return@update state
                if (
                    cur.keyword != keyword ||
                    cur.page != page ||
                    cur.resultRevision != resultRevision ||
                    cur.purchasedOnly ||
                    cur.collectedOnly
                ) return@update state
                cur.copy(isAsmrOneChecking = true, asmrOneChecked = 0, asmrOneTotal = total)
            }

            try {
                val cacheReadAt = SystemClock.elapsedRealtime()
                val cachedAvailability = indexByRj.keys.associateWith { rj ->
                    asmrOneAvailabilityCache[rj]
                }
                val cachedTrue = cachedAvailability
                    .filterValues { cached -> cached?.collected == true }
                    .keys
                if (cachedTrue.isNotEmpty()) {
                    updateAsmrOneAvailability(
                        keyword = keyword,
                        page = page,
                        resultRevision = resultRevision,
                        rjs = cachedTrue.toSet(),
                        indexByRj = indexByRj
                    )
                }

                val unknown = cachedAvailability
                    .filterValues { cached ->
                        cached == null ||
                            (!cached.collected &&
                                cacheReadAt - cached.checkedAtElapsedMs >= ASMR_ONE_NEGATIVE_CACHE_TTL_MS)
                    }
                    .keys
                    .toList()
                val availability = asmrOneAvailabilityApi.check(unknown)
                val checkedAt = SystemClock.elapsedRealtime()
                availability.forEach { (rj, collected) ->
                    asmrOneAvailabilityCache[rj] = CachedAsmrOneAvailability(
                        collected = collected,
                        checkedAtElapsedMs = checkedAt,
                    )
                }
                val newlyCollected = availability
                    .filterValues { collected -> collected }
                    .keys
                if (newlyCollected.isNotEmpty()) {
                    updateAsmrOneAvailability(
                        keyword = keyword,
                        page = page,
                        resultRevision = resultRevision,
                        rjs = newlyCollected,
                        indexByRj = indexByRj
                    )
                }

                if (BuildConfig.DEBUG) {
                    val dt = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
                    Log.d(
                        "SearchViewModel",
                        "asmrOne availability page done keyword=${keyword.trim()} page=$page " +
                            "rjCount=${indexByRj.size} dt=${dt}ms"
                    )
                }
            } finally {
                _uiState.update { state ->
                    val cur = state as? SearchUiState.Success ?: return@update state
                    if (
                        cur.keyword != keyword ||
                        cur.page != page ||
                        cur.resultRevision != resultRevision ||
                        cur.purchasedOnly ||
                        cur.collectedOnly
                    ) return@update state
                    cur.copy(isAsmrOneChecking = false, asmrOneChecked = total, asmrOneTotal = total)
                }
            }
        }
    }

    private fun updateAsmrOneAvailability(
        keyword: String,
        page: Int,
        resultRevision: Long,
        rjs: Set<String>,
        indexByRj: Map<String, List<Int>>
    ) {
        var changed = false
        _uiState.update { state ->
            val cur = state as? SearchUiState.Success ?: return@update state
            if (
                cur.keyword != keyword ||
                cur.page != page ||
                cur.resultRevision != resultRevision ||
                cur.purchasedOnly ||
                cur.collectedOnly
            ) return@update state

            val indices = rjs.flatMap { rj -> indexByRj[rj].orEmpty() }
            if (indices.isEmpty()) return@update state

            val list = cur.results.toMutableList()
            indices.forEach { idx ->
                if (idx !in list.indices) return@forEach
                val old = list[idx]
                if (!old.hasAsmrOne) {
                    list[idx] = old.copy(hasAsmrOne = true)
                    changed = true
                }
            }
            if (!changed) cur else cur.copy(results = list)
        }
        if (changed) {
            scheduleCacheWrite()
        }
    }

    private companion object {
        private const val ASMR_ONE_NEGATIVE_CACHE_TTL_MS = 5 * 60 * 1_000L
        private val RJ_CODE_REGEX = Regex("""RJ\d{6,}""")
    }
}

private data class CachedAsmrOneAvailability(
    val collected: Boolean,
    val checkedAtElapsedMs: Long,
)

private class BoundedLruCache<K, V>(
    private val maxEntries: Int,
) {
    private val entries = object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    operator fun get(key: K): V? = entries[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        entries[key] = value
    }
}

private fun normalizeSearchFilters(
    purchasedOnly: Boolean,
    presaleOnly: Boolean,
    chineseTranslatedOnly: Boolean,
    collectedOnly: Boolean
): SearchFilterFlags {
    val normalizedPurchasedOnly = purchasedOnly
    val normalizedChineseTranslatedOnly = !normalizedPurchasedOnly && chineseTranslatedOnly
    val normalizedPresaleOnly = !normalizedPurchasedOnly && !normalizedChineseTranslatedOnly && presaleOnly
    val normalizedCollectedOnly =
        !normalizedPurchasedOnly && !normalizedChineseTranslatedOnly && !normalizedPresaleOnly && collectedOnly
    return SearchFilterFlags(
        purchasedOnly = normalizedPurchasedOnly,
        presaleOnly = normalizedPresaleOnly,
        chineseTranslatedOnly = normalizedChineseTranslatedOnly,
        collectedOnly = normalizedCollectedOnly
    )
}

private data class SearchFilterFlags(
    val purchasedOnly: Boolean,
    val presaleOnly: Boolean,
    val chineseTranslatedOnly: Boolean,
    val collectedOnly: Boolean
)

private data class SearchPageResult(
    val items: List<Album>,
    val canGoNext: Boolean,
    val resolvedDetailRjCodes: Set<String> = emptySet()
)

internal fun mergeSearchAlbumDetail(base: Album, detail: Album): Album {
    return base.copy(
        title = base.title.ifBlank { detail.title },
        circle = base.circle.ifBlank { detail.circle },
        cv = detail.cv.ifBlank { base.cv },
        tags = if (base.tags.isEmpty()) detail.tags else base.tags,
        coverUrl = base.coverUrl.ifBlank { detail.coverUrl },
        ratingValue = detail.ratingValue ?: base.ratingValue,
        ratingCount = maxOf(base.ratingCount, detail.ratingCount),
        releaseDate = base.releaseDate.ifBlank { detail.releaseDate },
        dlCount = maxOf(base.dlCount, detail.dlCount),
        priceJpy = if (base.priceJpy > 0) base.priceJpy else detail.priceJpy
    )
}

internal fun appendBlockedKeywordsForOnlineSearch(
    keyword: String,
    blockedKeywords: List<String>
): String {
    val base = keyword.trim()
    val exclusions = blockedKeywords
        .map { it.trim() }
        .map { it.removePrefix("-").trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
    if (exclusions.isEmpty()) return base
    return buildString {
        if (base.isNotBlank()) append(base)
        exclusions.forEach { blocked ->
            if (isNotEmpty()) append(' ')
            append('-')
            append(blocked)
        }
    }
}

@Immutable
sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()

    data class Success(
        val results: List<Album>,
        val keyword: String,
        val page: Int,
        val order: SearchSortOption,
        val collectedSort: SearchCollectedSortOption,
        val purchasedOnly: Boolean,
        val presaleOnly: Boolean,
        val chineseTranslatedOnly: Boolean,
        val collectedOnly: Boolean,
        val locale: String?,
        val canGoPrev: Boolean,
        val canGoNext: Boolean,
        val pendingRequest: SearchPendingRequest? = null,
        val visitedPages: List<Int> = listOf(page),
        val isEnriching: Boolean = false,
        val enrichingRjCodes: Set<String> = emptySet(),
        val enrichedDetailRjCodes: Set<String> = emptySet(),
        val isAsmrOneChecking: Boolean = false,
        val asmrOneChecked: Int = 0,
        val asmrOneTotal: Int = 0,
        val resultRevision: Long = 0L
    ) : SearchUiState() {
        val isBusy: Boolean
            get() = pendingRequest != null

        val isPaging: Boolean
            get() = pendingRequest?.kind == SearchPendingRequestKind.Page

        val isSearching: Boolean
            get() = pendingRequest?.kind == SearchPendingRequestKind.Search
    }

    data class Error(val message: String) : SearchUiState()
}
