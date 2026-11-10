package com.asmr.player.ui.playlists

import com.asmr.player.i18n.appString
import com.asmr.player.R

import com.asmr.player.ui.common.LocalMainHeaderPadding

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import com.asmr.player.ui.common.CollectionPaneLayout
import com.asmr.player.ui.common.LocalTabletLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.data.local.db.dao.PlaylistStatsRow
import com.asmr.player.data.local.db.entities.PlaylistEntity
import com.asmr.player.ui.common.AsmrAsyncImage

import androidx.compose.ui.text.font.FontWeight
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.ui.common.FlatTextFieldDialog
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.smoothScrollToTop
import com.asmr.player.ui.common.EaraBrandedEmptyState
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.common.collectAsStateWhileActive

private val PlaylistsPageHorizontalPadding = 8.dp
private val PlaylistRowActionButtonSize = 34.dp
private val PlaylistRowActionIconSize = 18.dp

@Composable
fun PlaylistsScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    scrollToTopSignal: Long = 0L,
    detailContent: (@Composable (PlaylistEntity) -> Unit)? = null,
    viewModel: PlaylistsViewModel = hiltViewModel(),
    onCloudAlbumClick: (com.asmr.player.domain.model.Album) -> Unit = {},
    cloud: Boolean = false,
    onOpenAsmrOneLogin: () -> Unit = {},
) {
    val savedSources = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize().padding(top = LocalMainHeaderPadding.current)) {
        androidx.compose.runtime.CompositionLocalProvider(LocalMainHeaderPadding provides 0.dp) {
            savedSources.SaveableStateProvider(cloud) {
                if (cloud) {
                    com.asmr.player.ui.asmrone.AsmrOneCloudScreen(isDataActive, onCloudAlbumClick, scrollToTopSignal,
                        onOpenLogin = onOpenAsmrOneLogin)
                } else {
                    LocalPlaylistsScreen(windowSizeClass, isActive, isDataActive, onPlaylistClick, scrollToTopSignal, detailContent, viewModel)
                }
            }
        }
    }
}

