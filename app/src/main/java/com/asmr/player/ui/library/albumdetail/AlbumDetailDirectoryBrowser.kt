package com.asmr.player.ui.library

import com.asmr.player.ui.common.AudioMetadataLine
import com.asmr.player.ui.common.formatCvNames
import com.asmr.player.ui.common.AudioSource
import com.asmr.player.ui.common.rememberAudioMetadata

import com.asmr.player.translation.translatedPageText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.media3.common.MediaItem
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.ui.draw.clip
import com.asmr.player.ui.common.SubtitleStamp
import com.asmr.player.ui.common.AudioItemMenuButtonSize
import com.asmr.player.ui.common.AudioItemSubtitleStampSpacing
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.NoImageLoadingIndicator
import com.asmr.player.ui.common.CollapsibleHeaderState
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.util.Formatting

private fun directoryFileSelectionShape(position: DirectoryFolderPosition): RoundedCornerShape {
    return when (position) {
        DirectoryFolderPosition.Single -> RoundedCornerShape(DirectoryFileRowCornerRadius)
        DirectoryFolderPosition.First -> RoundedCornerShape(
            topStart = DirectoryFileRowCornerRadius,
            topEnd = DirectoryFileRowCornerRadius,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )
        DirectoryFolderPosition.Middle -> RoundedCornerShape(0.dp)
        DirectoryFolderPosition.Last -> RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = DirectoryFileRowCornerRadius,
            bottomEnd = DirectoryFileRowCornerRadius,
        )
    }
}

internal fun directoryBrowserHeaderBackground(colorScheme: AsmrColorScheme): Color {
    return colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.72f else 0.9f)
}

private val DirectoryBrowserPanelVerticalPadding = 4.dp

private val DirectoryFileRowCornerRadius = 12.dp

private val DirectoryBrowserPanelCornerRadius = 22.dp

@Composable
internal fun CompactBreadcrumbNode(
    text: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colorScheme.primary else colorScheme.textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = if (selected) {
                MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = if (selected) colorScheme.primary else colorScheme.textSecondary,
            modifier = Modifier.widthIn(max = 112.dp)
        )
    }
}

