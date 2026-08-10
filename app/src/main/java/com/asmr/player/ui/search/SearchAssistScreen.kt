package com.asmr.player.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.hotlistening.SearchSuggestionTerm
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AsmrShimmerPlaceholder
import com.asmr.player.ui.common.NoImageLoadingIndicator
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.clearFocusOnTapOutside
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.rememberCollapsibleHeaderState
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.DlsiteAntiHotlink
import kotlinx.coroutines.flow.distinctUntilChanged

internal const val SEARCH_ASSIST_INPUT_TAG = "search_assist_input"
internal const val SEARCH_ASSIST_SUBMIT_TAG = "search_assist_submit"
internal const val SEARCH_ASSIST_CLEAR_TAG = "search_assist_clear"
internal const val SEARCH_ASSIST_HISTORY_CLEAR_TAG = "search_assist_history_clear"
internal const val SEARCH_ASSIST_RECOMMENDATION_CARD_TAG = "search_assist_recommendation_card"
internal const val SEARCH_ASSIST_RECOMMENDATION_REFRESH_TAG = "search_assist_recommendation_refresh"
internal const val SEARCH_ASSIST_CHROME_TAG = "search_assist_chrome"
internal const val SEARCH_ASSIST_HOT_CVS_SECTION_TAG = "search_assist_hot_cvs_section"
internal const val SEARCH_ASSIST_HOT_TAGS_SECTION_TAG = "search_assist_hot_tags_section"
internal const val SEARCH_ASSIST_RECOMMENDATION_SECTION_TAG = "search_assist_recommendation_section"
private const val SearchAssistCollapsedRows = 2
private const val SearchAssistRecommendationColumns = 2
private const val SearchAssistRecommendationRows =
    (SEARCH_ASSIST_RECOMMENDATION_DISPLAY_LIMIT + SearchAssistRecommendationColumns - 1) /
        SearchAssistRecommendationColumns
private val SearchAssistChromeContentGap = 8.dp
private val SearchAssistSectionHeaderHeight = 32.dp
private val SearchAssistChipHeight = 30.dp
private val SearchAssistChipVerticalSpacing = 6.dp
private val SearchAssistCollapsedChipFlowHeight =
    SearchAssistChipHeight * SearchAssistCollapsedRows +
        SearchAssistChipVerticalSpacing * (SearchAssistCollapsedRows - 1)
private val SearchAssistWorkHeightCompact = 76.dp
private val SearchAssistWorkHeightExpanded = 84.dp
private val SearchAssistWorkRowSpacing = 8.dp

@Composable
fun SearchAssistScreen(
    windowSizeClass: WindowSizeClass,
    initialRequest: SearchAssistSearchRequest,
    onSubmitSearch: (SearchAssistSearchRequest) -> Unit,
    viewModel: SearchAssistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AsmrTheme.colorScheme.background,
        contentColor = AsmrTheme.colorScheme.onBackground
    ) {
        SearchAssistContent(
            windowSizeClass = windowSizeClass,
            initialRequest = initialRequest,
            uiState = uiState,
            onSubmitSearch = { request -> viewModel.submitSearch(request, onSubmitSearch) },
            onClearHistory = viewModel::clearHistory,
            onRefreshRecommendations = viewModel::refreshRecommendations
        )
    }
}

