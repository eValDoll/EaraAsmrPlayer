package com.asmr.player.ui.search

import android.util.Log
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.BuildConfig
import com.asmr.player.data.local.datastore.LastSearchStateV1
import com.asmr.player.data.local.datastore.SearchCacheStore
import com.asmr.player.data.remote.crawler.AsmrOneCrawler
import com.asmr.player.data.remote.dlsite.DlsitePlayLibraryClient
import com.asmr.player.data.remote.scraper.DLSiteScraper
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.util.MessageManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jsoup.HttpStatusException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val dlsiteScraper: DLSiteScraper,
    private val dlsitePlayLibraryClient: DlsitePlayLibraryClient,
    private val asmrOneCrawler: AsmrOneCrawler,
    private val settingsRepository: SettingsRepository,
    private val searchCacheStore: SearchCacheStore,
    private val messageManager: MessageManager
) : ViewModel() {
    private val pageSize = 30
    private var currentOrder: SearchSortOption = SearchSortOption.Trend
    private var purchasedOnly: Boolean = false
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
            if (cached != null && cached.results.isNotEmpty()) {
                applyCachedState(cached)
                lastRequestedKeyword = cached.keyword
                goToPage(cached.keyword, targetPage = cached.page, showFullScreenLoading = false)
            } else {
                purchasedOnly = initialPurchasedOnly
                currentLocale = initialLocale
                lastRequestedKeyword = initialKeyword.trim()
                goToPage(lastRequestedKeyword, targetPage = 1, showFullScreenLoading = true)
            }
        }
    }

    fun stopBackgroundLoading() {
        enrichJob?.cancel()
        asmrOneJob?.cancel()
        _uiState.update { state ->
            val cur = state as? SearchUiState.Success ?: return@update state
            cur.copy(isEnriching = false, isAsmrOneChecking = false, asmrOneChecked = 0, asmrOneTotal = 0)
        }
    }

    fun search(keyword: String) {
        Log.d("SearchViewModel", "Search requested: keyword=$keyword")
        val normalizedKeyword = keyword.trim()
        lastRequestedKeyword = normalizedKeyword
        goToPage(normalizedKeyword, targetPage = 1, showFullScreenLoading = true)
    }

    fun setOrder(order: SearchSortOption) {
        if (currentOrder == order) return
        currentOrder = order
        val current = _uiState.value as? SearchUiState.Success ?: return
        goToPage(current.keyword, targetPage = 1, showFullScreenLoading = false)
    }

    fun setPurchasedOnly(enabled: Boolean) {
        if (purchasedOnly == enabled) return
        purchasedOnly = enabled
        val current = _uiState.value as? SearchUiState.Success ?: return
        goToPage(current.keyword, targetPage = 1, showFullScreenLoading = false)
    }

    fun setLocale(locale: String?) {
        if (currentLocale == locale) return
        currentLocale = locale
        val current = _uiState.value as? SearchUiState.Success ?: return
        goToPage(current.keyword, targetPage = 1, showFullScreenLoading = false)
    }

    fun nextPage() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (!current.canGoNext || current.isPaging) return
        goToPage(current.keyword, targetPage = current.page + 1, showFullScreenLoading = false)
    }

    fun prevPage() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (!current.canGoPrev || current.isPaging) return
        goToPage(current.keyword, targetPage = current.page - 1, showFullScreenLoading = false)
    }

    fun refreshPage() {
        val current = _uiState.value as? SearchUiState.Success ?: return
        if (current.isPaging) return
        goToPage(current.keyword, targetPage = current.page, showFullScreenLoading = false)
    }

    private fun goToPage(keyword: String, targetPage: Int, showFullScreenLoading: Boolean) {
        val normalizedKeyword = keyword.trim()
        val page = targetPage.coerceAtLeast(1)
        lastRequestedKeyword = normalizedKeyword

        viewModelScope.launch {
            val current = _uiState.value as? SearchUiState.Success
            _uiState.value = when {
                showFullScreenLoading -> SearchUiState.Loading
                current != null && current.keyword == normalizedKeyword -> current.copy(
                    isPaging = true,
                    isEnriching = false,
                    isAsmrOneChecking = false,
                    asmrOneChecked = 0,
                    asmrOneTotal = 0
                )
                else -> SearchUiState.Loading
            }
            try {
                val pageResult = fetchPage(normalizedKeyword, page = page, order = currentOrder, purchasedOnly = purchasedOnly)
                val canGoNext = pageResult.canGoNext
                _uiState.value = SearchUiState.Success(
                    results = pageResult.items,
                    keyword = normalizedKeyword,
                    page = page,
                    order = currentOrder,
                    purchasedOnly = purchasedOnly,
                    locale = currentLocale,
                    canGoPrev = page > 1,
                    canGoNext = canGoNext,
                    isPaging = false,
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
                    enrichJob?.cancel()
                    asmrOneJob?.cancel()
                    val cur = _uiState.value as? SearchUiState.Success
                    if (cur != null && cur.keyword == normalizedKeyword && cur.page == page) {
                        _uiState.value = cur.copy(isAsmrOneChecking = false, asmrOneChecked = 0, asmrOneTotal = 0)
                    }
                }
                scheduleCacheWrite()
            } catch (e: Exception) {
                Log.e("SearchViewModel", "Search paging failed", e)
                val msg = toUserMessage(e)
                messageManager.showError(msg)
                val cur = _uiState.value as? SearchUiState.Success
                if (cur != null && cur.keyword == normalizedKeyword && cur.page == page) {
                    _uiState.value = cur.copy(
                        isPaging = false,
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

    private suspend fun fetchPage(keyword: String, page: Int, order: SearchSortOption, purchasedOnly: Boolean): SearchPageResult {
        if (purchasedOnly) {
            val resp = dlsitePlayLibraryClient.searchPurchased(keyword, page, pageSize)
            return SearchPageResult(items = resp.items, canGoNext = resp.canGoNext)
        }
        val normalizedKeyword = keyword.trim()
        val normalizedRj = normalizedKeyword.uppercase()
        if (page == 1 && Regex("""RJ\d{6,}""").matches(normalizedRj)) {
            val preferred = currentLocale
            val info = when {
                !preferred.isNullOrBlank() -> runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = preferred) }.getOrNull()
                    ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = "zh_CN") }.getOrNull()
                    ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = "ja_JP") }.getOrNull()
                    ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj) }.getOrNull()
                else -> runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = "zh_CN") }.getOrNull()
                    ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj, locale = "ja_JP") }.getOrNull()
                    ?: runCatching { dlsiteScraper.getWorkInfo(normalizedRj) }.getOrNull()
            }
            if (info != null) {
                val album = info.album.copy(workId = normalizedRj, rjCode = normalizedRj)
                return SearchPageResult(items = listOf(album), canGoNext = false)
            }
        }
        val items = dlsiteScraper.search(keyword, page, order.dlsiteOrder, locale = currentLocale)
        return SearchPageResult(items = items, canGoNext = items.size >= pageSize)
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
                    val (idx, detail) = runCatching { deferred.await() }.getOrNull() ?: return@forEach
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
        val cur = _uiState.value
        when (cur) {
            is SearchUiState.Success -> goToPage(cur.keyword, targetPage = cur.page, showFullScreenLoading = false)
            is SearchUiState.Error -> goToPage(lastRequestedKeyword, targetPage = 1, showFullScreenLoading = true)
            else -> goToPage(lastRequestedKeyword, targetPage = 1, showFullScreenLoading = true)
        }
    }

    private fun applyCachedState(cached: LastSearchStateV1) {
        val order = SearchSortOption.values().firstOrNull { it.name == cached.orderName } ?: SearchSortOption.Trend
        currentOrder = order
        purchasedOnly = cached.purchasedOnly
        currentLocale = cached.locale
        _uiState.value = SearchUiState.Success(
            results = cached.results,
            keyword = cached.keyword,
            page = cached.page.coerceAtLeast(1),
            order = order,
            purchasedOnly = cached.purchasedOnly,
            locale = cached.locale,
            canGoPrev = cached.page > 1,
            canGoNext = cached.canGoNext,
            isPaging = false,
            isEnriching = false,
            isAsmrOneChecking = false,
            asmrOneChecked = 0,
            asmrOneTotal = 0
        )
    }

    private fun scheduleCacheWrite() {
        val cur = _uiState.value as? SearchUiState.Success ?: return
        if (cur.isPaging) return
        cacheWriteJob?.cancel()
        cacheWriteJob = viewModelScope.launch(Dispatchers.IO) {
            delay(700)
            val latest = _uiState.value as? SearchUiState.Success ?: return@launch
            if (latest.isPaging) return@launch
            runCatching {
                searchCacheStore.writeLast(
                    LastSearchStateV1(
                        savedAtMs = System.currentTimeMillis(),
                        keyword = latest.keyword,
                        orderName = latest.order.name,
                        purchasedOnly = latest.purchasedOnly,
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
        if (raw.contains("请先登录")) return "请先登录后再使用“仅已购”搜索"
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

    private suspend fun hasWorkFastWithRetry(rj: String, timeoutMs: Long): Boolean? {
        val normalized = rj.trim().uppercase()
        if (normalized.isBlank()) return null
        var falseCount = 0
        repeat(3) { idx ->
            if (idx > 0) delay(250L * idx)
            val result = runCatching { asmrOneCrawler.hasWorkFast(normalized, timeoutMs = timeoutMs) }.getOrNull()
            when (result) {
                true -> return true
                false -> falseCount += 1
                null -> {}
            }
        }
        return if (falseCount == 3) false else null
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

            val normalizedKeywordUpper = keyword.trim().uppercase()
            if (BuildConfig.DEBUG && normalizedKeywordUpper == DEBUG_DELAY_TEST_RJ && debugDelayTestOnce.compareAndSet(false, true)) {
                launch {
                    runDebugDelayTestForRj01491538()
                }
            }
            try {
                val normalizedKeyword = keyword.trim()
                val isRjKeyword = RJ_CODE_REGEX.matches(normalizedKeyword.uppercase())
                if (!isRjKeyword && normalizedKeyword.isNotBlank()) {
                    val maxPages = 2
                    var pageNo = 1
                    while (pageNo <= maxPages) {
                        val resp = asmrOneCrawler.searchPrimaryOnly(normalizedKeyword, page = pageNo)
                        val idsPrimary = resp?.works
                            .orEmpty()
                            .asSequence()
                            .flatMap { w ->
                                sequenceOf(
                                    w.source_id,
                                    w.original_workno.orEmpty(),
                                    *w.language_editions.orEmpty().map { it.workno.orEmpty() }.toTypedArray()
                                )
                            }
                            .mapNotNull { s -> RJ_CODE_REGEX.find(s.trim().uppercase())?.value }
                            .toSet()
                        val idsBackup = asmrOneCrawler.searchBackupPreferredRjIds(normalizedKeyword, page = pageNo)
                        val ids = if (idsBackup.isEmpty()) idsPrimary else (idsPrimary + idsBackup)
                        if (ids.isNotEmpty()) {
                            indexByRj.keys.forEach { baseRj ->
                                if (asmrOneAvailabilityCache[baseRj] == true) return@forEach
                                if (baseRj in ids) {
                                    asmrOneAvailabilityCache[baseRj] = true
                                    updateAsmrOneAvailability(keyword = keyword, page = page, rj = baseRj, has = true, indexByRj = indexByRj)
                                }
                            }
                        }
                        val hasMorePrimary = resp?.pagination?.let { it.totalCount > it.page * it.pageSize } == true
                        if (!hasMorePrimary && idsBackup.isEmpty()) break
                        pageNo += 1
                    }
                }

                val ordered = indexByRj.keys.sortedByDescending { rj -> indexByRj[rj]?.maxOrNull() ?: -1 }
                val pending = ConcurrentLinkedQueue<String>()

                val semFast = Semaphore(4)
                val fastChecks = coroutineScope {
                    ordered.map { baseRj ->
                        async(enrichDispatcher) {
                            semFast.withPermit {
                                if (asmrOneAvailabilityCache[baseRj] == true) {
                                    updateAsmrOneAvailability(
                                        keyword = keyword,
                                        page = page,
                                        rj = baseRj,
                                        has = true,
                                        indexByRj = indexByRj
                                    )
                                    return@withPermit
                                }

                                val has = runCatching { asmrOneCrawler.hasWorkFast(baseRj, timeoutMs = 900L) }.getOrNull()
                                if (has == true) {
                                    asmrOneAvailabilityCache[baseRj] = true
                                    updateAsmrOneAvailability(
                                        keyword = keyword,
                                        page = page,
                                        rj = baseRj,
                                        has = true,
                                        indexByRj = indexByRj
                                    )
                                } else {
                                    pending.add(baseRj)
                                }
                            }
                        }
                    }
                }
                fastChecks.forEach { deferred -> runCatching { deferred.await() } }

                val retryTargets = pending.toList()
                if (retryTargets.isNotEmpty()) {
                    val semRetry = Semaphore(2)
                    val doneCounter = AtomicInteger(0)
                    val retries = coroutineScope {
                        retryTargets.map { baseRj ->
                            async(enrichDispatcher) {
                                semRetry.withPermit {
                                    val has = hasWorkFastWithRetry(baseRj, timeoutMs = 900L)
                                    when (has) {
                                        true -> {
                                            asmrOneAvailabilityCache[baseRj] = true
                                            updateAsmrOneAvailability(
                                                keyword = keyword,
                                                page = page,
                                                rj = baseRj,
                                                has = true,
                                                indexByRj = indexByRj
                                            )
                                        }
                                        false -> asmrOneAvailabilityCache[baseRj] = false
                                        null -> {}
                                    }
                                }
                                val done = doneCounter.incrementAndGet().coerceIn(0, total)
                                _uiState.update { state ->
                                    val cur = state as? SearchUiState.Success ?: return@update state
                                    if (cur.keyword != keyword || cur.page != page || cur.purchasedOnly) return@update state
                                    if (!cur.isAsmrOneChecking) cur else cur.copy(asmrOneChecked = done, asmrOneTotal = total)
                                }
                            }
                        }
                    }
                    retries.forEach { deferred -> runCatching { deferred.await() } }
                }

                if (BuildConfig.DEBUG) {
                    val dt = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
                    Log.d("SearchViewModel", "asmrOne availability page done keyword=$normalizedKeyword page=$page rjCount=${indexByRj.size} dt=${dt}ms")
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

    private suspend fun runDebugDelayTestForRj01491538() {
        val rj = DEBUG_DELAY_TEST_RJ
        val durations = ArrayList<Long>(DEBUG_DELAY_TEST_ROUNDS)
        val networkDurations = ArrayList<Long>(DEBUG_DELAY_TEST_ROUNDS)
        var cacheHits = 0
        var fallbackUsed = 0
        var networkCalls = 0

        repeat(DEBUG_DELAY_TEST_ROUNDS) { idx ->
            if (idx % 3 == 2) asmrOneAvailabilityCache.remove(rj)
            val cached = asmrOneAvailabilityCache[rj]
            val cacheHit = cached != null
            if (cacheHit) cacheHits++

            val start = SystemClock.elapsedRealtime()
            if (!cacheHit) {
                val result = runCatching { asmrOneCrawler.searchWithTrace(rj) }.getOrNull()
                val ok = result?.response?.works?.any { w ->
                    w.source_id.equals(rj, ignoreCase = true) ||
                        w.original_workno.orEmpty().equals(rj, ignoreCase = true) ||
                        w.language_editions.orEmpty().any { it.workno.orEmpty().equals(rj, ignoreCase = true) }
                } == true
                asmrOneAvailabilityCache[rj] = ok
                networkCalls++
                if (result?.trace?.fallbackUsed == true) fallbackUsed++
            }
            val dt = (SystemClock.elapsedRealtime() - start).coerceAtLeast(0L)
            durations.add(dt)
            if (!cacheHit) networkDurations.add(dt)
        }

        val networkStats = percentileStatsMs(networkDurations.ifEmpty { durations })
        val overallStats = percentileStatsMs(durations)
        Log.d(
            "SearchViewModel",
            "RJ01491538 delay test: n=$DEBUG_DELAY_TEST_ROUNDS cacheHit=$cacheHits network=$networkCalls fallbackUsed=$fallbackUsed " +
                "overall(p50=${overallStats.p50}ms p95=${overallStats.p95}ms max=${overallStats.max}ms) " +
                "network(p50=${networkStats.p50}ms p95=${networkStats.p95}ms max=${networkStats.max}ms)"
        )
    }

    private data class PercentileStats(val p50: Long, val p95: Long, val max: Long)

    private fun percentileStatsMs(values: List<Long>): PercentileStats {
        if (values.isEmpty()) return PercentileStats(0L, 0L, 0L)
        val sorted = values.sorted()
        val p50 = percentileNearestRank(sorted, 50.0)
        val p95 = percentileNearestRank(sorted, 95.0)
        val max = sorted.last()
        return PercentileStats(p50 = p50, p95 = p95, max = max)
    }

    private fun percentileNearestRank(sorted: List<Long>, percentile: Double): Long {
        if (sorted.isEmpty()) return 0L
        val n = sorted.size
        val rank = ceil(percentile / 100.0 * n).toInt().coerceIn(1, n)
        return sorted[rank - 1]
    }

    private companion object {
        private val RJ_CODE_REGEX = Regex("""RJ\d{6,}""")
        private const val DEBUG_DELAY_TEST_RJ = "RJ01491538"
        private const val DEBUG_DELAY_TEST_ROUNDS = 30
        private val debugDelayTestOnce = AtomicBoolean(false)
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
        val locale: String?,
        val canGoPrev: Boolean,
        val canGoNext: Boolean,
        val isPaging: Boolean,
        val isEnriching: Boolean = false,
        val isAsmrOneChecking: Boolean = false,
        val asmrOneChecked: Int = 0,
        val asmrOneTotal: Int = 0
    ) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}
