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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import com.asmr.player.ui.common.collapsibleHeaderUiState
import com.asmr.player.ui.common.rememberCollapsibleHeaderState
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.util.Formatting
import com.asmr.player.util.MessageManager
import com.asmr.player.util.RemoteSubtitleSource

@Composable
internal fun AlbumLocalBreadcrumbTabV2(
    stateKey: String,
    initialCurrentPath: String,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
    animateIntro: Boolean,
    album: Album,
    header: @Composable () -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onPlayVideo: (String, String, String, String) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    preferredCurrentPath: String,
    onTogglePreferredCurrentPath: (String, Boolean) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onManageTrackTags: (Track) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onSetCoverFromImage: (String) -> Unit,
    onPreviewFile: (LocalTreeUiEntry.File) -> Unit,
) {
    val queueTracks = remember(album.id, album.tracks) { album.tracks.sortedBy { it.path } }
    val queueTrackIds = remember(queueTracks) {
        queueTracks.asSequence().map { it.id }.filter { it > 0L }.distinct().toList()
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val allPaths = remember(album) { album.getAllLocalPaths() }
    var currentPath by rememberSaveable(stateKey) { mutableStateOf(initialCurrentPath.trim().trim('/')) }

    val listState = rememberSaveable("scroll:$stateKey", saver = LazyListState.Saver) {
        LazyListState(initialScroll.first, initialScroll.second)
    }
    LaunchedEffect(listState, stateKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, off) -> onPersistScroll(idx, off) }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
        }
    }
    LaunchedEffect(currentPath, stateKey) {
        onPersistCurrentPath(currentPath)
    }

    val subtitleTrackIds by produceState(initialValue = emptySet<Long>(), key1 = queueTrackIds) {
        value = withContext(Dispatchers.IO) {
            if (queueTrackIds.isEmpty()) emptySet()
            else AppDatabaseProvider.get(context).trackDao().getTrackIdsWithSubtitles(queueTrackIds).toSet()
        }
    }
    val remoteSubtitleTrackIds by produceState(initialValue = emptySet<Long>(), key1 = queueTrackIds) {
        value = withContext(Dispatchers.IO) {
            if (queueTrackIds.isEmpty()) emptySet()
            else AppDatabaseProvider.get(context).remoteSubtitleSourceDao()
                .getTrackIdsWithRemoteSources(queueTrackIds)
                .toSet()
        }
    }
    val treeIndex by produceState<LocalTreeIndex?>(initialValue = null, key1 = allPaths, key2 = queueTracks) {
        value = withContext(Dispatchers.IO) {
            loadOrBuildLocalTreeIndex(
                context = context,
                albumId = album.id,
                albumPaths = allPaths,
                tracks = queueTracks
            )
        }
    }
    val browser = remember(treeIndex, currentPath, subtitleTrackIds, remoteSubtitleTrackIds, album) {
        treeIndex?.let { index ->
            buildLocalDirectoryBrowser(
                index = index,
                currentPath = currentPath,
                album = album,
                shouldShowSubtitleStamp = { track ->
                    track?.let { subtitleTrackIds.contains(it.id) || remoteSubtitleTrackIds.contains(it.id) } == true
                }
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(chromeState.nestedScrollConnection)
            .thinScrollbar(listState),
        state = listState,
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item(key = "local-header:$stateKey") { header() }
        val browserValue = browser
        if (browserValue == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("当前目录暂无文件")
                }
            }
        } else {
            item {
                DirectoryBrowserPanelV4(
                    panelKey = stateKey,
                    currentPath = currentPath,
                    breadcrumbs = browserValue.breadcrumbs,
                    batchTargets = browserValue.batchTargets,
                    folders = browserValue.folders,
                    files = browserValue.files,
                    onNavigate = { path -> currentPath = path },
                    onAddToFavorites = onAddMediaItemsToFavorites,
                    onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                    onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                    animateIntro = animateIntro,
                    preferredPath = preferredCurrentPath,
                    onTogglePreferredPath = { enabled ->
                        onTogglePreferredCurrentPath(currentPath, enabled)
                    },
                    folderKeyPrefix = "folder",
                    fileKeyPrefix = "file",
                    fileContent = { file, selectionMode, selected, enterSelectionMode, onSelectedChange ->
                        val track = file.track
                        DirectoryFileRow(
                            file = file,
                            loadRemoteFileSize = { null },
                            onPrimary = {
                                scope.launch {
                                    val prepared = withContext(Dispatchers.Default) {
                                        val artwork = albumArtworkLabel(album)
                                        val artist = albumArtistLabel(album)
                                        if ((file.fileType == TreeFileType.Audio && track != null) || file.fileType == TreeFileType.Video) {
                                            val nodes = treeIndex?.let { siblingPlayableNodesForEntry(it, file.path) }.orEmpty()
                                            val siblingItems = nodes.mapNotNull { node ->
                                                val abs = node.absolutePath ?: return@mapNotNull null
                                                when (node.fileType) {
                                                    TreeFileType.Audio -> node.track?.let { MediaItemFactory.fromTrack(album, it) }
                                                    TreeFileType.Video -> buildVideoMediaItem(
                                                        title = node.name,
                                                        uriOrPath = abs,
                                                        artworkUri = artwork,
                                                        artist = artist
                                                    )
                                                    else -> null
                                                }
                                            }
                                            val clickedId = when (file.fileType) {
                                                TreeFileType.Audio -> track?.path?.trim().orEmpty()
                                                TreeFileType.Video -> file.absolutePath.trim()
                                                else -> ""
                                            }
                                            val items = if (siblingItems.isNotEmpty()) {
                                                siblingItems
                                            } else {
                                                when (file.fileType) {
                                                    TreeFileType.Audio -> queueTracks.map { MediaItemFactory.fromTrack(album, it) }
                                                    TreeFileType.Video -> listOfNotNull(
                                                        buildVideoMediaItem(
                                                            title = file.title,
                                                            uriOrPath = file.absolutePath,
                                                            artworkUri = artwork,
                                                            artist = artist
                                                        )
                                                    )
                                                    else -> emptyList()
                                                }
                                            }
                                            if (items.isNotEmpty()) {
                                                val startIndex = items.indexOfFirst { it.mediaId.trim() == clickedId }
                                                    .takeIf { it >= 0 } ?: 0
                                                return@withContext PreparedMediaPlayback(items, startIndex)
                                            }
                                        }
                                        null
                                    }
                                    if (prepared != null) {
                                        onPlayMediaItems(prepared.items, prepared.startIndex)
                                    } else {
                                        onPreviewFile(
                                            LocalTreeUiEntry.File(
                                                path = file.path,
                                                title = file.title,
                                                depth = 0,
                                                absolutePath = file.absolutePath,
                                                fileType = file.fileType,
                                                track = file.track
                                            )
                                        )
                                    }
                                }
                            },
                            selectionMode = selectionMode,
                            selected = selected,
                            onEnterSelectionMode = enterSelectionMode,
                            onSelectedChange = onSelectedChange,
                            onSetAsCover = if (file.fileType == TreeFileType.Image) ({ onSetCoverFromImage(file.absolutePath) }) else null,
                            onDownload = null,
                            onAddToQueue = track?.let { { onAddToQueue(it); Unit } },
                            onAddToPlaylist = track?.let { { onAddToPlaylist(it) } },
                            onManageTags = track?.let { { onManageTrackTags(it) } },
                            onRemoveFromAlbum = track?.let { { onRemoveTrack(it) } }
                        )
                    }
                )
            }
        }
    }
}
