package com.asmr.player.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.asmr.player.ui.common.ManualReorderDialog
import com.asmr.player.ui.common.ManualReorderListItem
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.SubtitleStamp
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor

internal const val PLAYLIST_DETAIL_ITEM_TAG_PREFIX = "playlistDetailItem"
internal const val PLAYLIST_DETAIL_ITEM_MENU_BUTTON_TAG_PREFIX = "playlistDetailItemMenu"
internal const val PLAYLIST_DETAIL_MOVE_TOP_MENU_ITEM_TAG = "playlistDetailMoveTopMenuItem"
internal const val PLAYLIST_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG = "playlistDetailMoveBottomMenuItem"
internal const val PLAYLIST_DETAIL_REORDER_DIALOG_TAG = "playlistDetailReorderDialog"
internal const val PLAYLIST_DETAIL_REORDER_ROW_TAG_PREFIX = "playlistDetailReorderRow"

private data class PlaylistReorderSession(
    val initialMediaId: String,
    val items: List<PlaylistItemWithSubtitles>
)

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
    val playItems = remember(items) {
        items.map {
            PlaylistItemEntity(
                playlistId = it.playlistId,
                mediaId = it.mediaId,
                title = it.title,
                artist = it.artist,
                uri = it.uri,
                artworkUri = it.playbackArtworkUri.ifBlank { it.artworkUri },
                itemOrder = it.itemOrder
            )
        }
    }
    var pendingRemoveItem by remember { mutableStateOf<PlaylistItemWithSubtitles?>(null) }
    var reorderSession by remember { mutableStateOf<PlaylistReorderSession?>(null) }

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
                modifier = contentModifier,
                contentPadding = PaddingValues(top = 6.dp, bottom = LocalBottomOverlayPadding.current)
            ) {
                itemsIndexed(items, key = { _, item -> item.mediaId }) { index, item ->
                    val startItem = PlaylistItemEntity(
                        playlistId = item.playlistId,
                        mediaId = item.mediaId,
                        title = item.title,
                        artist = item.artist,
                        uri = item.uri,
                        artworkUri = item.playbackArtworkUri.ifBlank { item.artworkUri },
                        itemOrder = item.itemOrder
                    )
                    PlaylistItemRow(
                        item = item,
                        showSubtitleStamp = item.hasSubtitles,
                        onPlay = { onPlayAll(playItems, startItem) },
                        onLongPress = {
                            if (items.size > 1) {
                                reorderSession = PlaylistReorderSession(
                                    initialMediaId = item.mediaId,
                                    items = items
                                )
                            }
                        },
                        onMoveToTop = { onMoveItemToTop(item.mediaId) },
                        onMoveToBottom = { onMoveItemToBottom(item.mediaId) },
                        onRemove = { pendingRemoveItem = item }
                    )
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
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

    reorderSession?.let { session ->
        ManualReorderDialog(
            title = "手动排序",
            items = session.items.map { item ->
                ManualReorderListItem(
                    key = item.mediaId,
                    title = item.title.ifBlank { "未命名" },
                    subtitle = item.artist,
                    artworkModel = item.playbackArtworkUri.ifBlank { item.artworkUri },
                    supportingText = if (item.hasSubtitles) "含字幕" else ""
                )
            },
            initialKey = session.initialMediaId,
            dialogTag = PLAYLIST_DETAIL_REORDER_DIALOG_TAG,
            rowTagPrefix = PLAYLIST_DETAIL_REORDER_ROW_TAG_PREFIX,
            onDismiss = { reorderSession = null },
            onOrderCommitted = onSaveManualOrder
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistItemRow(
    item: PlaylistItemWithSubtitles,
    showSubtitleStamp: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onRemove: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$PLAYLIST_DETAIL_ITEM_TAG_PREFIX:${item.mediaId}")
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