@Composable
private fun LocalPlaylistsScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    scrollToTopSignal: Long = 0L,
    detailContent: (@Composable (PlaylistEntity) -> Unit)? = null,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWhileActive(isDataActive)
    val colorScheme = AsmrTheme.colorScheme
    val listState = rememberLazyListState()
    var showCreate by remember { mutableStateOf(false) }
    val userPlaylists = remember(playlists) { playlists.filter { it.category == "user" } }

    // 屏幕尺寸判断
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val tabletLayout = LocalTabletLayout.current
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = userPlaylists.firstOrNull { it.id == selectedId } ?: userPlaylists.firstOrNull()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground
    ) { padding ->
        LaunchedEffect(isActive) {
            if (isActive) return@LaunchedEffect
            listState.stopScroll(MutatePriority.PreventUserInput)
        }
        LaunchedEffect(scrollToTopSignal) {
            if (scrollToTopSignal == 0L) return@LaunchedEffect
            listState.smoothScrollToTop()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentModifier = if (isCompact || tabletLayout.isTablet) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
            }
            CollectionPaneLayout(
                modifier = contentModifier.padding(horizontal = tabletLayout.horizontalPadding),
                hasDetail = selected != null && detailContent != null,
                detail = {
                    selected?.let { selected ->
                        detailContent?.invoke(PlaylistEntity(id = selected.id, name = selected.name, category = selected.category, createdAt = selected.createdAt))
                    }
                },
            ) {
                if (userPlaylists.isEmpty()) {
                    EaraBrandedEmptyState(
                        sectionTitle = appString(R.string.ui_playlists),
                        headline = appString(R.string.ui_no_list_created_yet),
                        sectionIcon = Icons.AutoMirrored.Rounded.QueueMusic,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = LocalMainHeaderPadding.current, bottom = LocalBottomOverlayPadding.current + 88.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        flingBehavior = rememberCalmScrollableFlingBehavior(),
                        contentPadding = PaddingValues(start = PlaylistsPageHorizontalPadding, end = PlaylistsPageHorizontalPadding, top = LocalMainHeaderPadding.current + 8.dp, bottom = 8.dp)
                            .withAddedBottomPadding(LocalBottomOverlayPadding.current + 72.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(userPlaylists, key = { it.id }) { playlist ->
                            val entity = remember(playlist.id, playlist.name, playlist.category, playlist.createdAt) {
                                PlaylistEntity(
                                    id = playlist.id,
                                    name = playlist.name,
                                    category = playlist.category,
                                    createdAt = playlist.createdAt
                                )
                            }
                            PlaylistRow(
                                playlist = playlist,
                                selected = tabletLayout.twoPane && selected?.id == entity.id,
                                onClick = {
                                    if (tabletLayout.twoPane && detailContent != null) selectedId = entity.id
                                    else onPlaylistClick(entity)
                                },
                                onDelete = { viewModel.deletePlaylist(entity) },
                                onRename = { newName -> viewModel.renamePlaylist(entity.id, newName) }
                            )
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { showCreate = true },
                    containerColor = colorScheme.primaryContainer,
                    contentColor = colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = PlaylistsPageHorizontalPadding, bottom = LocalBottomOverlayPadding.current + 16.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreate = false
            }
        )
    }
}

@Composable
private fun PlaylistRow(
    selected: Boolean = false,
    playlist: PlaylistStatsRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val cover = remember(playlist.firstArtworkUri) { playlist.firstArtworkUri?.trim().orEmpty() }
    var showDeleteConfirm by remember(playlist.id) { mutableStateOf(false) }
    var showRename by remember(playlist.id) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colorScheme.primarySoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsmrAsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholderCornerRadius = 12,
                peekAnySizeForInitial = true,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appString(R.string.ui_total_first, playlist.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.textTertiary
                )
            }
            IconButton(
                onClick = { showRename = true },
                enabled = playlist.category != "system",
                modifier = Modifier.size(PlaylistRowActionButtonSize)
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = null,
                    tint = if (playlist.category != "system") colorScheme.textSecondary else colorScheme.textTertiary,
                    modifier = Modifier.size(PlaylistRowActionIconSize)
                )
            }
            IconButton(
                onClick = { showDeleteConfirm = true },
                enabled = playlist.category != "system",
                modifier = Modifier.size(PlaylistRowActionButtonSize)
            ) {
                Icon(
                    Icons.Rounded.Delete, 
                    contentDescription = null, 
                    tint = if (playlist.category != "system") colorScheme.danger.copy(alpha = 0.7f) else colorScheme.textTertiary,
                    modifier = Modifier.size(PlaylistRowActionIconSize)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        FlatActionDialog(
            onDismissRequest = { showDeleteConfirm = false },
            message = appString(R.string.ui_are_you_sure_you_want_to_delete_list, playlist.name),
            actions = listOf(
                FlatDialogAction(appString(R.string.ui_cancel), onClick = { showDeleteConfirm = false }),
                FlatDialogAction(
                    text = appString(R.string.ui_delete),
                    tone = FlatDialogActionTone.Danger,
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                )
            )
        )
    }

    if (showRename) {
        RenamePlaylistDialog(
            initialName = playlist.name,
            onDismiss = { showRename = false },
            onRename = { newName ->
                showRename = false
                onRename(newName)
            }
        )
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    FlatTextFieldDialog(
        onDismissRequest = onDismiss,
        message = appString(R.string.ui_please_enter_a_new_list_name),
        value = name,
        onValueChange = { name = it },
        placeholder = appString(R.string.ui_please_enter_a_list_name),
        confirmText = appString(R.string.ui_create),
        confirmEnabled = name.trim().isNotBlank(),
        onConfirm = { onCreate(name.trim()) },
    )
}

@Composable
private fun RenamePlaylistDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    FlatTextFieldDialog(
        onDismissRequest = onDismiss,
        message = appString(R.string.ui_please_enter_a_new_list_name_761c9f),
        value = name,
        onValueChange = { name = it },
        placeholder = appString(R.string.ui_please_enter_a_list_name),
        confirmText = appString(R.string.ui_ok),
        confirmEnabled = name.trim().isNotBlank() && name.trim() != initialName.trim(),
        onConfirm = { onRename(name.trim()) },
    )
}
