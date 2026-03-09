package com.asmr.player.ui.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

@Composable
fun DownloadsScreen(
    windowSizeClass: WindowSizeClass,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val expandedTasks = remember { mutableStateListOf<Long>() }
    val context = LocalContext.current
    var rjQuery by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<PendingDeleteAction?>(null) }
    val downloadRoot = remember {
        File(context.getExternalFilesDir(null), "albums").absolutePath
    }

    // 屏幕尺寸判断
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter // 仅用于平板适配：居中显示内容
    ) {
        Column(
            modifier = if (isCompact) {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            } else {
                // 仅用于平板适配：限制内容区域最大宽度并填充可用空间
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = rjQuery,
                onValueChange = { rjQuery = it },
                label = { Text("RJ号精准搜索") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "下载目录：$downloadRoot",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val normalizedQuery = remember(rjQuery) {
                val raw = rjQuery.trim()
                when {
                    raw.isBlank() -> ""
                    raw.startsWith("RJ", ignoreCase = true) -> "RJ" + raw.substring(2).trim()
                    raw.all { it.isDigit() } -> "RJ$raw"
                    else -> raw
                }
            }
            val shownTasks = remember(tasks, normalizedQuery) {
                if (normalizedQuery.isBlank()) tasks
                else tasks.filter { it.title.equals(normalizedQuery, ignoreCase = true) }
            }

            if (shownTasks.isEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(if (normalizedQuery.isBlank()) "暂无下载任务" else "未找到任务：$normalizedQuery", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current)
                ) {
                    items(shownTasks, key = { it.taskId }) { task ->
                        DownloadTaskCard(
                            task = task,
                            expanded = expandedTasks.contains(task.taskId),
                            onToggleExpanded = {
                                if (expandedTasks.contains(task.taskId)) expandedTasks.remove(task.taskId) else expandedTasks.add(task.taskId)
                            },
                            onRequestDeleteTask = { pendingDelete = PendingDeleteAction.Task(task.taskId) },
                            onPauseItem = { viewModel.pauseItem(it) },
                            onResumeItem = { viewModel.resumeItem(it) },
                            onRetryItem = { viewModel.retryItem(it) },
                            onDeleteItem = { pendingDelete = PendingDeleteAction.Item(workId = it) },
                            onRetryFailedInTask = { viewModel.retryFailedInTask(task.taskId) }
                        )
                    }
                }
            }
        }

        val action = pendingDelete
        if (action != null) {
            val resolved = remember(action, tasks) {
                when (action) {
                    is PendingDeleteAction.Task -> {
                        val task = tasks.firstOrNull { it.taskId == action.taskId } ?: return@remember null
                        ResolvedDeleteText(
                            title = "确认删除",
                            message = "将物理删除“${task.title}”目录下的文件（不可恢复）。"
                        )
                    }
                    is PendingDeleteAction.Item -> {
                        val item = tasks.asSequence()
                            .flatMap { it.items.asSequence() }
                            .firstOrNull { it.workId == action.workId } ?: return@remember null
                        ResolvedDeleteText(
                            title = "确认删除",
                            message = "将物理删除文件“${item.fileName}”（不可恢复）。"
                        )
                    }
                }
            }
            if (resolved != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    DeleteConfirmBanner(
                        title = resolved.title,
                        message = resolved.message,
                        onConfirm = {
                            pendingDelete = null
                            when (action) {
                                is PendingDeleteAction.Task -> viewModel.deleteTask(action.taskId)
                                is PendingDeleteAction.Item -> viewModel.deleteItem(action.workId)
                            }
                        },
                        onDismiss = { pendingDelete = null }
                    )
                }
            } else {
                pendingDelete = null
            }
        }
    }
}

private sealed class PendingDeleteAction {
    data class Task(val taskId: Long) : PendingDeleteAction()
    data class Item(val workId: String) : PendingDeleteAction()
}

private data class ResolvedDeleteText(
    val title: String,
    val message: String
)

