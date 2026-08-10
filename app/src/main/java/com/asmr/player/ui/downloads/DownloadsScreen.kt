package com.asmr.player.ui.downloads

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.subtitle.SubtitleItemState
import com.asmr.player.subtitle.SubtitleTaskMode
import com.asmr.player.subtitle.SubtitleTaskItemUi
import com.asmr.player.subtitle.SubtitleTaskUi
import com.asmr.player.subtitle.normalizedSubtitleAlbumKey
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.FlatActionDialog
import com.asmr.player.ui.common.FlatDialogAction
import com.asmr.player.ui.common.FlatDialogActionTone
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.smoothScrollToTop
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.albumCoverImageModel
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.Formatting
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val DownloadsPageHorizontalPadding = 8.dp
private val SwipeActionButtonWidth = 56.dp
private val SwipeActionHeaderHeight = 68.dp
private val SwipeRevealSpringSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@Composable
fun DownloadsScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    scrollToTopSignal: Long = 0L,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val translationSubtitleGroups by viewModel.translationSubtitleGroups.collectAsStateWithLifecycle()
    val subtitleTasks by viewModel.subtitleTasks.collectAsStateWithLifecycle()
    val polishingRjCodes by viewModel.polishingRjCodes.collectAsStateWithLifecycle()
    val activeDownloadFileCount = remember(tasks) { countActiveDownloadFiles(tasks) }
    val activeTranslationTaskCount = remember(subtitleTasks) { countActiveSubtitleTaskItems(subtitleTasks) }
    val expandedTasks = remember { mutableStateListOf<Long>() }
    val context = LocalContext.current
    var rjQuery by rememberSaveable { mutableStateOf("") }
    var managementMode by rememberSaveable { mutableStateOf(DownloadManagementMode.Downloads) }
    var pendingDelete by remember { mutableStateOf<PendingDeleteAction?>(null) }
    var revealedDownloadTaskId by remember { mutableStateOf<Long?>(null) }
    var revealedTranslationRj by remember { mutableStateOf<String?>(null) }
    var revealedTaskBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    var pagePositionInRoot by remember { mutableStateOf(Offset.Zero) }
    val swipeRevealCloseController = remember { SwipeRevealCloseController() }
    val downloadRoot = remember {
        File(context.getExternalFilesDir(null), "albums").absolutePath
    }
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToTopSignal) {
        if (scrollToTopSignal == 0L) return@LaunchedEffect
        listState.smoothScrollToTop()
    }
    LaunchedEffect(isActive) {
        if (isActive) return@LaunchedEffect
        listState.stopScroll(MutatePriority.PreventUserInput)
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            revealedDownloadTaskId = null
            revealedTranslationRj = null
            revealedTaskBoundsInRoot = null
        }
    }
    LaunchedEffect(managementMode) {
        revealedDownloadTaskId = null
        revealedTranslationRj = null
        revealedTaskBoundsInRoot = null
    }

    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                pagePositionInRoot = coordinates.positionInRoot()
            }
            .pointerInput(
                revealedDownloadTaskId,
                revealedTranslationRj,
                revealedTaskBoundsInRoot,
                pagePositionInRoot
            ) {
                if (revealedDownloadTaskId == null && revealedTranslationRj == null) {
                    return@pointerInput
                }
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    val downInRoot = down.position + pagePositionInRoot
                    if (revealedTaskBoundsInRoot?.contains(downInRoot) != true) {
                        swipeRevealCloseController.requestClose()
                        revealedDownloadTaskId = null
                        revealedTranslationRj = null
                        revealedTaskBoundsInRoot = null
                    }
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = if (isCompact) {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = DownloadsPageHorizontalPadding, vertical = 10.dp)
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = DownloadsPageHorizontalPadding, vertical = 10.dp)
            },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DownloadManagementModeTabs(
                selected = managementMode,
                activeDownloadFileCount = activeDownloadFileCount,
                activeTranslationTaskCount = activeTranslationTaskCount,
                onSelected = { managementMode = it }
            )

            OutlinedTextField(
                value = rjQuery,
                onValueChange = { rjQuery = it },
                label = { Text("RJ号精准搜索") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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

            when (managementMode) {
                DownloadManagementMode.Downloads -> {
                    val shownTasks = remember(tasks, normalizedQuery) {
                        if (normalizedQuery.isBlank()) tasks
                        else tasks.filter { it.title.equals(normalizedQuery, ignoreCase = true) }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.thinScrollbar(listState),
                        flingBehavior = rememberCalmScrollableFlingBehavior(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(
                            top = 4.dp,
                            bottom = LocalBottomOverlayPadding.current + 6.dp
                        )
                    ) {
                        if (shownTasks.isEmpty()) {
                            item(key = "download_empty_state") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (normalizedQuery.isBlank()) "暂无下载任务" else "未找到任务：$normalizedQuery",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(shownTasks, key = { it.taskId }) { task ->
                                val colors = AsmrTheme.colorScheme
                                val expanded = expandedTasks.contains(task.taskId)
                                val hasFailedItems = task.items.any { it.state == DownloadItemState.FAILED }
                                val hasActiveItems = task.items.any {
                                    it.state == DownloadItemState.RUNNING || it.state == DownloadItemState.ENQUEUED
                                }
                                val hasPausedItems = task.items.any { it.state == DownloadItemState.PAUSED }
                                val actionCount = (if (hasFailedItems) 1 else 0) +
                                    (if (hasActiveItems || hasPausedItems) 1 else 0) + 1
                                SwipeRevealActionsBox(
                                    modifier = Modifier.fillMaxWidth(),
                                    revealed = revealedDownloadTaskId == task.taskId,
                                    enabled = !expanded,
                                    closeController = swipeRevealCloseController,
                                    onRevealedBoundsChanged = { revealedTaskBoundsInRoot = it },
                                    onRevealedChange = { open ->
                                        revealedDownloadTaskId = when {
                                            open -> task.taskId
                                            revealedDownloadTaskId == task.taskId -> null
                                            else -> revealedDownloadTaskId
                                        }
                                    },
                                    actionWidth = SwipeActionButtonWidth * actionCount,
                                    actions = {
                                        if (hasFailedItems) {
                                            SwipeRevealAction(
                                                backgroundColor = colors.surfaceVariant,
                                                tint = colors.danger,
                                                icon = Icons.Rounded.Refresh,
                                                contentDescription = "重试失败项",
                                                onClick = { viewModel.retryFailedInTask(task.taskId) }
                                            )
                                        }
                                        if (hasActiveItems) {
                                            SwipeRevealAction(
                                                backgroundColor = colors.primary,
                                                tint = colors.onPrimary,
                                                icon = Icons.Rounded.Pause,
                                                contentDescription = "暂停下载任务",
                                                onClick = { viewModel.pauseTask(task.taskId) }
                                            )
                                        } else if (hasPausedItems) {
                                            SwipeRevealAction(
                                                backgroundColor = colors.primary,
                                                tint = colors.onPrimary,
                                                icon = Icons.Rounded.PlayArrow,
                                                contentDescription = "继续下载任务",
                                                onClick = { viewModel.resumeTask(task.taskId) }
                                            )
                                        }
                                        SwipeRevealAction(
                                            backgroundColor = colors.danger,
                                            tint = Color.White,
                                            icon = Icons.Rounded.Close,
                                            contentDescription = "删除下载任务",
                                            onClick = { pendingDelete = PendingDeleteAction.Task(task.taskId) }
                                        )
                                    }
                                ) {
                                    DownloadTaskCard(
                                        task = task,
                                        expanded = expanded,
                                        onToggleExpanded = {
                                            if (expanded) {
                                                expandedTasks.remove(task.taskId)
                                            } else {
                                                revealedDownloadTaskId = null
                                                expandedTasks.add(task.taskId)
                                            }
                                        },
                                        onPauseItem = { viewModel.pauseItem(it) },
                                        onResumeItem = { viewModel.resumeItem(it) },
                                        onRetryItem = { viewModel.retryItem(it) },
                                        onDeleteItem = { pendingDelete = PendingDeleteAction.Item(workId = it) }
                                    )
                                }
                            }
                        }
                        item(key = "download_root") {
                            Text(
                                text = "下载目录：$downloadRoot",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                DownloadManagementMode.Translations -> {
                    TranslationManagementContent(
                        normalizedQuery = normalizedQuery,
                        listState = listState,
                        subtitleGroups = translationSubtitleGroups,
                        subtitleTasks = subtitleTasks,
                        polishingRjCodes = polishingRjCodes,
                        loadAlbumCovers = viewModel::loadTranslationAlbumCovers,
                        revealedRj = revealedTranslationRj,
                        closeController = swipeRevealCloseController,
                        onRevealedRjChange = { revealedTranslationRj = it },
                        onRevealedBoundsChanged = { revealedTaskBoundsInRoot = it },
                        onDeleteSubtitle = { trackId, title ->
                            pendingDelete = PendingDeleteAction.Subtitle(trackId = trackId, title = title)
                        },
                        onDeleteSubtitleGroup = { rjCode, trackIds ->
                            revealedTranslationRj = null
                            pendingDelete = PendingDeleteAction.SubtitleGroup(
                                rjCode = rjCode,
                                trackIds = trackIds
                            )
                        },
                        onRetrySubtitle = viewModel::retrySubtitleTranslation,
                        onPolishAlbum = viewModel::polishSubtitleAlbum,
                        onPauseItem = viewModel::pauseSubtitleItem,
                        onResumeItem = viewModel::resumeSubtitleItem,
                        onCancelItem = viewModel::cancelSubtitleItem,
                        onRetryItem = viewModel::retrySubtitleItem,
                        onPauseTask = viewModel::pauseSubtitleTask,
                        onResumeTask = viewModel::resumeSubtitleTask,
                        onCancelTask = viewModel::cancelSubtitleTask
                    )
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
                            message = "将物理删除“${task.title}”目录下的文件，且不可恢复。"
                        )
                    }

                    is PendingDeleteAction.Item -> {
                        val item = tasks.asSequence()
                            .flatMap { it.items.asSequence() }
                            .firstOrNull { it.workId == action.workId } ?: return@remember null
                        ResolvedDeleteText(
                            message = "将物理删除文件“${item.fileName}”，且不可恢复。"
                        )
                    }

                    is PendingDeleteAction.Subtitle -> ResolvedDeleteText(
                        message = "将删除字幕“${action.title}”，播放时不会再显示该字幕。"
                    )

                    is PendingDeleteAction.SubtitleGroup -> ResolvedDeleteText(
                        message = "将删除“${action.rjCode}”下的 ${action.trackIds.size} 个字幕，播放时不会再显示这些字幕。"
                    )
                }
            }

            if (resolved != null) {
                FlatActionDialog(
                    onDismissRequest = { pendingDelete = null },
                    message = resolved.message,
                    actions = listOf(
                        FlatDialogAction("取消", onClick = { pendingDelete = null }),
                        FlatDialogAction(
                            text = "删除",
                            tone = FlatDialogActionTone.Danger,
                            onClick = {
                                pendingDelete = null
                                when (action) {
                                    is PendingDeleteAction.Task -> viewModel.deleteTask(action.taskId)
                                    is PendingDeleteAction.Item -> viewModel.deleteItem(action.workId)
                                    is PendingDeleteAction.Subtitle -> viewModel.deleteSubtitleTrack(action.trackId)
                                    is PendingDeleteAction.SubtitleGroup -> viewModel.deleteSubtitleTracks(action.trackIds)
                                }
                            }
                        )
                    )
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
    data class Subtitle(val trackId: Long, val title: String) : PendingDeleteAction()
    data class SubtitleGroup(val rjCode: String, val trackIds: List<Long>) : PendingDeleteAction()
}

private data class ResolvedDeleteText(
    val message: String
)

private enum class DownloadManagementMode(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Downloads("下载任务", Icons.Rounded.Download),
    Translations("翻译任务", Icons.Rounded.Translate)
}

private data class TranslationTaskGroupUi(
    val rjCode: String,
    val title: String,
    val albumCover: TaskAlbumCoverUi,
    val subtitles: List<TranslationSubtitleUi>,
    val tasks: List<TranslationTaskUi>,
    val isPolishing: Boolean = false
)

internal data class TranslationTaskUi(
    val itemId: String,
    val taskId: String,
    val createdAtMillis: Long,
    val trackId: Long,
    val rjCode: String,
    val title: String,
    val state: String,
    val progress: Float?,
    val progressLabel: String,
    val completedLines: Int,
    val totalLines: Int,
    val stage: String,
    val message: String
)

private fun TaskAlbumCoverUi.hasImageSource(): Boolean =
    coverThumbPath.isNotBlank() || coverPath.isNotBlank() || coverUrl.isNotBlank()

@Composable
private fun DownloadManagementModeTabs(
    selected: DownloadManagementMode,
    activeDownloadFileCount: Int,
    activeTranslationTaskCount: Int,
    onSelected: (DownloadManagementMode) -> Unit
) {
    val colors = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface.copy(alpha = 0.55f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        DownloadManagementMode.entries.forEach { mode ->
            val isSelected = selected == mode
            val activeTaskCount = when (mode) {
                DownloadManagementMode.Downloads -> activeDownloadFileCount
                DownloadManagementMode.Translations -> activeTranslationTaskCount
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) colors.primarySoft else Color.Transparent)
                    .clickable { onSelected(mode) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) colors.primaryStrong else colors.textSecondary
                    )
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) colors.primaryStrong else colors.textSecondary
                    )
                    if (activeTaskCount > 0) {
                        Badge(
                            containerColor = colors.primaryStrong,
                            contentColor = colors.onPrimary
                        ) {
                            Text(activeTaskCount.toString())
                        }
                    }
                }
            }
        }
    }
}

