package com.asmr.player.ui.hotlistening

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.domain.model.Album
import com.asmr.player.hotlistening.HotListeningSortMode
import com.asmr.player.ui.common.EaraBrandedEmptyState
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.smoothScrollToTop
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.library.AlbumGridItem
import com.asmr.player.ui.library.AlbumGridItemSpacing
import com.asmr.player.ui.library.AlbumCoverBadge
import com.asmr.player.ui.library.AlbumItem
import com.asmr.player.ui.library.rememberAlbumMetaCopyAction
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.math.absoluteValue

private fun stableAlbumKey(album: Album): String {
    val id = album.rjCode.ifBlank { album.workId }.trim()
    if (id.isNotEmpty()) return id
    val seed = "${album.coverUrl}|${album.title}|${album.circle}|${album.cv}"
    return "h${seed.hashCode().absoluteValue}"
}

private fun hotListeningItemKey(section: String, index: Int, album: Album): String {
    return "hot-listening:$section:${stableAlbumKey(album)}:$index"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotListeningScreen(
    windowSizeClass: WindowSizeClass,
    onAlbumClick: (Album) -> Unit,
    scrollToTopSignal: Long = 0L,
    viewModel: HotListeningViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val selectedSortMode by viewModel.sortMode.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val colorScheme = AsmrTheme.colorScheme
    val copyMeta = rememberAlbumMetaCopyAction(viewModel.messageManager)
    val scope = rememberCoroutineScope()
    val isCompactWidth = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    var showBlockedEntries by rememberSaveable { mutableStateOf(false) }

    val savedScrollPosition = viewModel.scrollPosition
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState(
            savedScrollPosition.listFirstVisibleItemIndex,
            savedScrollPosition.listFirstVisibleItemScrollOffset
        )
    }
    val gridState = rememberSaveable(saver = LazyStaggeredGridState.Saver) {
        LazyStaggeredGridState(
            savedScrollPosition.gridFirstVisibleItemIndex,
            savedScrollPosition.gridFirstVisibleItemScrollOffset
        )
    }

    val periods = listOf("day" to "过去一天", "week" to "过去一周", "month" to "过去一月")

    fun scrollToTop() {
        viewModel.resetScrollPosition()
        scope.launch {
            runCatching { listState.scrollToItem(0) }
            runCatching { gridState.scrollToItem(0) }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { scrolling -> !scrolling }
            .map { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                viewModel.updateListScrollPosition(index, offset)
            }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }
            .filter { scrolling -> !scrolling }
            .map { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                viewModel.updateGridScrollPosition(index, offset)
            }
    }

    DisposableEffect(listState, gridState, viewModel) {
        onDispose {
            viewModel.updateListScrollPosition(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
            viewModel.updateGridScrollPosition(
                gridState.firstVisibleItemIndex,
                gridState.firstVisibleItemScrollOffset
            )
        }
    }

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal == 0L) return@LaunchedEffect
        viewModel.resetScrollPosition()
        when (viewMode) {
            0 -> listState.smoothScrollToTop()
            else -> gridState.smoothScrollToTop()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                periods.forEach { (period, label) ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = {
                            viewModel.selectPeriod(period)
                            scrollToTop()
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = colorScheme.primary
                        )
                    )
                }
            }
            Box(modifier = Modifier.padding(start = 18.dp)) {
                Row(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.selectSortMode(selectedSortMode.nextMode)
                            scrollToTop()
                        }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedSortMode.toggleLabel,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .thinScrollbar(listState),
                        contentPadding = PaddingValues(bottom = 8.dp)
                            .withAddedBottomPadding(LocalBottomOverlayPadding.current)
                    ) {
                        lazyItemsIndexed(
                            items = state.entries,
                            key = { index, entry -> hotListeningItemKey("visible", index, entry.album) },
                            contentType = { _, _ -> "album" }
                        ) { _, entry ->
                            HotListeningListItem(
                                entry = entry,
                                onAlbumClick = onAlbumClick,
                                copyMeta = copyMeta
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
                                    key = { index, entry -> hotListeningItemKey("blocked", index, entry.album) },
                                    contentType = { _, _ -> "album" }
                                ) { _, entry ->
                                    HotListeningListItem(
                                        entry = entry,
                                        onAlbumClick = onAlbumClick,
                                        copyMeta = copyMeta
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val adaptiveCellSize = if (isCompactWidth) 150.dp else 200.dp
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(adaptiveCellSize),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxSize()
                            .thinScrollbar(gridState),
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
                            key = { index -> hotListeningItemKey("visible", index, state.entries[index].album) },
                            contentType = { "albumGrid" }
                        ) { index ->
                            HotListeningGridItem(
                                entry = state.entries[index],
                                onAlbumClick = onAlbumClick,
                                copyMeta = copyMeta
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
                                        hotListeningItemKey("blocked", index, state.blockedEntries[index].album)
                                    },
                                    contentType = { "albumGrid" }
                                ) { index ->
                                    HotListeningGridItem(
                                        entry = state.blockedEntries[index],
                                        onAlbumClick = onAlbumClick,
                                        copyMeta = copyMeta
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

@Composable
private fun HotListeningListItem(
    entry: HotListeningEntry,
    onAlbumClick: (Album) -> Unit,
    copyMeta: (String, String) -> Unit
) {
    val album = entry.album
    AlbumItem(
        album = album,
        onClick = { onAlbumClick(album) },
        emptyCoverUseShimmer = true,
        coverRetainPainterDuringReload = true,
        coverBadge = entry.toCoverBadge(),
        onRjClick = { copyMeta("RJ", it) },
        onCircleClick = { copyMeta("社团", it) },
        onCvClick = { copyMeta("CV", it) },
        onTagClick = { copyMeta("标签", it) },
    )
}

@Composable
private fun HotListeningGridItem(
    entry: HotListeningEntry,
    onAlbumClick: (Album) -> Unit,
    copyMeta: (String, String) -> Unit
) {
    val album = entry.album
    AlbumGridItem(
        album = album,
        onClick = { onAlbumClick(album) },
        emptyCoverUseShimmer = true,
        coverRetainPainterDuringReload = true,
        coverBadge = entry.toCoverBadge(),
        onRjClick = { copyMeta("RJ", it) },
        onCircleClick = { copyMeta("社团", it) },
        onCvClick = { copyMeta("CV", it) },
        onTagClick = { copyMeta("标签", it) },
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
    return AlbumCoverBadge(icon = icon, text = metricLabel)
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
