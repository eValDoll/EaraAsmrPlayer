package com.asmr.player.ui.search

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.clearFocusOnTapOutside
import com.asmr.player.ui.common.rememberCollapsibleHeaderState
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.DlsiteAntiHotlink

internal const val SEARCH_ASSIST_INPUT_TAG = "search_assist_input"
internal const val SEARCH_ASSIST_SUBMIT_TAG = "search_assist_submit"
internal const val SEARCH_ASSIST_CLEAR_TAG = "search_assist_clear"
internal const val SEARCH_ASSIST_HISTORY_CLEAR_TAG = "search_assist_history_clear"
internal const val SEARCH_ASSIST_HOT_WORK_CARD_TAG = "search_assist_hot_work_card"
internal const val SEARCH_ASSIST_FULL_RANKING_TAG = "search_assist_full_ranking"
internal const val SEARCH_ASSIST_CHROME_TAG = "search_assist_chrome"
private const val SearchAssistCollapsedRows = 2
private val SearchAssistChromeContentGap = 8.dp

@Composable
fun SearchAssistScreen(
    windowSizeClass: WindowSizeClass,
    initialRequest: SearchAssistSearchRequest,
    onSubmitSearch: (SearchAssistSearchRequest) -> Unit,
    onOpenFullRanking: () -> Unit,
    viewModel: SearchAssistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        contentColor = AsmrTheme.colorScheme.onBackground
    ) {
        SearchAssistContent(
            windowSizeClass = windowSizeClass,
            initialRequest = initialRequest,
            uiState = uiState,
            onSubmitSearch = { request -> viewModel.submitSearch(request, onSubmitSearch) },
            onClearHistory = viewModel::clearHistory,
            onOpenFullRanking = onOpenFullRanking
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
    onOpenFullRanking: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
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
    val selectedOrder = remember(selectedOrderName) {
        SearchSortOption.values().firstOrNull { it.name == selectedOrderName } ?: SearchSortOption.Trend
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
    val animatedChromeOffsetPx by animateFloatAsState(
        targetValue = chromeState.offsetPx,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "searchAssistChromeOffset"
    )
    val chromeReservedHeightPx = if (chromeState.heightPx > 0f) {
        chromeState.heightPx
    } else {
        with(density) { 64.dp.toPx() }
    }
    val topPadding = with(density) { chromeReservedHeightPx.toDp() } + SearchAssistChromeContentGap

    fun submit(value: String = keyword) {
        val normalized = value.trim()
            .ifBlank { hotKeywordCarouselItem.keyword.orEmpty() }
        if (normalized.isBlank()) return
        keyword = normalized
        keyboardController?.hide()
        onSubmitSearch(
            SearchAssistSearchRequest(
                keyword = normalized,
                orderName = selectedOrder.name,
                purchasedOnly = purchasedOnly,
                presaleOnly = presaleOnly,
                chineseTranslatedOnly = chineseTranslatedOnly,
                collectedOnly = collectedOnly,
                locale = selectedLocale
            )
        )
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
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

            if (uiState.suggestions.hotCvs.isNotEmpty()) {
                item(contentType = "hotCvs") {
                    SearchAssistTermSection(
                        title = "热门 CV",
                        terms = uiState.suggestions.hotCvs,
                        expanded = hotCvsExpanded,
                        onExpandedChange = { hotCvsExpanded = it },
                        onTermClick = { submit(it) }
                    )
                }
            }

            if (uiState.suggestions.hotTags.isNotEmpty()) {
                item(contentType = "hotTags") {
                    SearchAssistTermSection(
                        title = "热门 tags",
                        terms = uiState.suggestions.hotTags,
                        expanded = hotTagsExpanded,
                        onExpandedChange = { hotTagsExpanded = it },
                        onTermClick = { submit(it) }
                    )
                }
            }

            if (uiState.suggestions.hotWorks.isNotEmpty()) {
                item(contentType = "hotWorks") {
                    SearchAssistSection(
                        title = "热门作品",
                        trailing = {
                            TextButton(
                                onClick = onOpenFullRanking,
                                modifier = Modifier.testTag(SEARCH_ASSIST_FULL_RANKING_TAG)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Leaderboard,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("查看完整榜单")
                            }
                        }
                    ) {
                        val rows = uiState.suggestions.hotWorks.chunked(2)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { work ->
                                        SearchAssistWorkChip(
                                            work = work,
                                            modifier = Modifier.weight(1f),
                                            compact = isCompact,
                                            onClick = {
                                                val rj = work.album.rjCode.ifBlank { work.album.workId }
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
            } else if (uiState.isLoadingSuggestions) {
                item(contentType = "loadingSuggestions") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EaraLogoLoadingIndicator(size = 28.dp, tint = colorScheme.primary)
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
            animatedOffsetPx = animatedChromeOffsetPx,
            collapseFraction = chromeState.collapseFraction,
            chromeTestTag = SEARCH_ASSIST_CHROME_TAG,
            inputTestTag = SEARCH_ASSIST_INPUT_TAG,
            clearButtonTestTag = SEARCH_ASSIST_CLEAR_TAG,
            submitButtonTestTag = SEARCH_ASSIST_SUBMIT_TAG,
            inputFocusRequester = inputFocusRequester,
            onMeasured = { size -> chromeState.updateHeight(size.height.toFloat()) },
            onSearchSubmit = { submit() },
            onFilterSelected = { option ->
                val nextOrder = option.sortOption ?: selectedOrder
                selectedOrderName = nextOrder.name
                purchasedOnly = option.isPurchasedOnly
                presaleOnly = option.isPresaleOnly
                chineseTranslatedOnly = option.isChineseTranslated
                collectedOnly = option.isCollectedOnly
            },
            onLocaleSelected = { locale -> selectedLocale = locale },
            onFirstPage = {},
            onPrev = {},
            onNext = {}
        )

        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("清空历史搜索") },
                text = { Text("是否清空历史搜索记录？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearHistoryDialog = false
                            onClearHistory()
                        }
                    ) {
                        Text("清空")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearHistoryDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
private fun SearchAssistSection(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
    onTermClick: (String) -> Unit
) {
    SearchAssistSection(
        title = title,
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
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
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
    SearchAssistChipFlow(expanded = expanded) {
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
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val horizontalSpacingPx = 8.dp.roundToPx()
        val verticalSpacingPx = 6.dp.roundToPx()
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
private fun SearchAssistWorkChip(
    work: SearchAssistHotWork,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val album = work.album
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
    val meta = listOf(album.cv, album.circle)
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()

    Row(
        modifier = modifier
            .height(if (compact) 76.dp else 84.dp)
            .clip(shape)
            .background(containerColor)
            .border(
                width = 1.dp,
                color = if (colorScheme.isDark) Color.White.copy(alpha = 0.10f) else colorScheme.primaryStrong.copy(alpha = 0.10f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .testTag(SEARCH_ASSIST_HOT_WORK_CARD_TAG),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsmrAsyncImage(
            model = imageModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = 0,
            modifier = Modifier
                .aspectRatio(1f)
                .height(if (compact) 76.dp else 84.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = album.title.ifBlank { "热门作品" },
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AssistMutedText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = AsmrTheme.colorScheme.textTertiary
    )
}
