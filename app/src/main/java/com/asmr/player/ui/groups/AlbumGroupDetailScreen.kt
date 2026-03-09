package com.asmr.player.ui.groups

import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asmr.player.data.local.db.dao.AlbumGroupTrackRow
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.Formatting
import kotlin.math.roundToLong

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
    val colorScheme = AsmrTheme.colorScheme

    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val expandedAlbumIds = rememberSaveable(groupId) { mutableStateOf(setOf<Long>()) }

    var pendingRemoveTrack by remember { mutableStateOf<AlbumGroupTrackRow?>(null) }
    var pendingRemoveAlbum by remember { mutableStateOf<Pair<Long, String>?>(null) }

    val sections = remember(tracks) {
        tracks.groupBy { it.albumId }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
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
                    title,
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
                        .ifBlank { "" }

                    val expanded = expandedAlbumIds.value.contains(albumId)
                    item(key = "header:$albumId") {
                        AlbumSectionHeader(
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
                            onRemoveAlbum = {
                                pendingRemoveAlbum = albumId to sectionTitle
                            }
                        )
                    }

                    if (expanded) {
                        itemsIndexed(list, key = { idx, item -> "${item.mediaId}#$idx" }) { index, item ->
                            GroupTrackRow(
                                item = item,
                                coverModel = coverModel,
                                onPlay = {
                                    val mediaItems = list.map { it.toMediaItem() }
                                    val startIndex = list.indexOfFirst { it.mediaId == item.mediaId }.coerceAtLeast(0)
                                    onPlayMediaItems(mediaItems, startIndex)
                                },
                                onRemove = { pendingRemoveTrack = item }
                            )
                            if (index < list.size - 1) {
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

    val track = pendingRemoveTrack
    if (track != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoveTrack = null },
            title = { Text("确认移除") },
            text = { Text("确定从「$title」移除“${track.trackTitle.ifBlank { "未命名" }}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveTrack = null
                        viewModel.removeTrack(track.mediaId)
                    }
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveTrack = null }) { Text("取消") }
            }
        )
    }

    val album = pendingRemoveAlbum
    if (album != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoveAlbum = null },
            title = { Text("确认移除") },
            text = { Text("确定从「$title」移除专辑“${album.second}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRemoveAlbum = null
                        viewModel.removeAlbum(album.first)
                    }
                ) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoveAlbum = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AlbumSectionHeader(
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
            .background(colorScheme.surface)
            .clickable { onToggle() }
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
                color = colorScheme.textTertiary,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        IconButton(onClick = onRemoveAlbum) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = colorScheme.danger.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun GroupTrackRow(
    item: AlbumGroupTrackRow,
    coverModel: Any?,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val durationMs = remember(item.trackDuration) { (item.trackDuration * 1000.0).roundToLong().coerceAtLeast(0L) }
    val subtitle = remember(durationMs) { Formatting.formatTrackTime(durationMs) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
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
            IconButton(onClick = { expanded = true }) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("播放") },
                    onClick = {
                        expanded = false
                        onPlay()
                    }
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
    return if (trimmed.startsWith("http", ignoreCase = true) ||
        trimmed.startsWith("content://", ignoreCase = true) ||
        trimmed.startsWith("file://", ignoreCase = true)
    ) {
        trimmed.toUri()
    } else {
        val f = java.io.File(trimmed)
        if (f.exists()) Uri.fromFile(f) else null
    }
}
