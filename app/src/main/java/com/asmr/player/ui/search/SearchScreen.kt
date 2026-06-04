package com.asmr.player.ui.search

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items as lazyItems
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.common.CustomSearchBar
import com.asmr.player.ui.common.EaraBrandedEmptyState
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.collapsibleHeaderUiState
import com.asmr.player.ui.common.rememberCollapsibleHeaderState
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.library.AlbumGridItem
import com.asmr.player.ui.library.AlbumGridItemSpacing
import com.asmr.player.ui.library.AlbumItem
import com.asmr.player.ui.library.rememberAlbumMetaCopyAction
import com.asmr.player.ui.sidepanel.LandscapeRightPanelHost
import com.asmr.player.ui.sidepanel.RecentAlbumsPanel
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

internal const val SEARCH_INPUT_TAG = "search_input"
internal const val SEARCH_SCOPE_BUTTON_TAG = "search_scope_button"
internal const val SEARCH_LANGUAGE_BUTTON_TAG = "search_language_button"
internal const val SEARCH_CLEAR_BUTTON_TAG = "search_clear_button"
internal const val SEARCH_SUBMIT_BUTTON_TAG = "search_submit_button"
internal const val SEARCH_SUBMIT_SPINNER_TAG = "search_submit_spinner"
internal const val SEARCH_FIRST_PAGE_BUTTON_TAG = "search_first_page_button"
internal const val SEARCH_PREV_BUTTON_TAG = "search_prev_button"
internal const val SEARCH_NEXT_BUTTON_TAG = "search_next_button"
internal const val SEARCH_PAGINATION_TAG = "search_pagination"
internal const val SEARCH_CHROME_TAG = "search_chrome"
private val SearchChromeContentGap = 16.dp
private const val SearchPullRefreshContentShiftRatio = 1f
private val SearchPullRefreshIndicatorSize = 40.dp
private val SearchPageHorizontalPadding = 8.dp

private fun stableAlbumKey(album: Album): String {
    val id = album.rjCode.ifBlank { album.workId }.trim()
    if (id.isNotEmpty()) return id
    val seed = "${album.coverUrl}|${album.title}|${album.circle}|${album.cv}"
    return "h${seed.hashCode().absoluteValue}"
}

