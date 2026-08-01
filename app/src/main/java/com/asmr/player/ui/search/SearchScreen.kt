package com.asmr.player.ui.search

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.domain.model.Album
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.cache.LazyListPreloader
import com.asmr.player.cache.LazyStaggeredGridPreloader
import com.asmr.player.ui.common.CustomSearchBar
import com.asmr.player.ui.common.ActiveDropdownMenuItem
import com.asmr.player.ui.common.EaraBrandedEmptyState
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.albumCoverImageModel
import com.asmr.player.ui.common.albumStableKey
import com.asmr.player.ui.common.interruptScrollableFlingOnPointerDown
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.clearFocusOnTapOutside
import com.asmr.player.ui.common.CollapsibleHeaderState
import com.asmr.player.ui.common.collapsibleHeaderUiState
import com.asmr.player.ui.common.consumeTapThrough
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.rememberCollapsibleHeaderState
import com.asmr.player.ui.common.rememberSaveablePrefetchedLazyListState
import com.asmr.player.ui.common.shouldFadeInCover
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.common.collectAsStateWhileActive
import com.asmr.player.ui.library.AlbumGridItem
import com.asmr.player.ui.library.AlbumGridItemSpacing
import com.asmr.player.ui.library.AlbumItem
import com.asmr.player.ui.library.AlbumMetaActionDialog
import com.asmr.player.ui.library.rememberAlbumMetaCopyAction
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.sidepanel.LandscapeRightPanelHost
import com.asmr.player.ui.sidepanel.RecentAlbumsPanel
import com.asmr.player.ui.theme.AsmrTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

internal const val SEARCH_INPUT_TAG = "search_input"
internal const val SEARCH_SCOPE_BUTTON_TAG = "search_scope_button"
internal const val SEARCH_SCOPE_OPTION_TAG_PREFIX = "search_scope_option"
internal const val SEARCH_LANGUAGE_BUTTON_TAG = "search_language_button"
internal const val SEARCH_LANGUAGE_OPTION_TAG_PREFIX = "search_language_option"
internal const val SEARCH_COLLECTED_SORT_BUTTON_TAG = "search_collected_sort_button"
internal const val SEARCH_COLLECTED_SORT_OPTION_TAG_PREFIX = "search_collected_sort_option"
internal const val SEARCH_CLEAR_BUTTON_TAG = "search_clear_button"
internal const val SEARCH_SUBMIT_BUTTON_TAG = "search_submit_button"
internal const val SEARCH_SUBMIT_SPINNER_TAG = "search_submit_spinner"
internal const val SEARCH_FIRST_PAGE_BUTTON_TAG = "search_first_page_button"
internal const val SEARCH_PREV_BUTTON_TAG = "search_prev_button"
internal const val SEARCH_NEXT_BUTTON_TAG = "search_next_button"
internal const val SEARCH_PAGINATION_TAG = "search_pagination"
internal const val SEARCH_CHROME_TAG = "search_chrome"
private val SearchChromeContentGap = 16.dp
private const val SearchPullRefreshFollowRatio = 0.86f
private val SearchPullRefreshSettleDistance = 68.dp
private val SearchPullRefreshMaxDistance = 112.dp
private val SearchPullActionHintHeight = 58.dp
private val SearchPageHorizontalPadding = 8.dp
private const val SearchPullNextPageDragResistance = 0.82f
private const val SearchPullNextPageFollowRatio = 0.84f
private const val SearchPullStretchExtraRatio = 0.28f
private const val SearchPullNextPageVerticalBias = 1.25f
private val SearchPullNextPageTriggerDistance = 96.dp
private val SearchPullNextPageMaxDistance = 172.dp
private val SearchPullNextPageMaxLift = 108.dp
private val SearchResultPlacementSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private fun searchResultItemKey(album: Album): String {
    return "search-result:${albumStableKey(album)}"
}

internal fun searchResultScrollKey(success: SearchUiState.Success?): String {
    if (success == null) return "search-results:none"
    return buildString {
        append("search-results:")
        append(success.resultRevision)
        append(':')
        append(success.page)
        append(':')
        append(success.keyword)
        append(':')
        append(success.order.name)
        append(':')
        append(success.collectedSort.name)
        append(':')
        append(success.purchasedOnly)
        append(':')
        append(success.presaleOnly)
        append(':')
        append(success.chineseTranslatedOnly)
        append(':')
        append(success.collectedOnly)
        append(':')
        append(success.locale.orEmpty())
    }
}

private fun onlineDetailLoadingFor(album: Album, state: SearchUiState.Success): Boolean {
    if (!state.isEnriching || state.purchasedOnly || state.collectedOnly) return false
    val rj = album.rjCode.ifBlank { album.workId }.trim().uppercase()
    return rj.isNotBlank() && rj in state.enrichingRjCodes
}

private fun searchRubberBandOffset(
    dragPx: Float,
    triggerPx: Float,
    maxOffsetPx: Float,
    followRatio: Float
): Float {
    val clampedDrag = dragPx.coerceAtLeast(0f)
    val safeTrigger = triggerPx.coerceAtLeast(1f)
    val base = clampedDrag.coerceAtMost(safeTrigger) * followRatio
    val extra = (clampedDrag - safeTrigger).coerceAtLeast(0f) * SearchPullStretchExtraRatio
    return (base + extra).coerceIn(0f, maxOffsetPx)
}

internal data class SearchChromeLockState(
    val interactionLocked: Boolean,
    val filterControlsLocked: Boolean,
    val searchSubmitLocked: Boolean,
    val showSearchSpinner: Boolean
)

internal fun resolveSearchChromeLockState(uiState: SearchUiState): SearchChromeLockState {
    val success = uiState as? SearchUiState.Success
    val interactionLocked = success?.isBusy == true
    val requestLocked = uiState is SearchUiState.Loading || interactionLocked
    return SearchChromeLockState(
        interactionLocked = interactionLocked,
        filterControlsLocked = requestLocked,
        searchSubmitLocked = requestLocked,
        showSearchSpinner = interactionLocked
    )
}

