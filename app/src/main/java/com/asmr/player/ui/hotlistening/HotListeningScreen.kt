package com.asmr.player.ui.hotlistening

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.cache.LazyListPreloader
import com.asmr.player.cache.LazyStaggeredGridPreloader
import com.asmr.player.domain.model.Album
import com.asmr.player.hotlistening.HotListeningSortMode
import com.asmr.player.ui.common.EaraBrandedEmptyState
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.albumCoverImageModel
import com.asmr.player.ui.common.albumStableKey
import com.asmr.player.ui.common.interruptScrollableFlingOnPointerDown
import com.asmr.player.ui.common.lightweightVerticalStretchOverscroll
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.rememberSaveablePrefetchedLazyListState
import com.asmr.player.ui.common.shouldFadeInCover
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.common.collectAsStateWhileActive
import com.asmr.player.ui.library.AlbumGridItem
import com.asmr.player.ui.library.AlbumGridItemSpacing
import com.asmr.player.ui.library.AlbumCoverBadge
import com.asmr.player.ui.library.AlbumItem
import com.asmr.player.ui.library.AlbumMetaActionDialog
import com.asmr.player.ui.library.rememberAlbumMetaCopyAction
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.theme.AsmrTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private fun hotListeningItemKey(section: String, album: Album): String {
    return "hot-listening:$section:${albumStableKey(album)}"
}

