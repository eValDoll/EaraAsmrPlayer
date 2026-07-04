package com.asmr.player.ui.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import com.asmr.player.data.repository.PlaylistRepository
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AudioItemMenuAction
import com.asmr.player.ui.common.AudioItemRow
import com.asmr.player.ui.common.EaraBrandedEmptyState
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.rememberAudioMeta
import com.asmr.player.ui.common.SubtitleStamp
import com.asmr.player.ui.common.smoothScrollToTop
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.rememberAudioMetaText
import com.asmr.player.ui.common.reorderable.ItemPosition
import com.asmr.player.ui.common.reorderable.ReorderableItem
import com.asmr.player.ui.common.reorderable.detectReorderAfterLongPress
import com.asmr.player.ui.common.reorderable.rememberReorderableLazyListState
import com.asmr.player.ui.common.reorderable.reorderable
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor

private val PlaylistDetailHorizontalPadding = 8.dp

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
    scrollToTopSignal: Long = 0L,
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
        onSaveManualOrder = viewModel::saveManualOrder,
        scrollToTopSignal = scrollToTopSignal,
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
    onSaveManualOrder: (List<String>) -> Unit,
    scrollToTopSignal: Long = 0L,
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
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal == 0L) return@LaunchedEffect
        listState.smoothScrollToTop()
    }

    val playItems by remember {
        derivedStateOf {
            localItems.map { item -> item.toPlaybackEntity() }
        }
    }
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val colorScheme = AsmrTheme.colorScheme
    val draggedItemShape = RoundedCornerShape(18.dp)
    val draggedItemContainerColor = dynamicPageContainerColor(colorScheme)
    val draggedItemElevation = if (colorScheme.isDark) 10.dp else 14.dp
    val isFavorites = title == PlaylistRepository.PLAYLIST_FAVORITES
    val emptyHeadline = if (isFavorites) {
        "收藏的内容会在这里出现"
    } else {
        "这个列表还没有内容"
    }
    val emptySectionTitle = if (isFavorites) "我的收藏" else title.ifBlank { "我的列表" }

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
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
            }
            if (localItems.isEmpty()) {
                EaraBrandedEmptyState(
                    sectionTitle = emptySectionTitle,
                    headline = emptyHeadline,
                    sectionIcon = if (isFavorites) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic,
                    modifier = contentModifier,
                    contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current + 88.dp)
                )
            } else {
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
                    itemsIndexed(
                        items = localItems,
                        key = { _, item -> item.mediaId },
                        contentType = { _, _ -> "playlistDetailItem" }
                    ) { index, item ->
                        ReorderableItem(
                            reorderableState = reorderState,
                            key = item.mediaId,
                            modifier = Modifier.fillMaxWidth(),
                            draggingDecorationModifier = Modifier
                                .shadow(draggedItemElevation, draggedItemShape, clip = false)
                                .clip(draggedItemShape)
                                .background(draggedItemContainerColor)
                        ) { isDragging ->
                            PlaylistItemRow(
                                item = item,
                                showSubtitleStamp = item.hasSubtitles,
                                showTopDivider = index > 0,
                                isDragging = isDragging,
                                loadFileSize = !listState.isScrollInProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("$PLAYLIST_DETAIL_ITEM_TAG_PREFIX:${item.mediaId}")
                                    .detectReorderAfterLongPress(reorderState),
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
    }

    pendingRemoveItem?.let { item ->
        FlatActionDialog(
            onDismissRequest = { pendingRemoveItem = null },
            message = "确定从「$title」移除“${item.title.ifBlank { "未命名" }}”吗？",
            actions = listOf(
                FlatDialogAction("取消", onClick = { pendingRemoveItem = null }),
                FlatDialogAction(
                    text = "移除",
                    tone = FlatDialogActionTone.Danger,
                    onClick = {
                        pendingRemoveItem = null
                        onRemoveItem(item.mediaId)
                    }
                )
            )
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
    loadFileSize: Boolean,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = modifier
    ) {
        if (showTopDivider && !isDragging) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlaylistDetailHorizontalPadding)
                    .align(Alignment.TopCenter),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
        }
        val prefixSegments = remember(item.artist, item.albumCv) {
            listOf(item.artist, item.albumCv)
        }
        val meta = rememberAudioMeta(
            sourcePath = item.uri.ifBlank { item.mediaId },
            durationSeconds = item.duration,
            prefixSegments = prefixSegments,
            loadSize = loadFileSize
        )
        val actions = remember(onPlay, onMoveToTop, onMoveToBottom, onRemove) {
            listOf(
                AudioItemMenuAction(
                    label = "播放",
                    onClick = onPlay
                ),
                AudioItemMenuAction(
                    label = "移至顶部",
                    onClick = onMoveToTop,
                    testTag = PLAYLIST_DETAIL_MOVE_TOP_MENU_ITEM_TAG
                ),
                AudioItemMenuAction(
                    label = "移至末尾",
                    onClick = onMoveToBottom,
                    testTag = PLAYLIST_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG
                ),
                AudioItemMenuAction(
                    label = "移除",
                    onClick = onRemove,
                    showDividerBefore = true
                )
            )
        }
        AudioItemRow(
            title = item.title.ifBlank { "未命名" },
            subtitle = meta.leadingText,
            fixedTrailingSubtitle = meta.trailingText,
            showSubtitleStamp = showSubtitleStamp,
            menuButtonTestTag = "$PLAYLIST_DETAIL_ITEM_MENU_BUTTON_TAG_PREFIX:${item.mediaId}",
            onClick = onPlay,
            showClickIndication = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PlaylistDetailHorizontalPadding, vertical = 2.dp),
            leadingContent = {
                AsmrAsyncImage(
                    model = item.artworkUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholderCornerRadius = 6,
                    peekAnySizeForInitial = true,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            },
            titleTextStyle = MaterialTheme.typography.bodyMedium,
            actions = actions
        )
    }
}

private fun PlaylistItemWithSubtitles.toPlaybackEntity(): PlaylistItemEntity {
    return PlaylistItemEntity(
        playlistId = playlistId,
        mediaId = mediaId,
        title = title,
        artist = artist,
        albumTitle = albumTitle,
        uri = uri,
        artworkUri = playbackArtworkUri.ifBlank { artworkUri },
        albumId = albumId,
        trackId = trackId,
        rjCode = rjCode,
        albumWorkId = albumWorkId,
        trackGroup = trackGroup,
        lyricsRelativePathNoExt = lyricsRelativePathNoExt,
        mimeType = mimeType,
        isVideo = isVideo,
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
