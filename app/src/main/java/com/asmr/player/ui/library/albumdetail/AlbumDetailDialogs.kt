package com.asmr.player.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asmr.player.data.local.db.AppDatabaseProvider
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.data.remote.scraper.DlsiteRecommendedWork
import com.asmr.player.data.remote.scraper.DlsiteRecommendations
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.Track
import com.asmr.player.playback.MediaItemFactory
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.cache.CacheImageModel
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import com.asmr.player.ui.dlsite.DlsitePlayViewModel
import com.asmr.player.util.DlsiteAntiHotlink
import com.asmr.player.util.SmartSortKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import com.asmr.player.ui.common.rememberDominantColor
import com.asmr.player.ui.common.SubtitleStamp
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AsmrShimmerPlaceholder
import com.asmr.player.ui.common.CvChipsFlow
import com.asmr.player.ui.common.EaraLogoLoadingIndicator
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.collapsibleHeaderUiState
import com.asmr.player.ui.common.rememberCollapsibleHeaderState
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.util.Formatting
import com.asmr.player.util.MessageManager
import com.asmr.player.util.RemoteSubtitleSource

private val AlbumDetailPickerSheetTopRadius = 28.dp
private val AlbumDetailPickerSheetTopGap = 11.dp
private val AlbumDetailPickerSheetContentTopInset = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumDetailPickerSheet(
    onDismissRequest: () -> Unit,
    color: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    content: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxHeight()
            .padding(top = AlbumDetailPickerSheetTopGap),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = Dp.Unspecified,
        shape = RoundedCornerShape(
            topStart = AlbumDetailPickerSheetTopRadius,
            topEnd = AlbumDetailPickerSheetTopRadius
        ),
        containerColor = color,
        contentColor = contentColor,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = AlbumDetailPickerSheetContentTopInset)
                .windowInsetsPadding(StableWindowInsets.navigationBars)
        ) {
            content()
        }
    }
}