internal fun countActiveDownloadFiles(tasks: List<DownloadTaskUi>): Int {
    return tasks.sumOf { task ->
        task.items.count { item ->
            item.state == DownloadItemState.RUNNING || item.state == DownloadItemState.ENQUEUED
        }
    }
}

internal fun countActiveSubtitleTaskItems(tasks: List<SubtitleTaskUi>): Int {
    return tasks.sumOf { task ->
        task.items.count { item -> item.state.isActivelyRunning() }
    }
}

@Composable
private fun TranslationManagementContent(
    normalizedQuery: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    subtitleGroups: List<TranslationSubtitleGroupUi>,
    subtitleTasks: List<SubtitleTaskUi>,
    polishingRjCodes: Set<String>,
    loadAlbumCovers: suspend (List<Long>) -> Map<Long, TaskAlbumCoverUi>,
    revealedRj: String?,
    closeController: SwipeRevealCloseController,
    onRevealedRjChange: (String?) -> Unit,
    onRevealedBoundsChanged: (Rect) -> Unit,
    onDeleteSubtitle: (trackId: Long, title: String) -> Unit,
    onDeleteSubtitleGroup: (rjCode: String, trackIds: List<Long>) -> Unit,
    onRetrySubtitle: (trackId: Long, title: String) -> Unit,
    onPolishAlbum: (rjCode: String) -> Unit,
    onPauseItem: (String) -> Unit,
    onResumeItem: (String) -> Unit,
    onCancelItem: (String) -> Unit,
    onRetryItem: (String) -> Unit,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onCancelTask: (String) -> Unit
) {
    val displayedTasks = remember(subtitleTasks) {
        subtitleTasks.flatMap { task ->
            task.items.map { item -> item.toTranslationTaskUi(task) }
        }
    }
    val taskTrackIds = remember(displayedTasks) {
        displayedTasks.map(TranslationTaskUi::trackId).distinct()
    }
    val taskAlbumCovers by produceState<Map<Long, TaskAlbumCoverUi>>(
        initialValue = emptyMap(),
        key1 = taskTrackIds
    ) {
        value = loadAlbumCovers(taskTrackIds)
    }
    val groups = remember(
        displayedTasks,
        subtitleGroups,
        taskAlbumCovers,
        polishingRjCodes,
        normalizedQuery
    ) {
        val tasksByRj = displayedTasks
            .sortedWith(
                compareBy<TranslationTaskUi> { it.state.translationTaskSortPriority() }
                    .thenByDescending(TranslationTaskUi::createdAtMillis)
            )
            .groupBy { it.rjCode }
        val subtitleGroupsByRj = subtitleGroups.associateBy(TranslationSubtitleGroupUi::rjCode)
        (tasksByRj.keys + subtitleGroupsByRj.keys)
            .asSequence()
            .filter { rjCode ->
                normalizedQuery.isBlank() || rjCode.equals(normalizedQuery, ignoreCase = true)
            }
            .sortedWith(compareBy<String> { it == "未知RJ" }.thenBy { it.lowercase() })
            .map { rjCode ->
                val subtitleGroup = subtitleGroupsByRj[rjCode]
                val tasks = tasksByRj[rjCode].orEmpty()
                val albumCover = sequenceOf(
                    subtitleGroup?.albumCover,
                    tasks.asSequence()
                        .mapNotNull { task -> taskAlbumCovers[task.trackId] }
                        .firstOrNull { it.hasImageSource() }
                ).filterNotNull()
                    .firstOrNull { it.hasImageSource() }
                    ?: TaskAlbumCoverUi()
                TranslationTaskGroupUi(
                    rjCode = rjCode,
                    title = subtitleGroup?.title?.takeIf { it.isNotBlank() }
                        ?: tasks.firstOrNull { it.title.isNotBlank() }?.title.orEmpty(),
                    albumCover = albumCover,
                    subtitles = subtitleGroup?.subtitles.orEmpty(),
                    tasks = tasks,
                    isPolishing = polishingRjCodes.contains(rjCode.normalizedSubtitleAlbumKey())
                )
            }
            .filter { it.subtitles.isNotEmpty() || it.tasks.isNotEmpty() }
            .toList()
    }
    val expandedGroups = remember { mutableStateListOf<String>() }

    if (groups.isEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (normalizedQuery.isBlank()) "暂无翻译任务" else "未找到任务：$normalizedQuery",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.thinScrollbar(listState),
        flingBehavior = rememberCalmScrollableFlingBehavior(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = LocalBottomOverlayPadding.current + 6.dp
        )
    ) {
        items(groups, key = { it.rjCode }) { group ->
            val colors = AsmrTheme.colorScheme
            val expanded = expandedGroups.contains(group.rjCode)
            val activeTask = group.tasks.firstOrNull { it.state.isActivelyRunning() }
            val controlledTask = activeTask ?: group.tasks.firstOrNull()
            // 操作列：暂停/继续、润色、（有任务时）取消；（只有字幕时）润色、删除
            val actionCount = if (controlledTask == null) 2 else 3
            val groupActionsEnabled = !group.isPolishing
            SwipeRevealActionsBox(
                modifier = Modifier.fillMaxWidth(),
                revealed = revealedRj == group.rjCode,
                enabled = !expanded,
                closeController = closeController,
                onRevealedBoundsChanged = onRevealedBoundsChanged,
                onRevealedChange = { open ->
                    onRevealedRjChange(
                        when {
                            open -> group.rjCode
                            revealedRj == group.rjCode -> null
                            else -> revealedRj
                        }
                    )
                },
                actionWidth = SwipeActionButtonWidth * actionCount,
                actions = {
                    controlledTask?.let { task ->
                        when (task.state) {
                            SubtitleItemState.PAUSED, SubtitleItemState.INTERRUPTED, SubtitleItemState.FAILED -> {
                                SwipeRevealAction(
                                    backgroundColor = colors.primary,
                                    tint = colors.onPrimary,
                                    icon = Icons.Rounded.PlayArrow,
                                    contentDescription = "继续字幕任务",
                                    enabled = groupActionsEnabled,
                                    onClick = { onResumeTask(task.taskId) }
                                )
                            }
                            else -> {
                                SwipeRevealAction(
                                    backgroundColor = colors.primary,
                                    tint = colors.onPrimary,
                                    icon = Icons.Rounded.Pause,
                                    contentDescription = "暂停字幕任务",
                                    enabled = groupActionsEnabled,
                                    onClick = { onPauseTask(task.taskId) }
                                )
                            }
                        }
                    }
                    SwipeRevealAction(
                        backgroundColor = colors.primaryContainer,
                        tint = colors.onPrimaryContainer,
                        icon = Icons.Rounded.AutoAwesome,
                        contentDescription = if (group.isPolishing) "润色中" else "润色该作品字幕",
                        enabled = groupActionsEnabled,
                        onClick = {
                            onRevealedRjChange(null)
                            onPolishAlbum(group.rjCode)
                        }
                    )
                    if (controlledTask != null) {
                        SwipeRevealAction(
                            backgroundColor = colors.danger,
                            tint = Color.White,
                            icon = Icons.Rounded.Close,
                            contentDescription = "取消字幕任务",
                            enabled = groupActionsEnabled,
                            onClick = { onCancelTask(controlledTask.taskId) }
                        )
                    } else {
                        SwipeRevealAction(
                            backgroundColor = colors.danger,
                            tint = Color.White,
                            icon = Icons.Rounded.Close,
                            contentDescription = "删除该作品的全部字幕",
                            enabled = groupActionsEnabled,
                            onClick = {
                                onDeleteSubtitleGroup(
                                    group.rjCode,
                                    group.subtitles.map(TranslationSubtitleUi::trackId)
                                )
                            }
                        )
                    }
                }
            ) {
                TranslationTaskGroupCard(
                    group = group,
                    expanded = expanded,
                    onToggleExpanded = {
                        if (expanded) {
                            expandedGroups.remove(group.rjCode)
                        } else {
                            onRevealedRjChange(null)
                            expandedGroups.add(group.rjCode)
                        }
                    },
                    onDeleteSubtitle = onDeleteSubtitle,
                    onRetrySubtitle = onRetrySubtitle,
                    onPauseItem = onPauseItem,
                    onResumeItem = onResumeItem,
                    onCancelItem = onCancelItem,
                    onRetryItem = onRetryItem
                )
            }
        }
    }
}

