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

private enum class AlbumPrimaryAction {
    Download,
    Save
}

private enum class OnlineDownloadSource {
    AsmrOne,
    DlsitePlay
}

private data class PreparedTrackPlayback(
    val tracks: List<Track>,
    val startTrack: Track,
    val onlineLyrics: Map<String, List<RemoteSubtitleSource>> = emptyMap()
)

private data class PreparedMediaPlayback(
    val items: List<MediaItem>,
    val startIndex: Int
)

private val AlbumDetailTabContentGap = 12.dp
private val AlbumDetailTabCollapseOvershoot = 10.dp

private val DlsiteElasticResizeSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow
)

private fun dlsiteElasticItemModifier(
    modifier: Modifier = Modifier
): Modifier {
    return modifier.animateContentSize(animationSpec = DlsiteElasticResizeSpring)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    windowSizeClass: WindowSizeClass,
    albumId: Long? = null,
    rjCode: String? = null,
    refreshToken: Long = 0L,
    onConsumeRefreshToken: (() -> Unit)? = null,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit = { _, _ -> },
    onAddToQueue: (Album, Track) -> Boolean = { _, _ -> false },
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit = {},
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit = {},
    onOpenPlaylistPicker: (mediaId: String, uri: String, title: String, artist: String, artworkUri: String, albumId: Long, trackId: Long, rjCode: String) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit = {},
    onOpenGroupPicker: (albumId: Long) -> Unit = { _ -> },
    onPlayVideo: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onOpenDlsiteLogin: () -> Unit = {},
    onOpenAlbumByRj: (String) -> Unit = {},
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val cloudSyncSelectionDialogState by viewModel.cloudSyncSelectionDialogState.collectAsState()
    val colorScheme = AsmrTheme.colorScheme
    val screenKey = remember(albumId, rjCode) {
        val idPart = albumId?.takeIf { it > 0 }?.toString().orEmpty()
        val rjPart = rjCode?.trim().orEmpty().uppercase()
        if (rjPart.isNotBlank()) "album:$rjPart" else "albumId:$idPart"
    }
    var selectedTab by rememberSaveable(screenKey) {
        mutableIntStateOf(if (albumId != null && albumId > 0) 0 else 1)
    }
    var lastChromeResetTab by rememberSaveable(screenKey) {
        mutableIntStateOf(selectedTab)
    }
    var userSelectedTab by rememberSaveable(screenKey) { mutableStateOf(false) }
    var showAsmrDownloadDialog by remember { mutableStateOf(false) }
    var showOnlineSaveDialog by remember { mutableStateOf(false) }
    var pendingOnlineSaveSelection by remember { mutableStateOf<Set<String>?>(null) }
    var batchPlaylistItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var downloadSource by remember { mutableStateOf(OnlineDownloadSource.AsmrOne) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(albumId, rjCode) {
        viewModel.loadAlbum(albumId, rjCode, force = false)
    }
    LaunchedEffect(refreshToken) {
        if (refreshToken == 0L) return@LaunchedEffect
        viewModel.loadAlbum(albumId, rjCode, force = true)
        onConsumeRefreshToken?.invoke()
    }
    LaunchedEffect(pendingOnlineSaveSelection) {
        val selected = pendingOnlineSaveSelection ?: return@LaunchedEffect
        pendingOnlineSaveSelection = null
        viewModel.saveOnlineSelectedToLibrary(selected)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent), // Background handled by MainActivity
        contentAlignment = Alignment.TopCenter // 仅用于平板适配：居中显示内容
    ) {
        val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
        
        Column(
            modifier = if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                // 仅用于平板适配：限制内容区域最大宽度并填充可用空间
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            }
        ) {
            when (val state = uiState) {
                is AlbumDetailUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    }
                }
                is AlbumDetailUiState.Success -> {
                    val model = state.model
                    val album = model.displayAlbum
                    val asmrOneTree = model.asmrOneTree
                    val canSaveOnline = selectedTab == 1 && asmrOneTree.isNotEmpty()
                    val showGroupButton = selectedTab == 0 && model.localAlbum != null
                    val availableTags by viewModel.availableTags.collectAsState()
                    val userTagsByTrackId by viewModel.userTagsByTrackId.collectAsState()
                    val libraryViewModel: LibraryViewModel = hiltViewModel()
                    val tagManagerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    var showTagManager by remember { mutableStateOf(false) }
                    var tagManageTrack by remember { mutableStateOf<Track?>(null) }
                    val context = LocalContext.current
                    val coverPicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument(),
                        onResult = { uri ->
                            if (uri == null) return@rememberLauncherForActivityResult
                            runCatching {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
                            viewModel.setLocalCoverPath(uri.toString())
                        }
                    )
                    var localPreviewFile by remember { mutableStateOf<LocalTreeUiEntry.File?>(null) }
                    var onlinePreviewFile by remember { mutableStateOf<AsmrTreeUiEntry.File?>(null) }
                    val tabChromeState = rememberCollapsibleHeaderState()
                    val animatedTabChromeOffsetPx by animateFloatAsState(
                        targetValue = tabChromeState.offsetPx,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "albumDetailTabChromeOffset"
                    )
                    val tabChromeVisibleHeight = if (tabChromeState.heightPx > 0f) {
                        with(LocalDensity.current) {
                            (tabChromeState.heightPx + tabChromeState.offsetPx)
                                .coerceIn(0f, tabChromeState.heightPx)
                                .toDp()
                        }
                    } else {
                        56.dp
                    }
                    val tabContentTopPadding = tabChromeVisibleHeight + AlbumDetailTabContentGap
    
                    LaunchedEffect(model.rjCode, model.dlsiteWorkno, model.localAlbum?.id, selectedTab) {
                        if (selectedTab != 0 && model.dlsiteInfo == null && model.dlsiteWorkno.isNotBlank()) {
                            viewModel.ensureDlsiteLoaded()
                        }
                    }
                    LaunchedEffect(selectedTab) {
                        if (lastChromeResetTab != selectedTab) {
                            tabChromeState.expand()
                            lastChromeResetTab = selectedTab
                        }
                    }
    
                    Column(modifier = Modifier.fillMaxSize()) {
                        val headerContent: @Composable () -> Unit = {
                            AlbumHeader(
                                album = if (selectedTab == 0) (model.localAlbum ?: album) else album,
                                dlsiteUrl = model.dlsiteWorkno.takeIf { it.isNotBlank() }?.let { "https://www.dlsite.com/maniax/work/=/product_id/$it.html" }.orEmpty(),
                                asmrOneUrl = model.asmrOneWorkId?.takeIf { it.isNotBlank() }?.let { "https://asmr.one/work/$it" }.orEmpty(),
                                dlsiteEditions = if (selectedTab == 0) emptyList() else model.dlsiteEditions,
                                dlsiteSelectedLang = model.dlsiteSelectedLang,
                                onDlsiteLangSelected = { viewModel.selectDlsiteLanguage(it) },
                                canSaveOnline = canSaveOnline,
                                onDownloadClick = {
                                    downloadSource = if (selectedTab == 2) {
                                        OnlineDownloadSource.DlsitePlay
                                    } else {
                                        OnlineDownloadSource.AsmrOne
                                    }
                                    showAsmrDownloadDialog = true
                                },
                                onSaveClick = {
                                    showOnlineSaveDialog = true
                                },
                                downloadEnabled = when (selectedTab) {
                                    1 -> asmrOneTree.isNotEmpty()
                                    2 -> model.dlsitePlayTree.isNotEmpty()
                                    else -> false
                                },
                                saveEnabled = canSaveOnline,
                                showGroupButton = showGroupButton,
                                onOpenGroupPicker = onOpenGroupPicker,
                                onPickLocalCover = if (selectedTab == 0 && model.localAlbum != null) {
                                    { coverPicker.launch(arrayOf("image/*")) }
                                } else null,
                                messageManager = viewModel.messageManager
                        )
                    }
                    
                    val tabTitles = remember { listOf("本地", "DL", "DL Play") }
                    LaunchedEffect(selectedTab, model.rjCode, model.dlsiteWorkno) {
                        when (selectedTab) {
                            1 -> {
                                viewModel.ensureDlsiteLoaded()
                                viewModel.ensureAsmrOneLoaded()
                            }
                            2 -> viewModel.ensureDlsitePlayLoaded()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .zIndex(0f)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Transparent,
                            shape = RectangleShape,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp
                        ) {
                            AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = {
                                    if (targetState > initialState) {
                                        (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                                    } else {
                                        (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                                    }
                                },
                                label = "albumDetailTabContent"
                            ) { tab ->
                                when (tab) {
                                    0 -> {
                                        val local = model.localAlbum
                                        if (local != null) {
                                            val localTreeStateKey = remember(albumId, rjCode, local.id) {
                                                val rjNorm = rjCode?.trim().orEmpty().uppercase()
                                                when {
                                                    albumId != null && albumId > 0 -> "localTree:id:$albumId"
                                                    rjNorm.isNotBlank() -> "localTree:rj:$rjNorm"
                                                    else -> "localTree:localId:${local.id}"
                                                }
                                            }
                                            AlbumLocalBreadcrumbTabV2(
                                                stateKey = localTreeStateKey,
                                                initialCurrentPath = viewModel.getPreferredTreeCurrentPath(localTreeStateKey)
                                                    .ifBlank { viewModel.getTreeCurrentPath(localTreeStateKey) },
                                                onPersistCurrentPath = { path ->
                                                    viewModel.persistTreeCurrentPath(localTreeStateKey, path)
                                                },
                                                initialScroll = viewModel.getListScrollPosition("scroll:$localTreeStateKey"),
                                                onPersistScroll = { index, offset ->
                                                    viewModel.persistListScrollPosition("scroll:$localTreeStateKey", index, offset)
                                                },
                                                topContentPadding = tabContentTopPadding,
                                                chromeState = tabChromeState,
                                                album = local,
                                                header = headerContent,
                                                onPlayTracks = onPlayTracks,
                                                onPlayMediaItems = onPlayMediaItems,
                                                onPlayVideo = onPlayVideo,
                                                onAddToQueue = { track ->
                                                    onAddToQueue(local, track)
                                                },
                                                onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                                onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                                onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                                preferredCurrentPath = viewModel.getPreferredTreeCurrentPath(localTreeStateKey),
                                                onTogglePreferredCurrentPath = { path, enabled ->
                                                    if (enabled) {
                                                        viewModel.persistPreferredTreeCurrentPath(localTreeStateKey, path)
                                                        viewModel.messageManager.showSuccess("已设为默认打开目录")
                                                    } else {
                                                        viewModel.clearPreferredTreeCurrentPath(localTreeStateKey)
                                                    }
                                                },
                                                onAddToPlaylist = { track ->
                                                    val target = PlaylistAddTarget.fromTrack(local, track)
                                                    onOpenPlaylistPicker(
                                                        target.mediaId,
                                                        target.uri,
                                                        target.title,
                                                        target.artist,
                                                        target.artworkUri,
                                                        target.albumId,
                                                        target.trackId,
                                                        target.rjCode
                                                    )
                                                },
                                                onManageTrackTags = { track ->
                                                    tagManageTrack = track
                                                },
                                                onRemoveTrack = { track ->
                                                    if (track.id > 0L) libraryViewModel.removeTrackFromAlbum(track.id)
                                                },
                                                onSetCoverFromImage = { pathOrUri ->
                                                    viewModel.setLocalCoverPath(pathOrUri)
                                                },
                                                onPreviewFile = { localPreviewFile = it }
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text("未下载到本地")
                                            }
                                        }
                                    }
                                    1 -> AlbumDlsiteInfoBreadcrumbTabV2(
                                        album = album,
                                        header = headerContent,
                                        dlsiteInfo = model.dlsiteInfo,
                                        galleryUrls = model.dlsiteGalleryUrls,
                                        trialTracks = model.dlsiteTrialTracks,
                                        isLoading = model.isLoadingDlsite,
                                        asmrOneTree = asmrOneTree,
                                        isLoadingAsmrOne = model.isLoadingAsmrOne,
                                        isLoadingTrial = model.isLoadingDlsiteTrial,
                                        onRefreshAsmrOne = { viewModel.refreshAsmrOneSection() },
                                        onRefreshTrial = { viewModel.refreshDlsiteTrialSection() },
                                        onPlayTracks = onPlayTracks,
                                        onPlayMediaItems = onPlayMediaItems,
                                        onAddToQueue = { track ->
                                            onAddToQueue(album, track)
                                        },
                                        onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                        onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                        onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                        onDownloadOne = { relPath ->
                                            viewModel.downloadAsmrOneSelected(setOf(relPath))
                                        },
                                        onAddToPlaylistOne = { relPath ->
                                            val target = PlaylistAddTarget.fromAsmrOne(album, asmrOneTree, relPath) ?: return@AlbumDlsiteInfoBreadcrumbTabV2
                                            onOpenPlaylistPicker(
                                                target.mediaId,
                                                target.uri,
                                                target.title,
                                                target.artist,
                                                target.artworkUri,
                                                target.albumId,
                                                target.trackId,
                                                target.rjCode
                                            )
                                        },
                                        onAddToPlaylist = { track ->
                                            val target = PlaylistAddTarget.fromTrack(album, track)
                                            onOpenPlaylistPicker(
                                                target.mediaId,
                                                target.uri,
                                                target.title,
                                                target.artist,
                                                target.artworkUri,
                                                target.albumId,
                                                target.trackId,
                                                target.rjCode
                                            )
                                        },
                                        onPreviewFile = { onlinePreviewFile = it },
                                        treeStateKey = "tree:asmrOne:${model.rjCode.trim().uppercase()}",
                                        initialCurrentPath = viewModel.getTreeCurrentPath("tree:asmrOne:${model.rjCode.trim().uppercase()}"),
                                        topContentPadding = tabContentTopPadding,
                                        chromeState = tabChromeState,
                                        onPersistCurrentPath = { path ->
                                            val rj = model.rjCode.trim().uppercase()
                                            viewModel.persistTreeCurrentPath("tree:asmrOne:$rj", path)
                                        },
                                        initialScroll = viewModel.getListScrollPosition("scroll:tree:asmrOne:${model.rjCode.trim().uppercase()}"),
                                        onPersistScroll = { index, offset ->
                                            viewModel.persistListScrollPosition("scroll:tree:asmrOne:${model.rjCode.trim().uppercase()}", index, offset)
                                        },
                                        dlsiteRecommendations = model.dlsiteRecommendations,
                                        onOpenAlbumByRj = onOpenAlbumByRj,
                                        loadRemoteFileSize = { viewModel.loadRemoteFileSize(it) }
                                    )
                                    else -> AlbumDlsitePlayBreadcrumbTabV2(
                                        header = headerContent,
                                        album = album,
                                        rjCode = model.rjCode,
                                        tree = model.dlsitePlayTree,
                                        isLoading = model.isLoadingDlsitePlay,
                                        onOpenLogin = onOpenDlsiteLogin,
                                        onEnsureLoaded = { viewModel.ensureDlsitePlayLoaded() },
                                        onPlayTracks = onPlayTracks,
                                        onPlayMediaItems = onPlayMediaItems,
                                        onPlayVideo = onPlayVideo,
                                        onAddToQueue = { track ->
                                            onAddToQueue(album, track)
                                        },
                                        onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                                        onAddMediaItemsToFavorites = onAddMediaItemsToFavorites,
                                        onOpenBatchPlaylistPicker = { items -> batchPlaylistItems = items },
                                        onDownloadOne = { relPath ->
                                            viewModel.downloadDlsitePlaySelected(setOf(relPath))
                                        },
                                        onPreviewFile = { onlinePreviewFile = it },
                                        treeStateKey = "tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}",
                                        initialCurrentPath = viewModel.getTreeCurrentPath("tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}"),
                                        topContentPadding = tabContentTopPadding,
                                        chromeState = tabChromeState,
                                        onPersistCurrentPath = { path ->
                                            val rj = model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()
                                            viewModel.persistTreeCurrentPath("tree:dlsitePlay:$rj", path)
                                        },
                                        initialScroll = viewModel.getListScrollPosition("scroll:tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}"),
                                        onPersistScroll = { index, offset ->
                                            viewModel.persistListScrollPosition("scroll:tree:dlsitePlay:${model.baseRjCode.ifBlank { model.rjCode }.trim().uppercase()}", index, offset)
                                        },
                                        loadRemoteFileSize = { viewModel.loadRemoteFileSize(it) }
                                    )
                                }
                            }
                        }
                        AlbumDetailTabChrome(
                            modifier = Modifier.align(Alignment.TopCenter),
                            titles = tabTitles,
                            selectedTab = selectedTab,
                            animatedOffsetPx = animatedTabChromeOffsetPx,
                            collapseFraction = tabChromeState.collapseFraction,
                            onMeasured = { tabChromeState.updateHeight(it.height.toFloat()) },
                            onTabSelected = { index ->
                                selectedTab = index
                                userSelectedTab = true
                            }
                        )
                    }
                }

                if (showAsmrDownloadDialog) {
                    val downloadTree = if (downloadSource == OnlineDownloadSource.DlsitePlay) {
                        model.dlsitePlayTree
                    } else {
                        asmrOneTree
                    }
                    AsmrOneDownloadDialog(
                        albumTitle = album.title,
                        trackTree = downloadTree,
                        onDismiss = { showAsmrDownloadDialog = false },
                        onConfirm = { selected ->
                            when (downloadSource) {
                                OnlineDownloadSource.AsmrOne -> viewModel.downloadAsmrOneSelected(selected)
                                OnlineDownloadSource.DlsitePlay -> viewModel.downloadDlsitePlaySelected(selected)
                            }
                            showAsmrDownloadDialog = false
                        }
                    )
                }

                if (showOnlineSaveDialog && canSaveOnline) {
                    val saveTree = if (model.dlsitePlayTree.isNotEmpty()) model.dlsitePlayTree else asmrOneTree
                    OnlineSaveDialog(
                        albumTitle = album.title,
                        trackTree = saveTree,
                        onDismiss = { showOnlineSaveDialog = false },
                        onConfirm = { selected ->
                            pendingOnlineSaveSelection = selected
                            showOnlineSaveDialog = false
                        }
                    )
                }

                batchPlaylistItems?.let { items ->
                    Dialog(
                        onDismissRequest = { batchPlaylistItems = null },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = colorScheme.background.copy(alpha = 0.96f),
                            contentColor = colorScheme.textPrimary
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp)
                            ) {
                                PlaylistPickerScreen(
                                    windowSizeClass = windowSizeClass,
                                    items = items,
                                    onBack = { batchPlaylistItems = null },
                                    embeddedInDialog = true
                                )
                            }
                        }
                    }
                }

                if (localPreviewFile != null) {
                    FilePreviewDialog(
                        title = localPreviewFile!!.title,
                        absolutePath = localPreviewFile!!.absolutePath,
                        fileType = localPreviewFile!!.fileType,
                        messageManager = viewModel.messageManager,
                        loadOnlineText = viewModel::loadOnlineTextPreview,
                        onDismiss = { localPreviewFile = null }
                    )
                }

                if (onlinePreviewFile != null) {
                    FilePreviewDialog(
                        title = onlinePreviewFile!!.title,
                        absolutePath = onlinePreviewFile!!.url ?: "",
                        fileType = onlinePreviewFile!!.fileType,
                        messageManager = viewModel.messageManager,
                        loadOnlineText = viewModel::loadOnlineTextPreview,
                        onDismiss = { onlinePreviewFile = null }
                    )
                }

                val track = tagManageTrack
                if (track != null && track.id > 0L) {
                    TagAssignDialog(
                        title = track.title,
                        inheritedTags = album.tags,
                        userTags = userTagsByTrackId[track.id].orEmpty(),
                        allTags = availableTags,
                        onApplyUserTags = { list ->
                            viewModel.setUserTagsForTrack(track.id, list)
                            tagManageTrack = null
                        },
                        onDismiss = { tagManageTrack = null },
                        onOpenTagManager = { showTagManager = true }
                    )
                }

                if (showTagManager) {
                    Dialog(
                        onDismissRequest = { showTagManager = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            TagManagerSheet(
                                tags = availableTags,
                                onRename = { tagId, newName -> libraryViewModel.renameUserTag(tagId, newName) },
                                onDelete = { tagId -> libraryViewModel.deleteUserTag(tagId) },
                                onClose = { showTagManager = false }
                            )
                        }
                    }
                }
            }
                is AlbumDetailUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        cloudSyncSelectionDialogState?.let { dialogState ->
            CloudSyncSelectionDialog(
                state = dialogState,
                onSelect = viewModel::confirmCloudSyncSelection,
                onCancel = viewModel::cancelCloudSyncSelection
            )
        }
    }
}