@Composable
private fun SearchFilterIconView(
    icon: SearchFilterIcon,
    tint: Color,
    modifier: Modifier = Modifier
) {
    when (icon) {
        is SearchFilterIcon.Vector -> Icon(
            painter = rememberVectorPainter(icon.imageVector),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )

        is SearchFilterIcon.Drawable -> Icon(
            painter = painterResource(icon.resId),
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    onAlbumClick: (Album, Boolean, Boolean) -> Unit,
    onOpenSearchAssist: (SearchAssistSearchRequest) -> Unit = {},
    submittedSearchKeyword: String = "",
    submittedSearchOrderName: String = SearchSortOption.Trend.name,
    submittedSearchPurchasedOnly: Boolean = false,
    submittedSearchPresaleOnly: Boolean = false,
    submittedSearchChineseTranslatedOnly: Boolean = false,
    submittedSearchCollectedOnly: Boolean = true,
    submittedSearchCollectedSortName: String = SearchCollectedSortOption.ReleaseNew.name,
    submittedSearchLocale: String = "ja_JP",
    submittedSearchSignal: Long = 0L,
    scrollToTopSignal: Long = 0L,
    onHorizontalPagerScrollLockChanged: (Boolean) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var purchasedOnly by rememberSaveable { mutableStateOf(false) }
    var presaleOnly by rememberSaveable { mutableStateOf(false) }
    var chineseTranslatedOnly by rememberSaveable { mutableStateOf(false) }
    var collectedOnly by rememberSaveable { mutableStateOf(true) }
    var selectedCollectedSortName by rememberSaveable { mutableStateOf(SearchCollectedSortOption.ReleaseNew.name) }
    var selectedLocale by rememberSaveable { mutableStateOf("ja_JP") }
    var selectedOrderName by rememberSaveable { mutableStateOf(SearchSortOption.Trend.name) }
    val selectedOrder = remember(selectedOrderName) {
        SearchSortOption.values().firstOrNull { it.name == selectedOrderName } ?: SearchSortOption.Trend
    }
    val selectedCollectedSort = remember(selectedCollectedSortName) {
        SearchCollectedSortOption.fromName(selectedCollectedSortName)
    }
    val selectedFilter = remember(selectedOrderName, purchasedOnly, presaleOnly, chineseTranslatedOnly, collectedOnly) {
        SearchFilterOption.fromState(
            order = selectedOrder,
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly
        )
    }
    val viewMode by viewModel.viewMode.collectAsStateWhileActive(isDataActive)
    val uiState by viewModel.uiState.collectAsStateWhileActive(isDataActive)
    val hotKeywordTerms by viewModel.hotKeywordTerms.collectAsStateWhileActive(isDataActive)
    val showHotKeywordFallback by viewModel.showHotKeywordFallback.collectAsStateWhileActive(isDataActive)
    val hotKeywordCarouselItem = rememberSearchHotKeywordCarouselItem(
        terms = hotKeywordTerms,
        showFallback = showHotKeywordFallback
    )
    val success = uiState as? SearchUiState.Success
    val resultScrollKey = searchResultScrollKey(success)
    val listState = rememberSaveablePrefetchedLazyListState(stateKey = resultScrollKey)
    val gridState = rememberSaveable(resultScrollKey, saver = LazyStaggeredGridState.Saver) { LazyStaggeredGridState() }
    val colorScheme = AsmrTheme.colorScheme
    val copyMeta = rememberAlbumMetaCopyAction(viewModel.messageManager)
    val playlistsViewModel: PlaylistsViewModel = hiltViewModel()
    val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val searchBlockedKeywords by settingsViewModel.searchBlockedKeywords.collectAsStateWhileActive(isDataActive)
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val chromeState = rememberCollapsibleHeaderState()
    val chromeResetKey = remember(resultScrollKey, viewMode) { "$resultScrollKey:$viewMode" }
    var lastChromeResetKey by rememberSaveable { mutableStateOf(chromeResetKey) }

    var keywordSyncedFromState by rememberSaveable { mutableStateOf(false) }
    var optionsSyncedFromState by rememberSaveable { mutableStateOf(false) }
    var lastHandledSubmittedSearchSignal by rememberSaveable { mutableStateOf(0L) }
    var metaActionKeyword by rememberSaveable { mutableStateOf<String?>(null) }

    fun openMetaActions(value: String) {
        val normalized = value.trim()
        if (normalized.isNotBlank()) metaActionKeyword = normalized
    }

    fun addMetaBlockedKeyword(value: String) {
        val normalized = value.trim()
        if (normalized.isBlank()) return
        val exists = searchBlockedKeywords.any { it.equals(normalized, ignoreCase = true) }
        settingsViewModel.addSearchBlockedKeyword(normalized)
        if (exists) {
            viewModel.messageManager.showInfo("屏蔽词已存在：$normalized")
        } else {
            viewModel.messageManager.showSuccess("已添加屏蔽词：$normalized")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.bootstrap(
            initialKeyword = keyword,
            initialPurchasedOnly = purchasedOnly,
            initialLocale = selectedLocale,
            initialCollectedOnly = collectedOnly,
            initialCollectedSort = selectedCollectedSort
        )
    }

    LaunchedEffect(success?.keyword) {
        val state = success ?: return@LaunchedEffect
        if (!keywordSyncedFromState) {
            keyword = state.keyword
            keywordSyncedFromState = true
        }
    }

    LaunchedEffect(
        success?.pendingRequest,
        success?.order,
        success?.collectedSort,
        success?.purchasedOnly,
        success?.presaleOnly,
        success?.chineseTranslatedOnly,
        success?.collectedOnly,
        success?.locale
    ) {
        val state = success ?: return@LaunchedEffect
        if (!optionsSyncedFromState || state.pendingRequest == null) {
            purchasedOnly = state.purchasedOnly
            presaleOnly = state.presaleOnly
            chineseTranslatedOnly = state.chineseTranslatedOnly
            collectedOnly = state.collectedOnly
            selectedCollectedSortName = state.collectedSort.name
            selectedLocale = state.locale ?: "ja_JP"
            selectedOrderName = state.order.name
            optionsSyncedFromState = true
        }
    }

    val chromeLockState = remember(uiState) { resolveSearchChromeLockState(uiState) }
    val interactionLocked = chromeLockState.interactionLocked
    val filterControlsLocked = chromeLockState.filterControlsLocked
    val searchSubmitLocked = chromeLockState.searchSubmitLocked
    val showSearchSpinner = chromeLockState.showSearchSpinner
    val highlightedPage = success?.page ?: 1
    val canGoPrev = success?.canGoPrev == true && !success.isSearching
    val canGoNext = success?.canGoNext == true && !success.isSearching
    val chromeReservedHeightPx = when {
        chromeState.heightPx > 0f -> chromeState.heightPx
        success != null -> with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
        else -> with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }
    }
    val topPadding = with(androidx.compose.ui.platform.LocalDensity.current) { chromeReservedHeightPx.toDp() } + SearchChromeContentGap

    fun scrollResultsToTop() {
        scope.launch {
            runCatching { listState.stopScroll(MutatePriority.PreventUserInput) }
            runCatching { gridState.stopScroll(MutatePriority.PreventUserInput) }
            runCatching { listState.scrollToItem(0) }
            runCatching { gridState.scrollToItem(0) }
        }
    }

    fun requestNextPage() {
        scrollResultsToTop()
        viewModel.nextPage()
    }

    fun submitSearch() {
        if (searchSubmitLocked) return
        val nextKeyword = keyword.trim()
            .ifBlank { hotKeywordCarouselItem.keyword.orEmpty() }
        if (nextKeyword.isBlank()) return
        keyboardController?.hide()
        val accepted = viewModel.search(nextKeyword)
        if (!accepted) return
        keyword = nextKeyword
        scrollResultsToTop()
    }

    fun clearKeywordAndSearch() {
        if (searchSubmitLocked) return
        keyword = ""
        keyboardController?.hide()
        val accepted = viewModel.search("")
        if (!accepted) return
        scrollResultsToTop()
        chromeState.expand()
    }

    fun searchMetaKeyword(value: String) {
        if (searchSubmitLocked) {
            viewModel.messageManager.showInfo("搜索处理中，请稍后再试")
            return
        }
        val normalized = value.trim()
        if (normalized.isBlank()) return
        keyboardController?.hide()
        val accepted = viewModel.search(
            keyword = normalized,
            order = selectedOrder,
            collectedSort = selectedCollectedSort,
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly,
            locale = selectedLocale
        )
        if (!accepted) return
        keyword = normalized
        scrollResultsToTop()
        chromeState.expand()
    }

    fun currentSearchAssistRequest(): SearchAssistSearchRequest {
        return SearchAssistSearchRequest(
            keyword = keyword,
            orderName = selectedOrder.name,
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly,
            collectedSortName = selectedCollectedSort.name,
            locale = selectedLocale
        )
    }

    LaunchedEffect(
        submittedSearchSignal,
        submittedSearchKeyword,
        submittedSearchOrderName,
        submittedSearchPurchasedOnly,
        submittedSearchPresaleOnly,
        submittedSearchChineseTranslatedOnly,
        submittedSearchCollectedOnly,
        submittedSearchCollectedSortName,
        submittedSearchLocale,
        searchSubmitLocked
    ) {
        if (
            submittedSearchSignal == 0L ||
            submittedSearchSignal == lastHandledSubmittedSearchSignal ||
            searchSubmitLocked
        ) {
            return@LaunchedEffect
        }
        val normalizedKeyword = submittedSearchKeyword.trim()
        if (searchSubmitLocked) return@LaunchedEffect
        val submittedOrder = SearchSortOption.values()
            .firstOrNull { it.name == submittedSearchOrderName }
            ?: SearchSortOption.Trend
        val submittedCollectedSort = SearchCollectedSortOption.fromName(submittedSearchCollectedSortName)
        keyboardController?.hide()
        val accepted = viewModel.search(
            keyword = normalizedKeyword,
            order = submittedOrder,
            collectedSort = submittedCollectedSort,
            purchasedOnly = submittedSearchPurchasedOnly,
            presaleOnly = submittedSearchPresaleOnly,
            chineseTranslatedOnly = submittedSearchChineseTranslatedOnly,
            collectedOnly = submittedSearchCollectedOnly,
            locale = submittedSearchLocale
        )
        if (!accepted) return@LaunchedEffect
        lastHandledSubmittedSearchSignal = submittedSearchSignal
        keyword = normalizedKeyword
        selectedOrderName = submittedOrder.name
        selectedCollectedSortName = submittedCollectedSort.name
        purchasedOnly = submittedSearchPurchasedOnly
        presaleOnly = submittedSearchPresaleOnly
        chineseTranslatedOnly = submittedSearchChineseTranslatedOnly
        collectedOnly = submittedSearchCollectedOnly
        selectedLocale = submittedSearchLocale
        scrollResultsToTop()
        chromeState.expand()
    }

    val pullToRefreshState = rememberPullToRefreshState()
    val pullNextPageEnabled =
        success?.results?.isNotEmpty() == true &&
            canGoNext &&
            !interactionLocked &&
            !pullToRefreshState.isRefreshing
    val pullNextPageTriggerDistancePx =
        with(androidx.compose.ui.platform.LocalDensity.current) { SearchPullNextPageTriggerDistance.toPx() }
    val pullNextPageMaxDistancePx =
        with(androidx.compose.ui.platform.LocalDensity.current) { SearchPullNextPageMaxDistance.toPx() }
    val pullNextPageMaxLiftPx =
        with(androidx.compose.ui.platform.LocalDensity.current) { SearchPullNextPageMaxLift.toPx() }
    var pullNextPageDragPx by remember(resultScrollKey, viewMode) { mutableFloatStateOf(0f) }
    val pullNextPageArmed = pullNextPageDragPx >= pullNextPageTriggerDistancePx
    val latestPullNextPageEnabled = rememberUpdatedState(pullNextPageEnabled)
    val latestIsAtBottom = rememberUpdatedState(
        if (viewMode == 0) !listState.canScrollForward else !gridState.canScrollForward
    )
    val latestPullNextPageTriggerDistancePx = rememberUpdatedState(pullNextPageTriggerDistancePx)
    val latestPullNextPageMaxDistancePx = rememberUpdatedState(pullNextPageMaxDistancePx)
    val latestRequestNextPage = rememberUpdatedState { requestNextPage() }
    val pullNextPageVisualTargetPx = remember(
        pullNextPageDragPx,
        pullNextPageTriggerDistancePx,
        pullNextPageMaxDistancePx,
        pullNextPageMaxLiftPx
    ) {
        searchRubberBandOffset(
            dragPx = pullNextPageDragPx,
            triggerPx = pullNextPageTriggerDistancePx,
            maxOffsetPx = pullNextPageMaxLiftPx,
            followRatio = SearchPullNextPageFollowRatio
        )
    }
    val pullNextPageVisualOffsetPx = pullNextPageVisualTargetPx
    val pullNextPageProgress =
        (pullNextPageVisualOffsetPx / pullNextPageMaxLiftPx).coerceIn(0f, 1f)
    val finishPullNextPageGesture = rememberUpdatedState {
        val shouldTrigger =
            latestPullNextPageEnabled.value &&
                pullNextPageDragPx >= latestPullNextPageTriggerDistancePx.value
        pullNextPageDragPx = 0f
        if (shouldTrigger) {
            latestRequestNextPage.value()
        }
    }
    val refreshGestureEnabled = !pullToRefreshState.isRefreshing
    val topPaddingPx = with(androidx.compose.ui.platform.LocalDensity.current) { topPadding.toPx() }
    val pullActionHintHeightPx =
        with(androidx.compose.ui.platform.LocalDensity.current) { SearchPullActionHintHeight.toPx() }
    val pullRefreshSettleDistancePx =
        with(androidx.compose.ui.platform.LocalDensity.current) { SearchPullRefreshSettleDistance.toPx() }
    val pullRefreshMaxDistancePx =
        with(androidx.compose.ui.platform.LocalDensity.current) { SearchPullRefreshMaxDistance.toPx() }
    val pullContentOffsetTargetPx = (
        if (pullToRefreshState.isRefreshing) {
            pullRefreshSettleDistancePx
        } else {
            searchRubberBandOffset(
                dragPx = pullToRefreshState.verticalOffset,
                triggerPx = pullToRefreshState.positionalThreshold
                    .takeIf { it > 0f }
                    ?: pullRefreshSettleDistancePx,
                maxOffsetPx = pullRefreshMaxDistancePx,
                followRatio = SearchPullRefreshFollowRatio
            )
        }
        ).coerceIn(
        minimumValue = 0f,
        maximumValue = pullRefreshMaxDistancePx
    )
    val pullContentOffsetPx by animateFloatAsState(
        targetValue = pullContentOffsetTargetPx,
        animationSpec = if (pullToRefreshState.progress > 0f && !pullToRefreshState.isRefreshing) {
            snap()
        } else {
            tween(durationMillis = 220, easing = FastOutSlowInEasing)
        },
        label = "searchPullContentOffset"
    )
    val pullRefreshProgress =
        if (pullToRefreshState.isRefreshing) {
            1f
        } else {
            val threshold = pullToRefreshState.positionalThreshold
                .takeIf { it > 0f }
                ?: pullRefreshSettleDistancePx
            (pullContentOffsetPx / threshold).coerceIn(0f, 1f)
        }
    val pullRefreshArmed = pullToRefreshState.progress >= 1f
    val pullRefreshHintVisible = pullContentOffsetPx > 1f || pullToRefreshState.isRefreshing
    val pullNextPageHintVisible = pullNextPageVisualOffsetPx > 1f
    val listStretchOffsetPx = pullContentOffsetPx - pullNextPageVisualOffsetPx
    val pullRefreshHintHeightPx = pullContentOffsetPx.coerceIn(0f, pullActionHintHeightPx)
    val pullRefreshHintHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        pullRefreshHintHeightPx.toDp()
    }
    val pullNextRevealHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        pullNextPageVisualOffsetPx.toDp()
    }
    val pullRefreshHintEdgeOffsetPx = topPaddingPx + pullContentOffsetPx - pullRefreshHintHeightPx
    val latestKeyword by rememberUpdatedState(keyword)
    val latestHorizontalPagerScrollLockChanged = rememberUpdatedState(onHorizontalPagerScrollLockChanged)
    fun stopActiveScroll() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            pullNextPageDragPx = 0f
            latestHorizontalPagerScrollLockChanged.value(false)
            runCatching { listState.stopScroll(MutatePriority.UserInput) }
            runCatching { gridState.stopScroll(MutatePriority.UserInput) }
        }
    }
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (!pullToRefreshState.isRefreshing) return@LaunchedEffect
        when (val state = uiState) {
            is SearchUiState.Success -> {
                if (state.isBusy) {
                    pullToRefreshState.endRefresh()
                } else {
                    viewModel.refreshPage()
                }
            }

            is SearchUiState.Loading -> Unit
            else -> viewModel.search(latestKeyword)
        }
    }
    LaunchedEffect(uiState) {
        if (!pullToRefreshState.isRefreshing) return@LaunchedEffect
        val canEnd = when (val state = uiState) {
            is SearchUiState.Success -> !state.isBusy
            is SearchUiState.Loading -> false
            else -> true
        }
        if (canEnd) pullToRefreshState.endRefresh()
    }
    LaunchedEffect(resultScrollKey, pullNextPageEnabled) {
        if (!pullNextPageEnabled) {
            if (pullNextPageDragPx != 0f) {
                pullNextPageDragPx = 0f
            }
            latestHorizontalPagerScrollLockChanged.value(false)
        }
    }
    LaunchedEffect(pullNextPageDragPx > 0f) {
        latestHorizontalPagerScrollLockChanged.value(pullNextPageDragPx > 0f)
    }
    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            latestHorizontalPagerScrollLockChanged.value(false)
        }
    }
    LaunchedEffect(chromeResetKey) {
        if (lastChromeResetKey != chromeResetKey) {
            chromeState.expand()
            lastChromeResetKey = chromeResetKey
        }
    }
    LaunchedEffect(listState, viewMode) {
        if (viewMode != 0) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop) chromeState.expand()
            }
    }
    LaunchedEffect(gridState, viewMode) {
        if (viewMode == 0) return@LaunchedEffect
        snapshotFlow {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop) chromeState.expand()
            }
    }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal == 0L) return@LaunchedEffect
        pullNextPageDragPx = 0f
        when (viewMode) {
            0 -> {
                runCatching { listState.stopScroll(MutatePriority.PreventUserInput) }
                runCatching { listState.scrollToItem(0) }
            }
            else -> {
                runCatching { gridState.stopScroll(MutatePriority.PreventUserInput) }
                runCatching { gridState.scrollToItem(0) }
            }
        }
        chromeState.expand()
    }
    LaunchedEffect(isActive, viewMode) {
        if (isActive) return@LaunchedEffect
        when (viewMode) {
            0 -> listState.stopScroll(MutatePriority.PreventUserInput)
            else -> gridState.stopScroll(MutatePriority.PreventUserInput)
        }
        pullNextPageDragPx = 0f
        latestHorizontalPagerScrollLockChanged.value(false)
    }

    Scaffold(
        contentWindowInsets = StableWindowInsets.navigationBars,
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground
    ) { padding ->
        LandscapeRightPanelHost(
            windowSizeClass = windowSizeClass,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            topPanel = {
                RecentAlbumsPanel(
                    onOpenAlbum = { album ->
                        onAlbumClick(
                            Album(
                                id = album.id,
                                title = album.title,
                                path = album.path,
                                localPath = album.localPath,
                                downloadPath = album.downloadPath,
                                circle = album.circle,
                                cv = album.cv,
                                tags = album.tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                coverUrl = album.coverUrl,
                                coverPath = album.coverPath,
                                coverThumbPath = album.coverThumbPath,
                                workId = album.workId,
                                rjCode = album.rjCode,
                                description = album.description
                            ),
                            false,
                            false
                        )
                    },
                    modifier = Modifier.fillMaxHeight()
                )
            },
            bottomPanel = null
        ) { contentModifier, hasRightPanel, rightPanelToggle ->
            Box(
                modifier = contentModifier,
                contentAlignment = if (hasRightPanel) Alignment.TopStart else Alignment.TopCenter
            ) {
                val searchContentModifier = if (isCompact || hasRightPanel) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                }
                Box(
                    modifier = searchContentModifier
                        .interruptScrollableFlingOnPointerDown { stopActiveScroll() }
                ) {
                    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(resultScrollKey, viewMode) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    var trackedPointerId = down.id
                                    var previousPosition = down.position
                                    var dragFromDown = Offset.Zero
                                    var pullNextGestureActive = false
                                    var horizontalGestureActive = false
                                    val touchSlop = viewConfiguration.touchSlop
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change =
                                            event.changes.firstOrNull { it.id == trackedPointerId }
                                                ?: event.changes.firstOrNull()
                                        if (change != null) {
                                            trackedPointerId = change.id
                                            val positionDelta = change.position - previousPosition
                                            previousPosition = change.position
                                            if (!pullNextGestureActive && !horizontalGestureActive) {
                                                dragFromDown += positionDelta
                                                val isPastTouchSlop = dragFromDown.getDistance() > touchSlop
                                                if (isPastTouchSlop) {
                                                    horizontalGestureActive =
                                                        dragFromDown.x.absoluteValue >=
                                                            dragFromDown.y.absoluteValue * SearchPullNextPageVerticalBias
                                                    pullNextGestureActive =
                                                        !horizontalGestureActive &&
                                                            dragFromDown.y < 0f &&
                                                            dragFromDown.y.absoluteValue >=
                                                            dragFromDown.x.absoluteValue * SearchPullNextPageVerticalBias &&
                                                            latestIsAtBottom.value &&
                                                            latestPullNextPageEnabled.value
                                                    if (pullNextGestureActive) {
                                                        latestHorizontalPagerScrollLockChanged.value(true)
                                                    }
                                                }
                                            }
                                            val deltaY = positionDelta.y
                                            when {
                                                horizontalGestureActive -> Unit

                                                !latestPullNextPageEnabled.value -> {
                                                    if (pullNextPageDragPx != 0f) {
                                                        pullNextPageDragPx = 0f
                                                    }
                                                }

                                                deltaY < 0f && latestIsAtBottom.value && pullNextGestureActive -> {
                                                    val delta = (-deltaY) * SearchPullNextPageDragResistance
                                                    pullNextPageDragPx =
                                                        (pullNextPageDragPx + delta)
                                                            .coerceIn(0f, latestPullNextPageMaxDistancePx.value)
                                                    latestHorizontalPagerScrollLockChanged.value(true)
                                                    change.consume()
                                                }

                                                deltaY > 0f && (pullNextPageDragPx > 0f || pullNextGestureActive) -> {
                                                    pullNextPageDragPx =
                                                        (pullNextPageDragPx - deltaY).coerceAtLeast(0f)
                                                    if (pullNextPageDragPx == 0f) {
                                                        pullNextGestureActive = false
                                                        latestHorizontalPagerScrollLockChanged.value(false)
                                                    }
                                                    change.consume()
                                                }

                                                pullNextGestureActive -> {
                                                    change.consume()
                                                }

                                                !latestIsAtBottom.value && pullNextPageDragPx > 0f -> {
                                                    pullNextPageDragPx = 0f
                                                }
                                            }
                                        }
                                    } while (event.changes.any { it.pressed })
                                    latestHorizontalPagerScrollLockChanged.value(false)
                                    finishPullNextPageGesture.value()
                                }
                            }
                            .then(
                                if (refreshGestureEnabled) {
                                    Modifier.nestedScroll(pullToRefreshState.nestedScrollConnection)
                                } else {
                                    Modifier
                                }
                            )
                                .clipToBounds()
                        ) {
                        if (pullRefreshHintVisible) {
                            SearchPullActionHint(
                                progress = pullRefreshProgress,
                                active = pullToRefreshState.isRefreshing,
                                armed = pullRefreshArmed,
                                direction = if (pullRefreshArmed) {
                                    SearchPullActionDirection.Up
                                } else {
                                    SearchPullActionDirection.Down
                                },
                                idleText = "下拉刷新",
                                armedText = "松手刷新",
                                activeText = "正在刷新",
                                height = pullRefreshHintHeight,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .graphicsLayer {
                                        alpha = pullRefreshProgress.coerceIn(0f, 1f)
                                        translationY = pullRefreshHintEdgeOffsetPx
                                    }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clearFocusOnTapOutside()
                                .graphicsLayer { translationY = listStretchOffsetPx }
                        ) {
                            when (val state = uiState) {
                            is SearchUiState.Loading -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(
                                        state = rememberScrollState(),
                                        flingBehavior = rememberCalmScrollableFlingBehavior()
                                    )
                                    .padding(top = topPadding),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                EaraLogoLoadingIndicator(tint = colorScheme.primary)
                            }

                            is SearchUiState.Success -> {
                                if (state.results.isEmpty()) {
                                    EaraBrandedEmptyState(
                                        sectionTitle = "在线搜索",
                                        headline = if (state.keyword.isBlank()) "还没有搜索结果" else "没有找到匹配结果",
                                        sectionIcon = Icons.Rounded.Search,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            top = topPadding,
                                            bottom = LocalBottomOverlayPadding.current + 24.dp
                                        )
                                    )
                                } else if (viewMode == 0) {
                                    val app = LocalContext.current.applicationContext
                                    val cacheManager = remember(app) {
                                        EntryPointAccessors.fromApplication(app, ImageCacheEntryPoint::class.java)
                                            .imageCacheManager()
                                    }
                                    val density = LocalDensity.current
                                    val screenWidthDp = LocalConfiguration.current.screenWidthDp
                                    val listItemHeight = (screenWidthDp.dp * 0.24f).coerceIn(112.dp, 140.dp)
                                    val coverPx = remember(listItemHeight, density) { with(density) { listItemHeight.roundToPx() } }
                                    val preloadSize = remember(coverPx) { IntSize(coverPx, coverPx) }
                                    val coverFadeIn = shouldFadeInCover(listState.isScrollInProgress)
                                    LazyListPreloader(
                                        state = listState,
                                        itemCount = state.results.size,
                                        enabled = isActive,
                                        preloadNext = 24,
                                        preloadSize = preloadSize,
                                        cacheManagerProvider = { cacheManager },
                                        modelAt = { idx ->
                                            state.results.getOrNull(idx)?.let { albumCoverImageModel(it) }
                                        }
                                    )
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .nestedScroll(chromeState.nestedScrollConnection)
                                            .thinScrollbar(listState),
                                        flingBehavior = rememberCalmScrollableFlingBehavior(),
                                        contentPadding = PaddingValues(top = topPadding, bottom = 8.dp)
                                            .withAddedBottomPadding(LocalBottomOverlayPadding.current)
                                    ) {
                                        lazyItemsIndexed(
                                            items = state.results,
                                            key = { _, album -> searchResultItemKey(album) },
                                            contentType = { _, _ -> "album" }
                                        ) { _, album ->
                                            val onlineDetailLoading = onlineDetailLoadingFor(album, state)
                                            val rj = album.rjCode.ifBlank { album.workId }.trim().uppercase()
                                            val hasResolvedDetail = rj.isNotBlank() && rj in state.enrichedDetailRjCodes
                                            AlbumItem(
                                                album = album,
                                                onClick = { onAlbumClick(album, state.purchasedOnly, hasResolvedDetail) },
                                                modifier = Modifier.animateItemPlacement(SearchResultPlacementSpring),
                                                onlineDetailLoading = onlineDetailLoading,
                                                onlineCvLoading = onlineDetailLoading,
                                                coverFadeIn = coverFadeIn,
                                                coverReloadKey = state.resultRevision,
                                                onRjClick = { copyMeta("RJ", it) },
                                                onCircleClick = { copyMeta("社团", it) },
                                                onCircleLongClick = ::openMetaActions,
                                                onCvClick = { copyMeta("CV", it) },
                                                onCvLongClick = ::openMetaActions,
                                                onTagClick = { copyMeta("标签", it) },
                                                onTagLongClick = ::openMetaActions,
                                            )
                                        }
                                    }
                                } else {
                                    val app = LocalContext.current.applicationContext
                                    val cacheManager = remember(app) {
                                        EntryPointAccessors.fromApplication(app, ImageCacheEntryPoint::class.java)
                                            .imageCacheManager()
                                    }
                                    val density = LocalDensity.current
                                    val gridCellSize = if (isCompact) 150.dp else 200.dp
                                    val gridCoverPx = remember(gridCellSize, density) { with(density) { gridCellSize.roundToPx() } }
                                    val gridPreloadSize = remember(gridCoverPx) { IntSize(gridCoverPx, gridCoverPx) }
                                    val coverFadeIn = shouldFadeInCover(gridState.isScrollInProgress)
                                    LazyStaggeredGridPreloader(
                                        state = gridState,
                                        itemCount = state.results.size,
                                        enabled = isActive,
                                        preloadNext = 24,
                                        preloadSize = gridPreloadSize,
                                        cacheManagerProvider = { cacheManager },
                                        modelAt = { idx ->
                                            state.results.getOrNull(idx)?.let { albumCoverImageModel(it) }
                                        }
                                    )
                                    LazyVerticalStaggeredGrid(
                                        columns = StaggeredGridCells.Adaptive(gridCellSize),
                                        state = gridState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .nestedScroll(chromeState.nestedScrollConnection)
                                            .thinScrollbar(gridState),
                                        flingBehavior = rememberCalmScrollableFlingBehavior(),
                                        contentPadding = PaddingValues(
                                            top = topPadding,
                                            start = SearchPageHorizontalPadding,
                                            end = SearchPageHorizontalPadding,
                                            bottom = 16.dp
                                        ).withAddedBottomPadding(LocalBottomOverlayPadding.current),
                                        horizontalArrangement = Arrangement.spacedBy(AlbumGridItemSpacing),
                                        verticalItemSpacing = AlbumGridItemSpacing
                                    ) {
                                        items(
                                            state.results.size,
                                            key = { index -> searchResultItemKey(state.results[index]) },
                                            contentType = { "albumGrid" }
                                        ) { index ->
                                            val album = state.results[index]
                                            val onlineDetailLoading = onlineDetailLoadingFor(album, state)
                                            val rj = album.rjCode.ifBlank { album.workId }.trim().uppercase()
                                            val hasResolvedDetail = rj.isNotBlank() && rj in state.enrichedDetailRjCodes
                                            AlbumGridItem(
                                                album = album,
                                                onClick = { onAlbumClick(album, state.purchasedOnly, hasResolvedDetail) },
                                                modifier = Modifier.animateItemPlacement(SearchResultPlacementSpring),
                                                onlineDetailLoading = onlineDetailLoading,
                                                onlineCvLoading = onlineDetailLoading,
                                                coverFadeIn = coverFadeIn,
                                                coverReloadKey = state.resultRevision,
                                                onRjClick = { copyMeta("RJ", it) },
                                                onCircleClick = { copyMeta("社团", it) },
                                                onCircleLongClick = ::openMetaActions,
                                                onCvClick = { copyMeta("CV", it) },
                                                onCvLongClick = ::openMetaActions,
                                                onTagClick = { copyMeta("标签", it) },
                                                onTagLongClick = ::openMetaActions,
                                            )
                                        }
                                    }
                                }
                            }

                            is SearchUiState.Error -> EaraBrandedEmptyState(
                                sectionTitle = "在线搜索",
                                headline = "网络连接出了点问题",
                                sectionIcon = Icons.Rounded.WifiOff,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = topPadding,
                                    bottom = LocalBottomOverlayPadding.current + 24.dp
                                ),
                                footer = {
                                    FilledTonalButton(
                                        onClick = { viewModel.retry() },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = colorScheme.primaryContainer,
                                            contentColor = colorScheme.onPrimaryContainer
                                        )
                                    ) {
                                        Text("重试")
                                    }
                                }
                            )

                            else -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(
                                        state = rememberScrollState(),
                                        flingBehavior = rememberCalmScrollableFlingBehavior()
                                    )
                            ) {}
                            }
                        }

                        if (pullNextPageHintVisible) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = LocalBottomOverlayPadding.current)
                                    .fillMaxWidth()
                                    .height(pullNextRevealHeight)
                                    .clipToBounds()
                                    .graphicsLayer {
                                        alpha = pullNextPageProgress.coerceIn(0f, 1f)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                SearchPullActionHint(
                                    progress = pullNextPageProgress,
                                    active = false,
                                    armed = pullNextPageArmed,
                                    direction = if (pullNextPageArmed) {
                                        SearchPullActionDirection.Down
                                    } else {
                                        SearchPullActionDirection.Up
                                    },
                                    idleText = "上拉下一页",
                                    armedText = "松手翻页",
                                    activeText = "正在翻页"
                                )
                            }
                        }
                        }
                    }

                    SearchChrome(
                        modifier = Modifier.align(Alignment.TopCenter),
                        keyword = keyword,
                        onKeywordChange = { keyword = it },
                        placeholder = hotKeywordCarouselItem.placeholder,
                        searchFieldReadOnly = true,
                        onSearchFieldClick = { onOpenSearchAssist(currentSearchAssistRequest()) },
                        selectedFilter = selectedFilter,
                        selectedCollectedSort = selectedCollectedSort,
                        selectedLocale = selectedLocale,
                        filterControlsLocked = filterControlsLocked,
                        searchSubmitLocked = searchSubmitLocked,
                        showSearchSpinner = showSearchSpinner,
                        showPagination = success != null,
                        page = highlightedPage,
                        canGoPrev = canGoPrev,
                        canGoNext = canGoNext,
                        controlsLocked = interactionLocked,
                        rightPanelToggle = rightPanelToggle,
                        chromeState = chromeState,
                        onMeasured = { size: IntSize -> chromeState.updateHeight(size.height.toFloat()) },
                        onSearchSubmit = { submitSearch() },
                        onClearKeyword = { clearKeywordAndSearch() },
                        onFilterSelected = { option ->
                            val nextOrder = option.sortOption ?: selectedOrder
                            val nextKeyword = keyword.trim()
                            val accepted = viewModel.search(
                                keyword = nextKeyword,
                                order = nextOrder,
                                collectedSort = selectedCollectedSort,
                                purchasedOnly = option.isPurchasedOnly,
                                presaleOnly = option.isPresaleOnly,
                                chineseTranslatedOnly = option.isChineseTranslated,
                                collectedOnly = option.isCollectedOnly,
                                locale = selectedLocale
                            )
                            if (accepted) {
                                selectedOrderName = nextOrder.name
                                purchasedOnly = option.isPurchasedOnly
                                presaleOnly = option.isPresaleOnly
                                chineseTranslatedOnly = option.isChineseTranslated
                                collectedOnly = option.isCollectedOnly
                                keyword = nextKeyword
                                scrollResultsToTop()
                                chromeState.expand()
                            }
                        },
                        onLocaleSelected = { locale ->
                            selectedLocale = locale
                            val accepted = viewModel.updateSearchOptions(
                                order = selectedOrder,
                                collectedSort = selectedCollectedSort,
                                purchasedOnly = purchasedOnly,
                                presaleOnly = presaleOnly,
                                chineseTranslatedOnly = chineseTranslatedOnly,
                                collectedOnly = collectedOnly,
                                locale = locale
                            )
                            if (accepted) {
                                scrollResultsToTop()
                                chromeState.expand()
                            }
                        },
                        onCollectedSortSelected = { sort ->
                            selectedCollectedSortName = sort.name
                            val accepted = viewModel.updateSearchOptions(
                                order = selectedOrder,
                                collectedSort = sort,
                                purchasedOnly = purchasedOnly,
                                presaleOnly = presaleOnly,
                                chineseTranslatedOnly = chineseTranslatedOnly,
                                collectedOnly = collectedOnly,
                                locale = selectedLocale
                            )
                            if (accepted) {
                                scrollResultsToTop()
                                chromeState.expand()
                            }
                        },
                        onFirstPage = {
                            scrollResultsToTop()
                            viewModel.firstPage()
                        },
                        onPrev = {
                            scrollResultsToTop()
                            viewModel.prevPage()
                        },
                        onNext = {
                            requestNextPage()
                        }
                    )
                }
            }
        }
    }

    metaActionKeyword?.let { targetKeyword ->
        AlbumMetaActionDialog(
            keyword = targetKeyword,
            onDismissRequest = { metaActionKeyword = null },
            onSearch = ::searchMetaKeyword,
            onCreatePlaylist = playlistsViewModel::createPlaylist,
            onCreateGroup = albumGroupsViewModel::createGroup,
            onAddBlockedKeyword = ::addMetaBlockedKeyword,
        )
    }
}


