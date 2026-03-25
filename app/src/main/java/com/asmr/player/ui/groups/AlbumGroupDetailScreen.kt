package com.asmr.player.ui.groups

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asmr.player.data.local.db.dao.AlbumGroupTrackRow
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.ManualReorderDialog
import com.asmr.player.ui.common.ManualReorderListItem
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.util.Formatting
import kotlin.math.roundToLong

internal const val GROUP_DETAIL_SECTION_HEADER_TAG_PREFIX = "groupDetailSectionHeader"
internal const val GROUP_DETAIL_TRACK_TAG_PREFIX = "groupDetailTrack"
internal const val GROUP_DETAIL_TRACK_MENU_BUTTON_TAG_PREFIX = "groupDetailTrackMenu"
internal const val GROUP_DETAIL_MOVE_TOP_MENU_ITEM_TAG = "groupDetailMoveTopMenuItem"
internal const val GROUP_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG = "groupDetailMoveBottomMenuItem"
internal const val GROUP_DETAIL_REORDER_DIALOG_TAG = "groupDetailReorderDialog"
internal const val GROUP_DETAIL_REORDER_ROW_TAG_PREFIX = "groupDetailReorderRow"

private data class GroupReorderSession(
    val albumId: Long,
    val albumTitle: String,
    val coverModel: String,
    val initialMediaId: String,
    val items: List<AlbumGroupTrackRow>
)

@Composable
fun AlbumGroupDetailScreen(
    windowSizeClass: WindowSizeClass,
    groupId: Long,
    title: String,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
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
        onSaveAlbumTrackOrder = viewModel::saveAlbumTrackOrder
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
    onSaveAlbumTrackOrder: (Long, List<String>) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val expandedAlbumIds = rememberSaveable { mutableStateOf(setOf<Long>()) }
    var pendingRemoveTrack by remember { mutableStateOf<AlbumGroupTrackRow?>(null) }
    var pendingRemoveAlbum by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var reorderSession by remember { mutableStateOf<GroupReorderSession?>(null) }

    val sections = remember(tracks) { tracks.groupBy { it.albumId } }

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
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.textPrimary
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current)
            ) {
                sections.forEach { (albumId, list) ->
                    val first = list.firstOrNull()
                    val sectionTitle = (first?.albumTitle ?: "").ifBlank {
                        (first?.albumRjCode ?: "").ifBlank { "专辑" }
                    }
                    val rj = first?.albumRjCode.orEmpty()
                    val coverModel = first?.albumCoverThumbPath.orEmpty()
                        .ifBlank { first?.albumCoverPath.orEmpty() }
                        .ifBlank { first?.albumCoverUrl.orEmpty() }
                        .trim()
                    val expanded = expandedAlbumIds.value.contains(albumId)

                    item(key = "header:$albumId") {
                        AlbumSectionHeader(
                            albumId = albumId,
                            albumTitle = sectionTitle,
                            rjCode = rj,
                            coverModel = coverModel,
                            expanded = expanded,
                            onToggle = {
                                expandedAlbumIds.value = if (expanded) {
                                    expandedAlbumIds.value - albumId
                                } else {
                                    expandedAlbumIds.value + albumId
                                }
                            },
                            onRemoveAlbum = { pendingRemoveAlbum = albumId to sectionTitle }
                        )
                    }

                    if (expanded) {
                        itemsIndexed(list, key = { _, item -> item.mediaId }) { index, item ->
                            GroupTrackRow(
                                item = item,
                                coverModel = coverModel,
                                onPlay = {
                                    val mediaItems = list.map { track -> track.toMediaItem() }
                                    val startIndex = list.indexOfFirst { track -> track.mediaId == item.mediaId }
                                        .coerceAtLeast(0)
                                    onPlayMediaItems(mediaItems, startIndex)
                                },
                                onLongPress = {
                                    if (list.size > 1) {
                                        reorderSession = GroupReorderSession(
                                            albumId = albumId,
                                            albumTitle = sectionTitle,
                                            coverModel = coverModel,
                                            initialMediaId = item.mediaId,
                                            items = list
                                        )
                                    }
                                },
                                onMoveToTop = { onMoveTrackToTop(albumId, item.mediaId) },
                                onMoveToBottom = { onMoveTrackToBottom(albumId, item.mediaId) },
                                onRemove = { pendingRemoveTrack = item }
                            )
                            if (index < list.lastIndex) {
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

    reorderSession?.let { session ->
        ManualReorderDialog(
            title = "手动排序",
            items = session.items.map { track ->
                val durationMs = (track.trackDuration * 1000.0).roundToLong().coerceAtLeast(0L)
                ManualReorderListItem(
                    key = track.mediaId,
                    title = track.trackTitle.ifBlank { "未命名" },
                    subtitle = Formatting.formatTrackTime(durationMs),
                    artworkModel = session.coverModel,
                    supportingText = session.albumTitle
                )
            },
            initialKey = session.initialMediaId,
            dialogTag = GROUP_DETAIL_REORDER_DIALOG_TAG,
            rowTagPrefix = GROUP_DETAIL_REORDER_ROW_TAG_PREFIX,
            onDismiss = { reorderSession = null },
            onOrderCommitted = { orderedMediaIds ->
                onSaveAlbumTrackOrder(session.albumId, orderedMediaIds)
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
    expanded: Boolean,
    onToggle: () -> Unit,
    onRemoveAlbum: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$GROUP_DETAIL_SECTION_HEADER_TAG_PREFIX:$albumId")
            .background(colorScheme.surface)
            .combinedClickable(onClick = onToggle, onLongClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = albumTitle.ifBlank { rjCode.ifBlank { "专辑" } },
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (rjCode.isNotBlank()) {
                Text(
                    text = if (expanded) "$rjCode · 已展开" else "$rjCode · 已折叠",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onRemoveAlbum) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = colorScheme.danger.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupTrackRow(
    item: AlbumGroupTrackRow,
    coverModel: Any?,
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
    val durationMs = remember(item.trackDuration) { (item.trackDuration * 1000.0).roundToLong().coerceAtLeast(0L) }
    val subtitle = remember(durationMs) { Formatting.formatTrackTime(durationMs) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$GROUP_DETAIL_TRACK_TAG_PREFIX:${item.mediaId}")
            .combinedClickable(
                onClick = onPlay,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsmrAsyncImage(
            model = coverModel?.toString().orEmpty(),
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
                text = item.trackTitle.ifBlank { "未命名" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textTertiary
            )
        }
        Box {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.testTag("$GROUP_DETAIL_TRACK_MENU_BUTTON_TAG_PREFIX:${item.mediaId}")
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
                        modifier = Modifier.testTag(GROUP_DETAIL_MOVE_TOP_MENU_ITEM_TAG),
                        onClick = {
                            expanded = false
                            onMoveToTop()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("移至末尾") },
                        modifier = Modifier.testTag(GROUP_DETAIL_MOVE_BOTTOM_MENU_ITEM_TAG),
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
                        text = { Text("从分组移除") },
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