@Composable
private fun DeleteConfirmBanner(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = onConfirm) { Text("删除") }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskUi,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onRequestDeleteTask: () -> Unit,
    onPauseItem: (String) -> Unit,
    onResumeItem: (String) -> Unit,
    onRetryItem: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onRetryFailedInTask: () -> Unit
) {
    val folderExpanded = remember(task.taskId) { mutableStateListOf<String>() }
    val treeEntries = remember(task.items, folderExpanded.toList()) {
        flattenDownloadTreeForUi(task.items, folderExpanded.toSet())
    }
    val hasFailedItems = remember(task.items) { task.items.any { it.state == DownloadItemState.FAILED } }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        task.subtitle.ifBlank { task.rootDir.substringAfterLast('\\').substringAfterLast('/') },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onRequestDeleteTask
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
                if (hasFailedItems) {
                    IconButton(onClick = onRetryFailedInTask) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
            }

            when {
                task.progressFraction != null -> {
                    LinearProgressIndicator(progress = task.progressFraction, modifier = Modifier.fillMaxWidth())
                }
                task.hasUnknownTotalRunning -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    treeEntries.forEachIndexed { index, entry ->
                        when (entry) {
                            is DownloadTreeUiEntry.Folder -> {
                                DownloadFolderRow(
                                    title = entry.title,
                                    depth = entry.depth,
                                    expanded = folderExpanded.contains(entry.path),
                                    onToggle = {
                                        if (folderExpanded.contains(entry.path)) folderExpanded.remove(entry.path) else folderExpanded.add(entry.path)
                                    }
                                )
                            }
                            is DownloadTreeUiEntry.File -> {
                                DownloadFileRow(
                                    item = entry.item,
                                    depth = entry.depth,
                                    onPause = { onPauseItem(entry.item.workId) },
                                    onResume = { onResumeItem(entry.item.workId) },
                                    onRetry = { onRetryItem(entry.item.workId) },
                                    onDelete = { onDeleteItem(entry.item.workId) }
                                )
                            }
                        }
                        if (index < treeEntries.size - 1) {
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

private sealed class DownloadTreeUiEntry {
    abstract val path: String
    abstract val title: String
    abstract val depth: Int

    data class Folder(
        override val path: String,
        override val title: String,
        override val depth: Int
    ) : DownloadTreeUiEntry()

    data class File(
        override val path: String,
        override val title: String,
        override val depth: Int,
        val item: DownloadItemUi
    ) : DownloadTreeUiEntry()
}

private fun flattenDownloadTreeForUi(
    items: List<DownloadItemUi>,
    expanded: Set<String>
): List<DownloadTreeUiEntry> {
    data class Node(
        val name: String,
        val path: String,
        val children: MutableMap<String, Node> = linkedMapOf(),
        var item: DownloadItemUi? = null
    )

    val root = Node(name = "", path = "")
    items.forEach { it ->
        val rel = it.relativePath.replace('\\', '/').trim().trimStart('/')
        val segments = rel.split('/').filter { seg -> seg.isNotBlank() }
        if (segments.isEmpty()) return@forEach
        var cur = root
        segments.forEachIndexed { idx, seg ->
            val isLeaf = idx == segments.lastIndex
            val nextPath = if (cur.path.isBlank()) seg else "${cur.path}/$seg"
            val child = cur.children.getOrPut(seg) { Node(name = seg, path = nextPath) }
            if (isLeaf) child.item = it
            cur = child
        }
    }

    fun nodeKey(n: Node): String = n.name.lowercase()
    val out = mutableListOf<DownloadTreeUiEntry>()
    fun walk(node: Node, depth: Int) {
        val folders = node.children.values.filter { it.children.isNotEmpty() }.sortedBy(::nodeKey)
        val files = node.children.values.filter { it.children.isEmpty() && it.item != null }.sortedBy(::nodeKey)
        folders.forEach { f ->
            out.add(DownloadTreeUiEntry.Folder(path = f.path, title = f.name, depth = depth))
            if (expanded.contains(f.path)) walk(f, depth + 1)
        }
        files.forEach { f ->
            val item = f.item ?: return@forEach
            out.add(DownloadTreeUiEntry.File(path = f.path, title = f.name, depth = depth, item = item))
        }
    }
    walk(root, 0)
    return out
}

@Composable
private fun DownloadFolderRow(
    title: String,
    depth: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (depth > 0) Spacer(modifier = Modifier.width((depth * 16).dp))
                Icon(Icons.Default.Folder, contentDescription = null)
            }
        },
        trailingContent = {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                contentDescription = null
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun DownloadFileRow(
    item: DownloadItemUi,
    depth: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val percent: Int? = when {
        item.state == DownloadItemState.SUCCEEDED -> 100
        item.total > 0 -> ((item.downloaded * 100 / item.total).toInt().coerceIn(0, 100))
        else -> null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                val text = buildString {
                    append(item.state.name)
                    if (percent != null) append(" · $percent%")
                }
                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (depth > 0) Spacer(modifier = Modifier.width((depth * 16).dp))
                    Icon(pickFileIcon(item.fileName), contentDescription = null)
                }
            },
            trailingContent = {
                Row {
                    when (item.state) {
                        DownloadItemState.RUNNING, DownloadItemState.ENQUEUED -> {
                            IconButton(onClick = onPause) { Icon(Icons.Default.Pause, contentDescription = null) }
                        }
                        DownloadItemState.PAUSED, DownloadItemState.CANCELLED -> {
                            IconButton(onClick = onResume) { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                        }
                        DownloadItemState.FAILED -> {
                            IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = null) }
                        }
                        else -> {}
                    }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = null) }
                }
            }
        )
        when {
            percent != null -> LinearProgressIndicator(progress = percent / 100f, modifier = Modifier.fillMaxWidth())
            item.state == DownloadItemState.SUCCEEDED -> LinearProgressIndicator(progress = 1f, modifier = Modifier.fillMaxWidth())
            item.total <= 0 && (item.state == DownloadItemState.RUNNING || item.state == DownloadItemState.ENQUEUED) -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun pickFileIcon(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext in setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus") -> Icons.Default.MusicNote
        ext in setOf("lrc", "srt", "vtt") -> Icons.Default.Subtitles
        ext in setOf("jpg", "jpeg", "png", "webp") -> Icons.Default.Image
        ext in setOf("mp4", "m4v", "webm", "mkv", "mov") -> Icons.Default.Movie
        else -> Icons.Default.InsertDriveFile
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return ""
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format("%.1f MB/s", mb)
        kb >= 1.0 -> String.format("%.1f KB/s", kb)
        else -> "$bytesPerSec B/s"
    }
}
