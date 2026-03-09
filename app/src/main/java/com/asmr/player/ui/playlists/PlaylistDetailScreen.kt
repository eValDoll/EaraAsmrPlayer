package com.asmr.player.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.data.local.db.entities.PlaylistItemEntity
import com.asmr.player.data.local.db.entities.PlaylistItemWithSubtitles
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.SubtitleStamp

import androidx.compose.foundation.lazy.itemsIndexed
import com.asmr.player.ui.common.LocalBottomOverlayPadding

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

    // 屏幕尺寸判断
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    val colorScheme = com.asmr.player.ui.theme.AsmrTheme.colorScheme
    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
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
                itemsIndexed(items, key = { idx, item -> "${item.mediaId}#$idx" }) { index, item ->
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
                        onRemove = {
                            pendingRemoveItem = item
                        }
                    )
                    if (index < items.size - 1) {
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

    val item = pendingRemoveItem
    if (item != null) {
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
                        viewModel.removeItem(item.mediaId)
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

@Composable
private fun PlaylistItemRow(
    item: PlaylistItemWithSubtitles,
    showSubtitleStamp: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val colorScheme = com.asmr.player.ui.theme.AsmrTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
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
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title.ifBlank { "未命名" }, 
                maxLines = 2, 
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.textPrimary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (showSubtitleStamp) {
            SubtitleStamp(modifier = Modifier.padding(end = 8.dp))
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