@Composable
internal fun SearchAssistContent(
    windowSizeClass: WindowSizeClass,
    initialRequest: SearchAssistSearchRequest,
    uiState: SearchAssistUiState,
    onSubmitSearch: (SearchAssistSearchRequest) -> Unit,
    onClearHistory: () -> Unit,
    onRefreshRecommendations: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }
    var keyword by rememberSaveable(initialRequest.keyword) { mutableStateOf(initialRequest.keyword) }
    var purchasedOnly by rememberSaveable(initialRequest.purchasedOnly) {
        mutableStateOf(initialRequest.purchasedOnly)
    }
    var presaleOnly by rememberSaveable(initialRequest.presaleOnly) {
        mutableStateOf(initialRequest.presaleOnly)
    }
    var chineseTranslatedOnly by rememberSaveable(initialRequest.chineseTranslatedOnly) {
        mutableStateOf(initialRequest.chineseTranslatedOnly)
    }
    var collectedOnly by rememberSaveable(initialRequest.collectedOnly) {
        mutableStateOf(initialRequest.collectedOnly)
    }
    var selectedLocale by rememberSaveable(initialRequest.locale) { mutableStateOf(initialRequest.locale) }
    var selectedOrderName by rememberSaveable(initialRequest.orderName) {
        mutableStateOf(initialRequest.orderName)
    }
    var selectedCollectedSortName by rememberSaveable(initialRequest.collectedSortName) {
        mutableStateOf(initialRequest.collectedSortName)
    }
    val selectedOrder = remember(selectedOrderName) {
        SearchSortOption.values().firstOrNull { it.name == selectedOrderName } ?: SearchSortOption.Trend
    }
    val selectedCollectedSort = remember(selectedCollectedSortName) {
        SearchCollectedSortOption.fromName(selectedCollectedSortName)
    }
    val selectedFilter = remember(
        selectedOrderName,
        purchasedOnly,
        presaleOnly,
        chineseTranslatedOnly,
        collectedOnly
    ) {
        SearchFilterOption.fromState(
            order = selectedOrder,
            purchasedOnly = purchasedOnly,
            presaleOnly = presaleOnly,
            chineseTranslatedOnly = chineseTranslatedOnly,
            collectedOnly = collectedOnly
        )
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var hotCvsExpanded by rememberSaveable { mutableStateOf(false) }
    var hotTagsExpanded by rememberSaveable { mutableStateOf(false) }
    val hotKeywordTerms = remember(uiState.suggestions.hotCvs, uiState.suggestions.hotTags) {
        buildSearchHotKeywordTerms(
            hotCvs = uiState.suggestions.hotCvs,
            hotTags = uiState.suggestions.hotTags
        )
    }
    val hotKeywordCarouselItem = rememberSearchHotKeywordCarouselItem(
        terms = hotKeywordTerms,
        showFallback = !uiState.isLoadingSuggestions
    )
    val chromeState = rememberCollapsibleHeaderState()
    val density = LocalDensity.current
    val chromeReservedHeightPx = if (chromeState.heightPx > 0f) {
        chromeState.heightPx
    } else {
        with(density) { 64.dp.toPx() }
    }
    val topPadding = with(density) { chromeReservedHeightPx.toDp() } + SearchAssistChromeContentGap

    fun buildRequest(
        requestKeyword: String,
        order: SearchSortOption = selectedOrder,
        purchasedOnlyValue: Boolean = purchasedOnly,
        presaleOnlyValue: Boolean = presaleOnly,
        chineseTranslatedOnlyValue: Boolean = chineseTranslatedOnly,
        collectedOnlyValue: Boolean = collectedOnly,
        collectedSort: SearchCollectedSortOption = selectedCollectedSort,
        locale: String = selectedLocale
    ) = SearchAssistSearchRequest(
        keyword = requestKeyword,
        orderName = order.name,
        purchasedOnly = purchasedOnlyValue,
        presaleOnly = presaleOnlyValue,
        chineseTranslatedOnly = chineseTranslatedOnlyValue,
        collectedOnly = collectedOnlyValue,
        collectedSortName = collectedSort.name,
        locale = locale
    )

    fun submit(value: String = keyword) {
        val normalized = value.trim()
            .ifBlank { hotKeywordCarouselItem.keyword.orEmpty() }
        if (normalized.isBlank()) return
        keyword = normalized
        keyboardController?.hide()
        onSubmitSearch(buildRequest(requestKeyword = normalized))
    }

    fun submitFilter(option: SearchFilterOption) {
        val nextOrder = option.sortOption ?: selectedOrder
        val nextKeyword = keyword.trim()
        val nextPurchasedOnly = option.isPurchasedOnly
        val nextPresaleOnly = option.isPresaleOnly
        val nextChineseTranslatedOnly = option.isChineseTranslated
        val nextCollectedOnly = option.isCollectedOnly

        keyword = nextKeyword
        selectedOrderName = nextOrder.name
        purchasedOnly = nextPurchasedOnly
        presaleOnly = nextPresaleOnly
        chineseTranslatedOnly = nextChineseTranslatedOnly
        collectedOnly = nextCollectedOnly
        if (nextCollectedOnly) {
            selectedCollectedSortName = selectedCollectedSort.name
        }
        keyboardController?.hide()
        onSubmitSearch(
            buildRequest(
                requestKeyword = nextKeyword,
                order = nextOrder,
                purchasedOnlyValue = nextPurchasedOnly,
                presaleOnlyValue = nextPresaleOnly,
                chineseTranslatedOnlyValue = nextChineseTranslatedOnly,
                collectedOnlyValue = nextCollectedOnly
            )
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
            .distinctUntilChanged()
            .collect { atTop ->
                if (atTop) chromeState.expand()
            }
    }

    LaunchedEffect(inputFocusRequester) {
        withFrameNanos { }
        inputFocusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .clearFocusOnTapOutside()
                .nestedScroll(chromeState.nestedScrollConnection)
                .thinScrollbar(listState),
            flingBehavior = rememberCalmScrollableFlingBehavior(),
            contentPadding = PaddingValues(
                top = topPadding,
                bottom = 24.dp
            ).withAddedBottomPadding(LocalBottomOverlayPadding.current),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(contentType = "history") {
                SearchAssistSection(
                    title = "搜索历史",
                    trailing = {
                        if (uiState.history.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HeaderIconButton(
                                    icon = Icons.Rounded.Delete,
                                    contentDescription = "清空搜索历史",
                                    onClick = { showClearHistoryDialog = true },
                                    modifier = Modifier.testTag(SEARCH_ASSIST_HISTORY_CLEAR_TAG)
                                )
                                ExpandCollapseIconButton(
                                    expanded = historyExpanded,
                                    onClick = { historyExpanded = !historyExpanded }
                                )
                            }
                        }
                    }
                ) {
                    if (uiState.history.isEmpty()) {
                        AssistMutedText("还没有搜索记录")
                    } else {
                        SearchAssistHistoryChips(
                            history = uiState.history,
                            expanded = historyExpanded,
                            onHistoryClick = { submit(it) }
                        )
                    }
                }
            }

            if (uiState.isLoadingSuggestions) {
                item(contentType = "hotCvsLoading") {
                    SearchAssistTermSectionSkeleton(
                        title = "热门声优",
                        modifier = Modifier.testTag(SEARCH_ASSIST_HOT_CVS_SECTION_TAG)
                    )
                }
                item(contentType = "hotTagsLoading") {
                    SearchAssistTermSectionSkeleton(
                        title = "热门标签",
                        modifier = Modifier.testTag(SEARCH_ASSIST_HOT_TAGS_SECTION_TAG)
                    )
                }
            } else {
                if (uiState.suggestions.hotCvs.isNotEmpty()) {
                    item(contentType = "hotCvs") {
                        SearchAssistTermSection(
                            title = "热门声优",
                            terms = uiState.suggestions.hotCvs,
                            expanded = hotCvsExpanded,
                            onExpandedChange = { hotCvsExpanded = it },
                            onTermClick = { submit(it) },
                            modifier = Modifier.testTag(SEARCH_ASSIST_HOT_CVS_SECTION_TAG)
                        )
                    }
                }
                if (uiState.suggestions.hotTags.isNotEmpty()) {
                    item(contentType = "hotTags") {
                        SearchAssistTermSection(
                            title = "热门标签",
                            terms = uiState.suggestions.hotTags,
                            expanded = hotTagsExpanded,
                            onExpandedChange = { hotTagsExpanded = it },
                            onTermClick = { submit(it) },
                            modifier = Modifier.testTag(SEARCH_ASSIST_HOT_TAGS_SECTION_TAG)
                        )
                    }
                }
            }

            if (uiState.isLoadingRecommendations && uiState.suggestions.recommendations.isEmpty()) {
                item(contentType = "recommendationsLoading") {
                    SearchAssistWorkSectionSkeleton(
                        title = "猜你喜欢",
                        compact = isCompact,
                        modifier = Modifier.testTag(SEARCH_ASSIST_RECOMMENDATION_SECTION_TAG)
                    )
                }
            } else if (uiState.suggestions.recommendations.isNotEmpty()) {
                item(contentType = "recommendations") {
                    SearchAssistSection(
                        title = "猜你喜欢",
                        modifier = Modifier.testTag(SEARCH_ASSIST_RECOMMENDATION_SECTION_TAG),
                        trailing = {
                            TextButton(
                                onClick = onRefreshRecommendations,
                                enabled = !uiState.isLoadingRecommendations &&
                                    uiState.hasMoreRecommendations,
                                modifier = Modifier
                                    .height(SearchAssistSectionHeaderHeight)
                                    .testTag(SEARCH_ASSIST_RECOMMENDATION_REFRESH_TAG),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    when {
                                        uiState.isLoadingRecommendations -> "推荐中"
                                        uiState.hasMoreRecommendations -> "换一批"
                                        else -> "已看完"
                                    }
                                )
                            }
                        }
                    ) {
                        val rows = uiState.suggestions.recommendations
                            .chunked(SearchAssistRecommendationColumns)
                        Column(
                            modifier = Modifier.height(searchAssistRecommendationGridHeight(isCompact)),
                            verticalArrangement = Arrangement.spacedBy(SearchAssistWorkRowSpacing)
                        ) {
                            rows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(SearchAssistWorkRowSpacing)
                                ) {
                                    row.forEach { recommendation ->
                                        SearchAssistRecommendationCard(
                                            recommendation = recommendation,
                                            modifier = Modifier.weight(1f),
                                            compact = isCompact,
                                            onClick = {
                                                val album = recommendation.album
                                                val rj = album.rjCode.ifBlank { album.workId }
                                                submit(rj)
                                            }
                                        )
                                    }
                                    if (row.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SearchChrome(
            modifier = Modifier.align(Alignment.TopCenter),
            keyword = keyword,
            onKeywordChange = { keyword = it },
            placeholder = hotKeywordCarouselItem.placeholder,
            selectedFilter = selectedFilter,
            selectedCollectedSort = selectedCollectedSort,
            selectedLocale = selectedLocale,
            filterControlsLocked = false,
            searchSubmitLocked = false,
            showSearchSpinner = false,
            showPagination = false,
            page = 1,
            canGoPrev = false,
            canGoNext = false,
            controlsLocked = false,
            rightPanelToggle = null,
            chromeState = chromeState,
            chromeTestTag = SEARCH_ASSIST_CHROME_TAG,
            inputTestTag = SEARCH_ASSIST_INPUT_TAG,
            clearButtonTestTag = SEARCH_ASSIST_CLEAR_TAG,
            submitButtonTestTag = SEARCH_ASSIST_SUBMIT_TAG,
            inputFocusRequester = inputFocusRequester,
            onMeasured = { size -> chromeState.updateHeight(size.height.toFloat()) },
            onSearchSubmit = { submit() },
            onFilterSelected = ::submitFilter,
            onLocaleSelected = { locale -> selectedLocale = locale },
            onCollectedSortSelected = { sort -> selectedCollectedSortName = sort.name },
            onFirstPage = {},
            onPrev = {},
            onNext = {}
        )

        if (showClearHistoryDialog) {
            FlatActionDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                message = "是否清空历史搜索记录？",
                actions = listOf(
                    FlatDialogAction("取消", onClick = { showClearHistoryDialog = false }),
                    FlatDialogAction(
                        text = "清空",
                        tone = FlatDialogActionTone.Danger,
                        onClick = {
                            showClearHistoryDialog = false
                            onClearHistory()
                        }
                    )
                )
            )
        }
    }
}

@Composable
private fun SearchAssistTermSectionSkeleton(
    title: String,
    modifier: Modifier = Modifier
) {
    SearchAssistSection(title = title, modifier = modifier) {
        SearchAssistChipFlow(
            expanded = false,
            reserveCollapsedHeight = true
        ) {
            listOf(0.26f, 0.22f, 0.30f, 0.18f, 0.24f, 0.28f).forEach { widthFraction ->
                SearchAssistTextChipSkeleton(widthFraction = widthFraction)
            }
        }
    }
}

@Composable
private fun SearchAssistWorkSectionSkeleton(
    title: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    SearchAssistSection(title = title, modifier = modifier) {
        Column(
            modifier = Modifier.height(searchAssistRecommendationGridHeight(compact)),
            verticalArrangement = Arrangement.spacedBy(SearchAssistWorkRowSpacing)
        ) {
            repeat(SearchAssistRecommendationRows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SearchAssistWorkRowSpacing)
                ) {
                    SearchAssistWorkChipSkeleton(
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                    SearchAssistWorkChipSkeleton(
                        compact = compact,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchAssistSection(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SearchAssistSectionHeaderHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AsmrTheme.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier.widthIn(min = 32.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                trailing?.invoke()
            }
        }
        content()
    }
}

@Composable
private fun SearchAssistTermSection(
    title: String,
    terms: List<SearchSuggestionTerm>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTermClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SearchAssistSection(
        title = title,
        modifier = modifier,
        trailing = {
            ExpandCollapseIconButton(
                expanded = expanded,
                onClick = { onExpandedChange(!expanded) }
            )
        }
    ) {
        SearchAssistTermChips(
            terms = terms,
            expanded = expanded,
            onTermClick = onTermClick
        )
    }
}

@Composable
private fun SearchAssistTextChipSkeleton(
    widthFraction: Float
) {
    val shape = RoundedCornerShape(7.dp)
    AsmrShimmerPlaceholder(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(SearchAssistChipHeight)
            .clip(shape),
        cornerRadius = 7
    )
}

@Composable
private fun SearchAssistTextChip(
    text: String,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = RoundedCornerShape(7.dp)
    val containerColor = lerp(
        colorScheme.surface,
        colorScheme.surfaceVariant,
        if (colorScheme.isDark) 0.22f else 0.34f
    ).copy(alpha = 0.96f).compositeOver(colorScheme.background)
    val borderColor = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    }
    Row(
        modifier = Modifier
            .widthIn(max = 220.dp)
            .height(SearchAssistChipHeight)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchAssistWorkChipSkeleton(
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val cardHeight = searchAssistRecommendationCardHeight(compact)
    Row(
        modifier = modifier
            .height(cardHeight)
            .clip(shape)
    ) {
        AsmrShimmerPlaceholder(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxHeight(),
            cornerRadius = 0
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AsmrShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(16.dp),
                cornerRadius = 6
            )
            AsmrShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(16.dp),
                cornerRadius = 6
            )
            Spacer(modifier = Modifier.weight(1f))
            AsmrShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxWidth(0.52f)
                    .height(12.dp),
                cornerRadius = 6
            )
        }
    }
}

@Composable
private fun SearchAssistHistoryChips(
    history: List<String>,
    expanded: Boolean,
    onHistoryClick: (String) -> Unit
) {
    SearchAssistChipFlow(expanded = expanded) {
        history.forEach { item ->
            SearchAssistTextChip(text = item, onClick = { onHistoryClick(item) })
        }
    }
}

@Composable
private fun SearchAssistTermChips(
    terms: List<SearchSuggestionTerm>,
    expanded: Boolean,
    onTermClick: (String) -> Unit
) {
    SearchAssistChipFlow(
        expanded = expanded,
        reserveCollapsedHeight = true
    ) {
        terms.forEach { term ->
            SearchAssistTextChip(
                text = term.value,
                onClick = { onTermClick(term.value) }
            )
        }
    }
}

@Composable
private fun SearchAssistChipFlow(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    reserveCollapsedHeight: Boolean = false,
    content: @Composable () -> Unit
) {
    val layoutModifier = if (!expanded && reserveCollapsedHeight) {
        modifier.height(SearchAssistCollapsedChipFlowHeight)
    } else {
        modifier
    }
    Layout(
        content = content,
        modifier = layoutModifier.fillMaxWidth()
    ) { measurables, constraints ->
        val horizontalSpacingPx = 8.dp.roundToPx()
        val verticalSpacingPx = SearchAssistChipVerticalSpacing.roundToPx()
        val maxWidth = constraints.maxWidth
        val placements = mutableListOf<SearchAssistChipPlacement>()
        var x = 0
        var y = 0
        var rowHeight = 0
        var row = 1

        measurables.forEach { measurable ->
            val placeable = measurable.measure(
                constraints.copy(minWidth = 0, minHeight = 0)
            )
            val nextX = if (x == 0) placeable.width else x + horizontalSpacingPx + placeable.width
            if (x > 0 && nextX > maxWidth) {
                x = 0
                y += rowHeight + verticalSpacingPx
                rowHeight = 0
                row += 1
            }
            val placeX = if (x == 0) 0 else x + horizontalSpacingPx
            if (expanded || row <= SearchAssistCollapsedRows) {
                placements += SearchAssistChipPlacement(placeable = placeable, x = placeX, y = y)
            }
            x = placeX + placeable.width
            rowHeight = maxOf(rowHeight, placeable.height)
        }

        val height = (placements.maxOfOrNull { it.y + it.placeable.height } ?: 0)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width = constraints.maxWidth, height = height) {
            placements.forEach { placement ->
                placement.placeable.placeRelative(placement.x, placement.y)
            }
        }
    }
}

private data class SearchAssistChipPlacement(
    val placeable: Placeable,
    val x: Int,
    val y: Int
)

@Composable
private fun ExpandCollapseIconButton(
    expanded: Boolean,
    onClick: () -> Unit
) {
    HeaderIconButton(
        icon = if (expanded) {
            Icons.Rounded.KeyboardArrowDown
        } else {
            Icons.AutoMirrored.Rounded.KeyboardArrowRight
        },
        contentDescription = if (expanded) "折叠" else "展开",
        onClick = onClick
    )
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SearchAssistRecommendationCard(
    recommendation: SearchAssistRecommendation,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val album = recommendation.album
    val shape = RoundedCornerShape(8.dp)
    val coverData = album.coverThumbPath.takeIf { it.isNotBlank() && it.contains("_v2") }
        .orEmpty()
        .ifBlank { album.coverPath }
        .ifBlank { album.coverUrl }
    val imageModel = remember(coverData) {
        val headers = if (coverData.startsWith("http", ignoreCase = true)) {
            DlsiteAntiHotlink.headersForImageUrl(coverData)
        } else {
            emptyMap()
        }
        if (headers.isEmpty()) coverData else CacheImageModel(data = coverData, headers = headers, keyTag = "dlsite")
    }
    val containerColor = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.72f else 0.82f)
        .compositeOver(colorScheme.background)
    val cv = album.cv.trim()

    Row(
        modifier = modifier
            .height(searchAssistRecommendationCardHeight(compact))
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = if (colorScheme.isDark) Color.White.copy(alpha = 0.10f) else colorScheme.primaryStrong.copy(alpha = 0.10f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .testTag(SEARCH_ASSIST_RECOMMENDATION_CARD_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsmrAsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = 0,
            loading = NoImageLoadingIndicator,
            modifier = Modifier
                .aspectRatio(1f)
                .height(searchAssistRecommendationCardHeight(compact))
                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = album.title.ifBlank { "推荐作品" },
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (cv.isNotBlank()) {
                Text(
                    text = cv,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun searchAssistRecommendationCardHeight(compact: Boolean) =
    if (compact) SearchAssistWorkHeightCompact else SearchAssistWorkHeightExpanded

private fun searchAssistRecommendationGridHeight(compact: Boolean) =
    searchAssistRecommendationCardHeight(compact) * SearchAssistRecommendationRows +
        SearchAssistWorkRowSpacing * (SearchAssistRecommendationRows - 1)

@Composable
private fun AssistMutedText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = AsmrTheme.colorScheme.textTertiary
    )
}
