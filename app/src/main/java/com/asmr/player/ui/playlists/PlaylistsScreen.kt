package com.asmr.player.ui.playlists

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
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

import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.font.FontWeight
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.withAddedBottomPadding

@Composable
fun PlaylistsScreen(
    windowSizeClass: WindowSizeClass,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val colorScheme = AsmrTheme.colorScheme
    var showCreate by remember { mutableStateOf(false) }
    val userPlaylists = remember(playlists) { playlists.filter { it.category == "user" } }

    // 屏幕尺寸判断
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

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
            Box(modifier = contentModifier) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
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
                            onClick = { onPlaylistClick(entity) },
                            onDelete = { viewModel.deletePlaylist(entity) },
                            onRename = { newName -> viewModel.renamePlaylist(entity.id, newName) }
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { showCreate = true },
                    containerColor = colorScheme.primaryContainer,
                    contentColor = colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = LocalBottomOverlayPadding.current + 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
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
            .background(colorScheme.surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsmrAsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholderCornerRadius = 12,
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
                    text = "共 ${playlist.itemCount} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.textTertiary
                )
            }
            IconButton(
                onClick = { showRename = true },
                enabled = playlist.category != "system"
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = if (playlist.category != "system") colorScheme.textSecondary else colorScheme.textTertiary
                )
            }
            IconButton(
                onClick = { showDeleteConfirm = true },
                enabled = playlist.category != "system"
            ) {
                Icon(
                    Icons.Default.Delete, 
                    contentDescription = null, 
                    tint = if (playlist.category != "system") colorScheme.danger.copy(alpha = 0.7f) else colorScheme.textTertiary
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = colorScheme.surface,
            titleContentColor = colorScheme.textPrimary,
            textContentColor = colorScheme.textSecondary,
            title = { Text("确认删除", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
            text = { Text("确定要删除列表“${playlist.name}”吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.danger)
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.textSecondary)
                ) {
                    Text("取消")
                }
            }
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
    val colorScheme = AsmrTheme.colorScheme
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        titleContentColor = colorScheme.textPrimary,
        textContentColor = colorScheme.textSecondary,
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.textSecondary)
            ) {
                Text("取消")
            }
        },
        title = { Text("新建列表", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("请输入列表名称", color = colorScheme.textTertiary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colorScheme.background,
                    unfocusedContainerColor = colorScheme.background,
                    focusedIndicatorColor = colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colorScheme.textPrimary,
                    unfocusedTextColor = colorScheme.textSecondary
                )
            )
        }
    )
}

@Composable
private fun RenamePlaylistDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val colorScheme = AsmrTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        titleContentColor = colorScheme.textPrimary,
        textContentColor = colorScheme.textSecondary,
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.isNotBlank() && name.trim() != initialName.trim(),
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.primary)
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = colorScheme.textSecondary)
            ) {
                Text("取消")
            }
        },
        title = { Text("重命名列表", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colorScheme.background,
                    unfocusedContainerColor = colorScheme.background,
                    focusedIndicatorColor = colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colorScheme.textPrimary,
                    unfocusedTextColor = colorScheme.textSecondary
                )
            )
        }
    )
}