private fun Modifier.consumeTapThrough(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { change -> change.consume() }
            } while (event.changes.any { it.pressed })
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    windowSizeClass: WindowSizeClass,
    onAlbumClick: (Album, Boolean) -> Unit,
    scrollToTopSignal: Long = 0L,
    viewModel: SearchViewModel = hiltViewModel()
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    var purchasedOnly by rememberSaveable { mutableStateOf(false) }
    var presaleOnly by rememberSaveable { mutableStateOf(false) }
    var chineseTranslatedOnly by rememberSaveable { mutableStateOf(false) }
    var collectedOnly by rememberSaveable { mutableStateOf(false) }
    var selectedLocale by rememberSaveable { mutableStateOf("ja_JP") }
    var selectedOrderName by rememberSaveable { mutableStateOf(SearchSortOption.Trend.name) }
    val selectedOrder = remember(selectedOrderName) {
        SearchSortOption.values().firstOrNull { it.name == selectedOrderName } ?: SearchSortOption.Trend
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
    val viewMode by viewModel.viewMode.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val success = uiState as? SearchUiState.Success
    val currentPageKey = success?.page ?: 0
    val listState = rememberSaveable(currentPageKey, saver = LazyListState.Saver) { LazyListState(0, 0) }
    val gridState = rememberSaveable(currentPageKey, saver = LazyStaggeredGridState.Saver) { LazyStaggeredGridState() }
    val colorScheme = AsmrTheme.colorScheme
    val copyMeta = rememberAlbumMetaCopyAction(viewModel.messageManager)
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val chromeState = rememberCollapsibleHeaderState()
    val chromeResetKey = remember(currentPageKey, viewMode) { "$currentPageKey:$viewMode" }
    var lastChromeResetKey by rememberSaveable { mutableStateOf(chromeResetKey) }

    var keywordSyncedFromState by rememberSaveable { mutableStateOf(false) }
    var optionsSyncedFromState by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.bootstrap(keyword, purchasedOnly, selectedLocale)
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
            selectedLocale = state.locale ?: "ja_JP"
            selectedOrderName = state.order.name
            optionsSyncedFromState = true
        }
    }

    val interactionLocked = success?.isBusy == true
    val filterControlsLocked = success == null || interactionLocked
    val searchSubmitLocked = uiState is SearchUiState.Loading || interactionLocked
    val showSearchSpinner = success?.isBusy == true
    val highlightedPage = success?.page ?: 1
    val canGoPrev = success?.canGoPrev == true && !success.isSearching
    val canGoNext = success?.canGoNext == true && !success.isSearching
    val animatedChromeOffsetPx by animateFloatAsState(
        targetValue = chromeState.offsetPx,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "searchChromeOffset"
    )
    val chromeReservedHeightPx = when {
        chromeState.heightPx > 0f -> chromeState.heightPx
        success != null -> with(androidx.compose.ui.platform.LocalDensity.current) { 120.dp.toPx() }
        else -> with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }
    }
    val topPadding = with(androidx.compose.ui.platform.LocalDensity.current) { chromeReservedHeightPx.toDp() } + SearchChromeContentGap

    fun scrollResultsToTop() {
        scope.launch {
            runCatching { listState.scrollToItem(0) }
            runCatching { gridState.scrollToItem(0) }
        }
    }

    fun submitSearch() {
        if (searchSubmitLocked) return
        keyboardController?.hide()
        viewModel.search(keyword)
        scrollResultsToTop()
    }

    val pullToRefreshState = rememberPullToRefreshState()
    val refreshGestureEnabled = !pullToRefreshState.isRefreshing
    val topPaddingPx = with(androidx.compose.ui.platform.LocalDensity.current) { topPadding.toPx() }
    val refreshIndicatorHoverOffsetPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        16.dp.toPx()
    }
    val pullContentOffsetTargetPx = (
        if (pullToRefreshState.isRefreshing) {
            0f
        } else {
            pullToRefreshState.verticalOffset * SearchPullRefreshContentShiftRatio
        }
        ).coerceIn(
        minimumValue = 0f,
        maximumValue = pullToRefreshState.positionalThreshold * SearchPullRefreshContentShiftRatio
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
    val pullIndicatorBaseOffsetPx =
        topPaddingPx +
            refreshIndicatorHoverOffsetPx +
            (pullContentOffsetPx / 2f)
    val latestKeyword by rememberUpdatedState(keyword)
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
    LaunchedEffect(chromeResetKey) {
        if (lastChromeResetKey != chromeResetKey) {
            chromeState.expand()
            lastChromeResetKey = chromeResetKey
        }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, viewMode) {
        if (viewMode == 0 && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
        }
    }
    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset, viewMode) {
        if (viewMode != 0 && gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
        }
    }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal == 0L) return@LaunchedEffect
        when (viewMode) {
            0 -> runCatching { listState.animateScrollToItem(0) }
            else -> runCatching { gridState.animateScrollToItem(0) }
        }
        chromeState.expand()
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
                                coverUrl = album.coverUrl,
                                coverPath = album.coverPath,
                                coverThumbPath = album.coverThumbPath,
                                workId = album.workId,
                                rjCode = album.rjCode,
                                description = album.description
                            ),
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
                Box(
                    modifier = if (isCompact || hasRightPanel) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxHeight()
                            .widthIn(max = 800.dp)
                            .fillMaxWidth()
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (refreshGestureEnabled) {
                                    Modifier.nestedScroll(pullToRefreshState.nestedScrollConnection)
                                } else {
                                    Modifier
                                }
                            )
                            .clipToBounds()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            when (val state = uiState) {
                            is SearchUiState.Loading -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
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
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .nestedScroll(chromeState.nestedScrollConnection)
                                            .thinScrollbar(listState),
                                        contentPadding = PaddingValues(top = topPadding, bottom = 8.dp)
                                            .withAddedBottomPadding(LocalBottomOverlayPadding.current)
                                    ) {
                                        lazyItems(
                                            items = state.results,
                                            key = { album -> stableAlbumKey(album) },
                                            contentType = { "album" }
                                        ) { album ->
                                            AlbumItem(
                                                album = album,
                                                onClick = { onAlbumClick(album, state.purchasedOnly) },
                                                emptyCoverUseShimmer = true,
                                                onRjClick = { copyMeta("RJ", it) },
                                                onCircleClick = { copyMeta("社团", it) },
                                                onCvClick = { copyMeta("CV", it) },
                                                onTagClick = { copyMeta("标签", it) },
                                            )
                                        }
                                    }
                                } else {
                                    LazyVerticalStaggeredGrid(
                                        columns = StaggeredGridCells.Adaptive(if (isCompact) 150.dp else 200.dp),
                                        state = gridState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .nestedScroll(chromeState.nestedScrollConnection)
                                            .thinScrollbar(gridState),
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
                                            key = { index -> stableAlbumKey(state.results[index]) },
                                            contentType = { "albumGrid" }
                                        ) { index ->
                                            val album = state.results[index]
                                            AlbumGridItem(
                                                album = album,
                                                onClick = { onAlbumClick(album, state.purchasedOnly) },
                                                emptyCoverUseShimmer = true,
                                                onRjClick = { copyMeta("RJ", it) },
                                                onCircleClick = { copyMeta("社团", it) },
                                                onCvClick = { copyMeta("CV", it) },
                                                onTagClick = { copyMeta("标签", it) },
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
                                /* modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = topPadding),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WifiOff,
                                    contentDescription = null,
                                    tint = colorScheme.textSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(92.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = "网络错误", color = colorScheme.textSecondary)
                                Spacer(modifier = Modifier.height(16.dp))
                                FilledTonalButton(
                                    onClick = { viewModel.retry() },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = colorScheme.primaryContainer,
                                        contentColor = colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Text("刷新")
                                }
                            } */

                            else -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {}
                            }
                        }

                        SearchPullRefreshIndicator(
                            progress = pullToRefreshState.progress,
                            isRefreshing = pullToRefreshState.isRefreshing,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .graphicsLayer { translationY = pullIndicatorBaseOffsetPx }
                                .then(
                                    if (pullToRefreshState.progress > 0 || pullToRefreshState.isRefreshing) {
                                        Modifier
                                    } else {
                                        Modifier.size(0.dp)
                                    }
                                )
                        )
                    }

                    SearchChrome(
                        modifier = Modifier.align(Alignment.TopCenter),
                        keyword = keyword,
                        onKeywordChange = { keyword = it },
                        selectedFilter = selectedFilter,
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
                        animatedOffsetPx = animatedChromeOffsetPx,
                        collapseFraction = chromeState.collapseFraction,
                        onMeasured = { size: IntSize -> chromeState.updateHeight(size.height.toFloat()) },
                        onSearchSubmit = ::submitSearch,
                        onFilterSelected = { option ->
                            val nextOrder = option.sortOption ?: selectedOrder
                            val accepted = viewModel.updateSearchOptions(
                                order = nextOrder,
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
                            }
                        },
                        onLocaleSelected = { locale ->
                            selectedLocale = locale
                            viewModel.updateSearchOptions(
                                order = selectedOrder,
                                purchasedOnly = purchasedOnly,
                                presaleOnly = presaleOnly,
                                chineseTranslatedOnly = chineseTranslatedOnly,
                                collectedOnly = collectedOnly,
                                locale = locale
                            )
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
                            scrollResultsToTop()
                            viewModel.nextPage()
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun SearchPullRefreshIndicator(
    progress: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val resolvedProgress = if (isRefreshing) 1f else progress.coerceIn(0f, 1f)
    val indicatorScale by animateFloatAsState(
        targetValue = 0.82f + resolvedProgress * 0.18f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "search_pull_refresh_scale"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = 0.48f + resolvedProgress * 0.52f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "search_pull_refresh_alpha"
    )
    val containerColor = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.92f else 0.98f)
    val borderColor = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        colorScheme.primary.copy(alpha = 0.16f)
    }

    Box(
        modifier = modifier
            .size(SearchPullRefreshIndicatorSize)
            .graphicsLayer(
                alpha = indicatorAlpha,
                scaleX = indicatorScale,
                scaleY = indicatorScale
            )
            .shadow(
                elevation = if (colorScheme.isDark) 12.dp else 8.dp,
                shape = CircleShape,
                spotColor = if (colorScheme.isDark) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.18f),
                ambientColor = if (colorScheme.isDark) Color.Black.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.18f)
            )
            .clip(CircleShape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        EaraLogoLoadingIndicator(
            size = 20.dp,
            tint = colorScheme.primary,
            glowColor = colorScheme.primarySoft,
            showGlow = isRefreshing || resolvedProgress > 0.45f
        )
    }
}

