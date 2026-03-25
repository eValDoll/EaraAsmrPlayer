package com.asmr.player.ui.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
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
import com.asmr.player.ui.theme.AsmrTheme
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.ui.common.thinScrollbar
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
    val listState = rememberLazyListState()

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
                    state = listState,
                    modifier = Modifier.thinScrollbar(listState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = LocalBottomOverlayPadding.current + 6.dp
                    )
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
                            onRetryFailedInTask = { viewModel.retryFailedInTask(task.taskId) },
                            onPauseTask = { viewModel.pauseTask(task.taskId) },
                            onResumeTask = { viewModel.resumeTask(task.taskId) }
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
                AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    title = { Text(resolved.title) },
                    text = { Text(resolved.message) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pendingDelete = null
                                when (action) {
                                    is PendingDeleteAction.Task -> viewModel.deleteTask(action.taskId)
                                    is PendingDeleteAction.Item -> viewModel.deleteItem(action.workId)
                                }
                            }
                        ) {
                            Text("删除")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) {
                            Text("取消")
                        }
                    }
                )
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
    onRetryFailedInTask: () -> Unit,
    onPauseTask: () -> Unit,
    onResumeTask: () -> Unit
) {
    val folderExpanded = remember(task.taskId) { mutableStateListOf<String>() }
    val treeEntries = remember(task.items, folderExpanded.toList()) {
        flattenDownloadTreeForUi(task.items, folderExpanded.toSet())
    }
    val hasFailedItems = remember(task.items) { task.items.any { it.state == DownloadItemState.FAILED } }
    val hasActiveItems = remember(task.items) { 
        task.items.any { it.state == DownloadItemState.RUNNING || it.state == DownloadItemState.ENQUEUED } 
    }
    val hasPausedItems = remember(task.items) { 
        task.items.any { it.state == DownloadItemState.PAUSED } 
    }
    val colors = AsmrTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleExpanded)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.subtitle.ifBlank { task.rootDir.substringAfterLast('\\').substringAfterLast('/') },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasFailedItems) {
                    IconButton(
                        onClick = onRetryFailedInTask,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = colors.primary
                        )
                    }
                }
                if (hasActiveItems) {
                    IconButton(
                        onClick = onPauseTask,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = null,
                            tint = colors.primary
                        )
                    }
                } else if (hasPausedItems) {
                    IconButton(
                        onClick = onResumeTask,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = colors.primary
                        )
                    }
                }
                IconButton(
                    onClick = onRequestDeleteTask,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = colors.textSecondary
                    )
                }
            }

            when {
                task.progressFraction != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "下载进度",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${(task.progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = colors.primary,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.surfaceVariant)
                        ) {
                            val animatedProgress by animateFloatAsState(
                                targetValue = task.progressFraction,
                                animationSpec = tween(durationMillis = 300),
                                label = "progress"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .fillMaxHeight()
                                    .background(colors.primary)
                            )
                        }
                    }
                }
                task.hasUnknownTotalRunning -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "下载中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = colors.primary,
                            trackColor = colors.surfaceVariant
                        )
                    }
                }
            }

            if (expanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    thickness = 0.5.dp,
                                    color = colors.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                            }
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
    val colors = AsmrTheme.colorScheme
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = colors.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
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
    val colors = AsmrTheme.colorScheme
    val percent: Int? = when {
        item.state == DownloadItemState.SUCCEEDED -> 100
        item.total > 0 -> ((item.downloaded * 100 / item.total).toInt().coerceIn(0, 100))
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when (item.state) {
                    DownloadItemState.SUCCEEDED -> colors.primary.copy(alpha = 0.05f)
                    DownloadItemState.FAILED -> colors.danger.copy(alpha = 0.05f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = pickFileIcon(item.fileName),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态标签
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (item.state) {
                                    DownloadItemState.SUCCEEDED -> colors.primary.copy(alpha = 0.15f)
                                    DownloadItemState.FAILED -> colors.danger.copy(alpha = 0.15f)
                                    DownloadItemState.RUNNING -> colors.primary.copy(alpha = 0.15f)
                                    else -> colors.surfaceVariant
                                }
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (item.state) {
                                DownloadItemState.SUCCEEDED -> "已完成"
                                DownloadItemState.FAILED -> "失败"
                                DownloadItemState.RUNNING -> "下载中"
                                DownloadItemState.PAUSED -> "已暂停"
                                DownloadItemState.CANCELLED -> "已取消"
                                DownloadItemState.ENQUEUED -> "等待中"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (item.state) {
                                DownloadItemState.SUCCEEDED -> colors.primary
                                DownloadItemState.FAILED -> colors.danger
                                DownloadItemState.RUNNING -> colors.primary
                                else -> colors.textSecondary
                            },
                            fontSize = 11.sp
                        )
                    }
                    
                    if (percent != null) {
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (item.state) {
                    DownloadItemState.RUNNING, DownloadItemState.ENQUEUED -> {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DownloadItemState.PAUSED, DownloadItemState.CANCELLED -> {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    DownloadItemState.FAILED -> {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = colors.danger,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    else -> {}
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        // 进度条
        when {
            percent != null && item.state != DownloadItemState.SUCCEEDED -> {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.surfaceVariant)
                ) {
                    val animatedProgress by animateFloatAsState(
                        targetValue = percent / 100f,
                        animationSpec = tween(durationMillis = 300),
                        label = "file_progress"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .background(colors.primary)
                    )
                }
            }
            item.state == DownloadItemState.SUCCEEDED -> {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.primary.copy(alpha = 0.3f))
                )
            }
            item.total <= 0 && (item.state == DownloadItemState.RUNNING || item.state == DownloadItemState.ENQUEUED) -> {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = colors.primary,
                    trackColor = colors.surfaceVariant
                )
            }
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
