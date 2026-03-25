package com.asmr.player.ui.playlists

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.PlaylistItemWithSubtitles
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.SubtitleStamp
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.reorderable.ItemPosition
import com.asmr.player.ui.common.reorderable.ReorderableItem
import com.asmr.player.ui.common.reorderable.detectReorderAfterLongPress
import com.asmr.player.ui.common.reorderable.rememberReorderableLazyListState
import com.asmr.player.ui.common.reorderable.reorderable
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor

internal const val PLAYLIST_DETAIL_ITEM_TAG_PREFIX = "playlistDetailItem"
internal const val PLAYLIST_DETAIL_ITEM_MENU_BUTTON_TAG_PREFIX = "playlistDetailItemMenu"
internal const val PLAYLIST_DETAIL_MOVE_TOP_MENU_ITEM_TAG = "playlistDetailMoveTopMenuItem"
internal const val PLAYLIST_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG = "playlistDetailMoveBottomMenuItem"
internal const val PLAYLIST_DETAIL_REORDER_DIALOG_TAG = "playlistDetailReorderDialog"
internal const val PLAYLIST_DETAIL_REORDER_ROW_TAG_PREFIX = "playlistDetailReorderRow"

private const val PLAYLIST_DETAIL_REORDER_SENTINEL_KEY = "__playlist_detail_reorder_sentinel__"

@Composable
fun PlaylistDetailScreen(
    windowSizeClass: WindowSizeClass,
    playlistId: Long,
    title: String,
    onPlayAll: (List<PlaylistItemEntity>, PlaylistItemEntity) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(playlistId) {
        viewModel.setPlaylistId(playlistId)
    }
    val items by viewModel.items.collectAsState()
    PlaylistDetailContent(
        windowSizeClass = windowSizeClass,
        title = title,
        items = items,
        onPlayAll = onPlayAll,
        onRemoveItem = viewModel::removeItem,
        onMoveItemToTop = viewModel::moveItemToTop,
        onMoveItemToBottom = viewModel::moveItemToBottom,
        onSaveManualOrder = viewModel::saveManualOrder
    )
}