@Composable
internal fun AsmrOneDownloadDialog(
    albumTitle: String,
    trackTree: List<AsmrOneTrackNodeResponse>,
    disabledPaths: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val mediaTree = remember(trackTree) { filterDownloadableMediaTree(trackTree) }
    val leafPaths = remember(mediaTree) { flattenAsmrOneLeafDownloads(mediaTree).map { it.relativePath } }
    val selectableLeafPaths = remember(leafPaths, disabledPaths) { leafPaths.filterNot(disabledPaths::contains) }
    val leafPathsByFolder = remember(mediaTree) { buildLeafPathIndex(mediaTree) }
    val expanded = remember { mutableStateListOf<String>() }
    val selected = remember(trackTree, disabledPaths) {
        mutableStateListOf<String>().apply { addAll(selectableLeafPaths) }
    }
    val listState = rememberLazyListState()

    AlbumDetailPickerSheet(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AlbumDetailSelectionSheetTopBar(
                title = "选择要下载的文件",
                confirmText = "开始下载",
                confirmIcon = Icons.Rounded.Download,
                confirmEnabled = selected.isNotEmpty(),
                onDismiss = onDismiss,
                onConfirm = { onConfirm(selected.toSet()) }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlbumDetailSelectionSummary(
                    albumTitle = albumTitle,
                    selectedCount = selected.size,
                    totalCount = selectableLeafPaths.size,
                    unavailableCount = leafPaths.size - selectableLeafPaths.size,
                    unavailableLabel = "已下载",
                    onSelectAll = {
                        selected.clear()
                        selected.addAll(selectableLeafPaths)
                    },
                    onClearSelection = { selected.clear() }
                )
                if (leafPaths.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("没有可下载文件")
                    }
                } else {
                    val entries = flattenAsmrOneTreeForUi(mediaTree, expanded.toSet()).entries
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        flingBehavior = rememberCalmScrollableFlingBehavior(),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        itemsIndexed(items = entries, key = { _, it -> it.path }) { index, entry ->
                            when (entry) {
                                is AsmrTreeUiEntry.Folder -> {
                                    val allFolderLeafPaths = leafPathsByFolder[entry.path].orEmpty()
                                    val selectableFolderLeafPaths = allFolderLeafPaths.filterNot(disabledPaths::contains)
                                    val checkedCount = allFolderLeafPaths.count { path ->
                                        disabledPaths.contains(path) || selected.contains(path)
                                    }
                                    val state = when {
                                        allFolderLeafPaths.isEmpty() -> ToggleableState.Off
                                        checkedCount == 0 -> ToggleableState.Off
                                        checkedCount == allFolderLeafPaths.size -> ToggleableState.On
                                        else -> ToggleableState.Indeterminate
                                    }
                                    AsmrTreeFolderCheckboxRow(
                                        title = entry.title,
                                        depth = entry.depth,
                                        expanded = expanded.contains(entry.path),
                                        toggleState = state,
                                        checkboxEnabled = selectableFolderLeafPaths.isNotEmpty(),
                                        onToggleExpand = {
                                            if (expanded.contains(entry.path)) expanded.remove(entry.path) else expanded.add(entry.path)
                                        },
                                        onToggleCheck = {
                                            if (selectableFolderLeafPaths.isEmpty()) return@AsmrTreeFolderCheckboxRow
                                            val shouldSelectAll = state != ToggleableState.On
                                            if (shouldSelectAll) {
                                                selectableFolderLeafPaths.forEach { if (!selected.contains(it)) selected.add(it) }
                                            } else {
                                                selected.removeAll(selectableFolderLeafPaths.toSet())
                                            }
                                        }
                                    )
                                }
                                is AsmrTreeUiEntry.File -> {
                                    val enabled = !disabledPaths.contains(entry.path)
                                    val isChecked = !enabled || selected.contains(entry.path)
                                    AsmrTreeFileCheckboxRow(
                                        title = entry.title,
                                        depth = entry.depth,
                                        fileType = entry.fileType,
                                        checked = isChecked,
                                        enabled = enabled,
                                        unavailableLabel = "已下载",
                                        onCheckedChange = { checked ->
                                            if (!enabled) return@AsmrTreeFileCheckboxRow
                                            if (checked) {
                                                if (!selected.contains(entry.path)) selected.add(entry.path)
                                            } else {
                                                selected.remove(entry.path)
                                            }
                                        }
                                    )
                                }
                            }
                            if (index < entries.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

internal data class OnlineSaveLeafUi(
    val relativePath: String,
    val title: String,
    val url: String,
    val fileType: TreeFileType
)

internal fun flattenOnlineSaveLeaves(tree: List<AsmrOneTrackNodeResponse>): List<OnlineSaveLeafUi> {
    val out = mutableListOf<OnlineSaveLeafUi>()
    fun sanitize(name: String): String = name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")

    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
        nodes.forEach { node ->
            val children = node.children.orEmpty()
            val titleRaw = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitize(titleRaw)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (children.isEmpty()) {
                if (url.isNullOrBlank()) return@forEach
                val type = treeFileTypeForNode(titleRaw, url, node.type)
                if (!isLibraryResourceSavableTreeFileType(type)) return@forEach
                out.add(
                    OnlineSaveLeafUi(
                        relativePath = path,
                        title = safeTitle.substringBeforeLast('.'),
                        url = url,
                        fileType = type
                    )
                )
            } else {
                walk(children, path)
            }
        }
    }

    walk(tree, "")
    return out
}

private fun buildSaveLeafPathIndex(tree: List<AsmrOneTrackNodeResponse>): Map<String, List<String>> {
    val folderToLeaves = linkedMapOf<String, MutableList<String>>()
    fun sanitize(name: String): String = name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String, folderStack: List<String>) {
        nodes.forEach { node ->
            val titleRaw = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitize(titleRaw)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (children.isEmpty()) {
                if (url.isNullOrBlank()) return@forEach
                val type = treeFileTypeForNode(titleRaw, url, node.type)
                if (!isLibraryResourceSavableTreeFileType(type)) return@forEach
                folderStack.forEach { folder ->
                    folderToLeaves.getOrPut(folder) { mutableListOf() }.add(path)
                }
                folderToLeaves.getOrPut(parentPath) { mutableListOf() }.add(path)
            } else {
                val nextStack = if (parentPath.isBlank()) listOf(path) else folderStack + path
                if (children.isNotEmpty()) {
                    walk(children, path, nextStack)
                }
            }
        }
    }
    walk(tree, "", emptyList())
    return folderToLeaves
}

private fun flattenAsmrOneSaveTreeForUi(
    tree: List<AsmrOneTrackNodeResponse>,
    expanded: Set<String>
): AsmrTreeUiResult {
    val out = mutableListOf<AsmrTreeUiEntry>()
    fun sanitize(name: String): String = name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")

    fun nodeHasMedia(node: AsmrOneTrackNodeResponse): Boolean {
        val children = node.children.orEmpty()
        val titleRaw = node.title?.trim().orEmpty().ifBlank { "item" }
        val url = node.mediaDownloadUrl ?: node.streamUrl
        return if (children.isEmpty()) {
            if (url.isNullOrBlank()) return false
            val type = treeFileTypeForNode(titleRaw, url, node.type)
            isLibraryResourceSavableTreeFileType(type)
        } else {
            children.any { nodeHasMedia(it) }
        }
    }

    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String, depth: Int) {
        nodes.forEach { node ->
            val titleRaw = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitize(titleRaw)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (children.isEmpty()) {
                if (url.isNullOrBlank()) return@forEach
                val type = treeFileTypeForNode(titleRaw, url, node.type)
                if (!isLibraryResourceSavableTreeFileType(type)) return@forEach
                out.add(
                    AsmrTreeUiEntry.File(
                        path = path,
                        title = safeTitle.substringBeforeLast('.'),
                        depth = depth,
                        fileType = type,
                        isPlayable = !url.isNullOrBlank(),
                        url = url
                    )
                )
            } else {
                if (!nodeHasMedia(node)) return@forEach
                out.add(AsmrTreeUiEntry.Folder(path = path, title = safeTitle, depth = depth))
                if (expanded.contains(path)) {
                    walk(children, path, depth + 1)
                }
            }
        }
    }

    walk(tree, "", 0)
    return AsmrTreeUiResult(entries = out)
}

@Composable
internal fun OnlineSaveDialog(
    albumTitle: String,
    trackTree: List<AsmrOneTrackNodeResponse>,
    disabledPaths: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val leaves = remember(trackTree) { flattenOnlineSaveLeaves(trackTree) }
    val leafPaths = remember(leaves) { leaves.map { it.relativePath } }
    val selectableLeafPaths = remember(leafPaths, disabledPaths) { leafPaths.filterNot(disabledPaths::contains) }
    val leafPathsByFolder = remember(trackTree) { buildSaveLeafPathIndex(trackTree) }
    val expanded = remember { mutableStateListOf<String>() }
    val selected = remember(trackTree, disabledPaths) {
        mutableStateListOf<String>().apply { addAll(selectableLeafPaths) }
    }
    val listState = rememberLazyListState()

    AlbumDetailPickerSheet(
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AlbumDetailSelectionSheetTopBar(
                title = "选择要保存的文件",
                confirmText = "保存到本地库",
                confirmIcon = Icons.Rounded.SaveAlt,
                confirmEnabled = selected.isNotEmpty(),
                onDismiss = onDismiss,
                onConfirm = { onConfirm(selected.toSet()) }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlbumDetailSelectionSummary(
                    albumTitle = albumTitle,
                    selectedCount = selected.size,
                    totalCount = selectableLeafPaths.size,
                    unavailableCount = leaves.size - selectableLeafPaths.size,
                    unavailableLabel = "已保存",
                    onSelectAll = {
                        selected.clear()
                        selected.addAll(selectableLeafPaths)
                    },
                    onClearSelection = { selected.clear() }
                )
                if (leaves.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("没有可保存文件")
                    }
                } else {
                    val entries = flattenAsmrOneSaveTreeForUi(trackTree, expanded.toSet()).entries
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        flingBehavior = rememberCalmScrollableFlingBehavior(),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        itemsIndexed(items = entries, key = { _, it -> it.path }) { index, entry ->
                            when (entry) {
                                is AsmrTreeUiEntry.Folder -> {
                                    val allFolderLeafPaths = leafPathsByFolder[entry.path].orEmpty()
                                    val selectableFolderLeafPaths = allFolderLeafPaths.filterNot(disabledPaths::contains)
                                    val checkedCount = allFolderLeafPaths.count { path ->
                                        disabledPaths.contains(path) || selected.contains(path)
                                    }
                                    val state = when {
                                        allFolderLeafPaths.isEmpty() -> ToggleableState.Off
                                        checkedCount == 0 -> ToggleableState.Off
                                        checkedCount == allFolderLeafPaths.size -> ToggleableState.On
                                        else -> ToggleableState.Indeterminate
                                    }
                                    AsmrTreeFolderCheckboxRow(
                                        title = entry.title,
                                        depth = entry.depth,
                                        expanded = expanded.contains(entry.path),
                                        toggleState = state,
                                        checkboxEnabled = selectableFolderLeafPaths.isNotEmpty(),
                                        onToggleExpand = {
                                            if (expanded.contains(entry.path)) expanded.remove(entry.path) else expanded.add(entry.path)
                                        },
                                        onToggleCheck = {
                                            if (selectableFolderLeafPaths.isEmpty()) return@AsmrTreeFolderCheckboxRow
                                            val shouldSelectAll = state != ToggleableState.On
                                            if (shouldSelectAll) {
                                                selectableFolderLeafPaths.forEach { if (!selected.contains(it)) selected.add(it) }
                                            } else {
                                                selected.removeAll(selectableFolderLeafPaths.toSet())
                                            }
                                        }
                                    )
                                }
                                is AsmrTreeUiEntry.File -> {
                                    val enabled = !disabledPaths.contains(entry.path)
                                    val isChecked = !enabled || selected.contains(entry.path)
                                    AsmrTreeFileCheckboxRow(
                                        title = entry.title,
                                        depth = entry.depth,
                                        fileType = entry.fileType,
                                        checked = isChecked,
                                        enabled = enabled,
                                        unavailableLabel = "已保存",
                                        onCheckedChange = { checked ->
                                            if (!enabled) return@AsmrTreeFileCheckboxRow
                                            if (checked) {
                                                if (!selected.contains(entry.path)) selected.add(entry.path)
                                            } else {
                                                selected.remove(entry.path)
                                            }
                                        }
                                    )
                                }
                            }
                            if (index < entries.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
                                )
                            }
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailSelectionSheetTopBar(
    title: String,
    confirmText: String,
    confirmIcon: ImageVector,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Icon(imageVector = confirmIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(confirmText, maxLines = 1)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AlbumDetailSelectionSummary(
    albumTitle: String,
    selectedCount: Int,
    totalCount: Int,
    unavailableCount: Int,
    unavailableLabel: String,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surface.copy(alpha = 0.58f),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = albumTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colorScheme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = buildString {
                        append("已选 $selectedCount / 可选 $totalCount")
                        if (unavailableCount > 0) {
                            append(" · $unavailableLabel $unavailableCount")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                    OutlinedButton(
                        onClick = onSelectAll,
                        enabled = totalCount > 0 && selectedCount < totalCount,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("全选", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onClearSelection,
                        enabled = selectedCount > 0,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("全不选", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AsmrTreeFolderCheckboxRow(
    title: String,
    depth: Int,
    expanded: Boolean,
    toggleState: ToggleableState,
    checkboxEnabled: Boolean,
    onToggleExpand: () -> Unit,
    onToggleCheck: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleExpand)
                .padding(start = (depth * 12).dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                TriStateCheckbox(
                    state = toggleState,
                    onClick = onToggleCheck.takeIf { checkboxEnabled },
                    enabled = checkboxEnabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = colorScheme.primary,
                        uncheckedColor = colorScheme.textSecondary,
                        checkmarkColor = colorScheme.onPrimary,
                        disabledCheckedColor = colorScheme.textTertiary.copy(alpha = 0.58f),
                        disabledUncheckedColor = colorScheme.textTertiary.copy(alpha = 0.48f),
                        disabledIndeterminateColor = colorScheme.textTertiary.copy(alpha = 0.58f)
                    ),
                    modifier = Modifier.size(30.dp)
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AsmrTreeFileCheckboxRow(
    title: String,
    depth: Int,
    fileType: TreeFileType,
    checked: Boolean,
    enabled: Boolean,
    unavailableLabel: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val icon = treeFileTypeIcon(fileType)
    val iconTint = treeFileTypeTint(fileType, colorScheme)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(start = (depth * 12).dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange.takeIf { enabled },
                    enabled = enabled,
                    colors = CheckboxDefaults.colors(
                        checkedColor = colorScheme.primary,
                        uncheckedColor = colorScheme.textSecondary,
                        checkmarkColor = colorScheme.onPrimary,
                        disabledCheckedColor = colorScheme.textTertiary.copy(alpha = 0.58f),
                        disabledUncheckedColor = colorScheme.textTertiary.copy(alpha = 0.48f)
                    ),
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) iconTint else colorScheme.textTertiary.copy(alpha = 0.58f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) colorScheme.textPrimary else colorScheme.textTertiary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (enabled) fileTypeLabel(fileType) else unavailableLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) colorScheme.textSecondary else colorScheme.textTertiary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.widthIn(max = 56.dp)
            )
        }
    }
}

private fun buildLeafPathIndex(tree: List<AsmrOneTrackNodeResponse>): Map<String, List<String>> {
    val folderToLeaves = linkedMapOf<String, MutableList<String>>()
    fun sanitize(name: String): String = name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String, folderStack: List<String>) {
        nodes.forEach { node ->
            val title = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitize(title)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (!url.isNullOrBlank() && children.isEmpty()) {
                folderStack.forEach { folder ->
                    folderToLeaves.getOrPut(folder) { mutableListOf() }.add(path)
                }
                folderToLeaves.getOrPut(parentPath) { mutableListOf() }.add(path)
            } else {
                val nextStack = if (parentPath.isBlank()) listOf(path) else folderStack + path
                if (children.isNotEmpty()) {
                    walk(children, path, nextStack)
                }
            }
        }
    }
    walk(tree, "", emptyList())
    return folderToLeaves
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun InlineVideoPlayer(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = false
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(it).apply {
                this.player = player
                useController = true
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
        }
    )
}

@Composable
internal fun FilePreviewDialog(
    title: String,
    absolutePath: String,
    fileType: TreeFileType,
    messageManager: MessageManager,
    loadOnlineText: (suspend (String) -> String?)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = AsmrTheme.colorScheme
    val initialCandidates by produceState<List<String>>(initialValue = listOf(absolutePath), absolutePath, fileType) {
        value = withContext(Dispatchers.IO) {
            if (absolutePath.startsWith("http", ignoreCase = true) || absolutePath.startsWith("content://")) {
                return@withContext listOf(absolutePath)
            }
            if (fileType != TreeFileType.Video) {
                return@withContext listOf(absolutePath)
            }
            val current = File(absolutePath)
            val parent = current.parentFile ?: return@withContext listOf(absolutePath)
            val exts = when (fileType) {
                TreeFileType.Video -> setOf("mp4", "mkv", "webm", "mov", "m4v")
                else -> emptySet()
            }
            parent.listFiles()
                ?.filter { it.isFile && exts.contains(it.extension.lowercase()) }
                ?.sortedBy { SmartSortKey.of(it.name) }
                ?.map { it.absolutePath }
                ?.ifEmpty { listOf(absolutePath) }
                ?: listOf(absolutePath)
        }
    }
    var currentIndex by remember(initialCandidates, absolutePath) {
        mutableIntStateOf(initialCandidates.indexOf(absolutePath).takeIf { it >= 0 } ?: 0)
    }
    val currentPath = initialCandidates.getOrElse(currentIndex) { absolutePath }
    val currentName = remember(currentPath, absolutePath, title) {
        if (currentPath == absolutePath && title.isNotBlank()) {
            title
        } else {
            currentPath.substringAfterLast('/').substringAfterLast('\\')
        }
    }
    val currentType = remember(currentPath, absolutePath, currentName, fileType) {
        val inferred = treeFileTypeForName(currentName)
        when {
            currentPath == absolutePath && fileType != TreeFileType.Other -> fileType
            inferred != TreeFileType.Other -> inferred
            else -> fileType
        }
    }
    val canNavigate = initialCandidates.size > 1
    var fullscreen by remember { mutableStateOf(false) }
    val canFullscreen = currentType == TreeFileType.Video

    data class MediaSizePx(val w: Int, val h: Int)
    val mediaSize by produceState<MediaSizePx?>(initialValue = null, currentPath, currentType) {
        value = withContext(Dispatchers.IO) {
            when (currentType) {
                TreeFileType.Image -> null
                TreeFileType.Video -> runCatching {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        if (currentPath.startsWith("content://")) {
                            retriever.setDataSource(context, Uri.parse(currentPath))
                        } else {
                            retriever.setDataSource(currentPath)
                        }
                        val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        if (w > 0 && h > 0) MediaSizePx(w, h) else null
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrNull()
                else -> null
            }
        }
    }
    
    fun openWithOtherApp() {
        val path = currentPath.trim()
        if (path.isBlank()) {
            messageManager.showError("无法打开：路径为空")
            return
        }

        runCatching {
            val uri = when {
                path.startsWith("content://", ignoreCase = true) -> Uri.parse(path)
                path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) -> Uri.parse(path)
                else -> {
                    val f = File(path)
                    if (!f.exists()) throw java.io.FileNotFoundException(path)
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        f
                    )
                }
            }

            val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                ?: currentName.substringAfterLast('.', "").lowercase().takeIf { it.isNotBlank() }?.let { ext ->
                    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                }
                ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "打开文件"))
        }.onFailure { t ->
            when (t) {
                is android.content.ActivityNotFoundException -> messageManager.showInfo("未找到可打开的应用")
                is java.io.FileNotFoundException -> messageManager.showError("文件不存在")
                else -> messageManager.showError("无法打开该文件")
            }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val cfg = androidx.compose.ui.platform.LocalConfiguration.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        val baseMaxW = (cfg.screenWidthDp.dp * 0.95f)
        val baseMaxH = (cfg.screenHeightDp.dp * 0.85f)
        val computedSize: Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp>? =
            remember(mediaSize, baseMaxW, baseMaxH, density, fullscreen, currentType) {
            if (fullscreen && currentType == TreeFileType.Video) return@remember null
            val s = mediaSize ?: return@remember null
            val maxWPx = with(density) { baseMaxW.toPx() }
            val maxHPx = with(density) { baseMaxH.toPx() }
            val scale = minOf(maxWPx / s.w.toFloat(), maxHPx / s.h.toFloat(), 1f)
            val wDp = ((s.w.toFloat() * scale) / density.density).dp.coerceAtLeast(260.dp)
            val hDp = ((s.h.toFloat() * scale) / density.density).dp.coerceAtLeast(260.dp)
            wDp to hDp
        }
        Card(
            modifier = Modifier
                .then(
                    if (fullscreen && currentType == TreeFileType.Video) {
                        Modifier.fillMaxSize()
                    } else if (computedSize != null && currentType == TreeFileType.Video) {
                        Modifier.width(computedSize.first).height(computedSize.second)
                    } else {
                        Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f)
                    }
                ),
            shape = if (fullscreen && currentType == TreeFileType.Video) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentName.ifBlank { title },
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (canNavigate) {
                        IconButton(onClick = { currentIndex = (currentIndex - 1 + initialCandidates.size) % initialCandidates.size }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "上一项")
                        }
                        IconButton(onClick = { currentIndex = (currentIndex + 1) % initialCandidates.size }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "下一项")
                        }
                    }
                    if (canFullscreen) {
                        TextButton(onClick = { fullscreen = !fullscreen }) {
                            Text(if (fullscreen) "退出全屏" else "全屏")
                        }
                    }
                    IconButton(onClick = ::openWithOtherApp) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "打开")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭")
                    }
                }
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (currentType) {
                        TreeFileType.Video -> {
                            InlineVideoPlayer(
                                url = currentPath,
                                modifier = Modifier
                                    .fillMaxSize()
                            )
                        }
                        TreeFileType.Subtitle, TreeFileType.Text -> {
                            val textContent by produceState<String?>(initialValue = "加载中...") {
                                value = withContext(Dispatchers.IO) {
                                    runCatching {
                                        if (currentPath.startsWith("content://")) {
                                            context.contentResolver.openInputStream(Uri.parse(currentPath))?.use { input ->
                                                input.bufferedReader().readText()
                                            }
                                        } else if (currentPath.startsWith("http")) {
                                            val loader = loadOnlineText
                                            if (loader != null) {
                                                loader(currentPath)?.takeIf { it.isNotBlank() } ?: "内容为空"
                                            } else {
                                                "在线文件暂不支持内容预览，请使用外部应用打开"
                                            }
                                        } else {
                                            java.io.File(currentPath).readText()
                                        }
                                    }.getOrNull() ?: "读取失败"
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(
                                        state = rememberScrollState(),
                                        flingBehavior = rememberCalmScrollableFlingBehavior()
                                    )
                            ) {
                                Text(
                                    text = textContent ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.textSecondary
                                )
                            }
                        }
                        TreeFileType.Pdf -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = colorScheme.danger
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("PDF 文件暂不支持直接预览", color = colorScheme.textSecondary)
                                Text("请使用外部应用打开", color = colorScheme.textTertiary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        else -> {
                            Text("暂不支持预览该文件类型", color = colorScheme.textTertiary)
                        }
                    }
                }
            }
        }
    }
}
