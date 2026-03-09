package com.asmr.player.ui.library

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import com.asmr.player.util.Formatting
import com.asmr.player.ui.common.SubtitleStamp
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.CoverContentRow
import com.asmr.player.ui.common.CvChipsFlow
import com.asmr.player.ui.common.CvChipsSingleLine
import com.asmr.player.ui.common.withAddedBottomPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.library.LibraryUiState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.sidepanel.LandscapeRightPanelHost
import com.asmr.player.ui.sidepanel.RecentAlbumsPanel
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.cache.LazyListPreloader
import dagger.hilt.android.EntryPointAccessors

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.Card
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.asmr.player.ui.common.AsmrAsyncImage
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.asmr.player.ui.common.CustomSearchBar
import com.asmr.player.ui.common.ActionButton

@Composable
private fun LibraryActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.textPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.textSecondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    windowSizeClass: WindowSizeClass,
    onAlbumClick: (Album) -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onOpenPlaylistPicker: (mediaId: String, uri: String, title: String, artist: String, artworkUri: String) -> Unit = { _, _, _, _, _ -> },
    onOpenGroupPicker: (albumId: Long) -> Unit = { _ -> },
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val colorScheme = AsmrTheme.colorScheme
    val uiState by viewModel.uiState.collectAsState()
    val viewMode by viewModel.libraryViewMode.collectAsState()
    val querySpec by viewModel.querySpec.collectAsState()
    val tags by viewModel.availableTags.collectAsState()
    val circles by viewModel.availableCircles.collectAsState()
    val cvs by viewModel.availableCvs.collectAsState()
    val presets by viewModel.filterPresets.collectAsState()
    val userTagsByAlbumId by viewModel.userTagsByAlbumId.collectAsState()
    val userTagsByTrackId by viewModel.userTagsByTrackId.collectAsState()
    val isGlobalSyncRunning by viewModel.isGlobalSyncRunning.collectAsState()
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var searchText by rememberSaveable { mutableStateOf(querySpec.textQuery.orEmpty()) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showTagManager by remember { mutableStateOf(false) }
    var tagAssignTarget by remember { mutableStateOf<TagAssignTarget?>(null) }

    LaunchedEffect(querySpec.textQuery) {
        val newText = querySpec.textQuery.orEmpty()
        if (newText != searchText) searchText = newText
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState(0, 0) }
    val gridState = rememberSaveable(saver = androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState.Saver) {
        androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState()
    }

    val mode = (viewMode ?: 0).coerceIn(0, 2)
    val isGrid = mode == 1
    val isTrackList = mode == 2
    val pagedAlbums = viewModel.pagedAlbums.collectAsLazyPagingItems()
    val pagedTrackAlbumHeaders = viewModel.pagedTrackAlbumHeaders.collectAsLazyPagingItems()
    val pagedAlbumSnapshot = pagedAlbums.itemSnapshotList
    val pagedAlbumIndices = remember(pagedAlbumSnapshot.items.size) { List(pagedAlbumSnapshot.items.size) { it } }
    val expandedAlbumTracks by (if (isTrackList) viewModel.expandedTrackAlbumTracks else flowOf(emptyMap())).collectAsState(initial = emptyMap())
    val expandedAlbumIds = remember { mutableStateListOf<Long>() }
    var actionAlbum by remember { mutableStateOf<Album?>(null) }
    var showAlbumActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tagManagerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(isTrackList) {
        if (!isTrackList) {
            expandedAlbumIds.clear()
            viewModel.setExpandedTrackAlbums(emptySet())
            return@LaunchedEffect
        }
        snapshotFlow { expandedAlbumIds.toSet() }
            .collect { ids -> viewModel.setExpandedTrackAlbums(ids) }
    }
    LaunchedEffect(showDeleteConfirm, actionAlbum) {
        if (showDeleteConfirm && (actionAlbum == null || actionAlbum?.id?.let { it <= 0L } == true)) {
            showDeleteConfirm = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground,
        // TopAppBar is now handled by MainActivity for better consistency
    ) { padding ->
        // 屏幕尺寸判断
        val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

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
        ) { contentModifier, hasRightPanel, rightPanelToggle ->
            Box(
                modifier = contentModifier,
                contentAlignment = if (hasRightPanel) Alignment.TopStart else Alignment.TopCenter
            ) {
                Column(
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
                    when (val state = uiState) {
                    is LibraryUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is LibraryUiState.BulkInProgress -> {
                        val progress = state.progress
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = when (progress.phase) {
                                    com.asmr.player.ui.library.BulkPhase.ScanningLocal -> "正在扫描本地库"
                                    com.asmr.player.ui.library.BulkPhase.SyncingCloud -> "正在云同步"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.textPrimary
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            if (progress.total > 0) {
                                LinearProgressIndicator(
                                    progress = { progress.fraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "进度 ${progress.current}/${progress.total}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.textSecondary
                            )
                            if (progress.currentAlbumTitle.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = progress.currentAlbumTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (progress.currentFile.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "正在扫描：${progress.currentFile}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(onClick = { viewModel.cancelBulkTask() }) { Text("取消") }
                        }
                    }
                    is LibraryUiState.Success -> {
                        if (viewMode == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            // Main content area
                            Box(modifier = Modifier.fillMaxSize()) {
                                val isTrackListLoading = isTrackList &&
                                    (pagedTrackAlbumHeaders.loadState.refresh is LoadState.Loading) &&
                                    pagedTrackAlbumHeaders.itemCount == 0
                                val isAlbumListLoading = !isTrackList &&
                                    (pagedAlbums.loadState.refresh is LoadState.Loading) &&
                                    pagedAlbums.itemCount == 0
                                val isLoading = isTrackListLoading || isAlbumListLoading
                                val isEmpty = if (isTrackList) {
                                    (pagedTrackAlbumHeaders.loadState.refresh is LoadState.NotLoading) && pagedTrackAlbumHeaders.itemCount == 0
                                } else {
                                    (pagedAlbums.loadState.refresh is LoadState.NotLoading) && pagedAlbums.itemCount == 0
                                }
                                if (isLoading) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = colorScheme.primary)
                                    }
                                } else if (isEmpty) {
                                    val hasAnyQuery =
                                        !querySpec.textQuery.isNullOrBlank() ||
                                            querySpec.includeTagIds.isNotEmpty() ||
                                            querySpec.excludeTagIds.isNotEmpty() ||
                                            querySpec.circles.isNotEmpty() ||
                                            querySpec.cvs.isNotEmpty() ||
                                            querySpec.source != null

                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 24.dp)
                                                .align(Alignment.Center),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            if (hasAnyQuery) {
                                                Text(
                                                    "没有匹配结果",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = colorScheme.textSecondary
                                                )
                                                Button(
                                                    onClick = {
                                                        searchText = ""
                                                        viewModel.setSearchQuery("")
                                                        viewModel.clearFilters()
                                                    }
                                                ) { Text("重置筛选与搜索") }
                                            } else {
                                                Text(
                                                    "还没有扫描到本地专辑",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = colorScheme.textSecondary
                                                )
                                                Text(
                                                    "请到「设置」→「本地库」添加目录并执行同步/刷新",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colorScheme.textSecondary
                                                )
                                            }
                                        }
                                    }
                                } else if (isTrackList) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 80.dp, bottom = 8.dp)
                                            .withAddedBottomPadding(LocalBottomOverlayPadding.current)
                                    ) {
                                        val headerCount = pagedTrackAlbumHeaders.itemCount
                                        for (headerIndex in 0 until headerCount) {
                                            val header = pagedTrackAlbumHeaders[headerIndex]
                                            if (header == null) {
                                                item(key = "header:$headerIndex") {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                                    ) {
                                                        Spacer(modifier = Modifier.height(50.dp))
                                                    }
                                                }
                                                continue
                                            }

                                            val albumId = header.albumId
                                            val expanded = expandedAlbumIds.contains(albumId)
                                            val rows = expandedAlbumTracks[albumId].orEmpty()

                                            stickyHeader(key = "album:$albumId") {
                                                Column {
                                                    if (headerIndex > 0) {
                                                        HorizontalDivider(
                                                            modifier = Modifier.padding(horizontal = 16.dp),
                                                            thickness = 0.5.dp,
                                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                                        )
                                                    }
                                                    TrackAlbumHeader(
                                                        albumTitle = header.albumTitle,
                                                        rjCode = header.rjCode.ifBlank { header.workId },
                                                        coverModel = header.coverPath.takeIf { it.isNotBlank() }.takeIf { it != "null" }
                                                            ?: header.coverUrl.takeIf { it.isNotBlank() },
                                                        expanded = expanded,
                                                        onToggle = {
                                                            if (expanded) expandedAlbumIds.remove(albumId) else expandedAlbumIds.add(albumId)
                                                        }
                                                    )
                                                }
                                            }

                                            if (expanded) {
                                                val album = Album(
                                                    id = header.albumId,
                                                    title = header.albumTitle,
                                                    path = "",
                                                    circle = header.circle,
                                                    cv = header.cv,
                                                    tags = emptyList(),
                                                    coverUrl = header.coverUrl,
                                                    coverPath = header.coverPath,
                                                    workId = header.workId,
                                                    rjCode = header.rjCode.ifBlank { header.workId }
                                                )
                                                itemsIndexed(
                                                    items = rows,
                                                    key = { _, row -> row.trackId },
                                                    contentType = { _, _ -> "albumTrackRow" }
                                                ) { index, row ->
                                                    val track = remember(
                                                        row.trackId,
                                                        row.albumId,
                                                        row.trackTitle,
                                                        row.trackPath,
                                                        row.duration,
                                                        row.trackGroup
                                                    ) {
                                                        Track(
                                                            id = row.trackId,
                                                            albumId = row.albumId,
                                                            title = row.trackTitle,
                                                            path = row.trackPath,
                                                            duration = row.duration,
                                                            group = row.trackGroup
                                                        )
                                                    }
                                                    val subtitleText = remember(row.duration) {
                                                        if (row.duration > 0) Formatting.formatTrackTime(row.duration.toLong()) else ""
                                                    }

                                                    Column {
                                                        TrackListRow(
                                                            title = track.title,
                                                            subtitle = subtitleText,
                                                            showSubtitleStamp = row.hasSubtitles,
                                                            onClick = {
                                                                scope.launch {
                                                                    val tracksForAlbum = withContext(Dispatchers.Default) {
                                                                        rows.map { r ->
                                                                            Track(
                                                                                id = r.trackId,
                                                                                albumId = r.albumId,
                                                                                title = r.trackTitle,
                                                                                path = r.trackPath,
                                                                                duration = r.duration,
                                                                                group = r.trackGroup
                                                                            )
                                                                        }
                                                                    }
                                                                    onPlayTracks(album, tracksForAlbum, track)
                                                                }
                                                            },
                                                            onAddToQueue = {
                                                                playerViewModel.addTrackToQueue(album, track)
                                                            },
                                                            onAddToPlaylist = {
                                                                val rj = album.rjCode.ifBlank { album.workId }
                                                                val artist = when {
                                                                    album.cv.isNotBlank() -> album.cv
                                                                    album.circle.isNotBlank() -> album.circle
                                                                    else -> rj
                                                                }
                                                                val artwork = album.coverPath.ifBlank { album.coverUrl }
                                                                val displayTitle = track.title.ifBlank {
                                                                    track.path.substringAfterLast('/').substringAfterLast('\\')
                                                                }
                                                                onOpenPlaylistPicker(
                                                                    track.path,
                                                                    track.path,
                                                                    displayTitle,
                                                                    artist.orEmpty(),
                                                                    artwork
                                                                )
                                                            },
                                                            onManageTags = {
                                                                scope.launch {
                                                                    val inherited = withContext(Dispatchers.IO) {
                                                                        viewModel.loadInheritedTagsForAlbum(album.id)
                                                                    }
                                                                    val user = userTagsByTrackId[track.id].orEmpty()
                                                                    tagAssignTarget = TagAssignTarget.Track(
                                                                        trackId = track.id,
                                                                        title = track.title,
                                                                        inheritedTags = inherited,
                                                                        userTags = user
                                                                    )
                                                                }
                                                            },
                                                            onRemove = {
                                                                viewModel.removeTrackFromAlbum(track.id)
                                                            }
                                                        )
                                                        if (index < rows.size - 1) {
                                                            HorizontalDivider(
                                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                                thickness = 0.5.dp,
                                                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else if (isGrid) {
                                    LazyVerticalStaggeredGrid(
                                        columns = StaggeredGridCells.Adaptive(150.dp),
                                        state = gridState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 80.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                                            .withAddedBottomPadding(LocalBottomOverlayPadding.current),
                                        verticalItemSpacing = 16.dp,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        staggeredItems(
                                            pagedAlbumIndices,
                                            key = { idx -> pagedAlbumSnapshot.items.getOrNull(idx)?.id?.takeIf { it > 0L } ?: idx }
                                        ) { idx ->
                                            val album = pagedAlbumSnapshot.items.getOrNull(idx) ?: return@staggeredItems
                                            AlbumGridItem(
                                                album = album,
                                                syncStatus = state.syncingAlbums[album.id] ?: SyncStatus.Idle,
                                                onClick = { onAlbumClick(album) },
                                                onLongClick = {
                                                    actionAlbum = album
                                                    showAlbumActions = true
                                                }
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
                                    val screenWidthDp = LocalConfiguration.current.screenWidthDp
                                    val listItemHeight = (screenWidthDp.dp * 0.24f).coerceIn(112.dp, 140.dp)
                                    val coverPx = remember(listItemHeight, density) { with(density) { listItemHeight.roundToPx() } }
                                    val preloadSize = remember(coverPx) { IntSize(coverPx, coverPx) }
                                    LazyListPreloader(
                                        state = listState,
                                        itemCount = pagedAlbums.itemCount,
                                        preloadNext = 10,
                                        preloadSize = preloadSize,
                                        cacheManagerProvider = { cacheManager },
                                        modelAt = { idx ->
                                            val a = pagedAlbums.itemSnapshotList.getOrNull(idx)
                                            if (a == null) {
                                                null
                                            } else {
                                                a.coverThumbPath.takeIf { it.isNotBlank() && it.contains("_v2") }
                                                    ?: a.coverPath.takeIf { it.isNotBlank() }
                                                    ?: a.coverUrl.takeIf { it.isNotBlank() }
                                            }
                                        }
                                    )
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 80.dp, bottom = 8.dp)
                                            .withAddedBottomPadding(LocalBottomOverlayPadding.current)
                                    ) {
                                        items(
                                            count = pagedAlbums.itemCount,
                                            key = { idx -> pagedAlbums.itemSnapshotList.getOrNull(idx)?.id?.takeIf { it > 0L } ?: idx },
                                            contentType = { "albumListItem" }
                                        ) { idx ->
                                            val album = pagedAlbums.itemSnapshotList.getOrNull(idx) ?: return@items
                                            AlbumItem(
                                                album = album,
                                                syncStatus = state.syncingAlbums[album.id] ?: SyncStatus.Idle,
                                                onClick = { onAlbumClick(album) },
                                                onLongClick = {
                                                    actionAlbum = album
                                                    showAlbumActions = true
                                                }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CustomSearchBar(
                                        value = searchText,
                                        onValueChange = {
                                            searchText = it
                                            viewModel.setSearchQuery(it)
                                        },
                                        placeholder = "社团 / CV / 标签...",
                                        modifier = Modifier.weight(1f),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                tint = colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingIcon = if (searchText.isNotBlank()) {
                                            {
                                                IconButton(
                                                    onClick = {
                                                        searchText = ""
                                                        viewModel.setSearchQuery("")
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = null,
                                                        tint = colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        } else null
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box {
                                        ActionButton(
                                            icon = Icons.Default.SwapVert,
                                            onClick = { sortMenuExpanded = true }
                                        )
                                        DropdownMenu(
                                            expanded = sortMenuExpanded,
                                            onDismissRequest = { sortMenuExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("最近播放") },
                                                onClick = {
                                                    sortMenuExpanded = false
                                                    viewModel.setSort(LibrarySort.LastPlayedDesc)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("最近加入") },
                                                onClick = {
                                                    sortMenuExpanded = false
                                                    viewModel.setSort(LibrarySort.AddedDesc)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("标题") },
                                                onClick = {
                                                    sortMenuExpanded = false
                                                    viewModel.setSort(LibrarySort.TitleAsc)
                                                }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ActionButton(
                                        icon = Icons.Default.FilterList,
                                        onClick = { showFilterSheet = true }
                                    )
                                    if (rightPanelToggle != null) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        rightPanelToggle(Modifier.size(50.dp))
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

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState
        ) {
            LibraryFilterSheet(
                querySpec = querySpec,
                tags = tags,
                circles = circles,
                cvs = cvs,
                presets = presets,
                onOpenTagManager = {
                    showFilterSheet = false
                    showTagManager = true
                },
                onSetSource = { viewModel.setSourceFilter(it) },
                onToggleTag = { viewModel.toggleTag(it) },
                onToggleCircle = { viewModel.toggleCircle(it) },
                onToggleCv = { viewModel.toggleCv(it) },
                onClear = { viewModel.clearFilters() },
                onApplyPreset = { viewModel.applyPreset(it) },
                onSavePreset = { viewModel.savePreset(it) },
                onDeletePreset = { viewModel.deletePreset(it) },
                onClose = { showFilterSheet = false }
            )
        }
    }

    if (showTagManager) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showTagManager = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                TagManagerSheet(
                    tags = tags,
                    onRename = { tagId, newName -> viewModel.renameUserTag(tagId, newName) },
                    onDelete = { tagId -> viewModel.deleteUserTag(tagId) },
                    onClose = { showTagManager = false }
                )
            }
        }
    }
    
    // ... rest of the file (ModalBottomSheet, AlertDialog)


    if (showAlbumActions) {
        val album = actionAlbum
        ModalBottomSheet(
            onDismissRequest = { showAlbumActions = false },
            sheetState = sheetState
        ) {
            if (album != null) {
                val syncStatus = (uiState as? LibraryUiState.Success)?.syncingAlbums?.get(album.id) ?: SyncStatus.Idle
                val isSyncing = syncStatus is SyncStatus.Syncing
                val hasLocalPaths = remember(album) { album.getAllLocalPaths().isNotEmpty() }
                ListItem(
                    headlineContent = { Text("删除") },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable(enabled = !isSyncing && !isGlobalSyncRunning) {
                            showAlbumActions = false
                            showDeleteConfirm = true
                        }
                )
                ListItem(
                    headlineContent = { Text("标签管理") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable {
                            showAlbumActions = false
                            val user = userTagsByAlbumId[album.id].orEmpty()
                            val inherited = album.tags.filterNot { user.contains(it) }
                            tagAssignTarget = TagAssignTarget.Album(
                                albumId = album.id,
                                title = album.title,
                                inheritedTags = inherited,
                                userTags = user
                            )
                        }
                )
                ListItem(
                    headlineContent = { Text("添加到分组") },
                    leadingContent = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable(enabled = album.id > 0L) {
                            showAlbumActions = false
                            onOpenGroupPicker(album.id)
                        }
                )
                ListItem(
                    headlineContent = { Text("本地同步") },
                    leadingContent = { Icon(Icons.Default.Sync, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable(enabled = hasLocalPaths && !isSyncing && !isGlobalSyncRunning) {
                            showAlbumActions = false
                            viewModel.rescanAlbum(album)
                        }
                )
                ListItem(
                    headlineContent = { Text("云同步") },
                    leadingContent = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable(enabled = !isSyncing && !isGlobalSyncRunning) {
                            showAlbumActions = false
                            viewModel.syncAlbumMetadata(album)
                        }
                )
                if (isSyncing) {
                    ListItem(
                        headlineContent = { Text("取消同步") },
                        leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clickable {
                                showAlbumActions = false
                                viewModel.cancelAlbumTask(album.id)
                            }
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        val album = actionAlbum
        if (album != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirm = false
                            viewModel.deleteAlbum(album)
                        }
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("取消")
                    }
                },
                title = { Text("删除专辑") },
                text = { Text("将从本地库中移除该专辑，并尝试删除本地文件。") }
            )
        }
    }
    val target = tagAssignTarget
    if (target != null) {
        when (target) {
            is TagAssignTarget.Album -> {
                TagAssignDialog(
                    title = target.title,
                    inheritedTags = target.inheritedTags,
                    userTags = target.userTags,
                    allTags = tags,
                    onApplyUserTags = { list ->
                        viewModel.setUserTagsForAlbum(target.albumId, list.joinToString(","))
                        tagAssignTarget = null
                    },
                    onDismiss = { tagAssignTarget = null },
                    onOpenTagManager = { showTagManager = true }
                )
            }
            is TagAssignTarget.Track -> {
                TagAssignDialog(
                    title = target.title,
                    inheritedTags = target.inheritedTags,
                    userTags = target.userTags,
                    allTags = tags,
                    onApplyUserTags = { list ->
                        viewModel.setUserTagsForTrack(target.trackId, list.joinToString(","))
                        tagAssignTarget = null
                    },
                    onDismiss = { tagAssignTarget = null },
                    onOpenTagManager = { showTagManager = true }
                )
            }
        }
    }

}

private sealed class TagAssignTarget {
    data class Album(
        val albumId: Long,
        val title: String,
        val inheritedTags: List<String>,
        val userTags: List<String>
    ) : TagAssignTarget()

    data class Track(
        val trackId: Long,
        val title: String,
        val inheritedTags: List<String>,
        val userTags: List<String>
    ) : TagAssignTarget()
}

@Composable
private fun TrackAlbumHeader(
    albumTitle: String,
    rjCode: String,
    coverModel: Any?,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface)
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsmrAsyncImage(
            model = coverModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = 8,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = albumTitle.ifBlank { rjCode.ifBlank { "专辑" } },
            style = MaterialTheme.typography.titleMedium,
            color = colorScheme.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (rjCode.isNotBlank()) {
            Text(
                text = rjCode,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textTertiary
            )
        }
    }
}

@Composable
private fun TrackListRow(
    title: String,
    subtitle: String,
    showSubtitleStamp: Boolean,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onManageTags: () -> Unit,
    onRemove: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    ListItem(
        headlineContent = {
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        trailingContent = {
            var expanded by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showSubtitleStamp) {
                    SubtitleStamp(modifier = Modifier.padding(end = 8.dp))
                }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("添加到播放队列") },
                            onClick = {
                                expanded = false
                                onAddToQueue()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("添加到歌单") },
                            onClick = {
                                expanded = false
                                onAddToPlaylist()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("标签管理") },
                            onClick = {
                                expanded = false
                                onManageTags()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("从专辑移除") },
                            onClick = {
                                expanded = false
                                onRemove()
                            }
                        )
                    }
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AlbumGridItem(
    album: Album,
    syncStatus: SyncStatus,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverShape = remember { RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colorScheme.surface.copy(alpha = 0.3f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val coverModel = remember(album.coverThumbPath, album.coverPath, album.coverUrl) {
                val thumb = album.coverThumbPath.trim().takeIf { it.isNotBlank() && it.contains("_v2") }.orEmpty()
                thumb.ifBlank { album.coverPath }.ifBlank { album.coverUrl }.trim().ifBlank { null }
            }
            AsmrAsyncImage(
                model = coverModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholderCornerRadius = 0,
                modifier = Modifier.fillMaxSize().clip(coverShape),
            )
            
            if (syncStatus is SyncStatus.Syncing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .blur(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                }
            } else if (syncStatus is SyncStatus.Error) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Red.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (album.releaseDate.isNotBlank()) {
                Text(
                    text = album.releaseDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            val rj = album.rjCode.ifBlank { album.workId }
            if (rj.isNotBlank()) {
                Text(
                    text = rj,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.textPrimary,
                overflow = TextOverflow.Clip
            )
            
            if (album.circle.isNotBlank()) {
                Text(
                    text = album.circle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            CvChipsFlow(cvText = album.cv)

            val statsText = buildString {
                val rv = album.ratingValue
                if (rv != null && rv > 0.0) {
                    append("★")
                    append(String.format("%.1f", rv))
                    if (album.ratingCount > 0) append("(${album.ratingCount})")
                }
                if (album.priceJpy > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("¥${album.priceJpy}")
                }
            }
            if (statsText.isNotBlank()) {
                Text(
                    text = statsText,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (album.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    album.tags.forEach { tag ->
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier
                                .background(colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumItem(
    album: Album,
    syncStatus: SyncStatus,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverShape = remember { RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val listItemHeight = (screenWidthDp.dp * 0.24f).coerceIn(112.dp, 140.dp)
    val coverSize = listItemHeight

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surface.copy(alpha = 0.5f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        CoverContentRow(
            coverWidth = coverSize,
            minHeight = coverSize,
            spacing = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(listItemHeight),
            cover = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    val coverModel = remember(album.coverThumbPath, album.coverPath, album.coverUrl) {
                        val thumb = album.coverThumbPath.trim().takeIf { it.isNotBlank() && it.contains("_v2") }.orEmpty()
                        thumb.ifBlank { album.coverPath }.ifBlank { album.coverUrl }.trim().ifBlank { null }
                    }
                    AsmrAsyncImage(
                        model = coverModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholderCornerRadius = 0,
                        modifier = Modifier.fillMaxSize().clip(coverShape),
                    )
                    
                    if (syncStatus is SyncStatus.Syncing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .blur(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    } else if (syncStatus is SyncStatus.Error) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Red.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            },
            content = {
                val statsText = buildString {
                    val rv = album.ratingValue
                    if (rv != null && rv > 0.0) {
                        append("★")
                        append(String.format("%.1f", rv))
                        if (album.ratingCount > 0) append("(${album.ratingCount})")
                    }
                    if (album.dlCount > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("DL ${album.dlCount}")
                    }
                    if (album.priceJpy > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("¥${album.priceJpy}")
                    }
                    if (album.releaseDate.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(album.releaseDate)
                    }
                }

                BalancedColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(top = 4.dp, bottom = 4.dp, end = 12.dp),
                    minGap = 4.dp,
                    maxGap = 12.dp,
                ) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val rj = album.rjCode.ifBlank { album.workId }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (rj.isNotBlank()) {
                            Text(
                                text = rj,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                                modifier = Modifier
                                    .background(colorScheme.primaryContainer, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (album.circle.isNotBlank()) {
                            Text(
                                text = album.circle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.primary.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (album.cv.isNotBlank()) {
                        CvChipsSingleLine(
                            cvText = album.cv,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (statsText.isNotBlank()) {
                        Text(
                            text = statsText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (album.tags.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clipToBounds()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            album.tags.forEach { tag ->
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .widthIn(max = 200.dp)
                                        .background(colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun BalancedColumn(
    modifier: Modifier = Modifier,
    minGap: Dp = 4.dp,
    maxGap: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints.copy(minHeight = 0))
        }

        val layoutWidth = if (constraints.maxWidth != Constraints.Infinity) {
            constraints.maxWidth
        } else {
            maxOf(constraints.minWidth, placeables.maxOfOrNull { it.width } ?: 0)
        }

        val childrenHeight = placeables.sumOf { it.height }
        val layoutHeight = if (constraints.maxHeight != Constraints.Infinity) {
            maxOf(constraints.minHeight, constraints.maxHeight, childrenHeight)
        } else {
            maxOf(constraints.minHeight, childrenHeight)
        }

        val remaining = (layoutHeight - childrenHeight).coerceAtLeast(0)
        val gapCount = placeables.size + 1
        val idealGap = if (gapCount > 0) remaining / gapCount else 0
        val gap = idealGap.coerceIn(minGap.roundToPx(), maxGap.roundToPx())
        val used = gap * gapCount
        val extra = remaining - used

        layout(layoutWidth, layoutHeight) {
            var y = (extra / 2) + gap
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height + gap
            }
        }
    }
}