@Composable
internal fun PlaylistDetailContent(
    windowSizeClass: WindowSizeClass,
    title: String,
    items: List<PlaylistItemWithSubtitles>,
    onPlayAll: (List<PlaylistItemEntity>, PlaylistItemEntity) -> Unit,
    onRemoveItem: (String) -> Unit,
    onMoveItemToTop: (String) -> Unit,
    onMoveItemToBottom: (String) -> Unit,
    onSaveManualOrder: (List<String>) -> Unit
) {
    val listState = rememberLazyListState()
    val localItems = remember { mutableStateListOf<PlaylistItemWithSubtitles>() }
    var pendingRemoveItem by remember { mutableStateOf<PlaylistItemWithSubtitles?>(null) }

    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        maxScrollPerFrame = 28.dp,
        scrollTriggerPadding = 112.dp,
        onMove = { from, to ->
            localItems.movePlaylistItems(from, to)
        },
        canDragOver = { draggedOver, _ ->
            localItems.any { item -> item.mediaId == draggedOver.key }
        },
        onDragEnd = { _, _ ->
            onSaveManualOrder(localItems.map { it.mediaId })
        }
    )

    LaunchedEffect(items) {
        if (reorderState.draggingItemIndex == null) {
            localItems.sync(items)
        }
    }

    val playItems = localItems.map { item -> item.toPlaybackEntity() }
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val colorScheme = AsmrTheme.colorScheme

    Scaffold(
        contentWindowInsets = StableWindowInsets.navigationBars,
        containerColor = Color.Transparent,
        contentColor = colorScheme.onBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentModifier = if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            }
            LazyColumn(
                state = listState,
                modifier = contentModifier
                    .reorderable(reorderState)
                    .thinScrollbar(listState),
                contentPadding = PaddingValues(top = 6.dp, bottom = LocalBottomOverlayPadding.current)
            ) {
                item(key = PLAYLIST_DETAIL_REORDER_SENTINEL_KEY) {
                    Spacer(modifier = Modifier.height(1.dp))
                }
                itemsIndexed(localItems, key = { _, item -> item.mediaId }) { index, item ->
                    ReorderableItem(
                        reorderableState = reorderState,
                        key = item.mediaId
                    ) { isDragging ->
                        PlaylistItemRow(
                            item = item,
                            showSubtitleStamp = item.hasSubtitles,
                            showTopDivider = index > 0,
                            isDragging = isDragging,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("$PLAYLIST_DETAIL_ITEM_TAG_PREFIX:${item.mediaId}")
                                .detectReorderAfterLongPress(reorderState)
                                .clickable { onPlayAll(playItems, item.toPlaybackEntity()) },
                            onPlay = { onPlayAll(playItems, item.toPlaybackEntity()) },
                            onMoveToTop = { onMoveItemToTop(item.mediaId) },
                            onMoveToBottom = { onMoveItemToBottom(item.mediaId) },
                            onRemove = { pendingRemoveItem = item }
                        )
                    }
                }
            }
        }
    }

    pendingRemoveItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRemoveItem = null },
            title = { Text("确认移除") },
            text = {
                Text(
                    text = "确定从「$title」移除“${item.title.ifBlank { "未命名" }}”吗？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveItem = null
                        onRemoveItem(item.mediaId)
                    }
                ) {
                    Text("移除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveItem = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistItemRow(
    item: PlaylistItemWithSubtitles,
    showSubtitleStamp: Boolean,
    showTopDivider: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onRemove: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    var expanded by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 18.dp else 0.dp,
        label = "playlistItemElevation"
    )

    Box(
        modifier = modifier.shadow(elevation, RoundedCornerShape(18.dp))
    ) {
        if (showTopDivider) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .align(Alignment.TopCenter),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsmrAsyncImage(
                model = item.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholderCornerRadius = 6,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "未命名" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.textPrimary
                )
                if (item.artist.isNotBlank()) {
                    Text(
                        text = item.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.textTertiary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (showSubtitleStamp) {
                SubtitleStamp(modifier = Modifier.padding(end = 8.dp))
            }
            Box {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.testTag("$PLAYLIST_DETAIL_ITEM_MENU_BUTTON_TAG_PREFIX:${item.mediaId}")
                ) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
                }
                MaterialTheme(
                    colorScheme = materialColorScheme.copy(
                        surface = dynamicContainerColor,
                        surfaceContainer = dynamicContainerColor
                    )
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(dynamicContainerColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("播放") },
                            onClick = {
                                expanded = false
                                onPlay()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("移至顶部") },
                            modifier = Modifier.testTag(PLAYLIST_DETAIL_MOVE_TOP_MENU_ITEM_TAG),
                            onClick = {
                                expanded = false
                                onMoveToTop()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("移至末尾") },
                            modifier = Modifier.testTag(PLAYLIST_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG),
                            onClick = {
                                expanded = false
                                onMoveToBottom()
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            thickness = 0.5.dp,
                            color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        DropdownMenuItem(
                            text = { Text("移除") },
                            onClick = {
                                expanded = false
                                onRemove()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun PlaylistItemWithSubtitles.toPlaybackEntity(): PlaylistItemEntity {
    return PlaylistItemEntity(
        playlistId = playlistId,
        mediaId = mediaId,
        title = title,
        artist = artist,
        uri = uri,
        artworkUri = playbackArtworkUri.ifBlank { artworkUri },
        itemOrder = itemOrder
    )
}

private fun SnapshotStateList<PlaylistItemWithSubtitles>.sync(
    items: List<PlaylistItemWithSubtitles>
) {
    clear()
    addAll(items)
}

private fun SnapshotStateList<PlaylistItemWithSubtitles>.movePlaylistItems(
    from: ItemPosition,
    to: ItemPosition
) {
    if (isEmpty()) return
    val fromIndex = from.index - 1
    val toIndex = to.index - 1
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return
    add(toIndex, removeAt(fromIndex))
}
