package com.asmr.player.ui.groups

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asmr.player.data.local.db.dao.AlbumGroupTrackRow
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AudioItemMenuAction
import com.asmr.player.ui.common.AudioItemRow
import com.asmr.player.ui.common.EaraBrandedEmptyState
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.queryTrackFileSize
import com.asmr.player.ui.common.rememberAudioMeta
import com.asmr.player.ui.common.rememberAudioMetaText
import com.asmr.player.ui.common.SubtitleStamp
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.rememberTrackMetaLine
import com.asmr.player.ui.common.reorderable.ItemPosition
import com.asmr.player.ui.common.reorderable.ReorderableItem
import com.asmr.player.ui.common.reorderable.detectReorderAfterLongPress
import com.asmr.player.ui.common.reorderable.rememberReorderableLazyListState
import com.asmr.player.ui.common.reorderable.reorderable
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.util.Formatting
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val GroupDetailHorizontalPadding = 8.dp
private val GroupAlbumHeaderCornerRadius = 10.dp
private val GroupActionButtonSize = 34.dp
private val GroupActionIconSize = 18.dp

internal const val GROUP_DETAIL_SECTION_HEADER_TAG_PREFIX = "groupDetailSectionHeader"
internal const val GROUP_DETAIL_TRACK_TAG_PREFIX = "groupDetailTrack"
internal const val GROUP_DETAIL_TRACK_MENU_BUTTON_TAG_PREFIX = "groupDetailTrackMenu"
internal const val GROUP_DETAIL_TRACK_SUBTITLE_STAMP_TAG_PREFIX = "groupDetailTrackSubtitleStamp"
internal const val GROUP_DETAIL_MOVE_TOP_MENU_ITEM_TAG = "groupDetailMoveTopMenuItem"
internal const val GROUP_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG = "groupDetailMoveBottomMenuItem"
internal const val GROUP_DETAIL_REORDER_DIALOG_TAG = "groupDetailReorderDialog"
internal const val GROUP_DETAIL_REORDER_ROW_TAG_PREFIX = "groupDetailReorderRow"

private const val GROUP_DETAIL_REORDER_SENTINEL_KEY = "__group_detail_reorder_sentinel__"

private sealed interface GroupDetailListRow {
    val key: String
}

private data class GroupDetailHeaderRow(
    val albumId: Long,
    val albumTitle: String,
    val rjCode: String,
    val coverModel: String,
    val tracks: List<AlbumGroupTrackRow>,
    val expanded: Boolean
) : GroupDetailListRow {
    override val key: String = "header:$albumId"
}

private data class GroupDetailTrackRow(
    val albumId: Long,
    val coverModel: String,
    val track: AlbumGroupTrackRow
) : GroupDetailListRow {
    override val key: String = track.mediaId
}

@Composable
fun AlbumGroupDetailScreen(
    windowSizeClass: WindowSizeClass,
    groupId: Long,
    title: String,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    scrollToTopSignal: Long = 0L,
    viewModel: AlbumGroupDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(groupId) {
        viewModel.setGroupId(groupId)
    }
    val tracks by viewModel.tracks.collectAsState()
    AlbumGroupDetailContent(
        windowSizeClass = windowSizeClass,
        title = title,
        tracks = tracks,
        onPlayMediaItems = onPlayMediaItems,
        onRemoveTrack = viewModel::removeTrack,
        onRemoveAlbum = viewModel::removeAlbum,
        onMoveTrackToTop = viewModel::moveTrackToTop,
        onMoveTrackToBottom = viewModel::moveTrackToBottom,
        onSaveAlbumTrackOrder = viewModel::saveAlbumTrackOrder,
        scrollToTopSignal = scrollToTopSignal,
    )
}

