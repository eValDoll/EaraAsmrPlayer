package com.asmr.player.ui.search

import android.os.SystemClock
import android.util.Log
import com.asmr.player.BuildConfig
import com.asmr.player.data.local.datastore.LastSearchStateV1
import com.asmr.player.data.local.datastore.SearchCacheStore
import com.asmr.player.data.remote.api.AsmrOneAvailabilityApi
import com.asmr.player.data.remote.dlsite.DlsitePlayLibraryClient
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.util.AppErrorMessageFormatter
import com.asmr.player.util.MessageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import org.jsoup.HttpStatusException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
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
    val messageManager: MessageManager
) : ViewModel() {
    private val pageSize = 30
    private var currentOrder: SearchSortOption = SearchSortOption.Trend
    private var purchasedOnly: Boolean = false
    private var presaleOnly: Boolean = false
    private var chineseTranslatedOnly: Boolean = false
    private var enrichJob: Job? = null
    private var asmrOneJob: Job? = null
    private var cacheWriteJob: Job? = null
    private val dlsiteDetailCache = ConcurrentHashMap<String, Album>()
    private val enrichDispatcher = Dispatchers.IO
    private val asmrOneAvailabilityCache = ConcurrentHashMap<String, Boolean>()
    private val bootstrapped = AtomicBoolean(false)

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState = _uiState.asStateFlow()

    val viewMode: StateFlow<Int> = settingsRepository.searchViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    private var currentLocale: String? = "ja_JP"
    private var lastRequestedKeyword: String = ""

    fun setViewMode(mode: Int) {
        viewModelScope.launch {
            settingsRepository.setSearchViewMode(mode)
        }
    }

    fun bootstrap(initialKeyword: String, initialPurchasedOnly: Boolean, initialLocale: String?) {
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
                isAsmrOneChecking = false,
                asmrOneChecked = 0,
                asmrOneTotal = 0
            )
        }
    }

    fun search(keyword: String) {
        if (_uiState.value is SearchUiState.Loading) return
        val current = _uiState.value as? SearchUiState.Success
        if (current?.isBusy == true) return
        val normalizedKeyword = keyword.trim()
        Log.d("SearchViewModel", "Search requested: keyword=$normalizedKeyword")
        lastRequestedKeyword = normalizedKeyword
        requestPage(normalizedKeyword, 1, SearchPendingRequestKind.Search)
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
        purchasedOnly: Boolean = this.purchasedOnly,
        presaleOnly: Boolean = this.presaleOnly,
        chineseTranslatedOnly: Boolean = this.chineseTranslatedOnly,
        locale: String? = currentLocale
    ): Boolean {
        val current = _uiState.value as? SearchUiState.Success ?: return false
        if (current.isBusy) return false
        if (purchasedOnly && !dlsitePlayLibraryClient.hasStoredCredentials()) {
            messageManager.showWarning("请先登录 DLsite 后再使用\"已购\"搜索")
            return false
        }
        if (
            currentOrder == order &&
            this.purchasedOnly == purchasedOnly &&
            this.presaleOnly == presaleOnly &&
            this.chineseTranslatedOnly == chineseTranslatedOnly &&
            currentLocale == locale
        ) return true
        currentOrder = order
        this.purchasedOnly = purchasedOnly
        this.presaleOnly = presaleOnly
        this.chineseTranslatedOnly = chineseTranslatedOnly
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
                isAsmrOneChecking = false,
                asmrOneChecked = 0,
                asmrOneTotal = 0
            ) ?: SearchUiState.Loading

            try {
                val pageResult = fetchPage(
                    keyword = normalizedKeyword,
                    page = page,
                    order = currentOrder,
                    purchasedOnly = purchasedOnly,
                    presaleOnly = presaleOnly,
                    chineseTranslatedOnly = chineseTranslatedOnly
                )
                _uiState.value = SearchUiState.Success(
                    results = pageResult.items,
                    keyword = normalizedKeyword,
                    page = page,
                    order = currentOrder,
                    purchasedOnly = purchasedOnly,
                    presaleOnly = presaleOnly,
                    chineseTranslatedOnly = chineseTranslatedOnly,
                    locale = currentLocale,
                    canGoPrev = page > 1,
                    canGoNext = pageResult.canGoNext,
                    pendingRequest = null,
                    visitedPages = buildVisitedPages(previousSuccess, requestKind, page),
                    isEnriching = false,
                    isAsmrOneChecking = false,
                    asmrOneChecked = 0,
                    asmrOneTotal = 0
                )
                if (!purchasedOnly && pageResult.items.isNotEmpty()) {
                    startEnrichDlsiteDetails(
                        keyword = normalizedKeyword,
                        page = page,
                        baseItems = pageResult.items
                    )
                    startMarkAsmrOneAvailability(
                        keyword = normalizedKeyword,
                        page = page,
                        baseItems = pageResult.items
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
                    purchasedOnly = previousSuccess.purchasedOnly
                    presaleOnly = previousSuccess.presaleOnly
                    chineseTranslatedOnly = previousSuccess.chineseTranslatedOnly
                    currentLocale = previousSuccess.locale
                    _uiState.value = previousSuccess.copy(
                        pendingRequest = null,
                        isEnriching = false,
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
        purchasedOnly: Boolean,
        presaleOnly: Boolean,
        chineseTranslatedOnly: Boolean
    ): SearchPageResult {
        if (purchasedOnly) {
            val resp = dlsitePlayLibraryClient.searchPurchased(keyword, page, pageSize)
            return SearchPageResult(items = resp.items, canGoNext = resp.canGoNext)
        }
        val normalizedKeyword = keyword.trim()
        val normalizedRj = normalizedKeyword.uppercase()
        if (!presaleOnly && !chineseTranslatedOnly && page == 1 && Regex("""RJ\d{6,}""").matches(normalizedRj)) {
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
                return SearchPageResult(items = listOf(album), canGoNext = false)
            }
        }
        val result = dlsiteScraper.search(
            keyword = keyword,
            page = page,
            order = order.dlsiteOrder,
            locale = currentLocale,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly
        )
        return SearchPageResult(items = result.items, canGoNext = result.canGoNext)
    }

    private fun startEnrichDlsiteDetails(keyword: String, page: Int, baseItems: List<Album>) {
        enrichJob?.cancel()
        enrichJob = viewModelScope.launch {
            val current0 = _uiState.value as? SearchUiState.Success ?: return@launch
            if (current0.keyword != keyword || current0.page != page || current0.purchasedOnly) return@launch
            _uiState.value = current0.copy(isEnriching = true)

            coroutineScope {
                val sem = Semaphore(6)
                val deferreds = baseItems.mapIndexedNotNull { index, base ->
                    val rj = base.rjCode.ifBlank { base.workId }.trim().uppercase()
                    if (rj.isBlank()) return@mapIndexedNotNull null
                    async(enrichDispatcher) {
                        sem.withPermit {
                            val cached = dlsiteDetailCache[rj]
                            val detail = cached ?: runCatching { dlsiteScraper.getWorkInfo(rj)?.album }.getOrNull()
                            if (detail != null) dlsiteDetailCache[rj] = detail
                            index to detail
                        }
                    }
                }
                deferreds.forEach { deferred ->
                    val result = runCatching { deferred.await() }.getOrNull() ?: return@forEach
                    val idx = result.first
                    val detail = result.second
                    if (detail == null) return@forEach
                    val cur = _uiState.value as? SearchUiState.Success ?: return@forEach
                    if (cur.keyword != keyword || cur.page != page || cur.purchasedOnly) return@forEach
                    val list = cur.results.toMutableList()
                    if (idx !in list.indices) return@forEach
                    list[idx] = mergeAlbum(list[idx], detail)
                    _uiState.value = cur.copy(results = list)
                    scheduleCacheWrite()
                }
            }

            val current1 = _uiState.value as? SearchUiState.Success ?: return@launch
            if (current1.keyword == keyword && current1.page == page && !current1.purchasedOnly) {
                _uiState.value = current1.copy(isEnriching = false)
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
        val page = cached.page.coerceAtLeast(1)
        currentOrder = order
        purchasedOnly = cached.purchasedOnly
        presaleOnly = cached.presaleOnly
        chineseTranslatedOnly = cached.chineseTranslatedOnly
        currentLocale = cached.locale
        _uiState.value = SearchUiState.Success(
            results = cached.results,
            keyword = cached.keyword,
            page = page,
            order = order,
            purchasedOnly = cached.purchasedOnly,
            presaleOnly = cached.presaleOnly,
            chineseTranslatedOnly = cached.chineseTranslatedOnly,
            locale = cached.locale,
            canGoPrev = page > 1,
            canGoNext = cached.canGoNext,
            pendingRequest = null,
            visitedPages = listOf(page),
            isEnriching = false,
            isAsmrOneChecking = false,
            asmrOneChecked = 0,
            asmrOneTotal = 0
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
                        purchasedOnly = latest.purchasedOnly,
                        presaleOnly = latest.presaleOnly,
                        chineseTranslatedOnly = latest.chineseTranslatedOnly,
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

    private fun mergeAlbum(base: Album, detail: Album): Album {
        return base.copy(
            title = base.title.ifBlank { detail.title },
            circle = base.circle.ifBlank { detail.circle },
            cv = base.cv.ifBlank { detail.cv },
            tags = if (base.tags.isEmpty()) detail.tags else base.tags,
            coverUrl = base.coverUrl.ifBlank { detail.coverUrl },
            ratingValue = detail.ratingValue ?: base.ratingValue,
            ratingCount = maxOf(base.ratingCount, detail.ratingCount),
            releaseDate = base.releaseDate.ifBlank { detail.releaseDate },
            dlCount = maxOf(base.dlCount, detail.dlCount),
            priceJpy = if (base.priceJpy > 0) base.priceJpy else detail.priceJpy
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

    private fun startMarkAsmrOneAvailability(keyword: String, page: Int, baseItems: List<Album>) {
        asmrOneJob?.cancel()
        asmrOneJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = SystemClock.elapsedRealtime()
            val cur0 = _uiState.value as? SearchUiState.Success ?: return@launch
            if (cur0.keyword != keyword || cur0.page != page || cur0.purchasedOnly) return@launch

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
                if (cur.keyword != keyword || cur.page != page || cur.purchasedOnly) return@update state
                cur.copy(isAsmrOneChecking = true, asmrOneChecked = 0, asmrOneTotal = total)
            }

            try {
                val cachedTrue = indexByRj.keys.filter { asmrOneAvailabilityCache[it] == true }
                cachedTrue.forEach { rj ->
                    updateAsmrOneAvailability(
                        keyword = keyword,
                        page = page,
                        rj = rj,
                        has = true,
                        indexByRj = indexByRj
                    )
                }

                val unknown = indexByRj.keys.filter { asmrOneAvailabilityCache[it] != true }
                val availability = asmrOneAvailabilityApi.check(unknown)
                availability.forEach { (rj, collected) ->
                    asmrOneAvailabilityCache[rj] = collected
                    if (collected) {
                        updateAsmrOneAvailability(
                            keyword = keyword,
                            page = page,
                            rj = rj,
                            has = true,
                            indexByRj = indexByRj
                        )
                    }
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
                    if (cur.keyword != keyword || cur.page != page || cur.purchasedOnly) return@update state
                    cur.copy(isAsmrOneChecking = false, asmrOneChecked = total, asmrOneTotal = total)
                }
            }
        }
    }

    private suspend fun updateAsmrOneAvailability(
        keyword: String,
        page: Int,
        rj: String,
        has: Boolean,
        indexByRj: Map<String, List<Int>>
    ) {
        var changed = false
        _uiState.update { state ->
            val cur = state as? SearchUiState.Success ?: return@update state
            if (cur.keyword != keyword || cur.page != page || cur.purchasedOnly) return@update state

            val indices = indexByRj[rj].orEmpty()
            if (indices.isEmpty()) return@update state

            val list = cur.results.toMutableList()
            indices.forEach { idx ->
                if (idx !in list.indices) return@forEach
                val old = list[idx]
                if (old.hasAsmrOne != has) {
                    list[idx] = old.copy(hasAsmrOne = has)
                    changed = true
                }
            }
            if (!changed) cur else cur.copy(results = list)
        }
        if (changed) {
            scheduleCacheWrite()
            yield()
        }
    }

    private companion object {
        private val RJ_CODE_REGEX = Regex("""RJ\d{6,}""")
    }
}

private data class SearchPageResult(
    val items: List<Album>,
    val canGoNext: Boolean
)

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()

    data class Success(
        val results: List<Album>,
        val keyword: String,
        val page: Int,
        val order: SearchSortOption,
        val purchasedOnly: Boolean,
        val presaleOnly: Boolean,
        val chineseTranslatedOnly: Boolean,
        val locale: String?,
        val canGoPrev: Boolean,
        val canGoNext: Boolean,
        val pendingRequest: SearchPendingRequest? = null,
        val visitedPages: List<Int> = listOf(page),
        val isEnriching: Boolean = false,
        val isAsmrOneChecking: Boolean = false,
        val asmrOneChecked: Int = 0,
        val asmrOneTotal: Int = 0
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