@Composable
private fun TranslationTaskGroupCard(
    group: TranslationTaskGroupUi,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onDeleteSubtitle: (trackId: Long, title: String) -> Unit,
    onRetrySubtitle: (trackId: Long, title: String) -> Unit,
    onPauseItem: (String) -> Unit,
    onResumeItem: (String) -> Unit,
    onCancelItem: (String) -> Unit,
    onRetryItem: (String) -> Unit
) {
    val colors = AsmrTheme.colorScheme
    val isPolishing = group.isPolishing
    val activeTask = remember(group.tasks) { group.tasks.firstOrNull { it.state.isActivelyRunning() } }
    val controlledTask = remember(group.tasks, activeTask) { activeTask ?: group.tasks.firstOrNull() }
    val summary = remember(group.subtitles, group.tasks, activeTask, isPolishing) {
        when {
            isPolishing -> "整体润色中"
            activeTask != null -> activeTask.stage
            group.subtitles.isNotEmpty() -> "${group.subtitles.size} 个字幕"
            else -> "暂无字幕"
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskGroupHeader(
                expanded = expanded,
                title = group.rjCode,
                subtitle = group.title,
                summary = summary,
                summaryColor = if (activeTask != null || isPolishing) colors.primary else colors.textSecondary,
                albumCover = group.albumCover,
                progress = if (isPolishing) null else activeTask?.progress,
                progressIndeterminate = false,
                reserveProgressSpace = !isPolishing && controlledTask != null,
                summaryOnTitleLine = true,
                onToggleExpanded = onToggleExpanded
            )

            if (expanded) {
                val taskByTrackId = group.tasks
                    .associateBy(TranslationTaskUi::trackId)
                    .toMap()
                val subtitleTrackIds = group.subtitles.mapTo(mutableSetOf(), TranslationSubtitleUi::trackId)
                val rows = buildList {
                    group.subtitles.forEach { subtitle ->
                        add(TranslationRowUi.Subtitle(subtitle, taskByTrackId[subtitle.trackId]))
                    }
                    group.tasks
                        .filter { task -> task.trackId !in subtitleTrackIds }
                        .forEach { task -> add(TranslationRowUi.Task(task)) }
                }
                val actionsEnabled = !isPolishing
                rows.forEachIndexed { index, row ->
                    when (row) {
                        is TranslationRowUi.Subtitle -> TranslationSubtitleRow(
                            subtitle = row.subtitle,
                            task = row.task,
                            actionsEnabled = actionsEnabled,
                            retryEnabled = true,
                            onDelete = { onDeleteSubtitle(row.subtitle.trackId, row.subtitle.title) },
                            onRetry = { onRetrySubtitle(row.subtitle.trackId, row.subtitle.title) },
                            onPause = row.task?.takeIf { it.state.isActivelyRunning() }
                                ?.let { { onPauseItem(it.itemId) } },
                            onResume = row.task?.takeIf { it.state in setOf(SubtitleItemState.PAUSED, SubtitleItemState.INTERRUPTED) }
                                ?.let { { onResumeItem(it.itemId) } },
                            onCancel = row.task?.let { { onCancelItem(it.itemId) } },
                            onRetryTask = row.task?.takeIf { it.state == SubtitleItemState.FAILED }
                                ?.let { { onRetryItem(it.itemId) } }
                        )

                        is TranslationRowUi.Task -> TranslationTaskRow(
                            task = row.task,
                            actionsEnabled = actionsEnabled,
                            retryEnabled = true,
                            onPause = { onPauseItem(row.task.itemId) },
                            onResume = { onResumeItem(row.task.itemId) },
                            onCancel = { onCancelItem(row.task.itemId) },
                            onRetry = { onRetryItem(row.task.itemId) }
                        )
                    }
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = colors.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
        if (isPolishing) {
            FinalPolishBottomProgress(
                trackColor = colors.primary.copy(alpha = 0.14f),
                progressColor = colors.primary
            )
        }
    }
}

internal const val FINAL_POLISH_PROGRESS_TAG = "final_polish_bottom_progress"

@Composable
internal fun BoxScope.FinalPolishBottomProgress(
    trackColor: Color,
    progressColor: Color
) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(1.dp)
            .testTag(FINAL_POLISH_PROGRESS_TAG)
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxSize(),
            color = progressColor,
            trackColor = trackColor
        )
    }
}