@Composable
private fun HotListeningPeriodTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = AsmrTheme.colorScheme
    val containerColor = if (selected) {
        colorScheme.primary.copy(alpha = 0.13f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.28f)
    }
    val labelColor = if (selected) {
        colorScheme.primary
    } else {
        colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    }

    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = labelColor,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HotListeningScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: State<Boolean>,
    onAlbumClick: (Album) -> Unit,
    onSearchKeyword: (String) -> Unit = {},
    scrollToTopSignal: Long = 0L,
    viewModel: HotListeningViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWhileActive(isDataActive)
    val viewMode by viewModel.viewMode.collectAsStateWhileActive(isDataActive)
    val selectedSortMode by viewModel.sortMode.collectAsStateWhileActive(isDataActive)
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWhileActive(isDataActive)
    val colorScheme = AsmrTheme.colorScheme
    val copyMeta = rememberAlbumMetaCopyAction(viewModel.messageManager)
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val searchBlockedKeywords by settingsViewModel.searchBlockedKeywords.collectAsStateWhileActive(isDataActive)
    val scope = rememberCoroutineScope()
    val isCompactWidth = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    var showBlockedEntries by rememberSaveable { mutableStateOf(false) }
    var scrollResetNonce by rememberSaveable { mutableIntStateOf(0) }
    var metaActionKeyword by rememberSaveable { mutableStateOf<String?>(null) }
    val contentScrollKey = remember(selectedPeriod, selectedSortMode, scrollResetNonce) {
        "hot-listening:$selectedPeriod:${selectedSortMode.name}:$scrollResetNonce"
    }
    val listState = rememberSaveablePrefetchedLazyListState(
        stateKey = contentScrollKey,
        forwardCompositionPrefetchCount = 4,
        backwardCompositionPrefetchCount = 4,
    )
    val gridState = rememberSaveable(contentScrollKey, saver = LazyStaggeredGridState.Saver) {
        LazyStaggeredGridState()
    }
    val periods = remember {
        listOf("day" to "过去一天", "week" to "过去一周", "month" to "过去一月")
    }

    fun stopActiveScroll() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            runCatching { listState.stopScroll(MutatePriority.UserInput) }
            runCatching { gridState.stopScroll(MutatePriority.UserInput) }
        }
    }

    fun requestScrollToTop() {
        scrollResetNonce += 1
        scope.launch {
            runCatching { listState.stopScroll(MutatePriority.PreventUserInput) }
            runCatching { gridState.stopScroll(MutatePriority.PreventUserInput) }
        }
    }

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

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal == 0L) return@LaunchedEffect
        requestScrollToTop()
    }
    LaunchedEffect(isActive, viewMode) {
        if (isActive) return@LaunchedEffect
        when (viewMode) {
            0 -> listState.stopScroll(MutatePriority.PreventUserInput)
            else -> gridState.stopScroll(MutatePriority.PreventUserInput)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .interruptScrollableFlingOnPointerDown { stopActiveScroll() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                periods.forEach { (period, label) ->
                    HotListeningPeriodTab(
                        label = label,
                        selected = selectedPeriod == period,
                        onClick = {
                            viewModel.selectPeriod(period)
                            requestScrollToTop()
                        },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val nextMode = selectedSortMode.nextMode
                        viewModel.selectSortMode(nextMode)
                        requestScrollToTop()
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedSortMode.toggleLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(14.dp)
                )
            }
        }

        when (val state = uiState) {
            is HotListeningUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EaraLogoLoadingIndicator(tint = colorScheme.primary)
            }

            is HotListeningUiState.Error -> EaraBrandedEmptyState(
                sectionTitle = "热门收听",
                headline = "数据加载失败",
                sectionIcon = Icons.Rounded.Whatshot,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current + 24.dp),
                footer = {
                    TextButton(onClick = { viewModel.refresh() }) {
                        Text("重试")
                    }
                }
            )

            is HotListeningUiState.Success -> {
                LaunchedEffect(state.period, state.sortMode) {
                    showBlockedEntries = false
                }

                if (state.entries.isEmpty() && state.blockedEntries.isEmpty()) {
                    EaraBrandedEmptyState(
                        sectionTitle = "热门收听",
                        headline = "暂无排行数据",
                        sectionIcon = Icons.Rounded.Whatshot,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current + 24.dp)
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
                    val listCoverFadeInState = remember(listState) {
                        derivedStateOf {
                            shouldFadeInCover(listState.isScrollInProgress)
                        }
                    }
                    LazyListPreloader(
                        state = listState,
                        itemCount = state.entries.size,
                        enabled = isActive,
                        preloadNext = 24,
                        preloadSize = preloadSize,
                        cacheManagerProvider = { cacheManager },
                        modelAt = { idx ->
                            state.entries.getOrNull(idx)?.album?.let { albumCoverImageModel(it) }
                        }
                    )
                    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .lightweightVerticalStretchOverscroll(
                                    isAtStart = { !listState.canScrollBackward },
                                    isAtEnd = { !listState.canScrollForward },
                                )
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .thinScrollbar(listState),
                                flingBehavior = rememberCalmScrollableFlingBehavior(),
                                contentPadding = PaddingValues(bottom = 8.dp)
                                    .withAddedBottomPadding(LocalBottomOverlayPadding.current)
                            ) {
                                lazyItemsIndexed(
                                    items = state.entries,
                                    key = { _, entry -> hotListeningItemKey("visible", entry.album) },
                                    contentType = { _, _ -> "album" }
                                ) { _, entry ->
                                    HotListeningListItem(
                                        entry = entry,
                                        onAlbumClick = onAlbumClick,
                                        onMetaLongClick = ::openMetaActions,
                                        coverFadeInState = listCoverFadeInState,
                                    )
                                }
                                if (state.blockedEntries.isNotEmpty()) {
                                    item(
                                        key = "blocked-footer",
                                        contentType = "blockedFooter"
                                    ) {
                                        BlockedHotListeningFooter(
                                            blockedCount = state.blockedEntries.size,
                                            expanded = showBlockedEntries,
                                            onToggle = { showBlockedEntries = !showBlockedEntries }
                                        )
                                    }
                                    if (showBlockedEntries) {
                                        lazyItemsIndexed(
                                            items = state.blockedEntries,
                                            key = { _, entry ->
                                                hotListeningItemKey("blocked", entry.album)
                                            },
                                            contentType = { _, _ -> "album" }
                                        ) { _, entry ->
                                            HotListeningListItem(
                                                entry = entry,
                                                onAlbumClick = onAlbumClick,
                                                onMetaLongClick = ::openMetaActions,
                                                coverFadeInState = listCoverFadeInState,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val adaptiveCellSize = if (isCompactWidth) 150.dp else 200.dp
                    val app = LocalContext.current.applicationContext
                    val cacheManager = remember(app) {
                        EntryPointAccessors.fromApplication(app, ImageCacheEntryPoint::class.java)
                            .imageCacheManager()
                    }
                    val density = LocalDensity.current
                    val gridCoverPx = remember(adaptiveCellSize, density) { with(density) { adaptiveCellSize.roundToPx() } }
                    val gridPreloadSize = remember(gridCoverPx) { IntSize(gridCoverPx, gridCoverPx) }
                    val gridCoverFadeInState = remember(gridState) {
                        derivedStateOf {
                            shouldFadeInCover(gridState.isScrollInProgress)
                        }
                    }
                    LazyStaggeredGridPreloader(
                        state = gridState,
                        itemCount = state.entries.size,
                        enabled = isActive,
                        preloadNext = 24,
                        preloadSize = gridPreloadSize,
                        cacheManagerProvider = { cacheManager },
                        modelAt = { idx ->
                            state.entries.getOrNull(idx)?.album?.let { albumCoverImageModel(it) }
                        }
                    )
                    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .lightweightVerticalStretchOverscroll(
                                    isAtStart = { !gridState.canScrollBackward },
                                    isAtEnd = { !gridState.canScrollForward },
                                )
                        ) {
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Adaptive(adaptiveCellSize),
                                state = gridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .thinScrollbar(gridState),
                                flingBehavior = rememberCalmScrollableFlingBehavior(),
                                contentPadding = PaddingValues(
                                    start = 8.dp,
                                    end = 8.dp,
                                    bottom = 16.dp
                                ).withAddedBottomPadding(LocalBottomOverlayPadding.current),
                                horizontalArrangement = Arrangement.spacedBy(AlbumGridItemSpacing),
                                verticalItemSpacing = AlbumGridItemSpacing
                            ) {
                                items(
                                    state.entries.size,
                                    key = { index -> hotListeningItemKey("visible", state.entries[index].album) },
                                    contentType = { "albumGrid" }
                                ) { index ->
                                    HotListeningGridItem(
                                        entry = state.entries[index],
                                        onAlbumClick = onAlbumClick,
                                        onMetaLongClick = ::openMetaActions,
                                        coverFadeInState = gridCoverFadeInState,
                                    )
                                }
                                if (state.blockedEntries.isNotEmpty()) {
                                    item(
                                        key = "blocked-footer",
                                        contentType = "blockedFooter",
                                        span = StaggeredGridItemSpan.FullLine
                                    ) {
                                        BlockedHotListeningFooter(
                                            blockedCount = state.blockedEntries.size,
                                            expanded = showBlockedEntries,
                                            onToggle = { showBlockedEntries = !showBlockedEntries }
                                        )
                                    }
                                    if (showBlockedEntries) {
                                        items(
                                            state.blockedEntries.size,
                                            key = { index ->
                                                hotListeningItemKey(
                                                    "blocked",
                                                    state.blockedEntries[index].album
                                                )
                                            },
                                            contentType = { "albumGrid" }
                                        ) { index ->
                                            HotListeningGridItem(
                                                entry = state.blockedEntries[index],
                                                onAlbumClick = onAlbumClick,
                                                onMetaLongClick = ::openMetaActions,
                                                coverFadeInState = gridCoverFadeInState,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    metaActionKeyword?.let { keyword ->
        val playlistsViewModel: PlaylistsViewModel = hiltViewModel()
        val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel()
        AlbumMetaActionDialog(
            keyword = keyword,
            onDismissRequest = { metaActionKeyword = null },
            onSearch = onSearchKeyword,
            onCreatePlaylist = playlistsViewModel::createPlaylist,
            onCreateGroup = albumGroupsViewModel::createGroup,
            onAddBlockedKeyword = ::addMetaBlockedKeyword,
            onCopy = { copyMeta("内容", it) },
        )
    }
}

@Composable
private fun HotListeningListItem(
    entry: HotListeningEntry,
    onAlbumClick: (Album) -> Unit,
    onMetaLongClick: (String) -> Unit,
    coverFadeInState: State<Boolean>? = null,
) {
    val album = entry.album
    val coverBadge = remember(entry) { entry.toCoverBadge() }
    AlbumItem(
        album = album,
        onClick = { onAlbumClick(album) },
        coverRetainPainterDuringReload = true,
        coverBadge = coverBadge,
        animateOnlineDetails = false,
        coverFadeInState = coverFadeInState,
        cacheRenderLayer = true,
        onRjLongClick = onMetaLongClick,
        onCircleLongClick = onMetaLongClick,
        onCvLongClick = onMetaLongClick,
        onTagLongClick = onMetaLongClick,
    )
}

@Composable
private fun HotListeningGridItem(
    entry: HotListeningEntry,
    onAlbumClick: (Album) -> Unit,
    onMetaLongClick: (String) -> Unit,
    coverFadeInState: State<Boolean>? = null,
) {
    val album = entry.album
    val coverBadge = remember(entry) { entry.toCoverBadge() }
    AlbumGridItem(
        album = album,
        onClick = { onAlbumClick(album) },
        coverRetainPainterDuringReload = true,
        coverBadge = coverBadge,
        animateOnlineDetails = false,
        coverFadeInState = coverFadeInState,
        onRjLongClick = onMetaLongClick,
        onCircleLongClick = onMetaLongClick,
        onCvLongClick = onMetaLongClick,
        onTagLongClick = onMetaLongClick,
    )
}

@Composable
private fun BlockedHotListeningFooter(
    blockedCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$blockedCount 个作品被屏蔽",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = colorScheme.textSecondary
        )
        TextButton(onClick = onToggle) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 2.dp)
                    .size(18.dp)
            )
            Text(if (expanded) "折叠" else "展开")
        }
    }
}

private fun HotListeningEntry.toCoverBadge(): AlbumCoverBadge {
    val icon = when (sortMode) {
        HotListeningSortMode.PlayCount -> Icons.Rounded.PlayArrow
        HotListeningSortMode.ListenDuration -> Icons.Rounded.AccessTime
    }
    return AlbumCoverBadge(
        icon = icon,
        text = metricLabel,
        showContainer = false,
        bottomScrim = true,
        compactOffset = true
    )
}

private val HotListeningSortMode.toggleLabel: String
    get() = when (this) {
        HotListeningSortMode.PlayCount -> "次数"
        HotListeningSortMode.ListenDuration -> "时长"
    }

private val HotListeningSortMode.nextMode: HotListeningSortMode
    get() = when (this) {
        HotListeningSortMode.PlayCount -> HotListeningSortMode.ListenDuration
        HotListeningSortMode.ListenDuration -> HotListeningSortMode.PlayCount
    }