@Composable
internal fun SearchChrome(
    modifier: Modifier = Modifier,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    selectedFilter: SearchFilterOption,
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
    animatedOffsetPx: Float,
    collapseFraction: Float,
    onMeasured: (IntSize) -> Unit,
    onSearchSubmit: () -> Unit,
    onFilterSelected: (SearchFilterOption) -> Unit,
    onLocaleSelected: (String) -> Unit,
    onFirstPage: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = modifier
            .onSizeChanged(onMeasured)
            .graphicsLayer {
                translationY = animatedOffsetPx
                alpha = 1f - (collapseFraction.coerceIn(0f, 1f) * 0.1f)
            }
            .semantics { stateDescription = collapsibleHeaderUiState(collapseFraction) }
            .testTag(SEARCH_CHROME_TAG)
    ) {
        SearchToolbar(
            keyword = keyword,
            onKeywordChange = onKeywordChange,
            selectedFilter = selectedFilter,
            selectedLocale = selectedLocale,
            filterControlsLocked = filterControlsLocked,
            searchSubmitLocked = searchSubmitLocked,
            showSearchSpinner = showSearchSpinner,
            onSearchSubmit = onSearchSubmit,
            onFilterSelected = onFilterSelected,
            onLocaleSelected = onLocaleSelected,
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
    selectedFilter: SearchFilterOption,
    selectedLocale: String,
    filterControlsLocked: Boolean,
    searchSubmitLocked: Boolean,
    showSearchSpinner: Boolean,
    onSearchSubmit: () -> Unit,
    onFilterSelected: (SearchFilterOption) -> Unit,
    onLocaleSelected: (String) -> Unit,
    rightPanelToggle: (@Composable (Modifier) -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    var scopeMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val dropdownContainerColor = lerp(
        colorScheme.surface,
        colorScheme.primarySoft,
        if (colorScheme.isDark) 0.16f else 0.26f
    ).copy(alpha = if (colorScheme.isDark) 0.95f else 0.97f)
        .compositeOver(colorScheme.background)

    LaunchedEffect(filterControlsLocked, searchSubmitLocked) {
        if (filterControlsLocked || searchSubmitLocked) {
            scopeMenuExpanded = false
            languageMenuExpanded = false
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
            placeholder = "搜索专辑、社团、CV...",
            modifier = Modifier
                .weight(1f)
                .testTag(SEARCH_INPUT_TAG),
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
                            onClick = { onKeywordChange("") },
                            enabled = !searchSubmitLocked,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag(SEARCH_CLEAR_BUTTON_TAG)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    val languageLabel = when (selectedLocale.trim()) {
                        "zh_CN" -> "简中"
                        "zh_TW" -> "繁中"
                        else -> "日语"
                    }
                    Box {
                        TextButton(
                            onClick = { languageMenuExpanded = true },
                            enabled = !filterControlsLocked,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 1.dp, minHeight = 30.dp)
                                .height(30.dp)
                                .testTag(SEARCH_LANGUAGE_BUTTON_TAG),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colorScheme.primary
                            )
                        ) {
                            Text(languageLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false },
                            modifier = Modifier.background(dropdownContainerColor)
                        ) {
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
                                DropdownMenuItem(
                                    text = { Text(label, color = colorScheme.textPrimary) },
                                    onClick = {
                                        languageMenuExpanded = false
                                        onLocaleSelected(locale)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onSearchSubmit,
                        enabled = !searchSubmitLocked,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag(SEARCH_SUBMIT_BUTTON_TAG)
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
                                tint = colorScheme.primary,
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
