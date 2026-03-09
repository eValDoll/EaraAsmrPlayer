package com.asmr.player.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.asmr.player.data.local.db.dao.TagWithCount

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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
        ) {
            items(visibleTags, key = { it.id }) { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    supportingContent = {
                        if (tag.albumCount == 0L) {
                            Text("未使用")
                        } else {
                            Text("用户标注 ${tag.userAlbumCount} / 总计 ${tag.albumCount}")
                        }
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
