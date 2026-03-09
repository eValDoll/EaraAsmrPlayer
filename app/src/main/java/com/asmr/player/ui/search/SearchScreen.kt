package com.asmr.player.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.domain.model.Album
import com.asmr.player.ui.library.AlbumGridItem
import com.asmr.player.ui.library.AlbumItem
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.CustomSearchBar
import com.asmr.player.ui.common.ActionButton
import androidx.compose.foundation.lazy.items as lazyItems

import androidx.compose.runtime.rememberCoroutineScope
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.sidepanel.RecentAlbumsPanel
import com.asmr.player.ui.sidepanel.LandscapeRightPanelHost
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import kotlin.math.absoluteValue
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border

private enum class SearchResultViewMode { Grid, List }

private fun stableAlbumKey(album: Album): String {
    val id = album.rjCode.ifBlank { album.workId }.trim()
    if (id.isNotEmpty()) return id
    val seed = "${album.coverUrl}|${album.title}|${album.circle}|${album.cv}"
    return "h${seed.hashCode().absoluteValue}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    windowSizeClass: WindowSizeClass,
    onAlbumClick: (Album) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    val viewMode by viewModel.viewMode.collectAsState()
    var scopeMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var purchasedOnly by rememberSaveable { mutableStateOf(false) }
    var selectedLocale by rememberSaveable { mutableStateOf("ja_JP") }
    val uiState by viewModel.uiState.collectAsState()
    val currentPageKey = (uiState as? SearchUiState.Success)?.page ?: 0
    val listState = rememberSaveable(currentPageKey, saver = LazyListState.Saver) { LazyListState(0, 0) }
    val gridState = rememberSaveable(currentPageKey, saver = LazyStaggeredGridState.Saver) { LazyStaggeredGridState() }
    val colorScheme = AsmrTheme.colorScheme
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.bootstrap(keyword, purchasedOnly, selectedLocale)
    }

    val success = uiState as? SearchUiState.Success
    var syncedFromState by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(success) {
        if (syncedFromState) return@LaunchedEffect
        val s = success ?: return@LaunchedEffect
        keyword = s.keyword
        purchasedOnly = s.purchasedOnly
        val loc = s.locale
        if (!loc.isNullOrBlank()) selectedLocale = loc
        syncedFromState = true
    }
    
    // 屏幕尺寸判断
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    val pullToRefreshState = rememberPullToRefreshState()
    val latestKeyword by rememberUpdatedState(keyword)
    val latestPurchasedOnly by rememberUpdatedState(purchasedOnly)
    val latestLocale by rememberUpdatedState(selectedLocale)
    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing) {
            val state = uiState
            if (state is SearchUiState.Success) {
                if (state.isPaging) {
                    pullToRefreshState.endRefresh()
                } else {
                    viewModel.refreshPage()
                }
            } else {
                viewModel.setPurchasedOnly(latestPurchasedOnly)
                viewModel.setLocale(latestLocale)
                viewModel.search(latestKeyword)
            }
        }
    }
    LaunchedEffect(uiState) {
        if (!pullToRefreshState.isRefreshing) return@LaunchedEffect
        val state = uiState
        val canEnd = when (state) {
            is SearchUiState.Success -> !state.isPaging
            is SearchUiState.Error -> true
            is SearchUiState.Loading -> false
            else -> true
        }
        if (canEnd) pullToRefreshState.endRefresh()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
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
                    onOpenAlbum = { a ->
                        onAlbumClick(
                            Album(
                                id = a.id,
                                title = a.title,
                                path = a.path,
                                localPath = a.localPath,
                                downloadPath = a.downloadPath,
                                circle = a.circle,
                                cv = a.cv,
                                coverUrl = a.coverUrl,
                                coverPath = a.coverPath,
                                coverThumbPath = a.coverThumbPath,
                                workId = a.workId,
                                rjCode = a.rjCode,
                                description = a.description
                            )
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
                    modifier = if (isCompact) {
                        Modifier.fillMaxSize()
                    } else if (hasRightPanel) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxHeight()
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                    }
                ) {
                    val topPadding = if (success != null) 120.dp else 80.dp

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(pullToRefreshState.nestedScrollConnection)
                            .clipToBounds()
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
                                CircularProgressIndicator(color = colorScheme.primary)
                            }

                            is SearchUiState.Success -> {
                                if (viewMode == 0) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = topPadding, bottom = 8.dp)
                                            .withAddedBottomPadding(LocalBottomOverlayPadding.current)
                                    ) {
                                        lazyItems(
                                            items = state.results,
                                            key = { album -> stableAlbumKey(album) },
                                            contentType = { "album" }
                                        ) { album ->
                                            AlbumItem(album = album, onClick = { onAlbumClick(album) }, emptyCoverUseShimmer = true)
                                        }
                                    }
                                } else {
                                    LazyVerticalStaggeredGrid(
                                        columns = StaggeredGridCells.Adaptive(150.dp),
                                        state = gridState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = topPadding, start = 16.dp, end = 16.dp, bottom = 16.dp)
                                            .withAddedBottomPadding(LocalBottomOverlayPadding.current),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalItemSpacing = 16.dp
                                    ) {
                                        items(
                                            state.results.size,
                                            key = { idx ->
                                                val a = state.results[idx]
                                                stableAlbumKey(a)
                                            },
                                            contentType = { "albumGrid" }
                                        ) { idx ->
                                            val album = state.results[idx]
                                            AlbumGridItem(
                                                album = album,
                                                onClick = { onAlbumClick(album) },
                                                emptyCoverUseShimmer = true
                                            )
                                        }
                                    }
                                }
                            }

                            is SearchUiState.Error -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(top = topPadding),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WifiOff,
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
                            }

                            else -> Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {}
                        }

                        PullToRefreshContainer(
                            state = pullToRefreshState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = topPadding)
                                .then(if (pullToRefreshState.progress > 0 || pullToRefreshState.isRefreshing) Modifier else Modifier.size(0.dp))
                        )
                    }

                    Column(modifier = Modifier.align(Alignment.TopCenter)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CustomSearchBar(
                                value = keyword,
                                onValueChange = { keyword = it },
                                placeholder = "搜索专辑、社团、CV...",
                                modifier = Modifier.weight(1f),
                                leadingIcon = {
                                val currentOrder = success?.order ?: SearchSortOption.Trend
                                val label = if (purchasedOnly) "仅已购" else currentOrder.label
                                Box {
                                    TextButton(
                                        onClick = { scopeMenuExpanded = true },
                                        enabled = success != null && !(success.isPaging),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                    DropdownMenu(
                                        expanded = scopeMenuExpanded,
                                        onDismissRequest = { scopeMenuExpanded = false },
                                        modifier = Modifier.background(colorScheme.surface)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("仅已购", color = colorScheme.textPrimary) },
                                            onClick = {
                                                scopeMenuExpanded = false
                                                purchasedOnly = true
                                                viewModel.setPurchasedOnly(true)
                                            }
                                        )
                                        SearchSortOption.values().forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option.label, color = colorScheme.textPrimary) },
                                                onClick = {
                                                    scopeMenuExpanded = false
                                                    purchasedOnly = false
                                                    viewModel.setPurchasedOnly(false)
                                                    viewModel.setOrder(option)
                                                }
                                            )
                                        }
                                    }
                                }
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val languageLabel = when (selectedLocale.trim()) {
                                        "zh_CN" -> "简中"
                                        "zh_TW" -> "繁中"
                                        else -> "日语"
                                    }
                                    Box {
                                        TextButton(
                                            onClick = { languageMenuExpanded = true },
                                            enabled = success != null && !(success.isPaging),
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
                                        ) {
                                            Text(languageLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }
                                        DropdownMenu(
                                            expanded = languageMenuExpanded,
                                            onDismissRequest = { languageMenuExpanded = false },
                                            modifier = Modifier.background(colorScheme.surface)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("日语", color = colorScheme.textPrimary) },
                                                onClick = {
                                                    languageMenuExpanded = false
                                                    purchasedOnly = false
                                                    selectedLocale = "ja_JP"
                                                    viewModel.setPurchasedOnly(false)
                                                    viewModel.setLocale("ja_JP")
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("简中", color = colorScheme.textPrimary) },
                                                onClick = {
                                                    languageMenuExpanded = false
                                                    purchasedOnly = false
                                                    selectedLocale = "zh_CN"
                                                    viewModel.setPurchasedOnly(false)
                                                    viewModel.setLocale("zh_CN")
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("繁中", color = colorScheme.textPrimary) },
                                                onClick = {
                                                    languageMenuExpanded = false
                                                    purchasedOnly = false
                                                    selectedLocale = "zh_TW"
                                                    viewModel.setPurchasedOnly(false)
                                                    viewModel.setLocale("zh_TW")
                                                }
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.search(keyword)
                                            scope.launch {
                                                listState.scrollToItem(0)
                                                gridState.scrollToItem(0)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = colorScheme.primary)
                                    }
                                }
                            }
                        )

                        if (rightPanelToggle != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            rightPanelToggle(Modifier.size(50.dp))
                        }
                    }

                    if (success != null) {
                            SearchPaginationHeader(
                                page = success.page,
                                canGoPrev = success.canGoPrev,
                                canGoNext = success.canGoNext,
                                isPaging = success.isPaging,
                                onPrev = {
                                    scope.launch {
                                        listState.scrollToItem(0)
                                        gridState.scrollToItem(0)
                                    }
                                    viewModel.prevPage()
                                },
                                onNext = {
                                    scope.launch {
                                        listState.scrollToItem(0)
                                        gridState.scrollToItem(0)
                                    }
                                    viewModel.nextPage()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPaginationHeader(
    page: Int,
    canGoPrev: Boolean,
    canGoNext: Boolean,
    isPaging: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDark) 12.dp else 8.dp,
                    shape = RoundedCornerShape(14.dp),
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f),
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f)
                )
                .then(
                    if (isDark) {
                        Modifier.border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(14.dp)
                        )
                    } else Modifier
                )
                .clip(RoundedCornerShape(14.dp))
                .background(if (isDark) colorScheme.surface.copy(alpha = 0.3f) else Color.White)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrev,
                enabled = canGoPrev && !isPaging,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, 
                    contentDescription = null,
                    tint = if (canGoPrev && !isPaging) colorScheme.primary else (if (isDark) colorScheme.textTertiary else Color.Gray)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 $page 页", 
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) colorScheme.textPrimary else Color.Black
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = onNext,
                enabled = canGoNext && !isPaging,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, 
                    contentDescription = null,
                    tint = if (canGoNext && !isPaging) colorScheme.primary else (if (isDark) colorScheme.textTertiary else Color.Gray)
                )
            }
        }
    }
}