@Composable
private fun TaskGroupHeader(
    expanded: Boolean,
    title: String,
    subtitle: String,
    summary: String,
    summaryColor: Color,
    albumCover: TaskAlbumCoverUi,
    progress: Float?,
    progressIndeterminate: Boolean,
    reserveProgressSpace: Boolean,
    summaryOnTitleLine: Boolean = false,
    onToggleExpanded: () -> Unit,
    actions: (@Composable () -> Unit)? = null
) {
    val colors = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggleExpanded
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (expanded) {
                Icons.Rounded.KeyboardArrowDown
            } else {
                Icons.AutoMirrored.Rounded.KeyboardArrowRight
            },
            contentDescription = if (expanded) "收起任务详情" else "展开任务详情",
            tint = colors.primary,
            modifier = Modifier.size(22.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (summaryOnTitleLine && summary.isNotBlank()) {
                    TaskGroupSummary(summary, summaryColor)
                }
            }
            if (subtitle.isNotBlank() || (!summaryOnTitleLine && summary.isNotBlank())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (!summaryOnTitleLine && summary.isNotBlank()) {
                        TaskGroupSummary(summary, summaryColor)
                    }
                }
            }
            if (reserveProgressSpace) {
                StableProgressSlot(
                    progress = progress,
                    visible = progress != null || progressIndeterminate,
                    trackColor = colors.surface.copy(alpha = 0.8f),
                    progressColor = colors.primary,
                    indeterminate = progressIndeterminate
                )
            }
        }
        if (actions != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                actions()
            }
        }
        TaskGroupCover(albumCover)
    }
}