@Composable
internal fun AlbumGroupDetailContent(
    windowSizeClass: WindowSizeClass,
    title: String,
    tracks: List<AlbumGroupTrackRow>,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onRemoveTrack: (String) -> Unit,
    onRemoveAlbum: (Long) -> Unit,
    onMoveTrackToTop: (Long, String) -> Unit,
    onMoveTrackToBottom: (Long, String) -> Unit,
    onSaveAlbumTrackOrder: (Long, List<String>) -> Unit,
    scrollToTopSignal: Long = 0L,
) {
    val colorScheme = AsmrTheme.colorScheme
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val draggedTrackShape = RoundedCornerShape(18.dp)
    val draggedTrackContainerColor = dynamicPageContainerColor(colorScheme)
    val draggedTrackElevation = if (colorScheme.isDark) 10.dp else 14.dp
    val listState = rememberLazyListState()
    val expandedAlbumIds = rememberSaveable { mutableStateOf(setOf<Long>()) }
    val localRows = remember { mutableStateListOf<GroupDetailListRow>() }
    var pendingRemoveTrack by remember { mutableStateOf<AlbumGroupTrackRow?>(null) }
    var pendingRemoveAlbum by remember { mutableStateOf<Pair<Long, String>?>(null) }

    val reorderState = rememberReorderableLazyListState(
        listState = listState,
        maxScrollPerFrame = 28.dp,
        scrollTriggerPadding = 112.dp,
        onMove = { from, to ->
            localRows.moveTrackRows(from, to)
        },
        canDragOver = { draggedOver, dragging ->
            val target = localRows.findTrackRowByKey(draggedOver.key)
            val dragged = localRows.findTrackRowByKey(dragging.key)
            target != null && dragged != null && target.albumId == dragged.albumId
        },
        onDragEnd = { startIndex, endIndex ->
            val albumId = localRows.resolveDraggedAlbumId(startIndex, endIndex) ?: return@rememberReorderableLazyListState
            onSaveAlbumTrackOrder(albumId, localRows.mediaIdsForAlbum(albumId))
        }
    )

    LaunchedEffect(tracks, expandedAlbumIds.value) {
        if (reorderState.draggingItemIndex == null) {
            localRows.sync(buildGroupDetailRows(tracks, expandedAlbumIds.value))
        }
    }
    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal == 0L) return@LaunchedEffect
        runCatching { listState.animateScrollToItem(0) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(StableWindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
            }
        ) {
            if (tracks.isEmpty()) {
                EaraBrandedEmptyState(
                    sectionTitle = title.ifBlank { "我的分组" },
                    headline = "这个分组还没有内容",
                    description = "把专辑加入分组后，它们会按专辑整理展示在这里。",
                    sectionIcon = Icons.Default.Folder,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current + 88.dp)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .reorderable(reorderState)
                        .thinScrollbar(listState),
                    contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current)
                ) {
                    item(key = GROUP_DETAIL_REORDER_SENTINEL_KEY) {
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                    itemsIndexed(localRows, key = { _, row -> row.key }) { index, row ->
                        when (row) {
                            is GroupDetailHeaderRow -> {
                                AlbumSectionHeader(
                                    albumId = row.albumId,
                                    albumTitle = row.albumTitle,
                                    rjCode = row.rjCode,
                                    coverModel = row.coverModel,
                                    tracks = row.tracks,
                                    expanded = row.expanded,
                                    onToggle = {
                                        expandedAlbumIds.value = if (row.expanded) {
                                            expandedAlbumIds.value - row.albumId
                                        } else {
                                            expandedAlbumIds.value + row.albumId
                                        }
                                    },
                                    onRemoveAlbum = {
                                        pendingRemoveAlbum = row.albumId to row.albumTitle
                                    }
                                )
                            }

                            is GroupDetailTrackRow -> {
                                ReorderableItem(
                                    reorderableState = reorderState,
                                    key = row.track.mediaId,
                                    modifier = Modifier.fillMaxWidth(),
                                    draggingDecorationModifier = Modifier
                                        .shadow(draggedTrackElevation, draggedTrackShape, clip = false)
                                        .clip(draggedTrackShape)
                                        .background(draggedTrackContainerColor)
                                ) { isDragging ->
                                    val albumTracks = localRows.tracksForAlbum(row.albumId)
                                    val startIndex = albumTracks.indexOfFirst { it.mediaId == row.track.mediaId }
                                        .coerceAtLeast(0)
                                    GroupTrackRow(
                                        item = row.track,
                                        coverModel = row.coverModel,
                                        showTopDivider = index > 0 && localRows[index - 1] is GroupDetailTrackRow,
                                        isDragging = isDragging,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("$GROUP_DETAIL_TRACK_TAG_PREFIX:${row.track.mediaId}")
                                            .detectReorderAfterLongPress(reorderState),
                                        onPlay = {
                                            onPlayMediaItems(
                                                albumTracks.map { it.toMediaItem() },
                                                startIndex
                                            )
                                        },
                                        onMoveToTop = { onMoveTrackToTop(row.albumId, row.track.mediaId) },
                                        onMoveToBottom = { onMoveTrackToBottom(row.albumId, row.track.mediaId) },
                                        onRemove = { pendingRemoveTrack = row.track }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRemoveTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { pendingRemoveTrack = null },
            title = { Text("确认移除") },
            text = { Text("确定从「$title」移除“${track.trackTitle.ifBlank { "未命名" }}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveTrack = null
                        onRemoveTrack(track.mediaId)
                    }
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveTrack = null }) { Text("取消") }
            }
        )
    }

    pendingRemoveAlbum?.let { album ->
        AlertDialog(
            onDismissRequest = { pendingRemoveAlbum = null },
            title = { Text("确认移除") },
            text = { Text("确定从「$title」移除专辑“${album.second}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveAlbum = null
                        onRemoveAlbum(album.first)
                    }
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveAlbum = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumSectionHeader(
    albumId: Long,
    albumTitle: String,
    rjCode: String,
    coverModel: Any?,
    tracks: List<AlbumGroupTrackRow>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRemoveAlbum: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val context = LocalContext.current
    val totalSizeBytes by androidx.compose.runtime.produceState<Long?>(initialValue = null, tracks) {
        value = withContext(Dispatchers.IO) {
            tracks.sumOf { row -> queryTrackFileSize(context, row.trackPath) ?: 0L }
                .takeIf { it > 0L }
        }
    }
    val footerSegments = remember(rjCode, tracks, totalSizeBytes) {
        buildList {
            if (rjCode.isNotBlank()) add(rjCode)
            add("${tracks.size} 音频")
            Formatting.formatTrackSeconds(tracks.sumOf { it.trackDuration }).takeIf { it.isNotBlank() }?.let(::add)
            totalSizeBytes?.let(Formatting::formatFileSize)?.let(::add)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$GROUP_DETAIL_SECTION_HEADER_TAG_PREFIX:$albumId")
            .clip(RoundedCornerShape(GroupAlbumHeaderCornerRadius))
            .background(colorScheme.surface.copy(alpha = 0.5f))
            .clickable { onToggle() }
            .padding(horizontal = GroupDetailHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsmrAsyncImage(
            model = coverModel?.toString().orEmpty(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = 8,
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = albumTitle.ifBlank { rjCode.ifBlank { "专辑" } },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = footerSegments.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemoveAlbum, modifier = Modifier.size(GroupActionButtonSize)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = colorScheme.danger.copy(alpha = 0.7f),
                modifier = Modifier.size(GroupActionIconSize)
            )
        }
    }
}

@Composable
private fun GroupTrackRow(
    item: AlbumGroupTrackRow,
    coverModel: Any?,
    showTopDivider: Boolean,
    isDragging: Boolean,
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
                    .padding(horizontal = GroupDetailHorizontalPadding)
                    .align(Alignment.TopCenter),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
        }
        val meta = rememberAudioMeta(
            sourcePath = item.trackPath,
            durationSeconds = item.trackDuration,
            prefixSegments = listOf(item.albumCv.orEmpty())
        )
        AudioItemRow(
            title = item.trackTitle.ifBlank { "未命名" },
            subtitle = meta.leadingText,
            fixedTrailingSubtitle = meta.trailingText,
            showSubtitleStamp = item.hasSubtitles,
            subtitleStampTestTag = "$GROUP_DETAIL_TRACK_SUBTITLE_STAMP_TAG_PREFIX:${item.mediaId}",
            menuButtonTestTag = "$GROUP_DETAIL_TRACK_MENU_BUTTON_TAG_PREFIX:${item.mediaId}",
            onClick = onPlay,
            showClickIndication = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GroupDetailHorizontalPadding, vertical = 2.dp),
            leadingContent = {
                AsmrAsyncImage(
                    model = coverModel?.toString().orEmpty(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholderCornerRadius = 6,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            },
            titleTextStyle = MaterialTheme.typography.bodyMedium,
            actions = listOf(
                AudioItemMenuAction(
                    label = "播放",
                    onClick = onPlay
                ),
                AudioItemMenuAction(
                    label = "移至顶部",
                    onClick = onMoveToTop,
                    testTag = GROUP_DETAIL_MOVE_TOP_MENU_ITEM_TAG
                ),
                AudioItemMenuAction(
                    label = "移至末尾",
                    onClick = onMoveToBottom,
                    testTag = GROUP_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG
                ),
                AudioItemMenuAction(
                    label = "从分组移除",
                    onClick = onRemove,
                    showDividerBefore = true
                )
            )
        )
    }
}

private fun buildGroupDetailRows(
    tracks: List<AlbumGroupTrackRow>,
    expandedAlbumIds: Set<Long>
): List<GroupDetailListRow> {
    val sections = tracks.groupBy { it.albumId }
    val rows = mutableListOf<GroupDetailListRow>()
    sections.forEach { (albumId, list) ->
        val first = list.firstOrNull() ?: return@forEach
        val sectionTitle = first.albumTitle.orEmpty().ifBlank {
            first.albumRjCode.orEmpty().ifBlank { "专辑" }
        }
        val coverModel = first.albumCoverThumbPath.orEmpty()
            .ifBlank { first.albumCoverPath.orEmpty() }
            .ifBlank { first.albumCoverUrl.orEmpty() }
            .trim()
        val expanded = expandedAlbumIds.contains(albumId)
        rows += GroupDetailHeaderRow(
            albumId = albumId,
            albumTitle = sectionTitle,
            rjCode = first.albumRjCode.orEmpty(),
            coverModel = coverModel,
            tracks = list,
            expanded = expanded
        )
        if (expanded) {
            rows += list.map { track ->
                GroupDetailTrackRow(
                    albumId = albumId,
                    coverModel = coverModel,
                    track = track
                )
            }
        }
    }
    return rows
}

private fun SnapshotStateList<GroupDetailListRow>.sync(rows: List<GroupDetailListRow>) {
    clear()
    addAll(rows)
}

private fun SnapshotStateList<GroupDetailListRow>.findTrackRowByKey(key: Any?): GroupDetailTrackRow? {
    return firstOrNull { row -> row is GroupDetailTrackRow && row.track.mediaId == key } as? GroupDetailTrackRow
}

private fun SnapshotStateList<GroupDetailListRow>.tracksForAlbum(albumId: Long): List<AlbumGroupTrackRow> {
    return filterIsInstance<GroupDetailTrackRow>()
        .filter { row -> row.albumId == albumId }
        .map { row -> row.track }
}

private fun SnapshotStateList<GroupDetailListRow>.mediaIdsForAlbum(albumId: Long): List<String> {
    return tracksForAlbum(albumId).map { track -> track.mediaId }
}

private fun SnapshotStateList<GroupDetailListRow>.resolveDraggedAlbumId(
    startIndex: Int,
    endIndex: Int
): Long? {
    val endRow = getOrNull(endIndex - 1) as? GroupDetailTrackRow
    if (endRow != null) return endRow.albumId
    val startRow = getOrNull(startIndex - 1) as? GroupDetailTrackRow
    return startRow?.albumId
}

private fun SnapshotStateList<GroupDetailListRow>.moveTrackRows(
    from: ItemPosition,
    to: ItemPosition
) {
    if (isEmpty()) return
    val fromIndex = from.index - 1
    val toIndex = to.index - 1
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return
    val fromRow = getOrNull(fromIndex) as? GroupDetailTrackRow ?: return
    val toRow = getOrNull(toIndex) as? GroupDetailTrackRow ?: return
    if (fromRow.albumId != toRow.albumId) return
    add(toIndex, removeAt(fromIndex))
}

private fun AlbumGroupTrackRow.toMediaItem(): MediaItem {
    val uri = toPlayableUri(trackPath)
    val artwork = albumCoverPath.orEmpty()
        .ifBlank { albumCoverUrl.orEmpty() }
        .ifBlank { albumCoverThumbPath.orEmpty() }
        .trim()
    val metadata = MediaMetadata.Builder()
        .setTitle(trackTitle)
        .setAlbumTitle(albumTitle.orEmpty())
        .setArtworkUri(artwork.toArtworkUriOrNull())
        .build()
    return MediaItem.Builder()
        .setMediaId(trackPath)
        .setUri(uri)
        .setMediaMetadata(metadata)
        .build()
}

private fun toPlayableUri(path: String): Uri {
    val trimmed = path.trim()
    return if (
        trimmed.startsWith("http", ignoreCase = true) ||
        trimmed.startsWith("content://", ignoreCase = true) ||
        trimmed.startsWith("file://", ignoreCase = true)
    ) {
        trimmed.toUri()
    } else {
        Uri.fromFile(java.io.File(trimmed))
    }
}

private fun String.toArtworkUriOrNull(): Uri? {
    val trimmed = trim()
    if (trimmed.isBlank()) return null
    return if (
        trimmed.startsWith("http", ignoreCase = true) ||
        trimmed.startsWith("content://", ignoreCase = true) ||
        trimmed.startsWith("file://", ignoreCase = true)
    ) {
        trimmed.toUri()
    } else {
        val file = java.io.File(trimmed)
        if (file.exists()) Uri.fromFile(file) else null
    }
}