@Composable
internal fun DirectoryActionGroupButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDisabledClick: (() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    TextButton(
        onClick = {
            if (enabled) onClick() else onDisabledClick?.invoke()
        },
        enabled = enabled || onDisabledClick != null,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (enabled) colorScheme.primary else colorScheme.textTertiary,
            disabledContentColor = colorScheme.textTertiary
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun CompactDirectoryBreadcrumbContent(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val displayedCrumbs = remember(breadcrumbs) {
        when {
            breadcrumbs.size <= 2 -> breadcrumbs
            else -> listOf(
                DirectoryBreadcrumbSegment(label = "...", path = ""),
                breadcrumbs[breadcrumbs.lastIndex - 1],
                breadcrumbs.last()
            )
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CompactBreadcrumbNode(
            text = "根目录",
            selected = currentPath.isBlank(),
            icon = Icons.Rounded.Home,
            onClick = { onNavigate("") }
        )
        displayedCrumbs.forEach { crumb ->
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.textTertiary.copy(alpha = 0.72f),
                modifier = Modifier.size(14.dp)
            )
            if (crumb.label == "..." && crumb.path.isBlank()) {
                Text(
                    text = "...",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.textTertiary,
                    maxLines = 1
                )
            } else {
                CompactBreadcrumbNode(
                    text = translatedPageText(crumb.label),
                    selected = crumb.path == currentPath,
                    onClick = { onNavigate(crumb.path) }
                )
            }
        }
    }
}

@Composable
internal fun DirectoryFolderRow(
    title: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(11.dp))
        Text(
            text = translatedPageText(title),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = colorScheme.textPrimary
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.textTertiary,
            modifier = Modifier.size(17.dp)
        )
        if (onDelete != null) {
            var showMenuExpanded by rememberSaveable(title) { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showMenuExpanded = true },
                    modifier = Modifier.size(AudioItemMenuButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "目录操作",
                        tint = colorScheme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                MaterialTheme(
                    colorScheme = materialColorScheme.copy(
                        surface = dynamicContainerColor,
                        surfaceContainer = dynamicContainerColor
                    )
                ) {
                    DropdownMenu(
                        expanded = showMenuExpanded,
                        onDismissRequest = { showMenuExpanded = false },
                        modifier = Modifier.background(dynamicContainerColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("删除目录", color = materialColorScheme.error) },
                            onClick = {
                                showMenuExpanded = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = materialColorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DirectoryBatchBarEmbedded(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    hintText: String,
    showActions: Boolean,
    modifier: Modifier = Modifier,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    showTranslateAction: Boolean = false,
    subtitleGenerationText: String = "生成并翻译字幕",
    subtitleGenerationEnabled: Boolean = false,
    onGenerateSubtitles: () -> Unit = {},
    onSubtitleGenerationUnavailable: (() -> Unit)? = null
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp
                ),
                color = AsmrTheme.colorScheme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hintText,
                style = MaterialTheme.typography.labelSmall,
                color = AsmrTheme.colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showActions) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DirectoryActionGroupButton(
                    text = "收藏",
                    icon = Icons.Rounded.FavoriteBorder,
                    enabled = hasMediaItems,
                    onClick = { onAddToFavorites(mediaItems) }
                )
                DirectoryActionGroupButton(
                    text = "列表",
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    enabled = hasMediaItems,
                    onClick = {
                        if (hasMediaItems) {
                            onOpenBatchPlaylistPicker(mediaItems.toList())
                        }
                    }
                )
                DirectoryActionGroupButton(
                    text = "队列",
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    enabled = hasMediaItems,
                    onClick = { onAddMediaItemsToQueue(mediaItems) }
                )
                if (showTranslateAction) {
                    DirectoryActionGroupButton(
                        text = subtitleGenerationText,
                        icon = Icons.Rounded.Translate,
                        enabled = subtitleGenerationEnabled,
                        onClick = onGenerateSubtitles,
                        onDisabledClick = onSubtitleGenerationUnavailable
                    )
                }
            }
        } else if (showTranslateAction) {
            DirectoryActionGroupButton(
                text = subtitleGenerationText,
                icon = Icons.Rounded.Translate,
                enabled = subtitleGenerationEnabled,
                onClick = onGenerateSubtitles,
                onDisabledClick = onSubtitleGenerationUnavailable
            )
        }
    }
}

@Composable
internal fun DirectoryBrowserPanel(
    panelKey: String,
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    batchTargets: List<PlaylistAddTarget>,
    folders: List<DirectoryFolderItem>,
    files: List<DirectoryFileItem>,
    onNavigate: (String) -> Unit,
    onDeleteFolder: ((DirectoryFolderItem) -> Unit)? = null,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    onGenerateSubtitlesForCurrentDirectory: (() -> Unit)? = null,
    subtitleGenerationForCurrentDirectoryEnabled: Boolean = false,
    onGenerateSubtitlesForSelectedFiles: ((List<DirectoryFileItem>) -> Unit)? = null,
    canGenerateSubtitleForSelectedFile: ((DirectoryFileItem) -> Boolean)? = null,
    subtitleModelAvailable: Boolean = true,
    animateIntro: Boolean = true,
    parentChromeState: CollapsibleHeaderState? = null,
    preferredPath: String = "",
    onTogglePreferredPath: ((Boolean) -> Unit)? = null,
    folderKeyPrefix: String,
    fileKeyPrefix: String,
    emptyText: String = "当前目录暂无文件",
    fileContent: @Composable (
        file: DirectoryFileItem,
        selectionMode: Boolean,
        selected: Boolean,
        selectedPosition: DirectoryFolderPosition,
        enterSelectionMode: () -> Unit,
        onSelectedChange: (Boolean) -> Unit
    ) -> Unit
) {
    val browserListState = rememberSaveable("dir-panel-v4:$panelKey", saver = LazyListState.Saver) {
        LazyListState()
    }
    val listNestedScrollConnection = remember(browserListState, parentChromeState) {
        object : NestedScrollConnection {
            private fun canScrollInside(deltaY: Float): Boolean = when {
                deltaY < 0f -> browserListState.canScrollForward
                deltaY > 0f -> browserListState.canScrollBackward
                else -> false
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                parentChromeState?.setDescendantScrollBlocked(canScrollInside(available.y))
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                parentChromeState?.setDescendantScrollBlocked(canScrollInside(available.y))
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                parentChromeState?.setDescendantScrollBlocked(false)
                return Velocity.Zero
            }
        }
    }
    DisposableEffect(parentChromeState) {
        onDispose {
            parentChromeState?.setDescendantScrollBlocked(false)
        }
    }
    var selectionMode by remember(panelKey, currentPath) { mutableStateOf(false) }
    var preferredPathState by rememberSaveable(panelKey) { mutableStateOf(preferredPath.trim().trim('/')) }
    val selectedPaths = remember(panelKey, currentPath) { mutableStateListOf<String>() }
    val selectedPathSet = remember(selectedPaths.toList()) { selectedPaths.toSet() }
    val selectedFiles = remember(files, selectedPathSet) {
        files.filter { selectedPathSet.contains(it.path) }
    }
    val activeTargets = remember(selectionMode, batchTargets, selectedFiles) {
        if (selectionMode) selectedFiles.mapNotNull { it.playlistTarget } else batchTargets
    }
    val batchSummaryText = remember(selectionMode, selectedPaths.size, batchTargets.size) {
        if (selectionMode) "已选 ${selectedPaths.size} 项" else "媒体 ${batchTargets.size} 项"
    }
    val batchHintText = remember(selectionMode) {
        if (selectionMode) "点击文件可增减选择" else "长按可批量操作"
    }
    val showTranslateAction = if (selectionMode) {
        onGenerateSubtitlesForSelectedFiles != null
    } else {
        onGenerateSubtitlesForCurrentDirectory != null
    }
    val hasSubtitleGenerationTargets = if (selectionMode) {
        val predicate = canGenerateSubtitleForSelectedFile
        predicate != null && selectedFiles.any(predicate)
    } else {
        subtitleGenerationForCurrentDirectoryEnabled
    }
    val subtitleGenerationEnabled = hasSubtitleGenerationTargets && subtitleModelAvailable
    val onGenerateSubtitles: () -> Unit = {
        if (selectionMode) {
            onGenerateSubtitlesForSelectedFiles?.invoke(selectedFiles)
        } else {
            onGenerateSubtitlesForCurrentDirectory?.invoke()
        }
        Unit
    }
    LaunchedEffect(preferredPath) {
        preferredPathState = preferredPath.trim().trim('/')
    }
    val normalizedCurrentPath = remember(currentPath) { currentPath.trim().trim('/') }
    val isPreferredPath = remember(preferredPathState, normalizedCurrentPath) {
        preferredPathState == normalizedCurrentPath
    }
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val fixedHeight = remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }
    val colorScheme = AsmrTheme.colorScheme
    val sectionDividerColor = colorScheme.textTertiary.copy(alpha = if (colorScheme.isDark) 0.14f else 0.09f)

    LaunchedEffect(panelKey, currentPath) {
        browserListState.scrollToItem(0)
    }

    Surface(
        shape = RoundedCornerShape(DirectoryBrowserPanelCornerRadius),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlbumDetailHorizontalPadding, vertical = DirectoryBrowserPanelVerticalPadding)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            CompactDirectoryBreadcrumbContent(
                currentPath = currentPath,
                breadcrumbs = breadcrumbs,
                onNavigate = onNavigate,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = sectionDividerColor
            )
            Row(
                modifier = dlsiteSectionRevealModifier(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    enabled = animateIntro
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DirectoryBatchBarEmbedded(
                    targets = activeTargets,
                    summaryText = batchSummaryText,
                    hintText = batchHintText,
                    showActions = selectionMode,
                    modifier = Modifier.weight(1f),
                    onAddToFavorites = onAddToFavorites,
                    onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                    onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                    showTranslateAction = showTranslateAction,
                    subtitleGenerationText = if (selectionMode) "翻译选中" else "批量翻译",
                    subtitleGenerationEnabled = subtitleGenerationEnabled,
                    onGenerateSubtitles = onGenerateSubtitles,
                    onSubtitleGenerationUnavailable = onGenerateSubtitles
                )
                if (onTogglePreferredPath != null && !selectionMode) {
                    val preferredIcon = if (isPreferredPath) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder
                    val preferredTextColor = if (isPreferredPath) colorScheme.primary else colorScheme.textSecondary
                    TextButton(
                        onClick = {
                            val enable = !isPreferredPath
                            preferredPathState = if (enable) normalizedCurrentPath else ""
                            onTogglePreferredPath(enable)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = preferredTextColor
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = preferredIcon,
                            contentDescription = null,
                            tint = preferredTextColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "默认打开",
                            style = MaterialTheme.typography.labelMedium,
                            color = preferredTextColor
                        )
                    }
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = sectionDividerColor
            )
            LazyColumn(
                state = browserListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fixedHeight)
                    .nestedScroll(listNestedScrollConnection),
                flingBehavior = rememberCalmScrollableFlingBehavior(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp)
            ) {
                    if (folders.isEmpty() && files.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(fixedHeight - 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                colorScheme.primary.copy(
                                                    alpha = if (colorScheme.isDark) 0.17f else 0.09f
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FolderOpen,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = emptyText,
                                        color = colorScheme.textSecondary,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = folders,
                            key = { _, folder -> "$folderKeyPrefix:${folder.path}" },
                            contentType = { _, _ -> "folder" }
                        ) { index, folder ->
                            Column {
                                DirectoryFolderRow(
                                    title = folder.title,
                                    onClick = { onNavigate(folder.path) },
                                    onDelete = onDeleteFolder?.let { deleteFolder ->
                                        { deleteFolder(folder) }
                                    },
                                )
                                if (index < folders.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 55.dp, end = 12.dp),
                                        thickness = 0.5.dp,
                                        color = sectionDividerColor
                                    )
                                }
                            }
                        }
                        itemsIndexed(
                            items = files,
                            key = { _, file -> "$fileKeyPrefix:${file.path}" },
                            contentType = { _, _ -> "file" }
                        ) { index, file ->
                            val isSelected = selectedPathSet.contains(file.path)
                            val selectedPosition = directorySelectedItemPosition(
                                selected = isSelected,
                                previousSelected = index > 0 && selectedPathSet.contains(files[index - 1].path),
                                nextSelected = index < files.lastIndex && selectedPathSet.contains(files[index + 1].path),
                            )
                            Column {
                                fileContent(
                                    file,
                                    selectionMode,
                                    isSelected,
                                    selectedPosition,
                                    {
                                        selectionMode = true
                                        if (!selectedPaths.contains(file.path)) {
                                            selectedPaths.add(file.path)
                                        }
                                    },
                                    { checked ->
                                        if (checked) {
                                            if (!selectedPaths.contains(file.path)) {
                                                selectedPaths.add(file.path)
                                            }
                                            selectionMode = true
                                        } else {
                                            selectedPaths.remove(file.path)
                                            if (selectedPaths.isEmpty()) {
                                                selectionMode = false
                                            }
                                        }
                                    }
                                )
                                if (index < files.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 50.dp, end = 12.dp),
                                        thickness = 0.5.dp,
                                        color = sectionDividerColor
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DirectoryFileRow(
    file: DirectoryFileItem,
    loadRemoteFileSize: suspend (String) -> Long?,
    onPrimary: () -> Unit,
    loadAudioMetadata: Boolean = true,
    albumCv: String = "",
    selectionMode: Boolean = false,
    selected: Boolean = false,
    selectedPosition: DirectoryFolderPosition = DirectoryFolderPosition.Single,
    onEnterSelectionMode: (() -> Unit)? = null,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    isCoverSelected: Boolean = false,
    onToggleCover: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onGenerateSubtitles: (() -> Unit)? = null,
    subtitleGenerationEnabled: Boolean = true,
    onManageTags: (() -> Unit)? = null,
    onRemoveFromAlbum: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val context = LocalContext.current
    val icon = treeFileTypeIcon(file.fileType)
    val iconTint = treeFileTypeTint(file.fileType, colorScheme)
    val metaLine = if (file.fileType == TreeFileType.Audio) "" else {
        val sizeText by produceState<String?>(initialValue = null, file.sizeSource) {
            val sizeBytes = when (val sizeSource = file.sizeSource) {
                FileSizeSource.None -> null
                is FileSizeSource.Local -> (sizeSource.sizeBytes ?: withContext(Dispatchers.IO) {
                    queryLocalFileSize(context, sizeSource.path)
                })
                is FileSizeSource.Remote -> loadRemoteFileSize(sizeSource.url)
            }?.takeIf { it > 0 }
            value = sizeBytes?.let(Formatting::formatFileSize)
        }
        remember(file.fileType, file.isOnline, file.durationSeconds, sizeText) {
            listOf(
                directoryFileTypeLabel(file),
                Formatting.formatTrackSeconds(file.durationSeconds).takeIf { it.isNotBlank() },
                sizeText
            ).filterNotNull().joinToString(" · ")
        }
    }

    val showPrimaryAction = file.isPlayable
    val showMenu = showPrimaryAction || onDownload != null || onAddToQueue != null || onAddToPlaylist != null || onGenerateSubtitles != null || onManageTags != null || onRemoveFromAlbum != null || onDelete != null
    val showTrailing = selectionMode || onToggleCover != null || file.showSubtitleStamp || showMenu
    val rowContainerColor = if (selected) {
        colorScheme.primary.copy(alpha = if (colorScheme.isDark) 0.18f else 0.09f)
    } else {
        Color.Transparent
    }

    Surface(
        shape = if (selected) directoryFileSelectionShape(selectedPosition) else RoundedCornerShape(DirectoryFileRowCornerRadius),
        color = rowContainerColor,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode && onSelectedChange != null) {
                        onSelectedChange(!selected)
                    } else {
                        onPrimary()
                    }
                },
                onLongClick = {
                    if (!selectionMode && onEnterSelectionMode != null && onSelectedChange != null) {
                        onEnterSelectionMode()
                        onSelectedChange(true)
                    }
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 58.dp)
                .padding(start = 8.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(
                    if (file.fileType == TreeFileType.Image && file.thumbnailModel != null) 42.dp else 32.dp
                ),
                contentAlignment = Alignment.Center
            ) {
                if (file.fileType == TreeFileType.Image && file.thumbnailModel != null) {
                    AsmrAsyncImage(
                        model = file.thumbnailModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                        placeholderCornerRadius = 8,
                        loading = NoImageLoadingIndicator
                    )
                } else {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = translatedPageText(file.title, fileName = true),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.textPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (file.fileType == TreeFileType.Audio) {
                    val audioMetadata = rememberAudioMetadata(
                        file.track?.path ?: file.url.ifBlank { file.absolutePath },
                        loadMetadata = loadAudioMetadata,
                    )
                    AudioMetadataLine(
                        text = remember(albumCv) { formatCvNames(albumCv) },
                        trailingText = remember(file.durationSeconds, audioMetadata?.durationSeconds) {
                            Formatting.formatTrackSeconds(file.durationSeconds?.takeIf { it > 0 } ?: audioMetadata?.durationSeconds)
                        },
                        source = if (file.isOnline) AudioSource.Online else AudioSource.Local,
                        quality = audioMetadata?.quality,
                        color = colorScheme.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        color = colorScheme.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (showTrailing) {
                Spacer(modifier = Modifier.width(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (file.showSubtitleStamp) {
                        SubtitleStamp(modifier = Modifier.padding(end = AudioItemSubtitleStampSpacing))
                    }
                    if (selectionMode && onSelectedChange != null) {
                        Box(
                            modifier = Modifier.size(AudioItemMenuButtonSize),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        width = 1.5.dp,
                                        color = if (selected) colorScheme.primary else colorScheme.textTertiary,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onSelectedChange(!selected) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "已选择",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else if (onToggleCover != null) {
                        IconToggleButton(
                            checked = isCoverSelected,
                            onCheckedChange = { onToggleCover() },
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconToggleButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = colorScheme.textSecondary,
                                checkedContainerColor = Color.Transparent,
                                checkedContentColor = colorScheme.primary,
                                disabledContainerColor = Color.Transparent,
                                disabledContentColor = colorScheme.textTertiary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Photo,
                                contentDescription = if (isCoverSelected) "取消指定封面" else "设为封面",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (!selectionMode && showMenu) {
                        var showMenuExpanded by rememberSaveable(file.path) { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showMenuExpanded = true },
                                modifier = Modifier.size(AudioItemMenuButtonSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "更多操作",
                                    tint = colorScheme.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            MaterialTheme(
                                colorScheme = materialColorScheme.copy(
                                    surface = dynamicContainerColor,
                                    surfaceContainer = dynamicContainerColor
                                )
                            ) {
                                DropdownMenu(
                                    expanded = showMenuExpanded,
                                    onDismissRequest = { showMenuExpanded = false },
                                    modifier = Modifier.background(dynamicContainerColor)
                                ) {
                                    if (showPrimaryAction) {
                                        DropdownMenuItem(
                                            text = { Text("播放") },
                                            onClick = {
                                                onPrimary()
                                                showMenuExpanded = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colorScheme.primary)
                                            }
                                        )
                                    }
                                    if (onDownload != null) {
                                        DropdownMenuItem(
                                            text = { Text("下载") },
                                            onClick = {
                                                onDownload()
                                                showMenuExpanded = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Download, contentDescription = null, tint = colorScheme.textSecondary)
                                            }
                                        )
                                    }
                                    if (onAddToQueue != null) {
                                        DropdownMenuItem(
                                            text = { Text("添加到播放队列") },
                                            onClick = {
                                                onAddToQueue()
                                                showMenuExpanded = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = colorScheme.textSecondary)
                                            }
                                        )
                                    }
                                    if (onAddToPlaylist != null) {
                                        DropdownMenuItem(
                                            text = { Text("添加到我的列表") },
                                            onClick = {
                                                onAddToPlaylist()
                                                showMenuExpanded = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = colorScheme.textSecondary)
                                            }
                                        )
                                    }
                                    if (onGenerateSubtitles != null) {
                                        val subtitleGenerationColor = if (subtitleGenerationEnabled) {
                                            colorScheme.textSecondary
                                        } else {
                                            colorScheme.textTertiary
                                        }
                                        DropdownMenuItem(
                                            text = { Text("生成并翻译字幕", color = subtitleGenerationColor) },
                                            onClick = {
                                                onGenerateSubtitles()
                                                showMenuExpanded = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = subtitleGenerationColor)
                                            }
                                        )
                                    }
                                    if (onManageTags != null) {
                                        DropdownMenuItem(
                                            text = { Text("标签管理") },
                                            onClick = {
                                                onManageTags()
                                                showMenuExpanded = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = null, tint = colorScheme.textSecondary)
                                            }
                                        )
                                    }
                                    if (onRemoveFromAlbum != null) {
                                        DropdownMenuItem(
                                            text = { Text("从专辑移除") },
                                            onClick = {
                                                onRemoveFromAlbum()
                                                showMenuExpanded = false
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = colorScheme.textSecondary)
                                            }
                                        )
                                    }
                                    if (onDelete != null) {
                                        DropdownMenuItem(
                                            text = { Text("删除", color = materialColorScheme.error) },
                                            onClick = {
                                                onDelete()
                                                showMenuExpanded = false
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = null,
                                                    tint = materialColorScheme.error
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
