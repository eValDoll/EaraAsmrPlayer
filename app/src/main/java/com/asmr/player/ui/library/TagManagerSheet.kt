package com.asmr.player.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asmr.player.data.local.db.dao.TagWithCount
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun TagManagerSheet(
    tags: List<TagWithCount>,
    onRename: (tagId: Long, newName: String) -> Unit,
    onDelete: (tagId: Long) -> Unit,
    onClose: () -> Unit
) {
    var selected by remember { mutableStateOf<TagWithCount?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val colorScheme = AsmrTheme.colorScheme

    val visibleTags = remember(tags, filter) {
        val q = filter.trim().lowercase()
        tags
            .asSequence()
            .filter { it.userAlbumCount > 0L || it.albumCount == 0L }
            .filter { q.isBlank() || it.name.lowercase().contains(q) }
            .toList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp)) {
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(text = "标签管理", modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("完成") }
        }

        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("搜索标签") }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .thinScrollbar(listState)
        ) {
            items(visibleTags, key = { it.id }) { tag ->
                ListItem(
                    headlineContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = colorScheme.textPrimary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (tag.albumCount == 0L) {
                                    TagManagerMetricBadge(label = "未使用")
                                } else {
                                    TagManagerMetricBadge(
                                        label = "用户标注",
                                        value = tag.userAlbumCount.toString(),
                                        highlighted = true
                                    )
                                    TagManagerMetricBadge(
                                        label = "总计",
                                        value = tag.albumCount.toString()
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = {
                        Text(
                            text = "点击可重命名或删除",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textTertiary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = tag
                            renameText = tag.name
                            showRenameDialog = true
                        }
                )
            }
            item { Spacer(modifier = Modifier.padding(bottom = 16.dp)) }
        }
    }

    if (showRenameDialog) {
        val tag = selected
        if (tag != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("重命名标签") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRename(tag.id, renameText)
                            showRenameDialog = false
                        }
                    ) { Text("保存") }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                showRenameDialog = false
                                showDeleteDialog = true
                            }
                        ) { Text("删除") }
                        TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
                    }
                }
            )
        }
    }

    if (showDeleteDialog) {
        val tag = selected
        if (tag != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除标签") },
                text = { Text("将从所有用户标注中移除该标签。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDelete(tag.id)
                            showDeleteDialog = false
                        }
                    ) { Text("删除") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun TagManagerMetricBadge(
    label: String,
    value: String? = null,
    highlighted: Boolean = false
) {
    val colorScheme = AsmrTheme.colorScheme
    val containerColor = if (highlighted) {
        colorScheme.primary.copy(alpha = 0.12f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val borderColor = if (highlighted) {
        colorScheme.primary.copy(alpha = 0.18f)
    } else {
        colorScheme.textTertiary.copy(alpha = 0.18f)
    }
    val contentColor = if (highlighted) colorScheme.primary else colorScheme.textSecondary
    val valueContainerColor = if (highlighted) {
        colorScheme.primary.copy(alpha = 0.14f)
    } else {
        colorScheme.textTertiary.copy(alpha = 0.12f)
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
            if (value != null) {
                Surface(
                    color = valueContainerColor,
                    contentColor = contentColor,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = value,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}