private enum class SearchPullActionDirection {
    Down,
    Up
}

@Composable
private fun SearchPullActionHint(
    progress: Float,
    active: Boolean,
    armed: Boolean,
    direction: SearchPullActionDirection,
    idleText: String,
    armedText: String,
    activeText: String,
    height: Dp = SearchPullActionHintHeight,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val resolvedProgress = progress.coerceIn(0f, 1f)
    val iconScale by animateFloatAsState(
        targetValue = if (armed || active) 1.08f else 0.88f + resolvedProgress * 0.12f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "search_pull_action_icon_scale"
    )
    val tint = if (armed || active) colorScheme.primary else colorScheme.textSecondary
    val label = when {
        active -> activeText
        armed -> armedText
        else -> idleText
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (active) {
                EaraLogoLoadingIndicator(
                    size = 18.dp,
                    tint = colorScheme.primary,
                    glowColor = colorScheme.primarySoft,
                    showGlow = false
                )
            } else {
                val icon = when (direction) {
                    SearchPullActionDirection.Down -> Icons.Rounded.KeyboardArrowDown
                    SearchPullActionDirection.Up -> Icons.Rounded.KeyboardArrowUp
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
        }
    }
}

@Composable
internal fun SearchChrome(
    modifier: Modifier = Modifier,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    placeholder: String = DefaultSearchPlaceholder,
    searchFieldReadOnly: Boolean = false,
    onSearchFieldClick: (() -> Unit)? = null,
    selectedFilter: SearchFilterOption,
    selectedCollectedSort: SearchCollectedSortOption = SearchCollectedSortOption.ReleaseNew,
    selectedLocale: String,
    filterControlsLocked: Boolean,
    searchSubmitLocked: Boolean,
    showSearchSpinner: Boolean,
    showPagination: Boolean,
    page: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    controlsLocked: Boolean,
    rightPanelToggle: (@Composable (Modifier) -> Unit)?,
    chromeState: CollapsibleHeaderState,
    chromeTestTag: String = SEARCH_CHROME_TAG,
    inputTestTag: String = SEARCH_INPUT_TAG,
    clearButtonTestTag: String = SEARCH_CLEAR_BUTTON_TAG,
    submitButtonTestTag: String = SEARCH_SUBMIT_BUTTON_TAG,
    inputFocusRequester: FocusRequester? = null,
    onMeasured: (IntSize) -> Unit,
    onSearchSubmit: () -> Unit,
    onClearKeyword: (() -> Unit)? = null,
    onFilterSelected: (SearchFilterOption) -> Unit,
    onLocaleSelected: (String) -> Unit,
    onCollectedSortSelected: (SearchCollectedSortOption) -> Unit = {},
    onFirstPage: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val collapseStateDescription by remember(chromeState) {
        derivedStateOf { collapsibleHeaderUiState(chromeState.collapseFraction) }
    }
    Column(
        modifier = modifier
            .onSizeChanged(onMeasured)
            // Use layout offset instead of a graphics layer so Android text selection
            // toolbars anchor to the real on-screen position of the editable field.
            .offset { IntOffset(x = 0, y = chromeState.offsetPx.roundToInt()) }
            .semantics { stateDescription = collapseStateDescription }
            .testTag(chromeTestTag)
    ) {
        SearchToolbar(
            keyword = keyword,
            onKeywordChange = onKeywordChange,
            placeholder = placeholder,
            searchFieldReadOnly = searchFieldReadOnly,
            onSearchFieldClick = onSearchFieldClick,
            selectedFilter = selectedFilter,
            selectedCollectedSort = selectedCollectedSort,
            selectedLocale = selectedLocale,
            filterControlsLocked = filterControlsLocked,
            searchSubmitLocked = searchSubmitLocked,
            showSearchSpinner = showSearchSpinner,
            inputTestTag = inputTestTag,
            clearButtonTestTag = clearButtonTestTag,
            submitButtonTestTag = submitButtonTestTag,
            inputFocusRequester = inputFocusRequester,
            onSearchSubmit = onSearchSubmit,
            onClearKeyword = onClearKeyword,
            onFilterSelected = onFilterSelected,
            onLocaleSelected = onLocaleSelected,
            onCollectedSortSelected = onCollectedSortSelected,
            rightPanelToggle = rightPanelToggle
        )
        if (showPagination) {
            SearchPaginationHeader(
                page = page,
                canGoPrev = canGoPrev,
                canGoNext = canGoNext,
                controlsLocked = controlsLocked,
                onFirstPage = onFirstPage,
                onPrev = onPrev,
                onNext = onNext
            )
        }
    }
}

@Composable
internal fun SearchToolbar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    placeholder: String = DefaultSearchPlaceholder,
    searchFieldReadOnly: Boolean = false,
    onSearchFieldClick: (() -> Unit)? = null,
    selectedFilter: SearchFilterOption,
    selectedCollectedSort: SearchCollectedSortOption = SearchCollectedSortOption.ReleaseNew,
    selectedLocale: String,
    filterControlsLocked: Boolean,
    searchSubmitLocked: Boolean,
    showSearchSpinner: Boolean,
    inputTestTag: String = SEARCH_INPUT_TAG,
    clearButtonTestTag: String = SEARCH_CLEAR_BUTTON_TAG,
    submitButtonTestTag: String = SEARCH_SUBMIT_BUTTON_TAG,
    inputFocusRequester: FocusRequester? = null,
    onSearchSubmit: () -> Unit,
    onClearKeyword: (() -> Unit)? = null,
    onFilterSelected: (SearchFilterOption) -> Unit,
    onLocaleSelected: (String) -> Unit,
    onCollectedSortSelected: (SearchCollectedSortOption) -> Unit = {},
    rightPanelToggle: (@Composable (Modifier) -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    var scopeMenuExpanded by remember { mutableStateOf(false) }
    var secondaryMenuExpanded by remember { mutableStateOf(false) }
    val dropdownContainerColor = lerp(
        colorScheme.surface,
        colorScheme.primarySoft,
        if (colorScheme.isDark) 0.16f else 0.26f
    ).copy(alpha = if (colorScheme.isDark) 0.95f else 0.97f)
        .compositeOver(colorScheme.background)

    LaunchedEffect(filterControlsLocked, searchSubmitLocked) {
        if (filterControlsLocked || searchSubmitLocked) {
            scopeMenuExpanded = false
            secondaryMenuExpanded = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SearchPageHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CustomSearchBar(
            value = keyword,
            onValueChange = onKeywordChange,
            placeholder = placeholder,
            modifier = Modifier
                .weight(1f),
            readOnly = searchFieldReadOnly,
            onFieldClick = onSearchFieldClick,
            focusRequester = inputFocusRequester,
            inputTestTag = inputTestTag,
            leadingIcon = {
                Box {
                    TextButton(
                        onClick = { scopeMenuExpanded = true },
                        enabled = !filterControlsLocked,
                        modifier = Modifier
                            .height(32.dp)
                            .testTag(SEARCH_SCOPE_BUTTON_TAG),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colorScheme.primary
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SearchFilterIconView(
                                icon = selectedFilter.icon,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = selectedFilter.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = scopeMenuExpanded,
                        onDismissRequest = { scopeMenuExpanded = false },
                        modifier = Modifier.background(dropdownContainerColor)
                    ) {
                        SearchFilterOption.values().forEachIndexed { index, option ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    thickness = 0.5.dp,
                                    color = colorScheme.textSecondary.copy(alpha = 0.2f)
                                )
                            }
                            DropdownMenuItem(
                                modifier = Modifier.testTag("${SEARCH_SCOPE_OPTION_TAG_PREFIX}_${option.name}"),
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SearchFilterIconView(
                                            icon = option.icon,
                                            tint = if (option == selectedFilter) colorScheme.primary else colorScheme.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = option.label,
                                            color = if (option == selectedFilter) colorScheme.primary else colorScheme.textPrimary
                                        )
                                    }
                                },
                                onClick = {
                                    scopeMenuExpanded = false
                                    onFilterSelected(option)
                                }
                            )
                        }
                    }
                }
            },
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (keyword.isNotBlank()) {
                        IconButton(
                            onClick = { onClearKeyword?.invoke() ?: onKeywordChange("") },
                            enabled = !searchSubmitLocked,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag(clearButtonTestTag)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Box {
                        val secondaryButtonTag = if (selectedFilter.isCollectedOnly) {
                            SEARCH_COLLECTED_SORT_BUTTON_TAG
                        } else {
                            SEARCH_LANGUAGE_BUTTON_TAG
                        }
                        TextButton(
                            onClick = { secondaryMenuExpanded = true },
                            enabled = !filterControlsLocked,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 1.dp, minHeight = 30.dp)
                                .height(30.dp)
                                .testTag(secondaryButtonTag),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colorScheme.primary
                            )
                        ) {
                            val label = if (selectedFilter.isCollectedOnly) {
                                selectedCollectedSort.label
                            } else {
                                when (selectedLocale.trim()) {
                                    "zh_CN" -> "简中"
                                    "zh_TW" -> "繁中"
                                    else -> "日语"
                                }
                            }
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = secondaryMenuExpanded,
                            onDismissRequest = { secondaryMenuExpanded = false },
                            modifier = Modifier.background(dropdownContainerColor)
                        ) {
                            if (selectedFilter.isCollectedOnly) {
                                SearchCollectedSortOption.values().forEachIndexed { index, option ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            thickness = 0.5.dp,
                                            color = colorScheme.textSecondary.copy(alpha = 0.2f)
                                        )
                                    }
                                    ActiveDropdownMenuItem(
                                        label = option.label,
                                        selected = option == selectedCollectedSort,
                                        testTag = "${SEARCH_COLLECTED_SORT_OPTION_TAG_PREFIX}_${option.name}",
                                        activeColor = colorScheme.primary,
                                        inactiveColor = colorScheme.textPrimary,
                                        onClick = {
                                            secondaryMenuExpanded = false
                                            onCollectedSortSelected(option)
                                        }
                                    )
                                }
                            } else {
                                listOf(
                                    "ja_JP" to "日语",
                                    "zh_CN" to "简中",
                                    "zh_TW" to "繁中"
                                ).forEachIndexed { index, (locale, label) ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            thickness = 0.5.dp,
                                            color = colorScheme.textSecondary.copy(alpha = 0.2f)
                                        )
                                    }
                                    ActiveDropdownMenuItem(
                                        label = label,
                                        selected = locale == selectedLocale.trim(),
                                        testTag = "${SEARCH_LANGUAGE_OPTION_TAG_PREFIX}_$locale",
                                        activeColor = colorScheme.primary,
                                        inactiveColor = colorScheme.textPrimary,
                                        onClick = {
                                            secondaryMenuExpanded = false
                                            onLocaleSelected(locale)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = onSearchSubmit,
                        enabled = !searchSubmitLocked,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag(submitButtonTestTag)
                    ) {
                        if (showSearchSpinner) {
                            EaraLogoLoadingIndicator(
                                size = 14.dp,
                                tint = colorScheme.primary,
                                modifier = Modifier
                                    .testTag(SEARCH_SUBMIT_SPINNER_TAG)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = if (!searchSubmitLocked) colorScheme.primary else colorScheme.textTertiary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { onSearchSubmit() }
            )
        )
        if (rightPanelToggle != null) {
            Spacer(modifier = Modifier.width(8.dp))
            rightPanelToggle(Modifier.size(50.dp))
        }
    }
}

@Composable
internal fun SearchPaginationHeader(
    page: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    controlsLocked: Boolean,
    onFirstPage: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val canGoFirst = canGoPrev
    val paginationContainerColor = lerp(
        colorScheme.surface,
        colorScheme.primarySoft,
        if (isDark) 0.06f else 0.10f
    ).copy(alpha = if (isDark) 0.93f else 0.95f)
        .compositeOver(colorScheme.background)
    val paginationBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.14f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SearchPageHorizontalPadding, vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDark) 10.dp else 6.dp,
                    shape = RoundedCornerShape(12.dp),
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f),
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f)
                )
                .then(
                    Modifier.border(
                        width = 1.dp,
                        color = paginationBorderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                )
                .clip(RoundedCornerShape(12.dp))
                .background(paginationContainerColor)
                .testTag(SEARCH_PAGINATION_TAG)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .consumeTapThrough()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SearchPaginationIconButton(
                            onClick = onFirstPage,
                            enabled = canGoFirst && !controlsLocked,
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "回到第一页",
                            modifier = Modifier.testTag(SEARCH_FIRST_PAGE_BUTTON_TAG)
                        )
                        SearchPaginationIconButton(
                            onClick = onPrev,
                            enabled = canGoPrev && !controlsLocked,
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "上一页",
                            modifier = Modifier.testTag(SEARCH_PREV_BUTTON_TAG)
                        )
                    }
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "第 ${page.coerceAtLeast(1)} 页",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) colorScheme.textPrimary else Color.Black
                    )
                }

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    SearchPaginationIconButton(
                        onClick = onNext,
                        enabled = canGoNext && !controlsLocked,
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "下一页",
                        modifier = Modifier.testTag(SEARCH_NEXT_BUTTON_TAG)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPaginationIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = AsmrTheme.colorScheme
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (enabled) colorScheme.primary else colorScheme.textTertiary,
            modifier = Modifier.size(17.dp)
        )
    }
}
