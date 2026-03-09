package com.asmr.player.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.data.local.db.dao.AlbumGroupStatsRow
import com.asmr.player.data.local.db.entities.AlbumGroupEntity
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.withAddedBottomPadding
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun AlbumGroupsScreen(
    windowSizeClass: WindowSizeClass,
    onGroupClick: (AlbumGroupEntity) -> Unit,
    viewModel: AlbumGroupsViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsState()
    val colorScheme = AsmrTheme.colorScheme
    var showCreate by remember { mutableStateOf(false) }

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
                    items(groups, key = { it.id }) { group ->
                        val entity = remember(group.id, group.name, group.createdAt) {
                            AlbumGroupEntity(
                                id = group.id,
                                name = group.name,
                                createdAt = group.createdAt
                            )
                        }
                        AlbumGroupRow(
                            group = group,
                            onClick = { onGroupClick(entity) },
                            onDelete = { viewModel.deleteGroup(entity) },
                            onRename = { newName -> viewModel.renameGroup(entity.id, newName) }
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
        CreateAlbumGroupDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                viewModel.createGroup(name)
                showCreate = false
            }
        )
    }
}

@Composable
private fun AlbumGroupRow(
    group: AlbumGroupStatsRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val cover = remember(group.firstArtworkUri) { group.firstArtworkUri?.trim().orEmpty() }
    var showDeleteConfirm by remember(group.id) { mutableStateOf(false) }
    var showRename by remember(group.id) { mutableStateOf(false) }

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
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "共 ${group.albumCount} 张专辑 · ${group.itemCount} 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.textTertiary
                )
            }
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = colorScheme.textSecondary)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = colorScheme.danger.copy(alpha = 0.7f))
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
            text = { Text("确定要删除分组“${group.name}”吗？此操作不可撤销。") },
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
        RenameAlbumGroupDialog(
            initialName = group.name,
            onDismiss = { showRename = false },
            onRename = { newName ->
                showRename = false
                onRename(newName)
            }
        )
    }
}

@Composable
private fun CreateAlbumGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val colorScheme = AsmrTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = { Text("新建分组") },
        text = {
            androidx.compose.material3.TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("分组名称") },
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = colorScheme.surface,
                    unfocusedContainerColor = colorScheme.surface
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.trim().isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun RenameAlbumGroupDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val colorScheme = AsmrTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = { Text("重命名分组") },
        text = {
            androidx.compose.material3.TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("分组名称") },
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = colorScheme.surface,
                    unfocusedContainerColor = colorScheme.surface
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.trim().isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