@Composable
private fun AlbumDetailTabChrome(
    modifier: Modifier = Modifier,
    titles: List<String>,
    selectedTab: Int,
    animatedOffsetPx: Float,
    collapseFraction: Float,
    onMeasured: (IntSize) -> Unit,
    onTabSelected: (Int) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val tabContainerShape = RoundedCornerShape(26.dp)
    val tabItemShape = RoundedCornerShape(18.dp)
    val collapseOvershootPx = with(LocalDensity.current) { AlbumDetailTabCollapseOvershoot.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .onSizeChanged(onMeasured)
            .graphicsLayer {
                translationY = animatedOffsetPx -
                    (collapseFraction.coerceIn(0f, 1f) * collapseOvershootPx)
                alpha = 1f - (collapseFraction.coerceIn(0f, 1f) * 0.08f)
            }
            .semantics { stateDescription = collapsibleHeaderUiState(collapseFraction) }
            .zIndex(1f)
    ) {
        val count = titles.size.coerceAtLeast(1)
        val segmentGap = 6.dp
        val segmentPadding = 6.dp
        val segmentHeight = 42.dp
        val slotWidth = (maxWidth - (segmentPadding * 2) - (segmentGap * (count - 1))) / count
        val highlightX by animateDpAsState(
            targetValue = (slotWidth + segmentGap) * selectedTab,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "albumDetailTabHighlightX"
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isDark) 12.dp else 8.dp,
                    shape = tabContainerShape,
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f),
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.25f)
                )
                .then(
                    if (isDark) {
                        Modifier.border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.2f),
                            shape = tabContainerShape
                        )
                    } else {
                        Modifier
                    }
                ),
            color = if (isDark) {
                colorScheme.surface.copy(alpha = 0.96f)
            } else {
                colorScheme.surface.copy(alpha = 0.96f)
            },
            contentColor = colorScheme.textPrimary,
            shape = tabContainerShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(segmentPadding)
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = highlightX)
                        .width(slotWidth)
                        .height(segmentHeight)
                        .clip(tabItemShape)
                        .background(
                            color = if (isDark) {
                                colorScheme.primary.copy(alpha = 0.22f)
                            } else {
                                colorScheme.primary.copy(alpha = 0.12f)
                            },
                            shape = tabItemShape
                        )
                        .border(
                            width = 1.dp,
                            color = if (isDark) {
                                colorScheme.primary.copy(alpha = 0.28f)
                            } else {
                                colorScheme.primary.copy(alpha = 0.18f)
                            },
                            shape = tabItemShape
                        )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(segmentGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    titles.forEachIndexed { index, title ->
                        val selected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .width(slotWidth)
                                .height(segmentHeight)
                                .clip(tabItemShape)
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onTabSelected(index) }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (selected) {
                                    if (isDark) {
                                        colorScheme.primary
                                    } else {
                                        colorScheme.primary
                                    }
                                } else {
                                    colorScheme.textSecondary
                                },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AlbumHeader(
    album: Album,
    dlsiteUrl: String,
    asmrOneUrl: String,
    dlsiteEditions: List<DlsiteLanguageEdition>,
    dlsiteSelectedLang: String,
    onDlsiteLangSelected: (String) -> Unit,
    canSaveOnline: Boolean,
    onDownloadClick: () -> Unit,
    onSaveClick: () -> Unit,
    downloadEnabled: Boolean,
    saveEnabled: Boolean,
    showGroupButton: Boolean,
    onOpenGroupPicker: (albumId: Long) -> Unit,
    onPickLocalCover: (() -> Unit)? = null,
    messageManager: MessageManager
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colorScheme = AsmrTheme.colorScheme
    val data = album.coverPath.ifEmpty { album.coverUrl }
    val imageModel = remember(data) {
        val headers = if (data.startsWith("http", ignoreCase = true)) DlsiteAntiHotlink.headersForImageUrl(data) else emptyMap()
        if (headers.isEmpty()) data else CacheImageModel(data = data, headers = headers, keyTag = "dlsite")
    }

    val rj = album.rjCode.ifBlank { album.workId }
    fun copy(label: String, value: String) {
        val v = value.trim()
        if (v.isBlank()) return
        clipboard.setText(AnnotatedString(v))
        messageManager.showSuccess("$label 已复制")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colorScheme.surface.copy(alpha = 0.5f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                AsmrAsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholderCornerRadius = 0,
                    modifier = Modifier.fillMaxSize(),
                    empty = { m -> DiscPlaceholder(modifier = m, cornerRadius = 0) },
                )
                if (onPickLocalCover != null) {
                    IconButton(
                        onClick = onPickLocalCover,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = "选择封面",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = album.title,
                        modifier = Modifier.clickable { copy("标题", album.title) },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (rj.isNotBlank()) {
                            Text(
                                text = rj,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary,
                                modifier = Modifier
                                    .clickable { copy("RJ", rj) }
                                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        val circle = album.circle.trim()
                        if (circle.isNotBlank()) {
                            Text(
                                text = circle,
                                modifier = Modifier.clickable { copy("社团", circle) },
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CvChipsFlow(
                    cvText = album.cv,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    onCvClick = { cv -> copy("CV", cv) },
                )

                val langCandidates = remember(dlsiteEditions) {
                    dlsiteEditions
                        .filter { it.lang in setOf("JPN", "CHI_HANS", "CHI_HANT") }
                        .distinctBy { it.lang }
                        .sortedWith(compareBy({ it.displayOrder }, { it.lang }))
                }

                if (langCandidates.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        langCandidates.forEach { edition ->
                            val selected = edition.lang.equals(dlsiteSelectedLang, ignoreCase = true)
                            val label = when (edition.lang) {
                                "CHI_HANS" -> "简中"
                                "CHI_HANT" -> "繁中"
                                else -> "日语"
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { onDlsiteLangSelected(edition.lang) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colorScheme.primary.copy(alpha = 0.2f),
                                    selectedLabelColor = colorScheme.primary,
                                    selectedLeadingIconColor = colorScheme.primary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = colorScheme.primary.copy(alpha = 0.3f),
                                    selectedBorderColor = colorScheme.primary
                                )
                            )
                        }
                    }
                }

                if (album.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        album.tags.forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                                modifier = Modifier
                                    .background(colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .clickable { copy("标签", tag) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.height(36.dp).weight(1f)) {
                        val radius = 10.dp
                        val leftShape = if (canSaveOnline) {
                            RoundedCornerShape(topStart = radius, bottomStart = radius, topEnd = 0.dp, bottomEnd = 0.dp)
                        } else {
                            RoundedCornerShape(radius)
                        }
                        val rightShape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = radius, bottomEnd = radius)

                        Button(
                            onClick = onDownloadClick,
                            enabled = downloadEnabled,
                            modifier = Modifier.fillMaxHeight().weight(1f),
                            shape = leftShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("下载", style = MaterialTheme.typography.labelMedium)
                        }

                        if (canSaveOnline) {
                            Button(
                                onClick = onSaveClick,
                                enabled = saveEnabled,
                                modifier = Modifier.fillMaxHeight().weight(1f),
                                shape = rightShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colorScheme.primary.copy(alpha = 0.14f),
                                    contentColor = colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("保存", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    
                    if (showGroupButton) {
                        OutlinedButton(
                            onClick = {
                                val id = album.id
                                if (id > 0L) onOpenGroupPicker(id)
                            },
                            enabled = album.id > 0L,
                            modifier = Modifier
                                .height(36.dp)
                                .widthIn(min = 98.dp, max = 140.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("分组", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        }
                    }

                    listOf(
                        "DLsite" to dlsiteUrl,
                        "ONE" to asmrOneUrl
                    ).forEach { (label, url) ->
                        OutlinedButton(
                            onClick = {
                                if (url.isNotBlank()) {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                            enabled = url.isNotBlank(),
                            modifier = Modifier
                                .height(36.dp)
                                .widthIn(min = 56.dp, max = 76.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlbumDescription(album: Album) {
    val colorScheme = AsmrTheme.colorScheme
    val processedText = remember(album.description) {
        album.description
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }
    val paragraphs = remember(processedText) {
        processedText.split(Regex("\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }
    val defaultMaxLines = 10
    var expanded by rememberSaveable(album.id, album.rjCode, album.workId) { mutableStateOf(false) }
    val shouldCollapse = remember(paragraphs) { paragraphs.size > defaultMaxLines }
    val visible = remember(paragraphs, expanded, shouldCollapse) {
        if (!shouldCollapse || expanded) paragraphs else paragraphs.take(defaultMaxLines)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        color = colorScheme.surface.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "简介",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                if (shouldCollapse) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "展开", color = colorScheme.primary)
                    }
                }
            }
            Divider(color = colorScheme.textTertiary.copy(alpha = 0.25f))
            if (paragraphs.isEmpty()) {
                Text("暂无介绍", color = colorScheme.textTertiary)
            } else {
                visible.forEach { p ->
                    val trimmed = p.trimStart()
                    val isBullet = trimmed.startsWith("・") || trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("★")
                    if (isBullet) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colorScheme.primary.copy(alpha = 0.8f))
                            )
                            Text(
                                text = trimmed.trimStart('・', '-', '•', '★', ' '),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp,
                                color = colorScheme.textSecondary
                            )
                        }
                    } else {
                        Text(
                            text = p,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = colorScheme.textSecondary
                        )
                    }
                }
                if (shouldCollapse && !expanded) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.textTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun DlsiteRecommendationsBlocks(
    recommendations: DlsiteRecommendations,
    onOpenAlbumByRj: (String) -> Unit
) {
    if (recommendations.circleWorks.isEmpty() && 
        recommendations.sameVoiceWorks.isEmpty() && 
        recommendations.alsoBoughtWorks.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DlsiteRecommendationsBlock(
            title = "社团作品一览",
            items = recommendations.circleWorks,
            onOpenAlbumByRj = onOpenAlbumByRj
        )
        DlsiteRecommendationsBlock(
            title = "同声优作品",
            items = recommendations.sameVoiceWorks,
            onOpenAlbumByRj = onOpenAlbumByRj
        )
        DlsiteRecommendationsBlock(
            title = "购买了此作品的人也购买了这些作品",
            items = recommendations.alsoBoughtWorks,
            onOpenAlbumByRj = onOpenAlbumByRj
        )
    }
}

@Composable
private fun DlsiteRecommendationsBlock(
    title: String,
    items: List<DlsiteRecommendedWork>,
    onOpenAlbumByRj: (String) -> Unit
) {
    if (items.isEmpty()) return
    val colorScheme = AsmrTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.textPrimary
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items.take(30), key = { sanitizeRj(it.rjCode).ifBlank { it.rjCode } }) { w ->
                val rj = sanitizeRj(w.rjCode).ifBlank { w.rjCode }
                DlsiteRecommendedWorkCard(work = w, displayRj = rj, onClick = { onOpenAlbumByRj(rj) })
            }
        }
    }
}

@Composable
private fun DlsiteRecommendedWorkCard(
    work: DlsiteRecommendedWork,
    displayRj: String,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val coverModel = remember(work.coverUrl, displayRj) {
        work.coverUrl.takeIf { it.isNotBlank() } ?: dlsiteCoverUrlForRj(displayRj)
    }
    val imageModel = remember(coverModel) {
        val s = coverModel.toString()
        val baseHeaders = if (s.startsWith("http", ignoreCase = true)) DlsiteAntiHotlink.headersForImageUrl(s) else emptyMap()
        if (baseHeaders.isNotEmpty()) {
            val cookieManager = CookieManager.getInstance()
            val cookie = cookieManager.getCookie(s).orEmpty().ifBlank { cookieManager.getCookie(NetworkHeaders.REFERER_DLSITE).orEmpty() }
            val headers = buildMap {
                putAll(baseHeaders)
                if (cookie.isNotBlank()) put("Cookie", cookie)
            }
            CacheImageModel(data = s, headers = headers, keyTag = "dlsite")
        } else {
            coverModel
        }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        color = colorScheme.surface.copy(alpha = 0.35f),
        modifier = Modifier
            .width(132.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                AsmrAsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { m ->
                        Box(
                            modifier = m
                                .fillMaxSize()
                                .background(colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = colorScheme.textTertiary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    },
                )
                val ribbon = work.ribbon?.trim().orEmpty()
                if (ribbon.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorScheme.primary.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = ribbon,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = work.title.ifBlank { displayRj },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.textPrimary
                )
                Text(
                    text = displayRj,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textTertiary
                )
            }
        }
    }
}

private fun sanitizeRj(raw: String): String {
    return Regex("""RJ\d+""", RegexOption.IGNORE_CASE).find(raw)?.value?.uppercase().orEmpty()
}

private fun dlsiteCoverUrlForRj(rj: String): String {
    val clean = sanitizeRj(rj)
    val digits = clean.removePrefix("RJ")
    val num = digits.toLongOrNull() ?: return ""
    val group = ((num + 999L) / 1000L) * 1000L
    val padded = group.toString().padStart(digits.length, '0')
    val folder = "RJ$padded"
    return "https://img.dlsite.jp/modpub/images2/work/doujin/$folder/${clean}_img_main.jpg"
}

@Composable
fun AlbumTracks(album: Album, onTrackClick: (Track) -> Unit) {
    val groupedTracks = album.getGroupedTracks()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val ids = remember(album.tracks) { album.tracks.map { it.id }.filter { it > 0L }.distinct() }
    val subtitleTrackIds by produceState(
        initialValue = emptySet<Long>(),
        key1 = ids
    ) {
        value = withContext(Dispatchers.IO) {
            if (ids.isEmpty()) emptySet() else AppDatabaseProvider.get(context).trackDao().getTrackIdsWithSubtitles(ids).toSet()
        }
    }
    val remoteSubtitleTrackIds by produceState(
        initialValue = emptySet<Long>(),
        key1 = ids
    ) {
        value = withContext(Dispatchers.IO) {
            if (ids.isEmpty()) emptySet() else AppDatabaseProvider.get(context).remoteSubtitleSourceDao()
                .getTrackIdsWithRemoteSources(ids)
                .toSet()
        }
    }
    
    if (groupedTracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无曲目")
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().thinScrollbar(listState),
            contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current)
        ) {
            groupedTracks.forEach { (group, tracks) ->
                if (group.isNotEmpty()) {
                    item(
                        key = "group:$group",
                        contentType = "groupHeader"
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = group,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                itemsIndexed(
                    items = tracks,
                    key = { index, track ->
                        if (track.id > 0L) track.id else "${track.path}#$index"
                    },
                    contentType = { _, _ -> "trackRow" }
                ) { index, track ->
                    val showStamp = track.id > 0L && (subtitleTrackIds.contains(track.id) || remoteSubtitleTrackIds.contains(track.id))
                    TrackItem(track = track, showSubtitleStamp = showStamp, onClick = { onTrackClick(track) })
                    if (index < tracks.size - 1) {
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

@Composable
fun TrackItem(
    track: Track,
    showSubtitleStamp: Boolean = false,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    ListItem(
        headlineContent = { 
            Text(
                track.title, 
                maxLines = 2, 
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.textPrimary
            ) 
        },
        supportingContent = { 
            val isOnline = remember(track.path) { track.path.trim().startsWith("http", ignoreCase = true) }
            val durationText = Formatting.formatTrackSeconds(track.duration)
            Text(
                when {
                    isOnline && durationText.isNotBlank() -> "在线 · $durationText"
                    isOnline -> "在线"
                    durationText.isNotBlank() -> durationText
                    else -> "在线播放"
                },
                color = colorScheme.textTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colorScheme.primary)
        },
        trailingContent = {
            if (showSubtitleStamp) {
                SubtitleStamp(modifier = Modifier.padding(end = 8.dp))
            }
            if (onAddToPlaylist != null) {
                IconButton(onClick = onAddToPlaylist) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun OnlineTrackRow(
    title: String,
    subtitle: String,
    onPlay: () -> Unit,
    onAddToQueue: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onManageTags: (() -> Unit)?
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    ListItem(
        headlineContent = {
            Text(
                title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.textPrimary
            )
        },
        supportingContent = {
            Text(
                subtitle,
                color = colorScheme.textTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colorScheme.primary)
        },
        trailingContent = {
            val showMenu = onAddToQueue != null || onAddToPlaylist != null || onManageTags != null
            if (!showMenu) return@ListItem
            var expanded by rememberSaveable { mutableStateOf(false) }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                }
                MaterialTheme(
                    colorScheme = materialColorScheme.copy(
                        surface = dynamicContainerColor,
                        surfaceContainer = dynamicContainerColor
                    )
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(dynamicContainerColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("播放") },
                            onClick = {
                                onPlay()
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colorScheme.primary)
                            }
                        )
                        if (onAddToQueue != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 0.5.dp,
                                color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            DropdownMenuItem(
                                text = { Text("添加到播放队列") },
                                onClick = {
                                    onAddToQueue.invoke()
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                }
                            )
                        }
                        if (onAddToPlaylist != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 0.5.dp,
                                color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            DropdownMenuItem(
                                text = { Text("添加到我的列表") },
                                onClick = {
                                    onAddToPlaylist.invoke()
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                }
                            )
                        }
                        if (onManageTags != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                thickness = 0.5.dp,
                                color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            DropdownMenuItem(
                                text = { Text("标签管理") },
                                onClick = {
                                    onManageTags.invoke()
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                }
                            )
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onPlay)
    )
}

@Composable
private fun AlbumLocalTab(
    stateKey: String,
    initialExpanded: List<String>,
    wasInitialized: Boolean,
    onPersistTreeState: (List<String>) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
    album: Album,
    header: @Composable () -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onPlayVideo: (String, String, String, String) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onAddToPlaylist: (Track) -> Unit,
    onManageTrackTags: (Track) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onSetCoverFromImage: (String) -> Unit,
    onPreviewFile: (LocalTreeUiEntry.File) -> Unit,
) {
    val queueTracks = remember(album.id, album.tracks) {
        album.tracks.sortedBy { it.path }
    }
    val scope = rememberCoroutineScope()
    val queueTrackIds = remember(queueTracks) {
        queueTracks.asSequence().map { it.id }.filter { it > 0L }.distinct().toList()
    }
    val expanded = remember(stateKey) {
        mutableStateListOf<String>().apply { addAll(initialExpanded) }
    }
    var initialized by remember(stateKey) {
        mutableStateOf(wasInitialized || initialExpanded.isNotEmpty())
    }
    val context = LocalContext.current
    
    val allPaths = remember(album) { album.getAllLocalPaths() }
    val hasOnlineTracks = remember(queueTracks) {
        queueTracks.any { it.path.trim().startsWith("http", ignoreCase = true) }
    }

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

    val subtitleTrackIds by produceState(
        initialValue = emptySet<Long>(),
        key1 = queueTrackIds
    ) {
        value = withContext(Dispatchers.IO) {
            if (queueTrackIds.isEmpty()) emptySet()
            else AppDatabaseProvider.get(context).trackDao().getTrackIdsWithSubtitles(queueTrackIds).toSet()
        }
    }

    val remoteSubtitleTrackIds by produceState(
        initialValue = emptySet<Long>(),
        key1 = queueTrackIds
    ) {
        value = withContext(Dispatchers.IO) {
            if (queueTrackIds.isEmpty()) emptySet()
            else AppDatabaseProvider.get(context).remoteSubtitleSourceDao()
                .getTrackIdsWithRemoteSources(queueTrackIds)
                .toSet()
        }
    }

    val treeIndex by produceState<LocalTreeIndex?>(
        initialValue = null,
        key1 = allPaths,
        key2 = queueTracks
    ) {
        value = withContext(Dispatchers.IO) {
            loadOrBuildLocalTreeIndex(
                context = context,
                albumId = album.id,
                albumPaths = allPaths,
                tracks = queueTracks
            )
        }
    }

    val treeResult by produceState(
        initialValue = LocalTreeUiResult(emptyList()),
        key1 = treeIndex,
        key2 = expanded.toList()
    ) {
        val index = treeIndex
        value = if (index == null) {
            LocalTreeUiResult(emptyList())
        } else {
            withContext(Dispatchers.Default) {
                flattenLocalTreeIndex(
                    index = index,
                    expanded = expanded.toSet()
                )
            }
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
        item { header() }
        if (treeResult.entries.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无文件")
                }
            }
        } else {
            itemsIndexed(
                items = treeResult.entries,
                key = { _, entry ->
                    when (entry) {
                        is LocalTreeUiEntry.Folder -> "d:${entry.path}"
                        is LocalTreeUiEntry.File -> "f:${entry.absolutePath}"
                    }
                }
            ) { index, entry ->
                val isFirst = index == 0
                val isLast = index == treeResult.entries.lastIndex
                val shape = RoundedCornerShape(
                    topStart = if (isFirst) 12.dp else 0.dp,
                    topEnd = if (isFirst) 12.dp else 0.dp,
                    bottomStart = if (isLast) 12.dp else 0.dp,
                    bottomEnd = if (isLast) 12.dp else 0.dp
                )

                Surface(
                    shape = shape,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = if (isFirst) 8.dp else 0.dp,
                            bottom = if (isLast) 8.dp else 0.dp
                        )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        when (entry) {
                            is LocalTreeUiEntry.Folder -> {
                                TreeFolderRow(
                                    title = entry.title,
                                    depth = entry.depth,
                                    expanded = expanded.contains(entry.path),
                                    onToggle = {
                                        if (expanded.contains(entry.path)) expanded.remove(entry.path) else expanded.add(entry.path)
                                        initialized = true
                                        onPersistTreeState(expanded.toList())
                                    }
                                )
                            }
                            is LocalTreeUiEntry.File -> {
                                val t = entry.track
                                val showStamp =
                                    t?.let { subtitleTrackIds.contains(it.id) || remoteSubtitleTrackIds.contains(it.id) } == true
                                TreeFileRow(
                                    title = entry.title,
                                    depth = entry.depth,
                                    fileType = entry.fileType,
                                    isPlayable = t != null,
                                    showSubtitleStamp = showStamp,
                                    thumbnailModel = if (entry.fileType == TreeFileType.Image) entry.absolutePath else null,
                                    onPrimary = {
                                        scope.launch {
                                            val prepared = withContext(Dispatchers.Default) {
                                                val artwork = album.coverPath.ifBlank { album.coverUrl }
                                                val artist = album.cv.ifBlank { album.circle }
                                                if ((entry.fileType == TreeFileType.Audio && t != null) || entry.fileType == TreeFileType.Video) {
                                                    val nodes = treeIndex?.let { siblingPlayableNodesForEntry(it, entry.path) }.orEmpty()
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
                                                    val clickedId = when (entry.fileType) {
                                                        TreeFileType.Audio -> t?.path?.trim().orEmpty()
                                                        TreeFileType.Video -> entry.absolutePath.trim()
                                                        else -> ""
                                                    }
                                                    val items = if (siblingItems.isNotEmpty()) {
                                                        siblingItems
                                                    } else {
                                                        when (entry.fileType) {
                                                            TreeFileType.Audio -> queueTracks.map { MediaItemFactory.fromTrack(album, it) }
                                                            TreeFileType.Video -> listOfNotNull(
                                                                buildVideoMediaItem(
                                                                    title = entry.title,
                                                                    uriOrPath = entry.absolutePath,
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
                                                onPreviewFile(entry)
                                            }
                                        }
                                    },
                                    onSetAsCover = if (entry.fileType == TreeFileType.Image) ({ onSetCoverFromImage(entry.absolutePath) }) else null,
                                    onDownload = null,
                                    onAddToQueue = t?.let { { onAddToQueue(it); Unit } },
                                    onAddToPlaylist = t?.let { { onAddToPlaylist(it) } },
                                    onManageTags = t?.let { { onManageTrackTags(it) } },
                                    onRemoveFromAlbum = t?.let { { onRemoveTrack(it) } }
                                )
                            }
                        }

                        if (!isLast) {
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


@Composable
private fun AlbumLocalBreadcrumbTab(
    stateKey: String,
    initialCurrentPath: String,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
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
    val queueTracks = remember(album.id, album.tracks) {
        album.tracks.sortedBy { it.path }
    }
    val queueTrackIds = remember(queueTracks) {
        queueTracks.asSequence().map { it.id }.filter { it > 0L }.distinct().toList()
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val allPaths = remember(album) { album.getAllLocalPaths() }
    var currentPath by rememberSaveable(stateKey) {
        mutableStateOf(initialCurrentPath.trim().trim('/'))
    }

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

    val subtitleTrackIds by produceState(
        initialValue = emptySet<Long>(),
        key1 = queueTrackIds
    ) {
        value = withContext(Dispatchers.IO) {
            if (queueTrackIds.isEmpty()) emptySet()
            else AppDatabaseProvider.get(context).trackDao().getTrackIdsWithSubtitles(queueTrackIds).toSet()
        }
    }
    val remoteSubtitleTrackIds by produceState(
        initialValue = emptySet<Long>(),
        key1 = queueTrackIds
    ) {
        value = withContext(Dispatchers.IO) {
            if (queueTrackIds.isEmpty()) emptySet()
            else AppDatabaseProvider.get(context).remoteSubtitleSourceDao()
                .getTrackIdsWithRemoteSources(queueTrackIds)
                .toSet()
        }
    }
    val treeIndex by produceState<LocalTreeIndex?>(
        initialValue = null,
        key1 = allPaths,
        key2 = queueTracks
    ) {
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
        item { header() }
        item {
            DirectoryBreadcrumbBar(
                currentPath = currentPath,
                breadcrumbs = browser?.breadcrumbs.orEmpty(),
                onNavigate = { path -> currentPath = path }
            )
        }

        val browserValue = browser
        if (browserValue == null || (browserValue.folders.isEmpty() && browserValue.files.isEmpty())) {
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
                DirectoryBatchBar(
                    targets = browserValue.batchTargets,
                    onAddToFavorites = onAddMediaItemsToFavorites,
                    onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                    onAddMediaItemsToQueue = onAddMediaItemsToQueue
                )
            }
            items(
                items = browserValue.folders,
                key = { folder -> "folder:${folder.path}" },
                contentType = { "folder" }
            ) { folder ->
                DirectoryFolderRow(
                    title = folder.title,
                    onClick = { currentPath = folder.path }
                )
            }
            items(
                items = browserValue.files,
                key = { file -> "file:${file.path}" },
                contentType = { "file" }
            ) { file ->
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
                    onSetAsCover = if (file.fileType == TreeFileType.Image) ({ onSetCoverFromImage(file.absolutePath) }) else null,
                    onDownload = null,
                    onAddToQueue = track?.let { { onAddToQueue(it); Unit } },
                    onAddToPlaylist = track?.let { { onAddToPlaylist(it) } },
                    onManageTags = track?.let { { onManageTrackTags(it) } },
                    onRemoveFromAlbum = track?.let { { onRemoveTrack(it) } }
                )
            }
        }
    }
}

@Composable
private fun AlbumLocalBreadcrumbTabV2(
    stateKey: String,
    initialCurrentPath: String,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
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
        item { header() }
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
@Composable
private fun AlbumAsmrOneTab(
    album: Album,
    header: @Composable () -> Unit,
    trackTree: List<AsmrOneTrackNodeResponse>,
    isLoading: Boolean,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onDownloadOne: (String) -> Unit,
    onAddToPlaylistOne: (String) -> Unit
) {
    val leafTracks = remember(trackTree) { flattenAsmrOneTracksForUi(trackTree) }
    val leafByRelPath = remember(leafTracks) { leafTracks.associateBy { it.relativePath } }
    val expanded = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val treeResult = remember(trackTree, expanded.toList()) { flattenAsmrOneTreeForUi(trackTree, expanded.toSet()) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().thinScrollbar(listState),
        contentPadding = PaddingValues(bottom = LocalBottomOverlayPadding.current)
    ) {
        item { header() }
        if (trackTree.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    } else {
                        Text("暂无在线音频")
                    }
                }
            }
        } else {
            itemsIndexed(
                items = treeResult.entries,
                key = { _, entry ->
                    when (entry) {
                        is AsmrTreeUiEntry.Folder -> "d:${entry.path}"
                        is AsmrTreeUiEntry.File -> "f:${entry.path}"
                    }
                }
            ) { index, entry ->
                val isFirst = index == 0
                val isLast = index == treeResult.entries.lastIndex
                val shape = RoundedCornerShape(
                    topStart = if (isFirst) 12.dp else 0.dp,
                    topEnd = if (isFirst) 12.dp else 0.dp,
                    bottomStart = if (isLast) 12.dp else 0.dp,
                    bottomEnd = if (isLast) 12.dp else 0.dp
                )

                Surface(
                    shape = shape,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = if (isFirst) 8.dp else 0.dp,
                            bottom = if (isLast) 8.dp else 0.dp
                        )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        when (entry) {
                            is AsmrTreeUiEntry.Folder -> {
                                TreeFolderRow(
                                    title = entry.title,
                                    depth = entry.depth,
                                    expanded = expanded.contains(entry.path),
                                    onToggle = {
                                        if (expanded.contains(entry.path)) expanded.remove(entry.path) else expanded.add(entry.path)
                                    }
                                )
                            }
                            is AsmrTreeUiEntry.File -> {
                                val canPlay = entry.fileType == TreeFileType.Audio && leafByRelPath.containsKey(entry.path)
                                TreeFileRow(
                                    title = entry.title,
                                    depth = entry.depth,
                                    fileType = entry.fileType,
                                    isPlayable = canPlay,
                                    showSubtitleStamp = canPlay && (leafByRelPath[entry.path]?.subtitles?.isNotEmpty() == true),
                                    onPrimary = {
                                        if (!canPlay) return@TreeFileRow
                                        scope.launch {
                                            val prepared = withContext(Dispatchers.Default) {
                                                val start = leafByRelPath[entry.path] ?: return@withContext null
                                                val folderPath = entry.path.substringBeforeLast('/', "")
                                                val siblingLeaves = leafTracks.filter {
                                                    it.relativePath.substringBeforeLast('/', "") == folderPath
                                                }
                                                val queueLeaves = siblingLeaves.ifEmpty { leafTracks }
                                                PreparedTrackPlayback(
                                                    tracks = queueLeaves.sortedBy { SmartSortKey.of(it.title) }.map { it.toTrack() },
                                                    startTrack = start.toTrack(),
                                                    onlineLyrics = queueLeaves.associate { it.url to it.subtitles }
                                                )
                                            } ?: return@launch
                                            com.asmr.player.util.OnlineLyricsStore.replaceAll(prepared.onlineLyrics)
                                            onPlayTracks(album, prepared.tracks, prepared.startTrack)
                                        }
                                    },
                                    onDownload = if (entry.fileType == TreeFileType.Audio) ({ onDownloadOne(entry.path) }) else null,
                                    onAddToQueue = null,
                                    onAddToPlaylist = if (entry.fileType == TreeFileType.Audio) ({ onAddToPlaylistOne(entry.path) }) else null
                                )
                            }
                        }

                        if (!isLast) {
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

private sealed class AsmrTreeUiEntry {
    abstract val path: String
    abstract val title: String
    abstract val depth: Int

    data class Folder(
        override val path: String,
        override val title: String,
        override val depth: Int
    ) : AsmrTreeUiEntry()

    data class File(
        override val path: String,
        override val title: String,
        override val depth: Int,
        val fileType: TreeFileType,
        val isPlayable: Boolean,
        val url: String? = null
    ) : AsmrTreeUiEntry()
}

private sealed class LocalTreeUiEntry {
    abstract val path: String
    abstract val title: String
    abstract val depth: Int

    data class Folder(
        override val path: String,
        override val title: String,
        override val depth: Int
    ) : LocalTreeUiEntry()

    data class File(
        override val path: String,
        override val title: String,
        override val depth: Int,
        val absolutePath: String,
        val fileType: TreeFileType,
        val track: Track?
    ) : LocalTreeUiEntry()
}

internal enum class TreeFileType {
    Audio,
    Video,
    Image,
    Subtitle,
    Text,
    Pdf,
    Other
}

internal fun treeFileTypeForName(fileName: String): TreeFileType {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp3", "wav", "flac", "m4a", "ogg", "aac", "opus" -> TreeFileType.Audio
        "mp4", "mkv", "webm", "mov", "m4v" -> TreeFileType.Video
        "jpg", "jpeg", "png", "webp", "gif" -> TreeFileType.Image
        "lrc", "srt", "vtt" -> TreeFileType.Subtitle
        "txt", "md", "nfo" -> TreeFileType.Text
        "pdf" -> TreeFileType.Pdf
        else -> TreeFileType.Other
    }
}

internal fun treeFileTypeForNode(title: String, url: String?): TreeFileType {
    val t = title.trim()
    val fromTitle = treeFileTypeForName(t)

    val urlName = url
        ?.substringBefore('#')
        ?.substringBefore('?')
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        .orEmpty()
    val fromUrl = if (urlName.isNotBlank()) treeFileTypeForName(urlName) else TreeFileType.Other

    return when {
        fromUrl != TreeFileType.Other -> fromUrl
        fromTitle != TreeFileType.Other -> fromTitle
        url != null && url.isNotBlank() -> TreeFileType.Audio
        else -> TreeFileType.Other
    }
}

private fun buildVideoMediaItem(
    title: String,
    uriOrPath: String,
    artworkUri: String,
    artist: String
): MediaItem? {
    val trimmed = uriOrPath.trim()
    if (trimmed.isBlank()) return null
    val uri = if (
        trimmed.startsWith("http", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
    ) {
        trimmed.toUri()
    } else {
        Uri.fromFile(File(trimmed))
    }
    val ext = trimmed.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
    val mimeType = when (ext) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        else -> "video/*"
    }
    val displayTitle = title.ifBlank { trimmed.substringAfterLast('/').substringAfterLast('\\') }
    val metadata = androidx.media3.common.MediaMetadata.Builder()
        .setTitle(displayTitle)
        .setArtist(artist.trim())
        .setArtworkUri(artworkUri.trim().takeIf { it.isNotBlank() }?.toUri())
        .setExtras(android.os.Bundle().apply { putBoolean("is_video", true) })
        .build()
    return MediaItem.Builder()
        .setMediaId(trimmed)
        .setUri(uri)
        .setMimeType(mimeType)
        .setMediaMetadata(metadata)
        .build()
}

private sealed class FileSizeSource {
    data object None : FileSizeSource()
    data class Local(val path: String) : FileSizeSource()
    data class Remote(val url: String) : FileSizeSource()
}

private data class DirectoryBreadcrumbSegment(
    val label: String,
    val path: String
)

private data class DirectoryFolderItem(
    val path: String,
    val title: String
)

private data class DirectoryFileItem(
    val path: String,
    val title: String,
    val fileType: TreeFileType,
    val isPlayable: Boolean,
    val durationSeconds: Double? = null,
    val sizeSource: FileSizeSource = FileSizeSource.None,
    val absolutePath: String = "",
    val url: String = "",
    val track: Track? = null,
    val thumbnailModel: Any? = null,
    val playlistTarget: PlaylistAddTarget? = null,
    val subtitleSources: List<RemoteSubtitleSource> = emptyList(),
    val showSubtitleStamp: Boolean = false
)

private data class DirectoryBrowserResult(
    val currentPath: String,
    val breadcrumbs: List<DirectoryBreadcrumbSegment>,
    val folders: List<DirectoryFolderItem>,
    val files: List<DirectoryFileItem>
) {
    val batchTargets: List<PlaylistAddTarget>
        get() = files.mapNotNull { file ->
            when (file.fileType) {
                TreeFileType.Audio, TreeFileType.Video -> file.playlistTarget
                else -> null
            }
        }
}

private fun buildBreadcrumbSegments(currentPath: String): List<DirectoryBreadcrumbSegment> {
    val normalized = currentPath.trim().trim('/')
    if (normalized.isBlank()) return emptyList()
    val segments = normalized.split('/').filter { it.isNotBlank() }
    val out = mutableListOf<DirectoryBreadcrumbSegment>()
    var path = ""
    segments.forEach { segment ->
        path = if (path.isBlank()) segment else "$path/$segment"
        out += DirectoryBreadcrumbSegment(label = segment, path = path)
    }
    return out
}

private fun albumArtistLabel(album: Album): String {
    return when {
        album.cv.isNotBlank() && album.circle.isNotBlank() -> "${album.circle} / ${album.cv}"
        album.cv.isNotBlank() -> album.cv
        album.circle.isNotBlank() -> album.circle
        album.rjCode.isNotBlank() -> album.rjCode
        else -> album.workId
    }.trim()
}

private fun albumArtworkLabel(album: Album): String {
    return album.coverPath.ifBlank { album.coverUrl }
}

private data class LocalTreeUiResult(
    val entries: List<LocalTreeUiEntry>
)

private data class AsmrTreeUiResult(
    val entries: List<AsmrTreeUiEntry>
)

private fun folderPathPrefixes(path: String): List<String> {
    val segs = path.split('/').filter { it.isNotBlank() }
    if (segs.isEmpty()) return emptyList()
    val out = ArrayList<String>(segs.size)
    var cur = ""
    for (seg in segs) {
        cur = if (cur.isBlank()) seg else "$cur/$seg"
        out.add(cur)
    }
    return out
}

private class LocalTreeNode(
    val name: String,
    val path: String,
    val children: MutableMap<String, LocalTreeNode> = linkedMapOf(),
    var absolutePath: String? = null,
    var fileType: TreeFileType = TreeFileType.Other,
    var track: Track? = null
)

private data class LocalFolderStats(
    var audioCount: Int = 0,
    var videoCount: Int = 0,
    var hasWav: Boolean = false,
    var hasMp4: Boolean = false
)

private data class LocalTreeIndex(
    val root: LocalTreeNode,
    val folderStats: Map<String, LocalFolderStats>
)

private fun findLocalTreeNode(root: LocalTreeNode, folderPath: String): LocalTreeNode? {
    val normalized = folderPath.trim().trimStart('/').trimEnd('/')
    if (normalized.isBlank()) return root
    var cur: LocalTreeNode = root
    val segments = normalized.split('/').filter { it.isNotBlank() }
    for (seg in segments) {
        val next = cur.children[seg] ?: return null
        cur = next
    }
    return cur
}

private fun siblingAudioTracksForEntry(index: LocalTreeIndex, entryPath: String): List<Track> {
    val folderPath = entryPath.substringBeforeLast('/', "")
    val node = findLocalTreeNode(index.root, folderPath) ?: index.root
    return node.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.absolutePath != null && it.fileType == TreeFileType.Audio && it.track != null }
        .sortedBy { SmartSortKey.of(it.name) }
        .mapNotNull { it.track }
        .toList()
}

private fun siblingPlayableNodesForEntry(index: LocalTreeIndex, entryPath: String): List<LocalTreeNode> {
    val folderPath = entryPath.substringBeforeLast('/', "")
    val node = findLocalTreeNode(index.root, folderPath) ?: index.root
    return node.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.absolutePath != null && (it.fileType == TreeFileType.Audio || it.fileType == TreeFileType.Video) }
        .filter { it.fileType != TreeFileType.Audio || it.track != null }
        .sortedBy { SmartSortKey.of(it.name) }
        .toList()
}

private fun buildLocalDirectoryBrowser(
    index: LocalTreeIndex,
    currentPath: String,
    album: Album,
    shouldShowSubtitleStamp: (Track?) -> Boolean
): DirectoryBrowserResult {
    val normalizedPath = currentPath.trim().trim('/')
    val currentNode = findLocalTreeNode(index.root, normalizedPath) ?: index.root
    val folders = currentNode.children.values
        .asSequence()
        .filter { it.children.isNotEmpty() }
        .sortedBy { SmartSortKey.of(it.name) }
        .map { child ->
            DirectoryFolderItem(
                path = child.path,
                title = child.name
            )
        }
        .toList()
    val files = currentNode.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.absolutePath != null }
        .sortedBy { SmartSortKey.of(it.name) }
        .mapNotNull { child ->
            val absolutePath = child.absolutePath ?: return@mapNotNull null
            val displayTitle = child.track?.title?.ifBlank { child.name.substringBeforeLast('.') }
                ?: child.name.substringBeforeLast('.')
            val playlistTarget = when (child.fileType) {
                TreeFileType.Audio -> child.track?.let { PlaylistAddTarget.fromTrack(album, it) }
                TreeFileType.Video -> PlaylistAddTarget.fromVideo(album, displayTitle, absolutePath)
                else -> null
            }
            DirectoryFileItem(
                path = child.path,
                title = displayTitle,
                fileType = child.fileType,
                isPlayable = child.track != null || child.fileType == TreeFileType.Video,
                durationSeconds = child.track?.duration?.takeIf { it > 0.0 },
                sizeSource = FileSizeSource.Local(absolutePath),
                absolutePath = absolutePath,
                url = absolutePath,
                track = child.track,
                thumbnailModel = if (child.fileType == TreeFileType.Image) absolutePath else null,
                playlistTarget = playlistTarget,
                showSubtitleStamp = shouldShowSubtitleStamp(child.track)
            )
        }
        .toList()
    return DirectoryBrowserResult(
        currentPath = normalizedPath,
        breadcrumbs = buildBreadcrumbSegments(normalizedPath),
        folders = folders,
        files = files
    )
}

private class RemoteTreeNode(
    val name: String,
    val path: String,
    val children: MutableMap<String, RemoteTreeNode> = linkedMapOf(),
    var fileType: TreeFileType = TreeFileType.Other,
    var url: String = "",
    var durationSeconds: Double? = null,
    var subtitleSources: List<RemoteSubtitleSource> = emptyList(),
    var playlistTarget: PlaylistAddTarget? = null
)

private data class RemoteTreeIndex(
    val root: RemoteTreeNode
)

private fun findRemoteTreeNode(root: RemoteTreeNode, folderPath: String): RemoteTreeNode? {
    val normalized = folderPath.trim().trim('/')
    if (normalized.isBlank()) return root
    var current = root
    normalized.split('/').filter { it.isNotBlank() }.forEach { segment ->
        current = current.children[segment] ?: return null
    }
    return current
}

private fun buildRemoteTreeIndex(
    tree: List<AsmrOneTrackNodeResponse>,
    album: Album
): RemoteTreeIndex {
    val root = RemoteTreeNode(name = "", path = "")
    val subtitleExts = setOf("lrc", "srt", "vtt")
    val mediaExts = setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "opus", "mp4", "mkv", "webm", "mov", "m4v")

    fun sanitize(name: String): String {
        return name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    data class LeafFile(
        val rawTitle: String,
        val safeTitle: String,
        val url: String,
        val duration: Double?,
        val fileType: TreeFileType
    ) {
        val ext: String = rawTitle.substringAfterLast('.', "").lowercase()
        val baseName: String = rawTitle.substringBeforeLast('.')
        val displayTitle: String = sanitize(baseName).ifBlank { safeTitle.substringBeforeLast('.') }
    }

    fun buildSubtitlesForMedia(media: LeafFile, siblings: List<LeafFile>): List<RemoteSubtitleSource> {
        val base = media.baseName
        if (base.isBlank()) return emptyList()
        return siblings.filter { subtitleExts.contains(it.ext) }.mapNotNull { sibling ->
            val siblingBase = sibling.baseName
            val language = when {
                siblingBase == base -> "default"
                siblingBase.startsWith("$base.") -> {
                    val suffix = siblingBase.substring(base.length + 1)
                    val parts = suffix.split('.').filter { it.isNotBlank() }
                    val valid = parts.filter { !mediaExts.contains(it.lowercase()) }
                    when {
                        valid.isEmpty() -> "zh"
                        valid.size == 1 -> valid[0]
                        else -> valid.last()
                    }
                }
                else -> return@mapNotNull null
            }
            RemoteSubtitleSource(
                url = sibling.url,
                language = language,
                ext = sibling.ext.ifBlank { "vtt" }
            )
        }.sortedWith(
            compareBy<RemoteSubtitleSource> { source ->
                val preferred = listOf("default", "zh", "cn", "chs", "ja", "jp", "jpn")
                val index = preferred.indexOf(source.language.lowercase())
                if (index >= 0) index else Int.MAX_VALUE
            }.thenBy { it.url }
        )
    }

    fun walk(
        nodes: List<AsmrOneTrackNodeResponse>,
        parentNode: RemoteTreeNode,
        parentPath: String
    ) {
        val leafFiles = nodes.mapNotNull { node ->
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (!url.isNullOrBlank() && children.isEmpty()) {
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitize(rawTitle)
                LeafFile(
                    rawTitle = rawTitle,
                    safeTitle = safeTitle,
                    url = url,
                    duration = node.duration,
                    fileType = treeFileTypeForNode(rawTitle, url)
                )
            } else {
                null
            }
        }

        leafFiles.forEach { leaf ->
            if (leaf.fileType == TreeFileType.Other || leaf.fileType == TreeFileType.Subtitle) return@forEach
            val path = if (parentPath.isBlank()) leaf.safeTitle else "$parentPath/${leaf.safeTitle}"
            val child = parentNode.children.getOrPut(leaf.safeTitle) {
                RemoteTreeNode(name = leaf.safeTitle, path = path)
            }
            val subtitleSources = when (leaf.fileType) {
                TreeFileType.Audio, TreeFileType.Video -> buildSubtitlesForMedia(leaf, leafFiles)
                else -> emptyList()
            }
            val playlistTarget = when (leaf.fileType) {
                TreeFileType.Audio -> PlaylistAddTarget.fromTrack(
                    album = album,
                    track = Track(
                        albumId = album.id,
                        title = leaf.displayTitle,
                        path = leaf.url,
                        duration = leaf.duration ?: 0.0
                    )
                )
                TreeFileType.Video -> PlaylistAddTarget.fromVideo(album, leaf.displayTitle, leaf.url)
                else -> null
            }
            child.fileType = leaf.fileType
            child.url = leaf.url
            child.durationSeconds = leaf.duration
            child.subtitleSources = subtitleSources
            child.playlistTarget = playlistTarget
        }

        nodes.forEach { node ->
            val children = node.children.orEmpty()
            if (children.isEmpty()) return@forEach
            val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitize(rawTitle)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val childNode = parentNode.children.getOrPut(safeTitle) {
                RemoteTreeNode(name = safeTitle, path = path)
            }
            walk(children, childNode, path)
        }
    }

    walk(tree, root, "")
    return RemoteTreeIndex(root = root)
}

private fun buildRemoteDirectoryBrowser(
    index: RemoteTreeIndex,
    currentPath: String
): DirectoryBrowserResult {
    val normalizedPath = currentPath.trim().trim('/')
    val currentNode = findRemoteTreeNode(index.root, normalizedPath) ?: index.root
    val folders = currentNode.children.values
        .asSequence()
        .filter { it.children.isNotEmpty() }
        .sortedBy { SmartSortKey.of(it.name) }
        .map { child ->
            DirectoryFolderItem(
                path = child.path,
                title = child.name
            )
        }
        .toList()
    val files = currentNode.children.values
        .asSequence()
        .filter { it.children.isEmpty() && it.url.isNotBlank() && it.fileType != TreeFileType.Subtitle && it.fileType != TreeFileType.Other }
        .sortedBy { SmartSortKey.of(it.name) }
        .map { child ->
            DirectoryFileItem(
                path = child.path,
                title = child.name.substringBeforeLast('.'),
                fileType = child.fileType,
                isPlayable = child.fileType == TreeFileType.Audio || child.fileType == TreeFileType.Video,
                durationSeconds = child.durationSeconds,
                sizeSource = if (child.url.isNotBlank()) FileSizeSource.Remote(child.url) else FileSizeSource.None,
                absolutePath = child.url,
                url = child.url,
                playlistTarget = child.playlistTarget,
                subtitleSources = child.subtitleSources,
                showSubtitleStamp = child.subtitleSources.isNotEmpty()
            )
        }
        .toList()
    return DirectoryBrowserResult(
        currentPath = normalizedPath,
        breadcrumbs = buildBreadcrumbSegments(normalizedPath),
        folders = folders,
        files = files
    )
}

private data class LocalTreeLeafCacheEntry(
    val relativePath: String,
    val absolutePath: String,
    val fileType: TreeFileType
)

private data class LocalTreeIndexBuildResult(
    val index: LocalTreeIndex,
    val leaves: List<LocalTreeLeafCacheEntry>
)

private suspend fun loadOrBuildLocalTreeIndex(
    context: android.content.Context,
    albumId: Long,
    albumPaths: List<String>,
    tracks: List<Track>
): LocalTreeIndex {
    val gson = Gson()
    val cacheKey = albumPaths.map { it.trim() }.filter { it.isNotBlank() }.sorted().joinToString("|")
    val stamp = computeAlbumPathsStamp(context, albumPaths)
    val dao = AppDatabaseProvider.get(context).localTreeCacheDao()
    val onlineTracks = tracks.filter { it.path.trim().startsWith("http", ignoreCase = true) }
    val onlineUrlSet = onlineTracks.map { it.path.trim() }.filter { it.isNotBlank() }.toSet()

    fun sanitizeSeg(name: String): String {
        return name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }

    fun guessExtFromUrl(url: String): String {
        val u = url.substringBefore('?').trim()
        val ext = u.substringAfterLast('.', "").lowercase()
        if (ext.isBlank() || ext.length > 6) return ""
        if (ext.contains('/') || ext.contains('\\')) return ""
        return ext
    }

    fun buildOnlineLeaves(): List<LocalTreeLeafCacheEntry> {
        if (onlineTracks.isEmpty()) return emptyList()
        val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
        val videoExts = setOf("mp4", "mkv", "webm")
        return onlineTracks.mapNotNull { t ->
            val url = t.path.trim()
            if (url.isBlank()) return@mapNotNull null
            val ext = guessExtFromUrl(url)
            val type = when {
                videoExts.contains(ext) -> TreeFileType.Video
                audioExts.contains(ext) -> TreeFileType.Audio
                else -> TreeFileType.Audio
            }
            val groupPath = t.group.trim()
                .trim('/')
                .split('/')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("/") { sanitizeSeg(it) }

            val baseName = sanitizeSeg(t.title.ifBlank { "track" })
            val fileName = if (ext.isNotBlank() && !baseName.endsWith(".$ext", ignoreCase = true)) "$baseName.$ext" else baseName
            val rel = if (groupPath.isBlank()) fileName else "$groupPath/$fileName"
            LocalTreeLeafCacheEntry(relativePath = rel, absolutePath = url, fileType = type)
        }
    }

    fun mergeLeaves(localLeaves: List<LocalTreeLeafCacheEntry>, onlineLeaves: List<LocalTreeLeafCacheEntry>): List<LocalTreeLeafCacheEntry> {
        val filteredLocal = localLeaves.filter { leaf ->
            val abs = leaf.absolutePath.trim()
            !(abs.startsWith("http", ignoreCase = true) && !onlineUrlSet.contains(abs))
        }
        val byRel = linkedMapOf<String, LocalTreeLeafCacheEntry>()
        filteredLocal.forEach { byRel[it.relativePath] = it }
        onlineLeaves.forEach { leaf ->
            val existing = byRel[leaf.relativePath]
            if (existing == null) {
                byRel[leaf.relativePath] = leaf
            } else {
                val abs = existing.absolutePath.trim()
                if (abs.startsWith("http", ignoreCase = true)) byRel[leaf.relativePath] = leaf
            }
        }
        return byRel.values.toList()
    }
    val onlineLeaves = buildOnlineLeaves()

    val cached = dao.getByAlbumAndKey(albumId = albumId, cacheKey = cacheKey)
    if (cached != null && cached.stamp == stamp && cached.payloadJson.isNotBlank()) {
        val type = object : TypeToken<List<LocalTreeLeafCacheEntry>>() {}.type
        val leaves = runCatching { gson.fromJson<List<LocalTreeLeafCacheEntry>>(cached.payloadJson, type) }
            .getOrDefault(emptyList())
        val merged = mergeLeaves(localLeaves = leaves, onlineLeaves = onlineLeaves)
        if (merged.isNotEmpty()) {
            return buildLocalTreeIndexFromLeaves(leaves = merged, tracks = tracks)
        }
    }

    val built = buildLocalTreeIndexByScanning(context = context, albumPaths = albumPaths, tracks = tracks)
    val merged = mergeLeaves(localLeaves = built.leaves, onlineLeaves = onlineLeaves)
    dao.upsert(
        LocalTreeCacheEntity(
            albumId = albumId,
            cacheKey = cacheKey,
            stamp = stamp,
            payloadJson = gson.toJson(merged),
            updatedAt = System.currentTimeMillis()
        )
    )
    return buildLocalTreeIndexFromLeaves(leaves = merged, tracks = tracks)
}

private fun computeAlbumPathsStamp(context: android.content.Context, albumPaths: List<String>): Long {
    val paths = albumPaths.map { it.trim() }.filter { it.isNotBlank() }.sorted()
    var acc = 1469598103934665603L
    paths.forEach { p ->
        val v = if (p.startsWith("content://")) {
            queryDocumentLastModified(context, p)
        } else {
            runCatching { java.io.File(p).lastModified() }.getOrDefault(0L)
        }
        acc = (acc xor v) * 1099511628211L
    }
    return acc
}

private fun queryDocumentLastModified(context: android.content.Context, uriString: String): Long {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return 0L
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            if (idx < 0) return@use 0L
            if (!cursor.moveToFirst()) return@use 0L
            cursor.getLong(idx)
        } ?: 0L
    }.getOrDefault(0L)
}

private fun buildLocalTreeIndexByScanning(
    context: android.content.Context,
    albumPaths: List<String>,
    tracks: List<Track>
): LocalTreeIndexBuildResult {
    val root = LocalTreeNode(name = "", path = "")
    val trackByAbsolutePath = tracks.associateBy { it.path }
    val folderStats = linkedMapOf<String, LocalFolderStats>()

    fun updateFolderStats(segments: List<String>, type: TreeFileType, extLower: String) {
        val folderSegs = segments.dropLast(1)
        if (folderSegs.isEmpty()) return
        var cur = ""
        folderSegs.forEach { seg ->
            cur = if (cur.isBlank()) seg else "$cur/$seg"
            val st = folderStats.getOrPut(cur) { LocalFolderStats() }
            when (type) {
                TreeFileType.Audio -> {
                    st.audioCount += 1
                    if (extLower == "wav") st.hasWav = true
                }
                TreeFileType.Video -> {
                    st.videoCount += 1
                    if (extLower == "mp4") st.hasMp4 = true
                }
                else -> Unit
            }
        }
    }

    albumPaths.forEach { albumPath ->
        if (albumPath.startsWith("content://")) {
            val uri = Uri.parse(albumPath)
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: ""
            val rootDocId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: treeId
            val treeUri = if (treeId.isNotBlank()) DocumentsContract.buildTreeDocumentUri(uri.authority, treeId) else uri
            
            fun query(parentDocId: String, parentRel: String) {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                context.contentResolver.query(childrenUri, arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ), null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIdx)
                        val name = cursor.getString(nameIdx)
                        val mime = cursor.getString(mimeIdx)
                        val rel = if (parentRel.isEmpty()) name else "$parentRel/$name"
                        
                        val segments = rel.split('/').filter { it.isNotBlank() }
                        var cur = root
                        segments.forEachIndexed { idx, seg ->
                            val nextPath = if (cur.path.isBlank()) seg else "${cur.path}/$seg"
                            val child = cur.children.getOrPut(seg) { LocalTreeNode(name = seg, path = nextPath) }
                            if (idx == segments.lastIndex) {
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id).toString()
                                val ft = if (mime == DocumentsContract.Document.MIME_TYPE_DIR) TreeFileType.Other else treeFileTypeForName(name)
                                child.fileType = ft
                                if (mime != DocumentsContract.Document.MIME_TYPE_DIR && ft != TreeFileType.Other && ft != TreeFileType.Subtitle) {
                                    val track = trackByAbsolutePath[fileUri]
                                    if (ft == TreeFileType.Audio && track == null) {
                                        child.absolutePath = null
                                        child.track = null
                                    } else {
                                        child.absolutePath = fileUri
                                        child.track = track
                                    }
                                    if (ft == TreeFileType.Video || (ft == TreeFileType.Audio && track != null)) {
                                        updateFolderStats(segments, ft, name.substringAfterLast('.', "").lowercase())
                                    }
                                } else {
                                    child.absolutePath = null
                                    child.track = null
                                }
                            }
                            cur = child
                        }
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            query(id, rel)
                        }
                    }
                }
            }
            if (rootDocId.isNotBlank()) {
                query(rootDocId, "")
            }
        } else {
            val rootDir = java.io.File(albumPath)
            if (rootDir.exists()) {
                rootDir.walkTopDown().forEach { file ->
                    if (!file.isFile) return@forEach
                    val type = treeFileTypeForName(file.name)
                    if (type == TreeFileType.Other || type == TreeFileType.Subtitle) return@forEach
                    val track = trackByAbsolutePath[file.absolutePath]
                    if (type == TreeFileType.Audio && track == null) return@forEach

                    val rawRel = runCatching { file.relativeTo(rootDir).path }.getOrElse { file.name }
                    val rel = rawRel.replace('\\', '/').trim().trimStart('/')
                    val segments = rel.split('/').filter { it.isNotBlank() }
                    if (segments.isEmpty()) return@forEach

                    var cur = root
                    segments.forEachIndexed { idx, seg ->
                        val nextPath = if (cur.path.isBlank()) seg else "${cur.path}/$seg"
                        val child = cur.children.getOrPut(seg) { LocalTreeNode(name = seg, path = nextPath) }
                        if (idx == segments.lastIndex) {
                            child.absolutePath = file.absolutePath
                            child.fileType = type
                            child.track = track
                            if (type == TreeFileType.Audio || type == TreeFileType.Video) {
                                updateFolderStats(segments, type, file.extension.lowercase())
                            }
                        }
                        cur = child
                    }
                }
            }
        }
    }

    fun collectLeaves(node: LocalTreeNode, out: MutableList<LocalTreeLeafCacheEntry>) {
        if (node.children.isEmpty() && node.absolutePath != null) {
            out.add(
                LocalTreeLeafCacheEntry(
                    relativePath = node.path,
                    absolutePath = node.absolutePath ?: return,
                    fileType = node.fileType
                )
            )
            return
        }
        node.children.values.forEach { child -> collectLeaves(child, out) }
    }

    val leaves = mutableListOf<LocalTreeLeafCacheEntry>()
    collectLeaves(root, leaves)
    return LocalTreeIndexBuildResult(
        index = LocalTreeIndex(root = root, folderStats = folderStats),
        leaves = leaves
    )
}

private fun buildLocalTreeIndexFromLeaves(
    leaves: List<LocalTreeLeafCacheEntry>,
    tracks: List<Track>
): LocalTreeIndex {
    val root = LocalTreeNode(name = "", path = "")
    val trackByAbsolutePath = tracks.associateBy { it.path }

    leaves.forEach { leaf ->
        if (leaf.fileType == TreeFileType.Subtitle) return@forEach
        val track = trackByAbsolutePath[leaf.absolutePath]
        if (leaf.fileType == TreeFileType.Audio && track == null) return@forEach
        val rel = leaf.relativePath.trim().trimStart('/')
        if (rel.isBlank()) return@forEach
        val segments = rel.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return@forEach
        var cur = root
        segments.forEachIndexed { idx, seg ->
            val nextPath = if (cur.path.isBlank()) seg else "${cur.path}/$seg"
            val child = cur.children.getOrPut(seg) { LocalTreeNode(name = seg, path = nextPath) }
            if (idx == segments.lastIndex) {
                child.absolutePath = leaf.absolutePath
                child.fileType = leaf.fileType
                child.track = track
            }
            cur = child
        }
    }

    return LocalTreeIndex(root = root, folderStats = emptyMap())
}

private fun flattenLocalTreeIndex(
    index: LocalTreeIndex,
    expanded: Set<String>
): LocalTreeUiResult {
    fun nodeSortKey(n: LocalTreeNode): SmartSortKey = SmartSortKey.of(n.name)
    val out = mutableListOf<LocalTreeUiEntry>()

    fun flatten(node: LocalTreeNode, depth: Int) {
        val folders = node.children.values.filter { it.children.isNotEmpty() }.sortedBy(::nodeSortKey)
        val files = node.children.values.filter { it.children.isEmpty() && it.absolutePath != null }.sortedBy(::nodeSortKey)

        folders.forEach { child ->
            out.add(LocalTreeUiEntry.Folder(path = child.path, title = child.name, depth = depth))
            if (expanded.contains(child.path)) {
                flatten(child, depth + 1)
            }
        }
        files.forEach { child ->
            val title = child.track?.title?.ifBlank { child.name.substringBeforeLast('.') }
                ?: child.name.substringBeforeLast('.')
            out.add(
                LocalTreeUiEntry.File(
                    path = child.path,
                    title = title,
                    depth = depth,
                    absolutePath = child.absolutePath ?: return@forEach,
                    fileType = child.fileType,
                    track = child.track
                )
            )
        }
    }

    flatten(index.root, 0)
    return LocalTreeUiResult(entries = out)
}

private data class AsmrOneLeafUi(
    val relativePath: String,
    val title: String,
    val url: String,
    val duration: Double?,
    val subtitles: List<com.asmr.player.util.RemoteSubtitleSource>
) {
    fun toTrack(): Track {
        return Track(
            albumId = 0,
            title = title,
            path = url,
            duration = duration ?: 0.0
        )
    }
}

private fun flattenAsmrOneTracksForUi(tree: List<AsmrOneTrackNodeResponse>): List<AsmrOneLeafUi> {
    val out = mutableListOf<AsmrOneLeafUi>()
    fun sanitize(name: String): String = name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")

    val audioExts = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus")
    val subtitleExts = setOf("lrc", "srt", "vtt")

    data class LeafFile(
        val rawTitle: String,
        val safeTitle: String,
        val url: String,
        val duration: Double?
    ) {
        val ext: String = rawTitle.substringAfterLast('.', "").lowercase()
        val baseName: String = rawTitle.substringBeforeLast('.')
    }

    fun buildSubtitlesForAudio(audio: LeafFile, siblings: List<LeafFile>): List<com.asmr.player.util.RemoteSubtitleSource> {
        val base = audio.baseName
        if (base.isBlank()) return emptyList()
        return siblings.filter { subtitleExts.contains(it.ext) }.mapNotNull { sib ->
            val sibBase = sib.baseName
            val lang = when {
                sibBase == base -> "default"
                sibBase.startsWith("$base.") -> {
                    val suffix = sibBase.substring(base.length + 1)
                    val parts = suffix.split('.').filter { it.isNotBlank() }
                    val valid = parts.filter { !audioExts.contains(it.lowercase()) }
                    when {
                        valid.isEmpty() -> "zh"
                        valid.size == 1 -> valid[0]
                        else -> valid.last()
                    }
                }
                else -> return@mapNotNull null
            }
            com.asmr.player.util.RemoteSubtitleSource(url = sib.url, language = lang, ext = sib.ext)
        }.sortedWith(
            compareBy<com.asmr.player.util.RemoteSubtitleSource> { s ->
                val prefer = listOf("default", "zh", "cn", "chs", "ja", "jp", "jpn")
                val idx = prefer.indexOf(s.language.lowercase())
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenBy { it.url }
        )
    }

    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
        val leaves = nodes.mapNotNull { node ->
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (!url.isNullOrBlank() && children.isEmpty()) {
                val rawTitle = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitize(rawTitle)
                LeafFile(rawTitle = rawTitle, safeTitle = safeTitle, url = url, duration = node.duration)
            } else null
        }

        leaves.filter { it.ext.isBlank() || audioExts.contains(it.ext) }.forEach { leaf ->
            val path = if (parentPath.isBlank()) leaf.safeTitle else "$parentPath/${leaf.safeTitle}"
            val displayTitle = sanitize(leaf.baseName).ifBlank { leaf.safeTitle }
            val subs = buildSubtitlesForAudio(leaf, leaves)
            out.add(
                AsmrOneLeafUi(
                    relativePath = path,
                    title = displayTitle,
                    url = leaf.url,
                    duration = leaf.duration,
                    subtitles = subs
                )
            )
        }

        nodes.forEach { node ->
            val children = node.children.orEmpty()
            if (children.isEmpty()) return@forEach
            val title = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitize(title)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            walk(children, path)
        }
    }
    walk(tree, "")
    return out
}

private fun flattenAsmrOneTreeForUi(
    tree: List<AsmrOneTrackNodeResponse>,
    expanded: Set<String>
): AsmrTreeUiResult {
    val out = mutableListOf<AsmrTreeUiEntry>()
    fun sanitize(name: String): String = name.trim().ifEmpty { "item" }.replace(Regex("""[\\/:*?"<>|]"""), "_")

    data class FolderStats(
        var audioCount: Int = 0,
        var videoCount: Int = 0,
        var hasWav: Boolean = false,
        var hasMp4: Boolean = false
    )
    val folderStats = linkedMapOf<String, FolderStats>()
    fun updateFolderStats(parentPath: String, type: TreeFileType, extLower: String) {
        if (parentPath.isBlank()) return
        folderPathPrefixes(parentPath).forEach { folder ->
            val st = folderStats.getOrPut(folder) { FolderStats() }
            when (type) {
                TreeFileType.Audio -> {
                    st.audioCount += 1
                    if (extLower == "wav") st.hasWav = true
                }
                TreeFileType.Video -> {
                    st.videoCount += 1
                    if (extLower == "mp4") st.hasMp4 = true
                }
                else -> Unit
            }
        }
    }

    fun chooseRecommendedExpand(): String? {
        val entries = folderStats.entries.filter { it.value.audioCount > 0 || it.value.videoCount > 0 }
        if (entries.isEmpty()) return null

        fun bestAudio(): Map.Entry<String, FolderStats>? {
            val audioEntries = entries.filter { it.value.audioCount > 0 }
            if (audioEntries.isEmpty()) return null
            val maxAudio = audioEntries.maxOf { it.value.audioCount }
            val threshold = maxOf(1, (maxAudio * 0.7f).toInt())
            val candidates = audioEntries.filter { it.value.audioCount >= threshold }.ifEmpty { audioEntries }
            return candidates
                .sortedWith(
                    compareByDescending<Map.Entry<String, FolderStats>> { it.key.count { ch -> ch == '/' } }
                        .thenByDescending { it.value.audioCount }
                        .thenByDescending { it.value.hasWav }
                        .thenBy { it.key }
                )
                .firstOrNull()
        }

        fun bestVideo(): Map.Entry<String, FolderStats>? {
            val videoEntries = entries.filter { it.value.videoCount > 0 }
            if (videoEntries.isEmpty()) return null
            val maxVideo = videoEntries.maxOf { it.value.videoCount }
            val threshold = maxOf(1, (maxVideo * 0.7f).toInt())
            val candidates = videoEntries.filter { it.value.videoCount >= threshold }.ifEmpty { videoEntries }
            return candidates
                .sortedWith(
                    compareByDescending<Map.Entry<String, FolderStats>> { it.key.count { ch -> ch == '/' } }
                        .thenByDescending { it.value.videoCount }
                        .thenByDescending { it.value.hasMp4 }
                        .thenBy { it.key }
                )
                .firstOrNull()
        }

        val a = bestAudio()
        val v = bestVideo()
        val aCount = a?.value?.audioCount ?: 0
        val vCount = v?.value?.videoCount ?: 0
        return when {
            vCount > aCount -> v?.key
            aCount > vCount -> a?.key
            else -> v?.key ?: a?.key
        }
    }

    fun collectFolderStatsFromFullTree() {
        fun walkAll(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String) {
            nodes.forEach { node ->
                val title = node.title?.trim().orEmpty().ifBlank { "item" }
                val safeTitle = sanitize(title)
                val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
                val children = node.children.orEmpty()
                val url = node.mediaDownloadUrl ?: node.streamUrl
                if (children.isEmpty()) {
                    val type = treeFileTypeForNode(title, url)
                    if (type == TreeFileType.Other || type == TreeFileType.Subtitle) return@forEach
                    val extLower = title.substringAfterLast('.', "").lowercase()
                    updateFolderStats(parentPath = parentPath, type = type, extLower = extLower)
                } else {
                    walkAll(children, path)
                }
            }
        }
        walkAll(tree, "")
    }

    fun walk(nodes: List<AsmrOneTrackNodeResponse>, parentPath: String, depth: Int) {
        nodes.forEach { node ->
            val title = node.title?.trim().orEmpty().ifBlank { "item" }
            val safeTitle = sanitize(title)
            val path = if (parentPath.isBlank()) safeTitle else "$parentPath/$safeTitle"
            val children = node.children.orEmpty()
            val url = node.mediaDownloadUrl ?: node.streamUrl
            if (children.isEmpty()) {
                val type = treeFileTypeForNode(title, url)
                if (type == TreeFileType.Other || type == TreeFileType.Subtitle) return@forEach
                out.add(
                    AsmrTreeUiEntry.File(
                        path = path,
                        title = safeTitle.substringBeforeLast('.'),
                        depth = depth,
                        fileType = type,
                        isPlayable = type == TreeFileType.Audio && !url.isNullOrBlank(),
                        url = url
                    )
                )
            } else {
                out.add(AsmrTreeUiEntry.Folder(path = path, title = safeTitle, depth = depth))
                if (expanded.contains(path)) {
                    walk(children, path, depth + 1)
                }
            }
        }
    }
    collectFolderStatsFromFullTree()
    walk(tree, "", 0)
    return AsmrTreeUiResult(entries = out)
}

private fun fileTypeLabel(fileType: TreeFileType): String = when (fileType) {
    TreeFileType.Audio -> "音频"
    TreeFileType.Video -> "视频"
    TreeFileType.Image -> "图片"
    TreeFileType.Subtitle -> "字幕"
    TreeFileType.Text -> "文本"
    TreeFileType.Pdf -> "PDF"
    TreeFileType.Other -> "文件"
}

private fun queryLocalFileSize(context: android.content.Context, path: String): Long? {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return null
    return when {
        trimmed.startsWith("content://", ignoreCase = true) -> {
            runCatching {
                context.contentResolver.query(
                    Uri.parse(trimmed),
                    arrayOf(DocumentsContract.Document.COLUMN_SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    if (index < 0 || !cursor.moveToFirst()) null else cursor.getLong(index)
                }
            }.getOrNull()
        }
        else -> runCatching { File(trimmed).takeIf { it.exists() }?.length() }.getOrNull()
    }?.takeIf { it > 0L }
}

@Composable
private fun DirectoryBreadcrumbBar(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = currentPath.isBlank(),
                onClick = { onNavigate("") },
                label = { Text("根目录") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
        items(
            items = breadcrumbs,
            key = { it.path },
            contentType = { "breadcrumb" }
        ) { crumb ->
            FilterChip(
                selected = crumb.path == currentPath,
                onClick = { onNavigate(crumb.path) },
                label = {
                    Text(
                        text = crumb.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun CompactDirectoryBreadcrumbBar(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val displayedCrumbs = remember(breadcrumbs) {
        when {
            breadcrumbs.size <= 3 -> breadcrumbs
            else -> listOf(
                DirectoryBreadcrumbSegment(label = "...", path = ""),
                breadcrumbs[breadcrumbs.lastIndex - 1],
                breadcrumbs.last()
            )
        }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surface.copy(alpha = 0.58f),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CompactBreadcrumbNode(
                text = "根目录",
                selected = currentPath.isBlank(),
                icon = Icons.Default.Home,
                onClick = { onNavigate("") }
            )
            displayedCrumbs.forEach { crumb ->
                Text(
                    text = "/",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.textTertiary
                )
                if (crumb.label == "..." && crumb.path.isBlank()) {
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.textTertiary,
                        maxLines = 1
                    )
                } else {
                    CompactBreadcrumbNode(
                        text = crumb.label,
                        selected = crumb.path == currentPath,
                        onClick = { onNavigate(crumb.path) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactBreadcrumbNode(
    text: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
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
private fun CompactDirectoryBreadcrumbContent(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val displayedCrumbs = remember(breadcrumbs) {
        when {
            breadcrumbs.size <= 3 -> breadcrumbs
            else -> listOf(
                DirectoryBreadcrumbSegment(label = "...", path = ""),
                breadcrumbs[breadcrumbs.lastIndex - 1],
                breadcrumbs.last()
            )
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CompactBreadcrumbNode(
            text = "根目录",
            selected = currentPath.isBlank(),
            icon = Icons.Default.Home,
            onClick = { onNavigate("") }
        )
        displayedCrumbs.forEach { crumb ->
            Text(
                text = "/",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.textTertiary
            )
            if (crumb.label == "..." && crumb.path.isBlank()) {
                Text(
                    text = "...",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.textTertiary,
                    maxLines = 1
                )
            } else {
                CompactBreadcrumbNode(
                    text = crumb.label,
                    selected = crumb.path == currentPath,
                    onClick = { onNavigate(crumb.path) }
                )
            }
        }
    }
}

@Composable
private fun DirectoryBrowserPanel(
    panelKey: String,
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    batchTargets: List<PlaylistAddTarget>,
    folders: List<DirectoryFolderItem>,
    files: List<DirectoryFileItem>,
    onNavigate: (String) -> Unit,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    folderKeyPrefix: String,
    fileKeyPrefix: String,
    emptyText: String = "当前目录暂无文件",
    fileContent: @Composable (
        file: DirectoryFileItem,
        selectionMode: Boolean,
        selected: Boolean,
        enterSelectionMode: () -> Unit,
        onSelectedChange: (Boolean) -> Unit
    ) -> Unit
) {
    val browserListState = rememberSaveable("dir-panel:$panelKey", saver = LazyListState.Saver) {
        LazyListState()
    }
    var selectionMode by remember(panelKey, currentPath) { mutableStateOf(false) }
    val selectedPaths = remember(panelKey, currentPath) { mutableStateListOf<String>() }
    val selectedFiles = remember(files, selectedPaths.toList()) {
        val selectedSet = selectedPaths.toSet()
        files.filter { selectedSet.contains(it.path) }
    }
    val activeTargets = remember(selectionMode, batchTargets, selectedFiles) {
        if (selectionMode) selectedFiles.mapNotNull { it.playlistTarget } else batchTargets
    }
    val batchSummaryText = remember(selectionMode, selectedPaths.size, batchTargets.size) {
        if (selectionMode) "已选 ${selectedPaths.size} 项" else "媒体 ${batchTargets.size} 项"
    }
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }

    LaunchedEffect(panelKey, currentPath) {
        browserListState.scrollToItem(0)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.44f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CompactDirectoryBreadcrumbContent(
                currentPath = currentPath,
                breadcrumbs = breadcrumbs,
                onNavigate = onNavigate
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            DirectoryBatchBarEmbeddedV2(
                targets = activeTargets,
                summaryText = batchSummaryText,
                onAddToFavorites = onAddToFavorites,
                onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                onAddMediaItemsToQueue = onAddMediaItemsToQueue
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            LazyColumn(
                state = browserListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .thinScrollbar(browserListState),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                if (folders.isEmpty() && files.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emptyText,
                                color = AsmrTheme.colorScheme.textSecondary
                            )
                        }
                    }
                } else {
                    items(
                        items = folders,
                        key = { folder -> "$folderKeyPrefix:${folder.path}" },
                        contentType = { "folder" }
                    ) { folder ->
                        DirectoryFolderRow(
                            title = folder.title,
                            onClick = { onNavigate(folder.path) }
                        )
                    }
                    items(
                        items = files,
                        key = { file -> "$fileKeyPrefix:${file.path}" },
                        contentType = { "file" }
                    ) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        fileContent(
                            file,
                            selectionMode,
                            isSelected,
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
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryBatchBarEmbedded(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "当前目录 ${mediaItems.size} 个音频/视频文件",
            style = MaterialTheme.typography.bodySmall,
            color = AsmrTheme.colorScheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(
                onClick = { onAddToFavorites(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("收藏", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onOpenBatchPlaylistPicker(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("列表", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onAddMediaItemsToQueue(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("队列", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DirectoryBatchBar(
    targets: List<PlaylistAddTarget>,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    embedded: Boolean = false
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.72f),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "当前目录 ${mediaItems.size} 个音频/视频文件",
                style = MaterialTheme.typography.titleSmall,
                color = AsmrTheme.colorScheme.textPrimary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = { onAddToFavorites(mediaItems) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("收藏")
                }
                OutlinedButton(
                    onClick = { onOpenBatchPlaylistPicker(mediaItems) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("列表")
                }
                OutlinedButton(
                    onClick = { onAddMediaItemsToQueue(mediaItems) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("队列")
                }
            }
        }
    }
}

@Composable
private fun DirectoryBatchBarEmbeddedV2(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodySmall,
            color = AsmrTheme.colorScheme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = { onAddToFavorites(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("收藏", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onOpenBatchPlaylistPicker(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("列表", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = { onAddMediaItemsToQueue(mediaItems) },
                enabled = hasMediaItems,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("队列", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CompactDirectoryBreadcrumbContentV2(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompactBreadcrumbNode(
            text = "根目录",
            selected = currentPath.isBlank(),
            icon = Icons.Default.Home,
            onClick = { onNavigate("") }
        )
        displayedCrumbs.forEach { crumb ->
            Text(
                text = "/",
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.textTertiary
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
                    text = crumb.label,
                    selected = crumb.path == currentPath,
                    onClick = { onNavigate(crumb.path) }
                )
            }
        }
    }
}

@Composable
private fun DirectoryFolderRowV2(
    title: String,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.primary.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.textPrimary
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DirectoryActionGroupButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DirectoryBatchBarEmbeddedV3(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    hintText: String,
    showActions: Boolean,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.titleSmall,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirectoryActionGroupButton(
                    text = "收藏",
                    icon = Icons.Default.FavoriteBorder,
                    enabled = hasMediaItems,
                    onClick = { onAddToFavorites(mediaItems) }
                )
                VerticalDivider(
                    color = dividerColor,
                    modifier = Modifier.height(18.dp),
                    thickness = 0.5.dp
                )
                DirectoryActionGroupButton(
                    text = "列表",
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    enabled = hasMediaItems,
                    onClick = { onOpenBatchPlaylistPicker(mediaItems) }
                )
                VerticalDivider(
                    color = dividerColor,
                    modifier = Modifier.height(18.dp),
                    thickness = 0.5.dp
                )
                DirectoryActionGroupButton(
                    text = "队列",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    enabled = hasMediaItems,
                    onClick = { onAddMediaItemsToQueue(mediaItems) }
                )
            }
        }
    }
}

@Composable
private fun DirectoryBrowserPanelV2(
    panelKey: String,
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    batchTargets: List<PlaylistAddTarget>,
    folders: List<DirectoryFolderItem>,
    files: List<DirectoryFileItem>,
    onNavigate: (String) -> Unit,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    folderKeyPrefix: String,
    fileKeyPrefix: String,
    emptyText: String = "当前目录暂无文件",
    fileContent: @Composable (
        file: DirectoryFileItem,
        selectionMode: Boolean,
        selected: Boolean,
        enterSelectionMode: () -> Unit,
        onSelectedChange: (Boolean) -> Unit
    ) -> Unit
) {
    val browserListState = rememberSaveable("dir-panel-v2:$panelKey", saver = LazyListState.Saver) {
        LazyListState()
    }
    var selectionMode by remember(panelKey, currentPath) { mutableStateOf(false) }
    val selectedPaths = remember(panelKey, currentPath) { mutableStateListOf<String>() }
    val selectedFiles = remember(files, selectedPaths.toList()) {
        val selectedSet = selectedPaths.toSet()
        files.filter { selectedSet.contains(it.path) }
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
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }

    LaunchedEffect(panelKey, currentPath) {
        browserListState.scrollToItem(0)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.44f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CompactDirectoryBreadcrumbContentV2(
                currentPath = currentPath,
                breadcrumbs = breadcrumbs,
                onNavigate = onNavigate
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            DirectoryBatchBarEmbeddedV3(
                targets = activeTargets,
                summaryText = batchSummaryText,
                hintText = batchHintText,
                showActions = selectionMode,
                onAddToFavorites = onAddToFavorites,
                onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                onAddMediaItemsToQueue = onAddMediaItemsToQueue
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            LazyColumn(
                state = browserListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .thinScrollbar(browserListState),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (folders.isEmpty() && files.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emptyText,
                                color = AsmrTheme.colorScheme.textSecondary
                            )
                        }
                    }
                } else {
                    items(
                        items = folders,
                        key = { folder -> "$folderKeyPrefix:${folder.path}" },
                        contentType = { "folder" }
                    ) { folder ->
                        DirectoryFolderRowV2(
                            title = folder.title,
                            onClick = { onNavigate(folder.path) }
                        )
                    }
                    items(
                        items = files,
                        key = { file -> "$fileKeyPrefix:${file.path}" },
                        contentType = { "file" }
                    ) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        fileContent(
                            file,
                            selectionMode,
                            isSelected,
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
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryFolderRow(
    title: String,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Surface(
        color = colorScheme.surface.copy(alpha = 0.54f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.textPrimary
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.textSecondary
            )
        }
    }
}

@Composable
private fun CompactDirectoryBreadcrumbContentV3(
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    onNavigate: (String) -> Unit
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompactBreadcrumbNode(
            text = "根目录",
            selected = currentPath.isBlank(),
            icon = Icons.Default.Home,
            onClick = { onNavigate("") }
        )
        displayedCrumbs.forEach { crumb ->
            Text(
                text = "/",
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.textTertiary
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
                    text = crumb.label,
                    selected = crumb.path == currentPath,
                    onClick = { onNavigate(crumb.path) }
                )
            }
        }
    }
}

@Composable
private fun DirectoryFolderRowV3(
    title: String,
    onClick: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.primary.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.textPrimary
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = colorScheme.textSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DirectoryBatchBarEmbeddedV4(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    hintText: String,
    showActions: Boolean,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.titleSmall,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirectoryActionGroupButton(
                    text = "收藏",
                    icon = Icons.Default.FavoriteBorder,
                    enabled = hasMediaItems,
                    onClick = { onAddToFavorites(mediaItems) }
                )
                Text("|", color = AsmrTheme.colorScheme.textTertiary, style = MaterialTheme.typography.labelMedium)
                DirectoryActionGroupButton(
                    text = "列表",
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    enabled = hasMediaItems,
                    onClick = { onOpenBatchPlaylistPicker(mediaItems) }
                )
                Text("|", color = AsmrTheme.colorScheme.textTertiary, style = MaterialTheme.typography.labelMedium)
                DirectoryActionGroupButton(
                    text = "队列",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    enabled = hasMediaItems,
                    onClick = { onAddMediaItemsToQueue(mediaItems) }
                )
            }
        }
    }
}

@Composable
private fun DirectoryBatchBarEmbeddedV5(
    targets: List<PlaylistAddTarget>,
    summaryText: String,
    hintText: String,
    showActions: Boolean,
    modifier: Modifier = Modifier,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit
) {
    val mediaItems = remember(targets) { targets.map { it.toMediaItem() } }
    val hasMediaItems = mediaItems.isNotEmpty()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summaryText,
                style = MaterialTheme.typography.titleSmall,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirectoryActionGroupButton(
                    text = "收藏",
                    icon = Icons.Default.FavoriteBorder,
                    enabled = hasMediaItems,
                    onClick = { onAddToFavorites(mediaItems) }
                )
                Text("|", color = AsmrTheme.colorScheme.textTertiary, style = MaterialTheme.typography.labelMedium)
                DirectoryActionGroupButton(
                    text = "列表",
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    enabled = hasMediaItems,
                    onClick = {
                        if (hasMediaItems) {
                            onOpenBatchPlaylistPicker(mediaItems.toList())
                        }
                    }
                )
                Text("|", color = AsmrTheme.colorScheme.textTertiary, style = MaterialTheme.typography.labelMedium)
                DirectoryActionGroupButton(
                    text = "队列",
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    enabled = hasMediaItems,
                    onClick = { onAddMediaItemsToQueue(mediaItems) }
                )
            }
        }
    }
}

@Composable
private fun DirectoryBrowserPanelV4(
    panelKey: String,
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    batchTargets: List<PlaylistAddTarget>,
    folders: List<DirectoryFolderItem>,
    files: List<DirectoryFileItem>,
    onNavigate: (String) -> Unit,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    preferredPath: String = "",
    onTogglePreferredPath: ((Boolean) -> Unit)? = null,
    folderKeyPrefix: String,
    fileKeyPrefix: String,
    emptyText: String = "当前目录暂无文件",
    fileContent: @Composable (
        file: DirectoryFileItem,
        selectionMode: Boolean,
        selected: Boolean,
        enterSelectionMode: () -> Unit,
        onSelectedChange: (Boolean) -> Unit
    ) -> Unit
) {
    val browserListState = rememberSaveable("dir-panel-v4:$panelKey", saver = LazyListState.Saver) {
        LazyListState()
    }
    var selectionMode by remember(panelKey, currentPath) { mutableStateOf(false) }
    var preferredPathState by rememberSaveable(panelKey) { mutableStateOf(preferredPath.trim().trim('/')) }
    val selectedPaths = remember(panelKey, currentPath) { mutableStateListOf<String>() }
    val selectedFiles = remember(files, selectedPaths.toList()) {
        val selectedSet = selectedPaths.toSet()
        files.filter { selectedSet.contains(it.path) }
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

    LaunchedEffect(panelKey, currentPath) {
        browserListState.scrollToItem(0)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.44f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CompactDirectoryBreadcrumbContentV3(
                currentPath = currentPath,
                breadcrumbs = breadcrumbs,
                onNavigate = onNavigate
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DirectoryBatchBarEmbeddedV5(
                    targets = activeTargets,
                    summaryText = batchSummaryText,
                    hintText = batchHintText,
                    showActions = selectionMode,
                    modifier = Modifier.weight(1f),
                    onAddToFavorites = onAddToFavorites,
                    onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                    onAddMediaItemsToQueue = onAddMediaItemsToQueue
                )
                if (onTogglePreferredPath != null && !selectionMode) {
                    val preferredIcon = if (isPreferredPath) Icons.Default.Bookmark else Icons.Default.BookmarkBorder
                    val preferredTextColor = if (isPreferredPath) AsmrTheme.colorScheme.primary else AsmrTheme.colorScheme.textSecondary
                    val preferredContainerColor = AsmrTheme.colorScheme.primary.copy(alpha = if (AsmrTheme.colorScheme.isDark) 0.24f else 0.14f)
                    val preferredButton: @Composable (@Composable () -> Unit) -> Unit = { content ->
                        if (isPreferredPath) {
                            FilledTonalButton(
                                onClick = {
                                    preferredPathState = ""
                                    onTogglePreferredPath(false)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = preferredContainerColor,
                                    contentColor = preferredTextColor
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) { content() }
                        } else {
                            TextButton(
                                onClick = {
                                    preferredPathState = normalizedCurrentPath
                                    onTogglePreferredPath(true)
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = preferredTextColor
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(34.dp)
                            ) { content() }
                        }
                    }
                    preferredButton {
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
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            LazyColumn(
                state = browserListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fixedHeight)
                    .thinScrollbar(browserListState),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (folders.isEmpty() && files.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(fixedHeight - 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emptyText,
                                color = AsmrTheme.colorScheme.textSecondary
                            )
                        }
                    }
                } else {
                    items(
                        items = folders,
                        key = { folder -> "$folderKeyPrefix:${folder.path}" },
                        contentType = { "folder" }
                    ) { folder ->
                        DirectoryFolderRowV3(
                            title = folder.title,
                            onClick = { onNavigate(folder.path) }
                        )
                    }
                    items(
                        items = files,
                        key = { file -> "$fileKeyPrefix:${file.path}" },
                        contentType = { "file" }
                    ) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        fileContent(
                            file,
                            selectionMode,
                            isSelected,
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
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryBrowserPanelV3(
    panelKey: String,
    currentPath: String,
    breadcrumbs: List<DirectoryBreadcrumbSegment>,
    batchTargets: List<PlaylistAddTarget>,
    folders: List<DirectoryFolderItem>,
    files: List<DirectoryFileItem>,
    onNavigate: (String) -> Unit,
    onAddToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    preferredPath: String = "",
    onTogglePreferredPath: ((Boolean) -> Unit)? = null,
    folderKeyPrefix: String,
    fileKeyPrefix: String,
    emptyText: String = "当前目录暂无文件",
    fileContent: @Composable (
        file: DirectoryFileItem,
        selectionMode: Boolean,
        selected: Boolean,
        enterSelectionMode: () -> Unit,
        onSelectedChange: (Boolean) -> Unit
    ) -> Unit
) {
    val browserListState = rememberSaveable("dir-panel-v3:$panelKey", saver = LazyListState.Saver) {
        LazyListState()
    }
    var selectionMode by remember(panelKey, currentPath) { mutableStateOf(false) }
    val selectedPaths = remember(panelKey, currentPath) { mutableStateListOf<String>() }
    val selectedFiles = remember(files, selectedPaths.toList()) {
        val selectedSet = selectedPaths.toSet()
        files.filter { selectedSet.contains(it.path) }
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
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val fixedHeight = remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }

    LaunchedEffect(panelKey, currentPath) {
        browserListState.scrollToItem(0)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.44f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CompactDirectoryBreadcrumbContentV3(
                currentPath = currentPath,
                breadcrumbs = breadcrumbs,
                onNavigate = onNavigate
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            DirectoryBatchBarEmbeddedV4(
                targets = activeTargets,
                summaryText = batchSummaryText,
                hintText = batchHintText,
                showActions = selectionMode,
                onAddToFavorites = onAddToFavorites,
                onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                onAddMediaItemsToQueue = onAddMediaItemsToQueue
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            LazyColumn(
                state = browserListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fixedHeight)
                    .thinScrollbar(browserListState),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (folders.isEmpty() && files.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(fixedHeight - 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = emptyText,
                                color = AsmrTheme.colorScheme.textSecondary
                            )
                        }
                    }
                } else {
                    items(
                        items = folders,
                        key = { folder -> "$folderKeyPrefix:${folder.path}" },
                        contentType = { "folder" }
                    ) { folder ->
                        DirectoryFolderRowV3(
                            title = folder.title,
                            onClick = { onNavigate(folder.path) }
                        )
                    }
                    items(
                        items = files,
                        key = { file -> "$fileKeyPrefix:${file.path}" },
                        contentType = { "file" }
                    ) { file ->
                        val isSelected = selectedPaths.contains(file.path)
                        fileContent(
                            file,
                            selectionMode,
                            isSelected,
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
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DirectoryFileRow(
    file: DirectoryFileItem,
    loadRemoteFileSize: suspend (String) -> Long?,
    onPrimary: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onEnterSelectionMode: (() -> Unit)? = null,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    onSetAsCover: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onManageTags: (() -> Unit)? = null,
    onRemoveFromAlbum: (() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val context = LocalContext.current
    val icon = when (file.fileType) {
        TreeFileType.Audio -> Icons.Default.Audiotrack
        TreeFileType.Video -> Icons.Default.Movie
        TreeFileType.Image -> Icons.Default.Image
        TreeFileType.Subtitle -> Icons.Default.Subtitles
        TreeFileType.Text -> Icons.Default.Description
        TreeFileType.Pdf -> Icons.Default.PictureAsPdf
        TreeFileType.Other -> Icons.Default.InsertDriveFile
    }
    val iconTint = when (file.fileType) {
        TreeFileType.Audio -> colorScheme.primary
        TreeFileType.Video -> colorScheme.accent
        TreeFileType.Image -> colorScheme.textSecondary
        TreeFileType.Subtitle -> colorScheme.textSecondary
        TreeFileType.Text -> colorScheme.textTertiary
        TreeFileType.Pdf -> colorScheme.danger
        TreeFileType.Other -> colorScheme.textTertiary
    }
    val sizeText by produceState<String?>(initialValue = null, file.sizeSource) {
        value = when (val sizeSource = file.sizeSource) {
            FileSizeSource.None -> null
            is FileSizeSource.Local -> withContext(Dispatchers.IO) {
                queryLocalFileSize(context, sizeSource.path)
            }?.let(Formatting::formatFileSize)
            is FileSizeSource.Remote -> loadRemoteFileSize(sizeSource.url)?.let(Formatting::formatFileSize)
        }
    }
    val metaLine = remember(file.fileType, file.durationSeconds, sizeText) {
        listOf(
            fileTypeLabel(file.fileType),
            Formatting.formatTrackSeconds(file.durationSeconds).takeIf { it.isNotBlank() },
            sizeText
        ).filterNotNull().joinToString(" · ")
    }

    val showPrimaryAction = file.isPlayable
    val showMenu = showPrimaryAction || onDownload != null || onAddToQueue != null || onAddToPlaylist != null || onManageTags != null || onRemoveFromAlbum != null
    val showTrailing = selectionMode || onSetAsCover != null || file.showSubtitleStamp || showMenu

    Surface(
        color = Color.Transparent,
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
        ListItem(
            headlineContent = {
                Text(
                    text = file.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.textPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
            },
            supportingContent = {
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        color = colorScheme.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            leadingContent = {
                if (file.fileType == TreeFileType.Image && file.thumbnailModel != null) {
                    AsmrAsyncImage(
                        model = file.thumbnailModel,
                        contentDescription = null,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                        placeholderCornerRadius = 8
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            trailingContent = if (showTrailing) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (file.showSubtitleStamp) {
                            SubtitleStamp(modifier = Modifier.padding(end = 8.dp))
                        }
                        if (selectionMode && onSelectedChange != null) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked -> onSelectedChange(checked) }
                            )
                        } else if (onSetAsCover != null) {
                            IconButton(
                                onClick = onSetAsCover,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = "设为封面",
                                    tint = colorScheme.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (!selectionMode && showMenu) {
                            var showMenuExpanded by rememberSaveable(file.path) { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showMenuExpanded = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
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
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colorScheme.primary)
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
                                                    Icon(Icons.Default.Download, contentDescription = null, tint = colorScheme.textSecondary)
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
                                                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null, tint = colorScheme.textSecondary)
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
                                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = colorScheme.textSecondary)
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
                                                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = colorScheme.textSecondary)
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
                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = colorScheme.textSecondary)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun TreeFolderRow(
    title: String,
    depth: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp)
    ) {
        ListItem(
            headlineContent = { 
                Text(
                    title, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.textPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                ) 
            },
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (depth > 0) Spacer(modifier = Modifier.width((depth * 12).dp))
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            trailingContent = {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colorScheme.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun TreeFileRow(
    title: String,
    depth: Int,
    fileType: TreeFileType,
    isPlayable: Boolean,
    showSubtitleStamp: Boolean = false,
    thumbnailModel: Any? = null,
    onPrimary: () -> Unit,
    onSetAsCover: (() -> Unit)? = null,
    onDownload: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onManageTags: (() -> Unit)? = null,
    onRemoveFromAlbum: (() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val icon = when (fileType) {
        TreeFileType.Audio -> Icons.Default.Audiotrack
        TreeFileType.Video -> Icons.Default.Movie
        TreeFileType.Image -> Icons.Default.Image
        TreeFileType.Subtitle -> Icons.Default.Subtitles
        TreeFileType.Text -> Icons.Default.Description
        TreeFileType.Pdf -> Icons.Default.PictureAsPdf
        TreeFileType.Other -> Icons.Default.InsertDriveFile
    }
    val iconTint = when (fileType) {
        TreeFileType.Audio -> colorScheme.primary
        TreeFileType.Video -> colorScheme.accent
        TreeFileType.Image -> colorScheme.textSecondary
        TreeFileType.Subtitle -> colorScheme.textSecondary
        TreeFileType.Text -> colorScheme.textTertiary
        TreeFileType.Pdf -> colorScheme.danger
        TreeFileType.Other -> colorScheme.textTertiary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPrimary)
            .padding(vertical = 2.dp)
    ) {
        val showPrimaryAction = isPlayable
        val showMenu = showPrimaryAction || onDownload != null || onAddToQueue != null || onAddToPlaylist != null || onManageTags != null || onRemoveFromAlbum != null
        val showTrailing = onSetAsCover != null || showMenu
        ListItem(
            headlineContent = { 
                Text(
                    title, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    color = colorScheme.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                ) 
            },
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (depth > 0) Spacer(modifier = Modifier.width((depth * 12).dp))
                    if (fileType == TreeFileType.Image && thumbnailModel != null) {
                        AsmrAsyncImage(
                            model = thumbnailModel,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                            placeholderCornerRadius = 6,
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            trailingContent = if (showTrailing) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showSubtitleStamp) {
                            SubtitleStamp(modifier = Modifier.padding(end = 8.dp))
                        }
                        if (onSetAsCover != null) {
                            IconButton(
                                onClick = onSetAsCover,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = "设为封面",
                                    tint = colorScheme.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (showMenu) {
                            var showMenuExpanded by rememberSaveable { mutableStateOf(false) }
                            Box {
                                IconButton(
                                    onClick = { showMenuExpanded = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
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
                                    val dividerColor = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                    var hasVisibleItem = false
                                    @Composable
                                    fun addDividerIfNeeded() {
                                        if (hasVisibleItem) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                thickness = 0.5.dp,
                                                color = dividerColor
                                            )
                                        }
                                        hasVisibleItem = true
                                    }
                                    DropdownMenu(
                                        expanded = showMenuExpanded,
                                        onDismissRequest = { showMenuExpanded = false },
                                        modifier = Modifier.background(dynamicContainerColor)
                                    ) {
                                        if (showPrimaryAction) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("播放") },
                                                onClick = {
                                                    onPrimary()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = colorScheme.primary
                                                    )
                                                }
                                            )
                                        }
                                        if (onDownload != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("下载") },
                                                onClick = {
                                                    onDownload.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Download,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onAddToQueue != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("添加到播放队列") },
                                                onClick = {
                                                    onAddToQueue.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.PlaylistPlay,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onAddToPlaylist != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("添加到我的列表") },
                                                onClick = {
                                                    onAddToPlaylist.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.PlaylistAdd,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onManageTags != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("标签管理") },
                                                onClick = {
                                                    onManageTags.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.Label,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
                                                    )
                                                }
                                            )
                                        }
                                        if (onRemoveFromAlbum != null) {
                                            addDividerIfNeeded()
                                            DropdownMenuItem(
                                                text = { Text("从专辑移除") },
                                                onClick = {
                                                    onRemoveFromAlbum.invoke()
                                                    showMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = colorScheme.textSecondary
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
            } else null,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun AsmrOneDownloadDialog(
    albumTitle: String,
    trackTree: List<AsmrOneTrackNodeResponse>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val leaves = remember(trackTree) { flattenAsmrOneTracksForUi(trackTree) }
    val leafPathsByFolder = remember(trackTree) { buildLeafPathIndex(trackTree) }
    val expanded = remember { mutableStateListOf<String>() }
    val selected = remember(trackTree) { mutableStateListOf<String>().apply { addAll(leaves.map { it.relativePath }) } }
    val listState = rememberLazyListState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    Text(
                        text = "选择要下载的文件",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { onConfirm(selected.toSet()) },
                        enabled = leaves.isNotEmpty() && selected.isNotEmpty()
                    ) { Text("开始下载") }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(albumTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            selected.clear()
                            selected.addAll(leaves.map { it.relativePath })
                        }) { Text("全选") }
                        OutlinedButton(onClick = { selected.clear() }) { Text("全不选") }
                    }
                    if (leaves.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有可下载的音频/视频文件")
                        }
                    } else {
                        val entries = flattenAsmrOneTreeForUi(trackTree, expanded.toSet()).entries
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().thinScrollbar(listState)
                        ) {
                            itemsIndexed(items = entries, key = { _, it -> it.path }) { index, entry ->
                                when (entry) {
                                    is AsmrTreeUiEntry.Folder -> {
                                        val leafPaths = leafPathsByFolder[entry.path].orEmpty()
                                        val checkedCount = leafPaths.count { selected.contains(it) }
                                        val state = when {
                                            leafPaths.isEmpty() -> ToggleableState.Off
                                            checkedCount == 0 -> ToggleableState.Off
                                            checkedCount == leafPaths.size -> ToggleableState.On
                                            else -> ToggleableState.Indeterminate
                                        }
                                        AsmrTreeFolderCheckboxRow(
                                            title = entry.title,
                                            depth = entry.depth,
                                            expanded = expanded.contains(entry.path),
                                            toggleState = state,
                                            onToggleExpand = {
                                                if (expanded.contains(entry.path)) expanded.remove(entry.path) else expanded.add(entry.path)
                                            },
                                            onToggleCheck = {
                                                if (leafPaths.isEmpty()) return@AsmrTreeFolderCheckboxRow
                                                val shouldSelectAll = state != ToggleableState.On
                                                if (shouldSelectAll) {
                                                    leafPaths.forEach { if (!selected.contains(it)) selected.add(it) }
                                                } else {
                                                    selected.removeAll(leafPaths.toSet())
                                                }
                                            }
                                        )
                                    }
                                    is AsmrTreeUiEntry.File -> {
                                        val isChecked = selected.contains(entry.path)
                                        AsmrTreeFileCheckboxRow(
                                            title = entry.title,
                                            depth = entry.depth,
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
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
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
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
}

private data class OnlineSaveLeafUi(
    val relativePath: String,
    val title: String,
    val url: String,
    val fileType: TreeFileType
)

private fun flattenOnlineSaveLeaves(tree: List<AsmrOneTrackNodeResponse>): List<OnlineSaveLeafUi> {
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
                val nameForType = titleRaw.ifBlank { url.substringBefore('?').substringAfterLast('/') }
                val type = treeFileTypeForName(nameForType)
                if (type != TreeFileType.Audio && type != TreeFileType.Video) return@forEach
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

private fun buildMediaLeafPathIndex(tree: List<AsmrOneTrackNodeResponse>): Map<String, List<String>> {
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
                val nameForType = titleRaw.ifBlank { url.substringBefore('?').substringAfterLast('/') }
                val type = treeFileTypeForName(nameForType)
                if (type != TreeFileType.Audio && type != TreeFileType.Video) return@forEach
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

private fun flattenAsmrOneMediaTreeForUi(
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
            val nameForType = titleRaw.ifBlank { url.substringBefore('?').substringAfterLast('/') }
            val type = treeFileTypeForName(nameForType)
            type == TreeFileType.Audio || type == TreeFileType.Video
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
                val nameForType = titleRaw.ifBlank { url.substringBefore('?').substringAfterLast('/') }
                val type = treeFileTypeForName(nameForType)
                if (type != TreeFileType.Audio && type != TreeFileType.Video) return@forEach
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
private fun OnlineSaveDialog(
    albumTitle: String,
    trackTree: List<AsmrOneTrackNodeResponse>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val leaves = remember(trackTree) { flattenOnlineSaveLeaves(trackTree) }
    val leafPathsByFolder = remember(trackTree) { buildMediaLeafPathIndex(trackTree) }
    val expanded = remember { mutableStateListOf<String>() }
    val selected = remember(trackTree) { mutableStateListOf<String>().apply { addAll(leaves.map { it.relativePath }) } }
    val listState = rememberLazyListState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    Text(
                        text = "选择要保存的文件",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { onConfirm(selected.toSet()) },
                        enabled = leaves.isNotEmpty() && selected.isNotEmpty()
                    ) { Text("保存到本地库") }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(albumTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            selected.clear()
                            selected.addAll(leaves.map { it.relativePath })
                        }) { Text("全选") }
                        OutlinedButton(onClick = { selected.clear() }) { Text("全不选") }
                    }
                    if (leaves.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("没有可保存的音频/视频文件")
                        }
                    } else {
                        val entries = flattenAsmrOneMediaTreeForUi(trackTree, expanded.toSet()).entries
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().thinScrollbar(listState)
                        ) {
                            itemsIndexed(items = entries, key = { _, it -> it.path }) { index, entry ->
                                when (entry) {
                                    is AsmrTreeUiEntry.Folder -> {
                                        val leafPaths = leafPathsByFolder[entry.path].orEmpty()
                                        val checkedCount = leafPaths.count { selected.contains(it) }
                                        val state = when {
                                            leafPaths.isEmpty() -> ToggleableState.Off
                                            checkedCount == 0 -> ToggleableState.Off
                                            checkedCount == leafPaths.size -> ToggleableState.On
                                            else -> ToggleableState.Indeterminate
                                        }
                                        AsmrTreeFolderCheckboxRow(
                                            title = entry.title,
                                            depth = entry.depth,
                                            expanded = expanded.contains(entry.path),
                                            toggleState = state,
                                            onToggleExpand = {
                                                if (expanded.contains(entry.path)) expanded.remove(entry.path) else expanded.add(entry.path)
                                            },
                                            onToggleCheck = {
                                                if (leafPaths.isEmpty()) return@AsmrTreeFolderCheckboxRow
                                                val shouldSelectAll = state != ToggleableState.On
                                                if (shouldSelectAll) {
                                                    leafPaths.forEach { if (!selected.contains(it)) selected.add(it) }
                                                } else {
                                                    selected.removeAll(leafPaths.toSet())
                                                }
                                            }
                                        )
                                    }
                                    is AsmrTreeUiEntry.File -> {
                                        val isChecked = selected.contains(entry.path)
                                        AsmrTreeFileCheckboxRow(
                                            title = entry.title,
                                            depth = entry.depth,
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
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
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
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
}

@Composable
private fun AsmrTreeFolderCheckboxRow(
    title: String,
    depth: Int,
    expanded: Boolean,
    toggleState: ToggleableState,
    onToggleExpand: () -> Unit,
    onToggleCheck: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(start = (2 + depth * 14).dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TriStateCheckbox(state = toggleState, onClick = onToggleCheck)
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AsmrTreeFileCheckboxRow(
    title: String,
    depth: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(start = (38 + depth * 14).dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
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

@Composable
private fun AlbumDlsiteInfoTab(
    album: Album,
    header: @Composable () -> Unit,
    dlsiteInfo: Album?,
    galleryUrls: List<String>,
    trialTracks: List<Track>,
    isLoading: Boolean,
    asmrOneTree: List<AsmrOneTrackNodeResponse>,
    isLoadingAsmrOne: Boolean,
    isLoadingTrial: Boolean,
    onRefreshAsmrOne: () -> Unit,
    onRefreshTrial: () -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onDownloadOne: (String) -> Unit,
    onAddToPlaylistOne: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPreviewFile: (AsmrTreeUiEntry.File) -> Unit,
    treeStateKey: String,
    treeInitialExpanded: List<String>,
    treeWasInitialized: Boolean,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
    onPersistTreeState: (List<String>) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    dlsiteRecommendations: DlsiteRecommendations,
    onOpenAlbumByRj: (String) -> Unit
) {
    val infoAlbum = dlsiteInfo ?: album
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewUrl by remember { mutableStateOf<String?>(null) }
    val videoTracks = remember(trialTracks) { trialTracks.filter { isVideoPreviewUrl(it.path) } }
    val audioTracks = remember(trialTracks) { trialTracks.filterNot { isVideoPreviewUrl(it.path) } }
    val asmrLeafTracks = remember(asmrOneTree) { flattenAsmrOneTracksForUi(asmrOneTree) }
    val asmrLeafByRelPath = remember(asmrLeafTracks) { asmrLeafTracks.associateBy { it.relativePath } }
    val asmrExpanded = remember(treeStateKey) {
        mutableStateListOf<String>().apply { addAll(treeInitialExpanded) }
    }
    var asmrInitialized by remember(treeStateKey) {
        mutableStateOf(treeWasInitialized || treeInitialExpanded.isNotEmpty())
    }
    val asmrTreeResult = remember(asmrOneTree, asmrExpanded.toList()) {
        flattenAsmrOneTreeForUi(asmrOneTree, asmrExpanded.toSet())
    }
    val listState = rememberSaveable("scroll:$treeStateKey", saver = LazyListState.Saver) {
        LazyListState(initialScroll.first, initialScroll.second)
    }
    LaunchedEffect(listState, treeStateKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, off) -> onPersistScroll(idx, off) }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
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
        item { header() }
        if (dlsiteInfo == null && isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                }
            }
            return@LazyColumn
        }
        item {
            Text(
                text = "Gallery",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (galleryUrls.isEmpty()) {
                Text(
                    text = "暂无样图",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    items(
                        items = galleryUrls,
                        key = { it },
                        contentType = { "galleryThumb" }
                    ) { url ->
                        val model = remember(url) {
                            val headers = DlsiteAntiHotlink.headersForImageUrl(url)
                            if (headers.isEmpty()) url else CacheImageModel(data = url, headers = headers, keyTag = "dlsite")
                        }
                        Card(
                            modifier = Modifier
                                .size(width = 140.dp, height = 100.dp)
                                .clickable { previewUrl = url },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            AsmrAsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                placeholderCornerRadius = 12,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        item {
            val title = if (asmrOneTree.isNotEmpty()) "ONE（已收录）" else if (isLoadingAsmrOne) "ONE" else "ONE（未收录）"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onRefreshAsmrOne,
                    enabled = !isLoadingAsmrOne
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
            if (asmrOneTree.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        asmrTreeResult.entries.forEachIndexed { index, entry ->
                            when (entry) {
                                is AsmrTreeUiEntry.Folder -> {
                                    TreeFolderRow(
                                        title = entry.title,
                                        depth = entry.depth,
                                        expanded = asmrExpanded.contains(entry.path),
                                        onToggle = {
                                            if (asmrExpanded.contains(entry.path)) asmrExpanded.remove(entry.path) else asmrExpanded.add(entry.path)
                                            asmrInitialized = true
                                            onPersistTreeState(asmrExpanded.toList())
                                        }
                                    )
                                }
                                is AsmrTreeUiEntry.File -> {
                                    val canPlay = entry.fileType == TreeFileType.Audio && asmrLeafByRelPath.containsKey(entry.path)
                                    TreeFileRow(
                                        title = entry.title,
                                        depth = entry.depth,
                                        fileType = entry.fileType,
                                    isPlayable = canPlay,
                                    showSubtitleStamp = canPlay && (asmrLeafByRelPath[entry.path]?.subtitles?.isNotEmpty() == true),
                                    onPrimary = {
                                        if (!canPlay) {
                                            onPreviewFile(entry)
                                            return@TreeFileRow
                                        }
                                        scope.launch {
                                            val prepared = withContext(Dispatchers.Default) {
                                                val start = asmrLeafByRelPath[entry.path] ?: return@withContext null
                                                val folderPath = entry.path.substringBeforeLast('/', "")
                                                val siblingLeaves = asmrLeafTracks.filter {
                                                    it.relativePath.substringBeforeLast('/', "") == folderPath
                                                }
                                                val queueLeaves = siblingLeaves.ifEmpty { listOf(start) }
                                                PreparedTrackPlayback(
                                                    tracks = queueLeaves.sortedBy { SmartSortKey.of(it.title) }.map { it.toTrack() },
                                                    startTrack = start.toTrack(),
                                                    onlineLyrics = queueLeaves.associate { it.url to it.subtitles }
                                                )
                                            } ?: return@launch
                                            com.asmr.player.util.OnlineLyricsStore.replaceAll(prepared.onlineLyrics)
                                            onPlayTracks(album, prepared.tracks, prepared.startTrack)
                                        }
                                    },
                                        onDownload = if (entry.fileType == TreeFileType.Audio) ({ onDownloadOne(entry.path) }) else null,
                                        onAddToQueue = if (canPlay) ({
                                            val leaf = asmrLeafByRelPath[entry.path]
                                            if (leaf != null) {
                                                com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                                                onAddToQueue(leaf.toTrack())
                                            }
                                        }) else null,
                                        onAddToPlaylist = if (entry.fileType == TreeFileType.Audio) ({ onAddToPlaylistOne(entry.path) }) else null
                                    )
                                }
                            }
                            if (index < asmrTreeResult.entries.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            } else if (isLoadingAsmrOne) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("未收录")
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "试听/试看",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onRefreshTrial,
                    enabled = !isLoading && !isLoadingTrial
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }
        if (trialTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingTrial) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    } else {
                        Text("暂无试听")
                    }
                }
            }
        } else {
            if (isLoadingTrial) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    }
                }
            }
            items(
                items = videoTracks,
                key = { track -> if (track.id > 0L) track.id else track.path },
                contentType = { "trialVideo" }
            ) { track ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InlineVideoPlayer(
                        url = track.path,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                }
            }
            items(
                items = audioTracks,
                key = { track -> if (track.id > 0L) track.id else track.path },
                contentType = { "trialAudioTrack" }
            ) { track ->
                TrackItem(
                    track = track,
                    onClick = { onPlayTracks(album, audioTracks, track) },
                    onAddToPlaylist = { onAddToPlaylist(track) }
                )
            }
        }
        item {
            DlsiteRecommendationsBlocks(
                recommendations = dlsiteRecommendations,
                onOpenAlbumByRj = onOpenAlbumByRj
            )
        }
    }

    if (previewUrl != null) {
        Dialog(onDismissRequest = { previewUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { previewUrl = null },
                contentAlignment = Alignment.Center
            ) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .wrapContentHeight()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = androidx.compose.ui.geometry.Offset.Zero
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    val url = previewUrl.orEmpty()
                    val model = remember(url) {
                        val headers = DlsiteAntiHotlink.headersForImageUrl(url)
                        if (headers.isEmpty()) url else CacheImageModel(data = url, headers = headers, keyTag = "dlsite")
                    }
                    AsmrAsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        placeholderCornerRadius = 16,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumDlsiteInfoBreadcrumbTab(
    album: Album,
    header: @Composable () -> Unit,
    dlsiteInfo: Album?,
    galleryUrls: List<String>,
    trialTracks: List<Track>,
    isLoading: Boolean,
    asmrOneTree: List<AsmrOneTrackNodeResponse>,
    isLoadingAsmrOne: Boolean,
    isLoadingTrial: Boolean,
    onRefreshAsmrOne: () -> Unit,
    onRefreshTrial: () -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onDownloadOne: (String) -> Unit,
    onAddToPlaylistOne: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPreviewFile: (AsmrTreeUiEntry.File) -> Unit,
    treeStateKey: String,
    initialCurrentPath: String,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    dlsiteRecommendations: DlsiteRecommendations,
    onOpenAlbumByRj: (String) -> Unit,
    loadRemoteFileSize: suspend (String) -> Long?
) {
    val infoAlbum = dlsiteInfo ?: album
    val scope = rememberCoroutineScope()
    var previewUrl by remember { mutableStateOf<String?>(null) }
    val videoTracks = remember(trialTracks) { trialTracks.filter { isVideoPreviewUrl(it.path) } }
    val audioTracks = remember(trialTracks) { trialTracks.filterNot { isVideoPreviewUrl(it.path) } }
    val asmrLeafTracks = remember(asmrOneTree) { flattenAsmrOneTracksForUi(asmrOneTree) }
    val asmrLeafByRelPath = remember(asmrLeafTracks) { asmrLeafTracks.associateBy { it.relativePath } }
    val remoteIndex = remember(asmrOneTree, album.id, album.coverPath, album.coverUrl) {
        buildRemoteTreeIndex(asmrOneTree, album)
    }
    var currentPath by rememberSaveable(treeStateKey) {
        mutableStateOf(initialCurrentPath.trim().trim('/'))
    }
    val browser = remember(remoteIndex, currentPath) {
        buildRemoteDirectoryBrowser(remoteIndex, currentPath)
    }
    val listState = rememberSaveable("scroll:$treeStateKey", saver = LazyListState.Saver) {
        LazyListState(initialScroll.first, initialScroll.second)
    }
    LaunchedEffect(listState, treeStateKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, off) -> onPersistScroll(idx, off) }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
        }
    }
    LaunchedEffect(currentPath, treeStateKey) {
        onPersistCurrentPath(currentPath)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(chromeState.nestedScrollConnection)
            .thinScrollbar(listState),
        state = listState,
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item { header() }
        if (dlsiteInfo == null && isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                }
            }
            return@LazyColumn
        }
        item {
            Text(
                text = "Gallery",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (galleryUrls.isEmpty()) {
                Text(
                    text = "暂无样图",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    items(
                        items = galleryUrls,
                        key = { it },
                        contentType = { "galleryThumb" }
                    ) { url ->
                        val model = remember(url) {
                            val headers = DlsiteAntiHotlink.headersForImageUrl(url)
                            if (headers.isEmpty()) url else CacheImageModel(data = url, headers = headers, keyTag = "dlsite")
                        }
                        Card(
                            modifier = Modifier
                                .size(width = 140.dp, height = 100.dp)
                                .clickable { previewUrl = url },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            AsmrAsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                placeholderCornerRadius = 12,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (asmrOneTree.isNotEmpty()) "ONE（已收录）" else "ONE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onRefreshAsmrOne,
                    enabled = !isLoadingAsmrOne
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }
        if (asmrOneTree.isNotEmpty()) {
            item {
                DirectoryBreadcrumbBar(
                    currentPath = currentPath,
                    breadcrumbs = browser.breadcrumbs,
                    onNavigate = { path -> currentPath = path }
                )
            }
            item {
                DirectoryBatchBar(
                    targets = browser.batchTargets,
                    onAddToFavorites = onAddMediaItemsToFavorites,
                    onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                    onAddMediaItemsToQueue = onAddMediaItemsToQueue
                )
            }
            items(
                items = browser.folders,
                key = { folder -> "asmr-folder:${folder.path}" },
                contentType = { "folder" }
            ) { folder ->
                DirectoryFolderRow(
                    title = folder.title,
                    onClick = { currentPath = folder.path }
                )
            }
            items(
                items = browser.files,
                key = { file -> "asmr-file:${file.path}" },
                contentType = { "file" }
            ) { file ->
                val leaf = asmrLeafByRelPath[file.path]
                DirectoryFileRow(
                    file = file.copy(
                        showSubtitleStamp = file.subtitleSources.isNotEmpty()
                    ),
                    loadRemoteFileSize = loadRemoteFileSize,
                    onPrimary = {
                        when (file.fileType) {
                            TreeFileType.Audio -> {
                                scope.launch {
                                    val prepared = withContext(Dispatchers.Default) {
                                        val start = asmrLeafByRelPath[file.path] ?: return@withContext null
                                        val folderPath = file.path.substringBeforeLast('/', "")
                                        val siblingLeaves = asmrLeafTracks.filter {
                                            it.relativePath.substringBeforeLast('/', "") == folderPath
                                        }
                                        val queueLeaves = siblingLeaves.ifEmpty { listOf(start) }
                                        PreparedTrackPlayback(
                                            tracks = queueLeaves.sortedBy { SmartSortKey.of(it.title) }.map { it.toTrack() },
                                            startTrack = start.toTrack(),
                                            onlineLyrics = queueLeaves.associate { it.url to it.subtitles }
                                        )
                                    } ?: return@launch
                                    com.asmr.player.util.OnlineLyricsStore.replaceAll(prepared.onlineLyrics)
                                    onPlayTracks(album, prepared.tracks, prepared.startTrack)
                                }
                            }
                            TreeFileType.Video -> {
                                val item = file.playlistTarget?.toMediaItem()
                                if (item != null) {
                                    onPlayMediaItems(listOf(item), 0)
                                } else {
                                    onPreviewFile(
                                        AsmrTreeUiEntry.File(
                                            path = file.path,
                                            title = file.title,
                                            depth = 0,
                                            fileType = file.fileType,
                                            isPlayable = false,
                                            url = file.url
                                        )
                                    )
                                }
                            }
                            else -> onPreviewFile(
                                AsmrTreeUiEntry.File(
                                    path = file.path,
                                    title = file.title,
                                    depth = 0,
                                    fileType = file.fileType,
                                    isPlayable = false,
                                    url = file.url
                                )
                            )
                        }
                    },
                    onDownload = if (file.fileType == TreeFileType.Audio || file.fileType == TreeFileType.Video) ({
                        onDownloadOne(file.path)
                    }) else null,
                    onAddToQueue = if (leaf != null) ({
                        com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                        onAddToQueue(leaf.toTrack())
                    }) else null,
                    onAddToPlaylist = if (file.fileType == TreeFileType.Audio) ({
                        onAddToPlaylistOne(file.path)
                    }) else null
                )
            }
        } else if (isLoadingAsmrOne) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("未收录")
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "试听 / 试看",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onRefreshTrial,
                    enabled = !isLoading && !isLoadingTrial
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }
        if (trialTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingTrial) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    } else {
                        Text("暂无试听")
                    }
                }
            }
        } else {
            if (isLoadingTrial) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    }
                }
            }
            items(
                items = videoTracks,
                key = { track -> if (track.id > 0L) track.id else track.path },
                contentType = { "trialVideo" }
            ) { track ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InlineVideoPlayer(
                        url = track.path,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                }
            }
            items(
                items = audioTracks,
                key = { track -> if (track.id > 0L) track.id else track.path },
                contentType = { "trialAudioTrack" }
            ) { track ->
                TrackItem(
                    track = track,
                    onClick = { onPlayTracks(album, audioTracks, track) },
                    onAddToPlaylist = { onAddToPlaylist(track) }
                )
            }
        }
        item {
            DlsiteRecommendationsBlocks(
                recommendations = dlsiteRecommendations,
                onOpenAlbumByRj = onOpenAlbumByRj
            )
        }
    }

    if (previewUrl != null) {
        Dialog(onDismissRequest = { previewUrl = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { previewUrl = null },
                contentAlignment = Alignment.Center
            ) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .wrapContentHeight()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = androidx.compose.ui.geometry.Offset.Zero
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    val url = previewUrl.orEmpty()
                    val model = remember(url) {
                        val headers = DlsiteAntiHotlink.headersForImageUrl(url)
                        if (headers.isEmpty()) url else CacheImageModel(data = url, headers = headers, keyTag = "dlsite")
                    }
                    AsmrAsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        placeholderCornerRadius = 16,
                    )
                }
            }
        }
    }
}

@Composable
private fun DlsiteGalleryLoadingRow() {
    val placeholders = remember { listOf(0, 1, 2, 3) }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp)
    ) {
        items(placeholders, key = { it }, contentType = { "galleryLoadingThumb" }) {
            AsmrShimmerPlaceholder(
                modifier = Modifier.size(width = 140.dp, height = 100.dp),
                cornerRadius = 12
            )
        }
    }
}

@Composable
private fun DlsiteSectionPlaceholderLine(
    widthFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    cornerRadius: Int = 8
) {
    AsmrShimmerPlaceholder(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        cornerRadius = cornerRadius
    )
}

@Composable
private fun DlsiteDirectoryLoadingPanel() {
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    val fixedHeight = remember(screenHeight) {
        (screenHeight * 0.48f).coerceIn(240.dp, 460.dp)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = AsmrTheme.colorScheme.surface.copy(alpha = 0.44f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DlsiteSectionPlaceholderLine(widthFraction = 0.56f, height = 16.dp)
                DlsiteSectionPlaceholderLine(widthFraction = 0.32f, height = 12.dp)
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsmrShimmerPlaceholder(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    cornerRadius = 12
                )
                AsmrShimmerPlaceholder(
                    modifier = Modifier.size(width = 92.dp, height = 34.dp),
                    cornerRadius = 16
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fixedHeight)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(5) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (index % 3 == 0) 0.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val iconSize = if (index % 3 == 0) 18.dp else 14.dp
                        AsmrShimmerPlaceholder(
                            modifier = Modifier.size(iconSize),
                            cornerRadius = 999
                        )
                        DlsiteSectionPlaceholderLine(
                            widthFraction = if (index % 3 == 0) 0.72f else 0.54f,
                            modifier = Modifier.weight(1f),
                            height = 14.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DlsiteTrialLoadingList() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) { index ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp,
                color = AsmrTheme.colorScheme.surface.copy(alpha = 0.36f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DlsiteSectionPlaceholderLine(
                        widthFraction = if (index == 0) 0.62f else 0.48f,
                        height = 15.dp
                    )
                    DlsiteSectionPlaceholderLine(
                        widthFraction = if (index == 2) 0.26f else 0.18f,
                        height = 11.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun DlsiteRecommendationsLoadingBlocks() {
    val placeholders = remember { listOf(0, 1, 2) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        placeholders.forEach { sectionIndex ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DlsiteSectionPlaceholderLine(
                    widthFraction = when (sectionIndex) {
                        0 -> 0.34f
                        1 -> 0.28f
                        else -> 0.52f
                    },
                    height = 18.dp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(listOf(0, 1, 2, 3), key = { it }, contentType = { "dlsiteRecommendationLoadingCard" }) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 1.dp,
                            color = AsmrTheme.colorScheme.surface.copy(alpha = 0.35f),
                            modifier = Modifier.width(132.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsmrShimmerPlaceholder(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    cornerRadius = 14
                                )
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DlsiteSectionPlaceholderLine(widthFraction = 0.88f, height = 12.dp)
                                    DlsiteSectionPlaceholderLine(widthFraction = 0.46f, height = 10.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDlsiteInfoBreadcrumbTabV2(
    album: Album,
    header: @Composable () -> Unit,
    dlsiteInfo: Album?,
    galleryUrls: List<String>,
    trialTracks: List<Track>,
    isLoading: Boolean,
    asmrOneTree: List<AsmrOneTrackNodeResponse>,
    isLoadingAsmrOne: Boolean,
    isLoadingTrial: Boolean,
    onRefreshAsmrOne: () -> Unit,
    onRefreshTrial: () -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onDownloadOne: (String) -> Unit,
    onAddToPlaylistOne: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPreviewFile: (AsmrTreeUiEntry.File) -> Unit,
    treeStateKey: String,
    initialCurrentPath: String,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    dlsiteRecommendations: DlsiteRecommendations,
    onOpenAlbumByRj: (String) -> Unit,
    loadRemoteFileSize: suspend (String) -> Long?
) {
    val scope = rememberCoroutineScope()
    var previewUrl by remember { mutableStateOf<String?>(null) }
    val videoTracks = remember(trialTracks) { trialTracks.filter { isVideoPreviewUrl(it.path) } }
    val audioTracks = remember(trialTracks) { trialTracks.filterNot { isVideoPreviewUrl(it.path) } }
    val asmrLeafTracks = remember(asmrOneTree) { flattenAsmrOneTracksForUi(asmrOneTree) }
    val asmrLeafByRelPath = remember(asmrLeafTracks) { asmrLeafTracks.associateBy { it.relativePath } }
    val remoteIndex = remember(asmrOneTree, album.id, album.coverPath, album.coverUrl) {
        buildRemoteTreeIndex(asmrOneTree, album)
    }
    var currentPath by rememberSaveable(treeStateKey) { mutableStateOf(initialCurrentPath.trim().trim('/')) }
    val browser = remember(remoteIndex, currentPath) { buildRemoteDirectoryBrowser(remoteIndex, currentPath) }
    val listState = rememberSaveable("scroll:$treeStateKey", saver = LazyListState.Saver) {
        LazyListState(initialScroll.first, initialScroll.second)
    }
    LaunchedEffect(listState, treeStateKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, off) -> onPersistScroll(idx, off) }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
        }
    }
    LaunchedEffect(currentPath, treeStateKey) {
        onPersistCurrentPath(currentPath)
    }
    val isInitialDlsiteLoading = dlsiteInfo == null && isLoading

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(chromeState.nestedScrollConnection)
            .thinScrollbar(listState),
        state = listState,
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item(key = "dlsite-header") { header() }
        if (isInitialDlsiteLoading) {
            item(key = "dlsite-gallery-section") {
                Column(modifier = dlsiteElasticItemModifier(Modifier.fillMaxWidth())) {
                    Text(
                        text = "Gallery",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    DlsiteGalleryLoadingRow()
                }
            }
            item(key = "dlsite-one-header") {
                Row(
                    modifier = dlsiteElasticItemModifier(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ONE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            item(key = "dlsite-one-content") {
                Box(modifier = dlsiteElasticItemModifier(Modifier.fillMaxWidth())) {
                    DlsiteDirectoryLoadingPanel()
                }
            }
            item(key = "dlsite-trial-loading") {
                Column(modifier = dlsiteElasticItemModifier(Modifier.fillMaxWidth())) {
                Row(
                    modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "试听 / 试看",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                DlsiteTrialLoadingList()
                }
            }
            item(key = "dlsite-recommendations") {
                Box(modifier = dlsiteElasticItemModifier(Modifier.fillMaxWidth())) {
                    DlsiteRecommendationsLoadingBlocks()
                }
            }
            return@LazyColumn
        }
        item(key = "dlsite-gallery-section") {
            Column(modifier = dlsiteElasticItemModifier(Modifier.fillMaxWidth())) {
            Text(
                text = "Gallery",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (galleryUrls.isEmpty()) {
                Text(
                    text = "暂无样图",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    items(items = galleryUrls, key = { it }, contentType = { "galleryThumb" }) { url ->
                        val model = remember(url) {
                            val headers = DlsiteAntiHotlink.headersForImageUrl(url)
                            if (headers.isEmpty()) url else CacheImageModel(data = url, headers = headers, keyTag = "dlsite")
                        }
                        Card(
                            modifier = Modifier.size(width = 140.dp, height = 100.dp).clickable { previewUrl = url },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            AsmrAsyncImage(
                                model = model,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                placeholderCornerRadius = 12,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            }
        }
        item(key = "dlsite-one-header") {
            Row(
                modifier = dlsiteElasticItemModifier(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (asmrOneTree.isNotEmpty()) "ONE（已收录）" else "ONE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRefreshAsmrOne, enabled = !isLoadingAsmrOne) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }
        if (asmrOneTree.isNotEmpty()) {
            item(key = "dlsite-one-content") {
                Box(modifier = dlsiteElasticItemModifier(Modifier.fillMaxWidth())) {
                    DirectoryBrowserPanelV4(
                    panelKey = treeStateKey,
                    currentPath = currentPath,
                    breadcrumbs = browser.breadcrumbs,
                    batchTargets = browser.batchTargets,
                    folders = browser.folders,
                    files = browser.files,
                    onNavigate = { path -> currentPath = path },
                    onAddToFavorites = onAddMediaItemsToFavorites,
                    onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                    onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                    folderKeyPrefix = "asmr-folder",
                    fileKeyPrefix = "asmr-file",
                    fileContent = { file, selectionMode, selected, enterSelectionMode, onSelectedChange ->
                        val leaf = asmrLeafByRelPath[file.path]
                        DirectoryFileRow(
                            file = file.copy(showSubtitleStamp = file.subtitleSources.isNotEmpty()),
                            loadRemoteFileSize = loadRemoteFileSize,
                            onPrimary = {
                                when (file.fileType) {
                                    TreeFileType.Audio -> {
                                        scope.launch {
                                            val prepared = withContext(Dispatchers.Default) {
                                                val start = asmrLeafByRelPath[file.path] ?: return@withContext null
                                                val folderPath = file.path.substringBeforeLast('/', "")
                                                val siblingLeaves = asmrLeafTracks.filter {
                                                    it.relativePath.substringBeforeLast('/', "") == folderPath
                                                }
                                                val queueLeaves = siblingLeaves.ifEmpty { listOf(start) }
                                                PreparedTrackPlayback(
                                                    tracks = queueLeaves.sortedBy { SmartSortKey.of(it.title) }.map { it.toTrack() },
                                                    startTrack = start.toTrack(),
                                                    onlineLyrics = queueLeaves.associate { it.url to it.subtitles }
                                                )
                                            } ?: return@launch
                                            com.asmr.player.util.OnlineLyricsStore.replaceAll(prepared.onlineLyrics)
                                            onPlayTracks(album, prepared.tracks, prepared.startTrack)
                                        }
                                    }
                                    TreeFileType.Video -> {
                                        val item = file.playlistTarget?.toMediaItem()
                                        if (item != null) {
                                            onPlayMediaItems(listOf(item), 0)
                                        } else {
                                            onPreviewFile(
                                                AsmrTreeUiEntry.File(
                                                    path = file.path,
                                                    title = file.title,
                                                    depth = 0,
                                                    fileType = file.fileType,
                                                    isPlayable = false,
                                                    url = file.url
                                                )
                                            )
                                        }
                                    }
                                    else -> onPreviewFile(
                                        AsmrTreeUiEntry.File(
                                            path = file.path,
                                            title = file.title,
                                            depth = 0,
                                            fileType = file.fileType,
                                            isPlayable = false,
                                            url = file.url
                                        )
                                    )
                                }
                            },
                            selectionMode = selectionMode,
                            selected = selected,
                            onEnterSelectionMode = enterSelectionMode,
                            onSelectedChange = onSelectedChange,
                            onDownload = if (file.fileType == TreeFileType.Audio || file.fileType == TreeFileType.Video) ({ onDownloadOne(file.path) }) else null,
                            onAddToQueue = if (leaf != null) ({
                                com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                                onAddToQueue(leaf.toTrack())
                            }) else null,
                            onAddToPlaylist = if (file.fileType == TreeFileType.Audio) ({ onAddToPlaylistOne(file.path) }) else null
                        )
                    }
                    )
                }
            }
        } else if (isLoadingAsmrOne) {
            item(key = "dlsite-one-content") {
                Box(modifier = dlsiteElasticItemModifier(Modifier.fillMaxWidth())) {
                    DlsiteDirectoryLoadingPanel()
                }
            }
        } else {
            item(key = "dlsite-one-content") {
                Box(
                    modifier = dlsiteElasticItemModifier(
                        Modifier.fillMaxWidth().height(120.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("未收录")
                }
            }
        }
        item(key = "dlsite-trial-header") {
            Row(
                modifier = dlsiteElasticItemModifier(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "试听 / 试看",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRefreshTrial, enabled = !isLoading && !isLoadingTrial) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        }
        if (trialTracks.isEmpty()) {
            item(key = "dlsite-trial-content") {
                Box(
                    modifier = dlsiteElasticItemModifier(Modifier.fillMaxWidth()),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingTrial) {
                        DlsiteTrialLoadingList()
                    } else {
                        Text("暂无试听")
                    }
                }
            }
        } else {
            if (isLoadingTrial) {
                item(key = "dlsite-trial-progress") {
                    LinearProgressIndicator(
                        modifier = dlsiteElasticItemModifier(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    )
                }
            }
            items(items = videoTracks, key = { track -> if (track.id > 0L) track.id else track.path }, contentType = { "trialVideo" }) { track ->
                Column(
                    modifier = dlsiteElasticItemModifier(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    InlineVideoPlayer(
                        url = track.path,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    )
                }
            }
            items(items = audioTracks, key = { track -> if (track.id > 0L) track.id else track.path }, contentType = { "trialAudioTrack" }) { track ->
                Box(modifier = dlsiteElasticItemModifier(Modifier.fillMaxWidth())) {
                    TrackItem(
                        track = track,
                        onClick = { onPlayTracks(album, audioTracks, track) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )
                }
            }
        }
        item(key = "dlsite-recommendations") {
            Box(modifier = dlsiteElasticItemModifier(Modifier.fillMaxWidth())) {
                DlsiteRecommendationsBlocks(
                    recommendations = dlsiteRecommendations,
                    onOpenAlbumByRj = onOpenAlbumByRj
                )
            }
        }
    }

    if (previewUrl != null) {
        Dialog(onDismissRequest = { previewUrl = null }) {
            Box(
                modifier = Modifier.fillMaxSize().clickable { previewUrl = null },
                contentAlignment = Alignment.Center
            ) {
                var scale by remember { mutableStateOf(1f) }
                var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .wrapContentHeight()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) offset += pan else offset = androidx.compose.ui.geometry.Offset.Zero
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    val url = previewUrl.orEmpty()
                    val model = remember(url) {
                        val headers = DlsiteAntiHotlink.headersForImageUrl(url)
                        if (headers.isEmpty()) url else CacheImageModel(data = url, headers = headers, keyTag = "dlsite")
                    }
                    AsmrAsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        placeholderCornerRadius = 16
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumDlsitePlayBreadcrumbTab(
    header: @Composable () -> Unit,
    album: Album,
    rjCode: String,
    tree: List<AsmrOneTrackNodeResponse>,
    isLoading: Boolean,
    onOpenLogin: () -> Unit,
    onEnsureLoaded: () -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onPlayVideo: (String, String, String, String) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onDownloadOne: (String) -> Unit,
    onPreviewFile: (AsmrTreeUiEntry.File) -> Unit,
    treeStateKey: String,
    initialCurrentPath: String,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    loadRemoteFileSize: suspend (String) -> Long?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val authStore = remember { DlsiteAuthStore(context) }
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(authStore.isLoggedIn()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loggedIn = authStore.isLoggedIn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!loggedIn) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("需要先登录 DLsite 才能使用已购播放/下载")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpenLogin) { Text("去登录") }
            }
        }
        return
    }

    LaunchedEffect(loggedIn, rjCode) {
        if (loggedIn) onEnsureLoaded()
    }

    val headerItemCount = 4
    val restoredIndex = if (initialScroll.first <= 0) 0 else initialScroll.first + headerItemCount
    val listState = rememberSaveable("scroll:$treeStateKey", saver = LazyListState.Saver) {
        LazyListState(restoredIndex, initialScroll.second)
    }
    LaunchedEffect(listState, treeStateKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, off) ->
                val persistedIndex = (idx - headerItemCount).coerceAtLeast(0)
                onPersistScroll(persistedIndex, off)
            }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
        }
    }

    val rj = rjCode.trim().uppercase()
    val leafTracks = remember(tree) { flattenAsmrOneTracksForUi(tree) }
    val leafByRelPath = remember(leafTracks) { leafTracks.associateBy { it.relativePath } }
    val remoteIndex = remember(tree, album.id, album.coverPath, album.coverUrl) {
        buildRemoteTreeIndex(tree, album)
    }
    var currentPath by rememberSaveable(treeStateKey) {
        mutableStateOf(initialCurrentPath.trim().trim('/'))
    }
    val browser = remember(remoteIndex, currentPath) {
        buildRemoteDirectoryBrowser(remoteIndex, currentPath)
    }
    LaunchedEffect(currentPath, treeStateKey) {
        onPersistCurrentPath(currentPath)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(chromeState.nestedScrollConnection)
            .thinScrollbar(listState),
        state = listState,
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item { header() }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已购内容",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onOpenLogin) { Text("登录 / 切换账号") }
            }
        }

        if (rj.isBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("缺少 RJ 编号，无法加载")
                }
            }
            return@LazyColumn
        }

        if (tree.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    } else {
                        Text("暂无可播放资源")
                    }
                }
            }
            return@LazyColumn
        }

        item {
            DirectoryBreadcrumbBar(
                currentPath = currentPath,
                breadcrumbs = browser.breadcrumbs,
                onNavigate = { path -> currentPath = path }
            )
        }
        item {
            DirectoryBatchBar(
                targets = browser.batchTargets,
                onAddToFavorites = onAddMediaItemsToFavorites,
                onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                onAddMediaItemsToQueue = onAddMediaItemsToQueue
            )
        }
        items(
            items = browser.folders,
            key = { folder -> "dlplay-folder:${folder.path}" },
            contentType = { "folder" }
        ) { folder ->
            DirectoryFolderRow(
                title = folder.title,
                onClick = { currentPath = folder.path }
            )
        }
        items(
            items = browser.files,
            key = { file -> "dlplay-file:${file.path}" },
            contentType = { "file" }
        ) { file ->
            val leaf = leafByRelPath[file.path]
            DirectoryFileRow(
                file = file.copy(
                    showSubtitleStamp = file.subtitleSources.isNotEmpty()
                ),
                loadRemoteFileSize = loadRemoteFileSize,
                onPrimary = {
                    when (file.fileType) {
                        TreeFileType.Audio, TreeFileType.Video -> {
                            scope.launch {
                                val prepared = withContext(Dispatchers.Default) {
                                    val folderPath = file.path.substringBeforeLast('/', "")
                                    val siblings = browser.files
                                        .filter { sibling ->
                                            sibling.path.substringBeforeLast('/', "") == folderPath &&
                                                (sibling.fileType == TreeFileType.Audio || sibling.fileType == TreeFileType.Video) &&
                                                sibling.playlistTarget != null
                                        }
                                        .sortedBy { SmartSortKey.of(it.title) }
                                    val items = siblings.mapNotNull { it.playlistTarget?.toMediaItem() }
                                    if (items.isEmpty()) return@withContext null
                                    val clickedId = file.playlistTarget?.mediaId.orEmpty()
                                    val startIndex = items.indexOfFirst { it.mediaId == clickedId }
                                        .takeIf { it >= 0 } ?: 0
                                    PreparedMediaPlayback(items, startIndex)
                                }
                                if (prepared != null) {
                                    if (leaf != null) {
                                        com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                                    }
                                    onPlayMediaItems(prepared.items, prepared.startIndex)
                                } else {
                                    onPreviewFile(
                                        AsmrTreeUiEntry.File(
                                            path = file.path,
                                            title = file.title,
                                            depth = 0,
                                            fileType = file.fileType,
                                            isPlayable = false,
                                            url = file.url
                                        )
                                    )
                                }
                            }
                        }
                        else -> onPreviewFile(
                            AsmrTreeUiEntry.File(
                                path = file.path,
                                title = file.title,
                                depth = 0,
                                fileType = file.fileType,
                                isPlayable = false,
                                url = file.url
                            )
                        )
                    }
                },
                onDownload = if (file.fileType == TreeFileType.Audio || file.fileType == TreeFileType.Video) ({
                    onDownloadOne(file.path)
                }) else null,
                onAddToQueue = if (leaf != null) ({
                    com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                    onAddToQueue(leaf.toTrack())
                }) else null,
                onAddToPlaylist = null
            )
        }
    }
}

@Composable
private fun AlbumDlsitePlayBreadcrumbTabV2(
    header: @Composable () -> Unit,
    album: Album,
    rjCode: String,
    tree: List<AsmrOneTrackNodeResponse>,
    isLoading: Boolean,
    onOpenLogin: () -> Unit,
    onEnsureLoaded: () -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onPlayVideo: (String, String, String, String) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onAddMediaItemsToQueue: (List<MediaItem>) -> Unit,
    onAddMediaItemsToFavorites: (List<MediaItem>) -> Unit,
    onOpenBatchPlaylistPicker: (List<MediaItem>) -> Unit,
    onDownloadOne: (String) -> Unit,
    onPreviewFile: (AsmrTreeUiEntry.File) -> Unit,
    treeStateKey: String,
    initialCurrentPath: String,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
    onPersistCurrentPath: (String) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
    loadRemoteFileSize: suspend (String) -> Long?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val authStore = remember { DlsiteAuthStore(context) }
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(authStore.isLoggedIn()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loggedIn = authStore.isLoggedIn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!loggedIn) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("需要先登录 DLsite 才能使用已购播放/下载")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpenLogin) { Text("去登录") }
            }
        }
        return
    }

    LaunchedEffect(loggedIn, rjCode) {
        if (loggedIn) onEnsureLoaded()
    }

    val headerItemCount = 2
    val restoredIndex = if (initialScroll.first <= 0) 0 else initialScroll.first + headerItemCount
    val listState = rememberSaveable("scroll:$treeStateKey", saver = LazyListState.Saver) {
        LazyListState(restoredIndex, initialScroll.second)
    }
    LaunchedEffect(listState, treeStateKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, off) ->
                val persistedIndex = (idx - headerItemCount).coerceAtLeast(0)
                onPersistScroll(persistedIndex, off)
            }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
        }
    }

    val rj = rjCode.trim().uppercase()
    val leafTracks = remember(tree) { flattenAsmrOneTracksForUi(tree) }
    val leafByRelPath = remember(leafTracks) { leafTracks.associateBy { it.relativePath } }
    val remoteIndex = remember(tree, album.id, album.coverPath, album.coverUrl) {
        buildRemoteTreeIndex(tree, album)
    }
    var currentPath by rememberSaveable(treeStateKey) { mutableStateOf(initialCurrentPath.trim().trim('/')) }
    val browser = remember(remoteIndex, currentPath) { buildRemoteDirectoryBrowser(remoteIndex, currentPath) }
    LaunchedEffect(currentPath, treeStateKey) {
        onPersistCurrentPath(currentPath)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(chromeState.nestedScrollConnection)
            .thinScrollbar(listState),
        state = listState,
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item { header() }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已购内容",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onOpenLogin) { Text("登录 / 切换账号") }
            }
        }

        if (rj.isBlank()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    Text("缺少 RJ 编号，无法加载")
                }
            }
            return@LazyColumn
        }

        if (tree.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    } else {
                        Text("暂无可播放资源")
                    }
                }
            }
            return@LazyColumn
        }

        item {
            DirectoryBrowserPanelV4(
                panelKey = treeStateKey,
                currentPath = currentPath,
                breadcrumbs = browser.breadcrumbs,
                batchTargets = browser.batchTargets,
                folders = browser.folders,
                files = browser.files,
                onNavigate = { path -> currentPath = path },
                onAddToFavorites = onAddMediaItemsToFavorites,
                onOpenBatchPlaylistPicker = onOpenBatchPlaylistPicker,
                onAddMediaItemsToQueue = onAddMediaItemsToQueue,
                folderKeyPrefix = "dlplay-folder",
                fileKeyPrefix = "dlplay-file",
                fileContent = { file, selectionMode, selected, enterSelectionMode, onSelectedChange ->
                    val leaf = leafByRelPath[file.path]
                    DirectoryFileRow(
                        file = file.copy(showSubtitleStamp = file.subtitleSources.isNotEmpty()),
                        loadRemoteFileSize = loadRemoteFileSize,
                        onPrimary = {
                            when (file.fileType) {
                                TreeFileType.Audio, TreeFileType.Video -> {
                                    scope.launch {
                                        val prepared = withContext(Dispatchers.Default) {
                                            val folderPath = file.path.substringBeforeLast('/', "")
                                            val siblings = browser.files
                                                .filter { sibling ->
                                                    sibling.path.substringBeforeLast('/', "") == folderPath &&
                                                        (sibling.fileType == TreeFileType.Audio || sibling.fileType == TreeFileType.Video) &&
                                                        sibling.playlistTarget != null
                                                }
                                                .sortedBy { SmartSortKey.of(it.title) }
                                            val items = siblings.mapNotNull { it.playlistTarget?.toMediaItem() }
                                            if (items.isEmpty()) return@withContext null
                                            val clickedId = file.playlistTarget?.mediaId.orEmpty()
                                            val startIndex = items.indexOfFirst { it.mediaId == clickedId }
                                                .takeIf { it >= 0 } ?: 0
                                            PreparedMediaPlayback(items, startIndex)
                                        }
                                        if (prepared != null) {
                                            if (leaf != null) {
                                                com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                                            }
                                            onPlayMediaItems(prepared.items, prepared.startIndex)
                                        } else {
                                            onPreviewFile(
                                                AsmrTreeUiEntry.File(
                                                    path = file.path,
                                                    title = file.title,
                                                    depth = 0,
                                                    fileType = file.fileType,
                                                    isPlayable = false,
                                                    url = file.url
                                                )
                                            )
                                        }
                                    }
                                }
                                else -> onPreviewFile(
                                    AsmrTreeUiEntry.File(
                                        path = file.path,
                                        title = file.title,
                                        depth = 0,
                                        fileType = file.fileType,
                                        isPlayable = false,
                                        url = file.url
                                    )
                                )
                            }
                        },
                        selectionMode = selectionMode,
                        selected = selected,
                        onEnterSelectionMode = enterSelectionMode,
                        onSelectedChange = onSelectedChange,
                        onDownload = if (file.fileType == TreeFileType.Audio || file.fileType == TreeFileType.Video) ({ onDownloadOne(file.path) }) else null,
                        onAddToQueue = if (leaf != null) ({
                            com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                            onAddToQueue(leaf.toTrack())
                        }) else null,
                        onAddToPlaylist = null
                    )
                }
            )
        }
    }
}

@Composable
private fun AlbumDlsitePlayTreeTab(
    header: @Composable () -> Unit,
    album: Album,
    rjCode: String,
    tree: List<AsmrOneTrackNodeResponse>,
    isLoading: Boolean,
    onOpenLogin: () -> Unit,
    onEnsureLoaded: () -> Unit,
    onPlayTracks: (Album, List<Track>, Track) -> Unit,
    onPlayMediaItems: (List<MediaItem>, Int) -> Unit,
    onPlayVideo: (String, String, String, String) -> Unit,
    onAddToQueue: (Track) -> Boolean,
    onDownloadOne: (String) -> Unit,
    onPreviewFile: (AsmrTreeUiEntry.File) -> Unit,
    treeStateKey: String,
    treeInitialExpanded: List<String>,
    treeWasInitialized: Boolean,
    topContentPadding: Dp,
    chromeState: com.asmr.player.ui.common.CollapsibleHeaderState,
    onPersistTreeState: (List<String>) -> Unit,
    initialScroll: Pair<Int, Int>,
    onPersistScroll: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val authStore = remember { DlsiteAuthStore(context) }
    val scope = rememberCoroutineScope()
    var loggedIn by remember { mutableStateOf(authStore.isLoggedIn()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loggedIn = authStore.isLoggedIn()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!loggedIn) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("需要先登录 DLsite 才能使用已购播放/下载")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpenLogin) { Text("去登录") }
            }
        }
        return
    }
    
    LaunchedEffect(loggedIn, rjCode) {
        if (loggedIn) onEnsureLoaded()
    }

    val headerItemCount = 2
    val restoredIndex = if (initialScroll.first <= 0) 0 else initialScroll.first + headerItemCount
    val listState = rememberSaveable("scroll:$treeStateKey", saver = LazyListState.Saver) {
        LazyListState(restoredIndex, initialScroll.second)
    }
    LaunchedEffect(listState, treeStateKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, off) ->
                val persistedIndex = (idx - headerItemCount).coerceAtLeast(0)
                onPersistScroll(persistedIndex, off)
            }
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            chromeState.expand()
        }
    }

    val rj = rjCode.trim().uppercase()
    val leafTracks = remember(tree) { flattenAsmrOneTracksForUi(tree) }
    val leafByRelPath = remember(leafTracks) { leafTracks.associateBy { it.relativePath } }
    val expanded = remember(treeStateKey) {
        mutableStateListOf<String>().apply { addAll(treeInitialExpanded) }
    }
    var initialized by remember(treeStateKey) {
        mutableStateOf(treeWasInitialized || treeInitialExpanded.isNotEmpty())
    }
    val treeResult = remember(tree, expanded.toList()) { flattenAsmrOneTreeForUi(tree, expanded.toSet()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(chromeState.nestedScrollConnection)
            .thinScrollbar(listState),
        state = listState,
        contentPadding = PaddingValues(top = topContentPadding, bottom = LocalBottomOverlayPadding.current)
    ) {
        item { header() }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已购内容",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onOpenLogin) { Text("登录/切换账号") }
            }
        }

        if (rj.isBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("缺少 RJ 编号，无法加载")
                }
            }
            return@LazyColumn
        }

        if (tree.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        EaraLogoLoadingIndicator(tint = AsmrTheme.colorScheme.primary)
                    } else {
                        Text("暂无可播放资源")
                    }
                }
            }
            return@LazyColumn
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    treeResult.entries.forEachIndexed { index, entry ->
                        when (entry) {
                            is AsmrTreeUiEntry.Folder -> {
                                TreeFolderRow(
                                    title = entry.title,
                                    depth = entry.depth,
                                    expanded = expanded.contains(entry.path),
                                    onToggle = {
                                        if (expanded.contains(entry.path)) expanded.remove(entry.path) else expanded.add(entry.path)
                                        initialized = true
                                        onPersistTreeState(expanded.toList())
                                    }
                                )
                            }
                            is AsmrTreeUiEntry.File -> {
                                val canPlay = entry.fileType == TreeFileType.Audio && leafByRelPath.containsKey(entry.path)
                                val canPlayVideo = entry.fileType == TreeFileType.Video && !entry.url.isNullOrBlank()
                                TreeFileRow(
                                    title = entry.title,
                                    depth = entry.depth,
                                    fileType = entry.fileType,
                                    isPlayable = canPlay || canPlayVideo,
                                    showSubtitleStamp = canPlay && (leafByRelPath[entry.path]?.subtitles?.isNotEmpty() == true),
                                    onPrimary = {
                                        if (!canPlay && !canPlayVideo) {
                                            onPreviewFile(entry)
                                            return@TreeFileRow
                                        }
                                        scope.launch {
                                            val prepared = withContext(Dispatchers.Default) {
                                                val artwork = album.coverPath.ifBlank { album.coverUrl }
                                                val artist = album.cv.ifBlank { album.circle }
                                                val folderPath = entry.path.substringBeforeLast('/', "")
                                                val siblings = treeResult.entries
                                                    .asSequence()
                                                    .filterIsInstance<AsmrTreeUiEntry.File>()
                                                    .filter { it.path.substringBeforeLast('/', "") == folderPath }
                                                    .filter { file ->
                                                        val audioOk = file.fileType == TreeFileType.Audio &&
                                                            leafByRelPath.containsKey(file.path)
                                                        val videoOk = file.fileType == TreeFileType.Video &&
                                                            !file.url.isNullOrBlank()
                                                        audioOk || videoOk
                                                    }
                                                    .sortedBy { SmartSortKey.of(it.title) }
                                                    .toList()

                                                val paired = siblings.mapNotNull { file ->
                                                    when (file.fileType) {
                                                        TreeFileType.Audio -> {
                                                            val leaf = leafByRelPath[file.path] ?: return@mapNotNull null
                                                            val track = leaf.toTrack()
                                                            val id = track.path.trim()
                                                            id to MediaItemFactory.fromTrack(album, track)
                                                        }
                                                        TreeFileType.Video -> {
                                                            val url = file.url?.trim().orEmpty()
                                                            if (url.isBlank()) return@mapNotNull null
                                                            url to buildVideoMediaItem(
                                                                title = file.title,
                                                                uriOrPath = url,
                                                                artworkUri = artwork,
                                                                artist = artist
                                                            )
                                                        }
                                                        else -> null
                                                    }
                                                }.mapNotNull { (id, item) -> item?.let { id to it } }

                                                if (paired.isEmpty()) return@withContext null

                                                val clickedId = if (canPlay) {
                                                    leafByRelPath[entry.path]?.url?.trim().orEmpty()
                                                } else {
                                                    entry.url?.trim().orEmpty()
                                                }
                                                val startIndex = paired.indexOfFirst { (id, _) -> id == clickedId }
                                                    .takeIf { it >= 0 } ?: 0
                                                PreparedMediaPlayback(
                                                    items = paired.map { it.second },
                                                    startIndex = startIndex
                                                )
                                            }
                                            if (prepared != null) {
                                                onPlayMediaItems(prepared.items, prepared.startIndex)
                                            } else {
                                                onPreviewFile(entry)
                                            }
                                        }
                                    },
                                    onDownload = if (entry.fileType == TreeFileType.Audio || entry.fileType == TreeFileType.Video) {
                                        ({ onDownloadOne(entry.path) })
                                    } else null,
                                    onAddToQueue = if (canPlay) ({
                                        val leaf = leafByRelPath[entry.path]
                                        if (leaf != null) {
                                            com.asmr.player.util.OnlineLyricsStore.set(leaf.url, leaf.subtitles)
                                            onAddToQueue(leaf.toTrack())
                                        }
                                    }) else null,
                                    onAddToPlaylist = null
                                )
                            }
                        }
                        if (index < treeResult.entries.size - 1) {
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun InlineVideoPlayer(
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

private fun isVideoPreviewUrl(url: String): Boolean {
    val u = url.substringBefore('#').substringBefore('?').lowercase()
    return u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".webm") || u.endsWith(".m3u8")
}

private data class PlaylistAddTarget(
    val mediaId: String,
    val uri: String,
    val title: String,
    val artist: String,
    val artworkUri: String,
    val albumTitle: String = "",
    val albumId: Long = 0L,
    val trackId: Long = 0L,
    val rjCode: String = "",
    val mimeType: String? = null,
    val isVideo: Boolean = false
) {
    fun toMediaItem(): MediaItem {
        return MediaItemFactory.fromDetails(
            mediaId = mediaId,
            uri = uri,
            title = title,
            artist = artist,
            albumTitle = albumTitle,
            artworkUri = artworkUri,
            albumId = albumId,
            trackId = trackId,
            rjCode = rjCode,
            mimeType = mimeType,
            isVideo = isVideo
        )
    }

    companion object {
        fun fromTrack(album: Album, track: Track): PlaylistAddTarget {
            val rj = album.rjCode.ifBlank { album.workId }
            val artist = albumArtistLabel(album).ifBlank { rj }
            val artwork = albumArtworkLabel(album)
            val title = track.title.ifBlank { track.path.substringAfterLast('/').substringAfterLast('\\') }
            return PlaylistAddTarget(
                mediaId = track.path,
                uri = track.path,
                title = title,
                artist = artist.orEmpty(),
                artworkUri = artwork,
                albumTitle = album.title,
                albumId = album.id,
                trackId = track.id,
                rjCode = rj
            )
        }

        fun fromVideo(
            album: Album,
            title: String,
            uriOrPath: String
        ): PlaylistAddTarget? {
            val trimmed = uriOrPath.trim()
            if (trimmed.isBlank()) return null
            return PlaylistAddTarget(
                mediaId = trimmed,
                uri = trimmed,
                title = title.ifBlank { trimmed.substringAfterLast('/').substringAfterLast('\\') },
                artist = albumArtistLabel(album),
                artworkUri = albumArtworkLabel(album),
                albumTitle = album.title,
                albumId = album.id,
                rjCode = album.rjCode.ifBlank { album.workId },
                mimeType = MediaItemFactory.guessMimeType(trimmed),
                isVideo = true
            )
        }

        fun fromAsmrOne(album: Album, tree: List<AsmrOneTrackNodeResponse>, relativePath: String): PlaylistAddTarget? {
            val leaf = flattenAsmrOneTracksForUi(tree).firstOrNull { it.relativePath == relativePath } ?: return null
            return fromTrack(album, leaf.toTrack())
        }
    }
}

@Composable
private fun FilePreviewDialog(
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
            if (fileType != TreeFileType.Image && fileType != TreeFileType.Video) {
                return@withContext listOf(absolutePath)
            }
            val current = File(absolutePath)
            val parent = current.parentFile ?: return@withContext listOf(absolutePath)
            val exts = when (fileType) {
                TreeFileType.Image -> setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
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
    val currentName = remember(currentPath) { currentPath.substringAfterLast('/').substringAfterLast('\\') }
    val currentType = remember(currentName) { treeFileTypeForName(currentName) }
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
                    } else if (computedSize != null && (currentType == TreeFileType.Image || currentType == TreeFileType.Video)) {
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
                            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "上一项")
                        }
                        IconButton(onClick = { currentIndex = (currentIndex + 1) % initialCandidates.size }) {
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "下一项")
                        }
                    }
                    if (canFullscreen) {
                        TextButton(onClick = { fullscreen = !fullscreen }) {
                            Text(if (fullscreen) "退出全屏" else "全屏")
                        }
                    }
                    IconButton(onClick = ::openWithOtherApp) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "打开")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
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
                        TreeFileType.Image -> {
                            var scale by remember { mutableStateOf(1f) }
                            var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                            AsmrAsyncImage(
                                model = currentPath,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 5f)
                                            if (scale > 1f) {
                                                offset += pan
                                            } else {
                                                offset = androidx.compose.ui.geometry.Offset.Zero
                                            }
                                        }
                                    }
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offset.x,
                                        translationY = offset.y
                                    ),
                                placeholderCornerRadius = 0,
                            )
                        }
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
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
                                    imageVector = Icons.Default.PictureAsPdf,
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