@Composable
private fun TaskGroupSummary(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 112.dp)
    )
}

@Composable
private fun TaskGroupCover(albumCover: TaskAlbumCoverUi) {
    val coverModel = remember(
        albumCover.coverThumbPath,
        albumCover.coverPath,
        albumCover.coverUrl
    ) {
        albumCoverImageModel(
            coverThumbPath = albumCover.coverThumbPath,
            coverPath = albumCover.coverPath,
            coverUrl = albumCover.coverUrl
        )
    }
    if (coverModel == null) {
        DiscPlaceholder(
            cornerRadius = 8,
            modifier = Modifier.size(48.dp)
        )
    } else {
        AsmrAsyncImage(
            model = coverModel,
            contentDescription = "作品封面",
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = 8,
            fadeIn = false,
            peekAnySizeForInitial = true,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun TranslationSubtitleRow(
    subtitle: TranslationSubtitleUi,
    task: TranslationTaskUi?,
    actionsEnabled: Boolean = true,
    retryEnabled: Boolean = actionsEnabled,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onPause: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onRetryTask: (() -> Unit)?
) {
    val colors = AsmrTheme.colorScheme
    val progressText = task?.let(::translationTaskProgressText)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Rounded.Subtitles,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = subtitle.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = task?.message?.ifBlank { task.stage }
                    ?: "${subtitle.subtitleCount} 行字幕",
                style = MaterialTheme.typography.labelSmall,
                color = if (task?.state == SubtitleItemState.FAILED) colors.danger else colors.textTertiary,
                maxLines = if (task?.state == SubtitleItemState.FAILED) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (task != null) {
                StableProgressSlot(
                    progress = task.progress,
                    visible = task.progress != null,
                    trackColor = colors.surface.copy(alpha = 0.8f),
                    progressColor = colors.primary,
                    indeterminate = false
                )
            }
        }
        if (progressText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (task.state in setOf(SubtitleItemState.TRANSCRIBING, SubtitleItemState.TRANSLATING) && task.progress == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = colors.primary,
                        trackColor = colors.surface.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = when (task.state) {
                        SubtitleItemState.FAILED -> colors.danger
                        else -> colors.textSecondary
                    },
                    maxLines = 1
                )
            }
        }
        if (onPause != null) {
            IconButton(onClick = onPause, enabled = actionsEnabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Pause, "暂停字幕任务", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        if (onResume != null) {
            IconButton(onClick = onResume, enabled = actionsEnabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.PlayArrow, "继续字幕任务", tint = colors.primary, modifier = Modifier.size(16.dp))
            }
        }
        if (onRetryTask != null) {
            IconButton(onClick = onRetryTask, enabled = retryEnabled, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Refresh, "重试字幕任务", tint = colors.primary, modifier = Modifier.size(16.dp))
            }
        }
        if (onCancel != null) {
            IconButton(
                onClick = onCancel,
                enabled = actionsEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "取消翻译",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (task == null) {
            IconButton(
                onClick = onRetry,
                enabled = retryEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "重新翻译",
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = actionsEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "删除字幕",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun TranslationTaskRow(
    task: TranslationTaskUi,
    actionsEnabled: Boolean = true,
    retryEnabled: Boolean = actionsEnabled,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = AsmrTheme.colorScheme
    val progressText = translationTaskProgressText(task)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("translation_task_row_${task.itemId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Rounded.Subtitles,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(20.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = when (task.state) {
                        SubtitleItemState.FAILED -> colors.danger
                        else -> colors.textSecondary
                    },
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (task.state in setOf(SubtitleItemState.TRANSCRIBING, SubtitleItemState.TRANSLATING) && task.progress == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = colors.primary,
                        trackColor = colors.surface.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = task.message.ifBlank { task.stage },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (task.state == SubtitleItemState.FAILED) colors.danger else colors.textTertiary,
                    maxLines = if (task.state == SubtitleItemState.FAILED) 2 else 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            StableProgressSlot(
                progress = task.progress,
                visible = task.progress != null,
                trackColor = colors.surface.copy(alpha = 0.8f),
                progressColor = colors.primary,
                indeterminate = false
            )
        }

        when (task.state) {
            SubtitleItemState.PAUSED, SubtitleItemState.INTERRUPTED -> IconButton(
                onClick = onResume,
                enabled = actionsEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, "继续字幕任务", tint = colors.primary, modifier = Modifier.size(16.dp))
            }
            SubtitleItemState.FAILED -> IconButton(
                onClick = onRetry,
                enabled = retryEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Rounded.Refresh, "重试字幕任务", tint = colors.primary, modifier = Modifier.size(16.dp))
            }
            else -> IconButton(
                onClick = onPause,
                enabled = actionsEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Rounded.Pause, "暂停字幕任务", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
        run {
            IconButton(
                onClick = onCancel,
                enabled = actionsEnabled,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "取消翻译",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private sealed class TranslationRowUi {
    data class Subtitle(
        val subtitle: TranslationSubtitleUi,
        val task: TranslationTaskUi?
    ) : TranslationRowUi()

    data class Task(
        val task: TranslationTaskUi
    ) : TranslationRowUi()
}

private fun translationTaskProgressText(task: TranslationTaskUi): String {
    return when {
        task.progressLabel.isNotBlank() -> task.progressLabel
        task.totalLines > 0 -> "${task.completedLines}/${task.totalLines} 行"
        else -> translationStateLabel(task.state)
    }
}

internal fun SubtitleTaskItemUi.toTranslationTaskUi(task: SubtitleTaskUi): TranslationTaskUi {
    val usesTranscriptionProgress = translationTotal <= 0 && mode == SubtitleTaskMode.GENERATED
    val fraction = when {
        translationTotal > 0 -> translationCursor.toFloat() / translationTotal.toFloat()
        usesTranscriptionProgress -> transcriptionProgress / 100f
        else -> null
    }?.coerceIn(0f, 1f)
    return TranslationTaskUi(
        itemId = id,
        taskId = task.id,
        createdAtMillis = createdAt,
        trackId = trackId,
        rjCode = task.rjCode,
        title = title,
        state = state,
        progress = fraction,
        progressLabel = when {
            usesTranscriptionProgress -> "$transcriptionProgress%"
            translationTotal > 0 -> "已确认 $translationCursor/$translationTotal"
            else -> ""
        },
        completedLines = translationCursor,
        totalLines = translationTotal,
        stage = subtitleItemStage(this),
        message = errorMessage.takeIf { state == SubtitleItemState.FAILED }.orEmpty()
    )
}

internal fun subtitleItemStage(item: SubtitleTaskItemUi): String = when (item.state) {
    SubtitleItemState.QUEUED_TRANSCRIPTION -> "排队转录"
    SubtitleItemState.TRANSCRIBING -> "转录中"
    SubtitleItemState.QUEUED_TRANSLATION -> "日文已生成，等待翻译"
    SubtitleItemState.WAITING_SLOT -> "等待翻译槽位"
    SubtitleItemState.WAITING_NETWORK -> "等待网络"
    SubtitleItemState.TRANSLATING -> "AI 正在确认字幕"
    SubtitleItemState.RETRY_WAIT -> "重试等待 ${item.attempt + 1}/4${item.errorMessage.asStageReason()}"
    SubtitleItemState.PAUSE_REQUESTED -> "暂停中"
    SubtitleItemState.PAUSED -> "已暂停"
    SubtitleItemState.INTERRUPTED -> "异常中断${item.errorMessage.asStageReason()}"
    SubtitleItemState.CANCEL_REQUESTED -> "取消中"
    SubtitleItemState.FAILED -> "失败"
    SubtitleItemState.SUCCEEDED -> "已完成"
    SubtitleItemState.CANCELED -> "已取消"
    else -> item.state
}

private fun String.asStageReason(): String = trim().takeIf(String::isNotEmpty)?.let { "：$it" }.orEmpty()

private fun translationStateLabel(state: String): String = when (state) {
    SubtitleItemState.PAUSED -> "已暂停"
    SubtitleItemState.INTERRUPTED -> "异常中断"
    SubtitleItemState.FAILED -> "失败"
    else -> "处理中"
}

private fun String.isActivelyRunning(): Boolean = this !in setOf(
    SubtitleItemState.PAUSED,
    SubtitleItemState.INTERRUPTED,
    SubtitleItemState.FAILED,
    SubtitleItemState.SUCCEEDED,
    SubtitleItemState.CANCELED
)

private fun String.translationTaskSortPriority(): Int = when (this) {
    SubtitleItemState.TRANSCRIBING, SubtitleItemState.TRANSLATING -> 0
    SubtitleItemState.QUEUED_TRANSCRIPTION,
    SubtitleItemState.QUEUED_TRANSLATION,
    SubtitleItemState.WAITING_SLOT,
    SubtitleItemState.WAITING_NETWORK,
    SubtitleItemState.RETRY_WAIT -> 1
    SubtitleItemState.PAUSE_REQUESTED, SubtitleItemState.CANCEL_REQUESTED -> 2
    SubtitleItemState.PAUSED, SubtitleItemState.INTERRUPTED -> 3
    SubtitleItemState.FAILED -> 4
    else -> 5
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskUi,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPauseItem: (String) -> Unit,
    onResumeItem: (String) -> Unit,
    onRetryItem: (String) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    val folderExpanded = remember(task.taskId) { mutableStateListOf<String>() }
    val treeEntries = remember(task.items, folderExpanded.toList()) {
        flattenDownloadTreeForUi(task.items, folderExpanded.toSet())
    }
    val hasUnknownTotalRunningItem = remember(task.items) {
        task.items.any { it.state == DownloadItemState.RUNNING && it.total <= 0 }
    }
    val colors = AsmrTheme.colorScheme
    val taskSummary by rememberTaskSummary(
        downloadedBytes = task.downloadedBytes,
        totalBytes = task.totalBytes,
        speed = task.speed,
        hasUnknownTotalRunning = hasUnknownTotalRunningItem,
        state = task.state
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TaskGroupHeader(
                expanded = expanded,
                title = task.title,
                subtitle = task.subtitle,
                summary = taskSummary,
                summaryColor = colors.textSecondary,
                summaryOnTitleLine = true,
                albumCover = task.albumCover,
                progress = task.progressFraction,
                progressIndeterminate = task.progressFraction == null && hasUnknownTotalRunningItem,
                reserveProgressSpace = true,
                onToggleExpanded = onToggleExpanded
            )

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    treeEntries.forEachIndexed { index, entry ->
                        when (entry) {
                            is DownloadTreeUiEntry.Folder -> {
                                DownloadFolderRow(
                                    title = entry.title,
                                    depth = entry.depth,
                                    expanded = folderExpanded.contains(entry.path),
                                    onToggle = {
                                        if (folderExpanded.contains(entry.path)) {
                                            folderExpanded.remove(entry.path)
                                        } else {
                                            folderExpanded.add(entry.path)
                                        }
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
    items.forEach { item ->
        val rel = item.relativePath.replace('\\', '/').trim().trimStart('/')
        val segments = rel.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return@forEach
        var current = root
        segments.forEachIndexed { index, segment ->
            val isLeaf = index == segments.lastIndex
            val nextPath = if (current.path.isBlank()) segment else "${current.path}/$segment"
            val child = current.children.getOrPut(segment) { Node(name = segment, path = nextPath) }
            if (isLeaf) child.item = item
            current = child
        }
    }

    fun nodeKey(node: Node): String = node.name.lowercase()

    val out = mutableListOf<DownloadTreeUiEntry>()
    fun walk(node: Node, depth: Int) {
        val folders = node.children.values.filter { it.children.isNotEmpty() }.sortedBy(::nodeKey)
        val files = node.children.values.filter { it.children.isEmpty() && it.item != null }.sortedBy(::nodeKey)

        folders.forEach { folder ->
            out.add(DownloadTreeUiEntry.Folder(path = folder.path, title = folder.name, depth = depth))
            if (expanded.contains(folder.path)) walk(folder, depth + 1)
        }

        files.forEach { file ->
            val item = file.item ?: return@forEach
            out.add(DownloadTreeUiEntry.File(path = file.path, title = file.name, depth = depth, item = item))
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
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onToggle
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Folder,
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
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
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
            .padding(start = 10.dp + (depth * 12).dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = pickFileIcon(item.fileName),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.textPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = downloadItemStateLabel(item.state),
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

                    formatSpeed(item.speed).takeIf { it.isNotBlank() }?.let { speed ->
                        Text(
                            text = speed,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                when {
                    percent != null && item.state != DownloadItemState.SUCCEEDED -> {
                        CompactProgressBar(
                            progress = percent / 100f,
                            trackColor = colors.surface.copy(alpha = 0.8f),
                            progressColor = colors.primary,
                            indeterminate = false
                        )
                    }

                    item.state == DownloadItemState.SUCCEEDED -> {
                        CompactProgressBar(
                            progress = 1f,
                            trackColor = colors.primary.copy(alpha = 0.2f),
                            progressColor = colors.primary.copy(alpha = 0.45f),
                            indeterminate = false
                        )
                    }

                    item.total <= 0 && item.state == DownloadItemState.RUNNING -> {
                        CompactProgressBar(
                            progress = null,
                            trackColor = colors.surface.copy(alpha = 0.8f),
                            progressColor = colors.primary,
                            indeterminate = true
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                when (item.state) {
                    DownloadItemState.RUNNING, DownloadItemState.ENQUEUED -> {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Pause,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DownloadItemState.PAUSED, DownloadItemState.CANCELLED -> {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DownloadItemState.FAILED -> {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = colors.danger,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    else -> Unit
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberTaskSummary(
    downloadedBytes: Long,
    totalBytes: Long?,
    speed: Long,
    hasUnknownTotalRunning: Boolean,
    state: DownloadItemState
) : androidx.compose.runtime.State<String> {
    val latestDownloadedBytes = rememberUpdatedState(downloadedBytes)
    val latestTotalBytes = rememberUpdatedState(totalBytes)
    val latestSpeed = rememberUpdatedState(speed)
    val latestHasUnknownTotalRunning = rememberUpdatedState(hasUnknownTotalRunning)
    val latestState = rememberUpdatedState(state)

    return produceState(
        initialValue = buildTaskSummaryText(
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            speed = speed,
            hasUnknownTotalRunning = hasUnknownTotalRunning,
            state = state
        )
    ) {
        while (true) {
            value = buildTaskSummaryText(
                downloadedBytes = latestDownloadedBytes.value,
                totalBytes = latestTotalBytes.value,
                speed = latestSpeed.value,
                hasUnknownTotalRunning = latestHasUnknownTotalRunning.value,
                state = latestState.value
            )
            delay(1_000)
        }
    }
}

private fun buildTaskSummaryText(
    downloadedBytes: Long,
    totalBytes: Long?,
    speed: Long,
    hasUnknownTotalRunning: Boolean,
    state: DownloadItemState
): String {
    val progressText = buildString {
        if (state == DownloadItemState.SUCCEEDED) {
            // Task is complete: "x / x" is redundant, show the final size only.
            append(Formatting.formatFileSize(totalBytes ?: downloadedBytes))
        } else {
            append(Formatting.formatFileSize(downloadedBytes))
            totalBytes?.takeIf { it > 0L }?.let {
                append(" / ")
                append(Formatting.formatFileSize(it))
            }
        }
    }
    val speedText = when {
        speed > 0L -> formatSpeed(speed)
        hasUnknownTotalRunning || state == DownloadItemState.RUNNING -> "下载中"
        else -> ""
    }
    return when {
        progressText.isBlank() -> speedText
        speedText.isBlank() -> progressText
        else -> "$progressText · $speedText"
    }
}

@Composable
private fun TaskProgressMeta(
    progressFraction: Float?,
    hasUnknownTotalRunning: Boolean,
    state: DownloadItemState,
    emphasizeProgress: Boolean = false
) {
    val colors = AsmrTheme.colorScheme
    val text = when {
        progressFraction != null -> "${(progressFraction * 100).toInt()}%"
        hasUnknownTotalRunning -> "下载中"
        else -> downloadItemStateLabel(state)
    }
    val color = when (state) {
        DownloadItemState.FAILED -> colors.danger
        DownloadItemState.RUNNING, DownloadItemState.ENQUEUED, DownloadItemState.SUCCEEDED -> if (emphasizeProgress) colors.textSecondary else colors.primary
        else -> colors.textSecondary
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
        color = color,
        maxLines = 1
    )
}

@Composable
private fun StableProgressSlot(
    progress: Float?,
    visible: Boolean,
    trackColor: Color,
    progressColor: Color,
    indeterminate: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        if (visible) {
            CompactProgressBar(
                progress = progress,
                trackColor = trackColor,
                progressColor = progressColor,
                indeterminate = indeterminate
            )
        }
    }
}

@Composable
private fun CompactProgressBar(
    progress: Float?,
    trackColor: Color,
    progressColor: Color,
    indeterminate: Boolean,
    modifier: Modifier = Modifier
) {
    if (indeterminate) {
        LinearProgressIndicator(
            modifier = modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = progressColor,
            trackColor = trackColor
        )
        return
    }

    val animatedProgress = animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(durationMillis = 300),
        label = "compact_progress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = trackColor, cornerRadius = radius)
                clipRect(right = size.width * animatedProgress.value) {
                    drawRoundRect(color = progressColor, cornerRadius = radius)
                }
            }
    )
}

private fun downloadItemStateLabel(state: DownloadItemState): String {
    return when (state) {
        DownloadItemState.SUCCEEDED -> "已完成"
        DownloadItemState.FAILED -> "失败"
        DownloadItemState.RUNNING -> "下载中"
        DownloadItemState.PAUSED -> "已暂停"
        DownloadItemState.CANCELLED -> "已取消"
        DownloadItemState.ENQUEUED -> "等待中"
    }
}

@Composable
private fun pickFileIcon(fileName: String): androidx.compose.ui.graphics.vector.ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext in setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus") -> Icons.Rounded.MusicNote
        ext in setOf("lrc", "srt", "vtt") -> Icons.Rounded.Subtitles
        ext in setOf("jpg", "jpeg", "png", "webp") -> Icons.Rounded.Image
        ext in setOf("mp4", "m4v", "webm", "mkv", "mov") -> Icons.Rounded.Movie
        else -> Icons.AutoMirrored.Rounded.InsertDriveFile
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

private enum class RevealAnchor { Closed, Open }

internal class SwipeRevealCloseController {
    private var owner: Any? = null
    private var closeAction: (() -> Unit)? = null

    fun register(owner: Any, closeAction: () -> Unit) {
        this.owner = owner
        this.closeAction = closeAction
    }

    fun unregister(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            closeAction = null
        }
    }

    fun requestClose() {
        closeAction?.invoke()
    }
}

/**
 * Swipe-left-to-reveal container for task-level (RJ number) cards. The trailing
 * [actions] are hidden behind the card by default; the card translates left while
 * dragging, exposing the action buttons. The [revealed] flag is the single source
 * of truth owned by the caller (so only one card is open at a time), and settles
 * are reported back through [onRevealedChange].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SwipeRevealActionsBox(
    modifier: Modifier = Modifier,
    revealed: Boolean,
    enabled: Boolean,
    closeController: SwipeRevealCloseController,
    onRevealedBoundsChanged: (Rect) -> Unit,
    onRevealedChange: (Boolean) -> Unit,
    actionWidth: Dp,
    actions: (@Composable RowScope.() -> Unit)?,
    content: @Composable () -> Unit
) {
    if (actions == null || actionWidth <= 0.dp) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val closeControllerOwner = remember { Any() }
    val currentOnRevealedChange by rememberUpdatedState(onRevealedChange)
    val currentOnRevealedBoundsChanged by rememberUpdatedState(onRevealedBoundsChanged)
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val state = remember(actionWidthPx) {
        AnchoredDraggableState(
            initialValue = RevealAnchor.Closed,
            anchors = DraggableAnchors {
                RevealAnchor.Closed at 0f
                RevealAnchor.Open at -actionWidthPx
            },
            positionalThreshold = { distance -> distance * 0.35f },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            snapAnimationSpec = SwipeRevealSpringSpec,
            decayAnimationSpec = exponentialDecay()
        )
    }
    var internallyReportedRevealed by remember(state) { mutableStateOf(false) }
    LaunchedEffect(revealed, enabled, state) {
        val target = if (revealed && enabled) RevealAnchor.Open else RevealAnchor.Closed
        if (!enabled || revealed != internallyReportedRevealed || state.targetValue != target) {
            if (target == RevealAnchor.Closed) {
                state.animateToFromRest(target)
            } else {
                state.animateTo(target)
            }
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.currentValue == RevealAnchor.Open }
            .distinctUntilChanged()
            .collect { open ->
                internallyReportedRevealed = open
                currentOnRevealedChange(open)
            }
    }
    val interceptContentTap by remember(state, revealed, enabled) {
        derivedStateOf {
            enabled && (revealed || state.requireOffset() < 0f)
        }
    }
    val closeFromRest = remember(state, coroutineScope) {
        {
            coroutineScope.launch {
                state.animateToFromRest(RevealAnchor.Closed)
            }
            Unit
        }
    }
    SideEffect {
        if (revealed && enabled) {
            closeController.register(closeControllerOwner, closeFromRest)
        } else {
            closeController.unregister(closeControllerOwner)
        }
    }
    DisposableEffect(closeController, closeControllerOwner) {
        onDispose { closeController.unregister(closeControllerOwner) }
    }

    val colors = AsmrTheme.colorScheme
    var boundsInRoot by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(revealed, boundsInRoot) {
        if (revealed) {
            boundsInRoot?.let(currentOnRevealedBoundsChanged)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                val currentBounds = coordinates.boundsInRoot()
                boundsInRoot = currentBounds
                if (revealed) {
                    currentOnRevealedBoundsChanged(currentBounds)
                }
            }
    ) {
        // 操作列只占用卡片 header 的高度，展开详情时不会被拉长。
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(actionWidth)
                .height(SwipeActionHeaderHeight)
                .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()
        }
        // Foreground card content. The opaque background keeps the
        // semi-transparent card from ghosting the buttons underneath.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
                .background(colors.background)
                .then(
                    if (enabled) {
                        Modifier.anchoredDraggable(
                            state = state,
                            orientation = Orientation.Horizontal,
                            startDragImmediately = false
                        )
                    } else {
                        Modifier.pointerInput(Unit) {
                            detectHorizontalDragGestures { change, _ -> change.consume() }
                        }
                    }
                )
        ) {
            content()
        }
        // 收起动画完全归零前持续拦截卡片点击，避免快速再次点击穿透并展开详情。
        if (interceptContentTap) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(SwipeActionHeaderHeight)
                    .padding(end = actionWidth)
                    .anchoredDraggable(
                        state = state,
                        orientation = Orientation.Horizontal,
                        startDragImmediately = false
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        closeFromRest()
                    }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private suspend fun AnchoredDraggableState<RevealAnchor>.animateToFromRest(
    target: RevealAnchor
) {
    anchoredDrag(targetValue = target) { anchors, latestTarget ->
        val targetOffset = anchors.positionOf(latestTarget)
        if (targetOffset.isNaN()) return@anchoredDrag

        animate(
            initialValue = requireOffset(),
            targetValue = targetOffset,
            initialVelocity = 0f,
            animationSpec = SwipeRevealSpringSpec
        ) { value, velocity ->
            dragTo(value, velocity)
        }
    }
}

@Composable
private fun RowScope.SwipeRevealAction(
    backgroundColor: Color,
    tint: Color,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(backgroundColor.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp)
        )
    }
}
