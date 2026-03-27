package com.asmr.player.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import kotlinx.coroutines.delay
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
import java.util.UUID

private enum class AlbumPrimaryAction {
    Download,
    Save
}

private enum class OnlineDownloadSource {
    AsmrOne,
    DlsitePlay
}

internal data class PreparedTrackPlayback(
    val tracks: List<Track>,
    val startTrack: Track,
    val onlineLyrics: Map<String, List<RemoteSubtitleSource>> = emptyMap()
)

internal data class PreparedMediaPlayback(
    val items: List<MediaItem>,
    val startIndex: Int
)

private val AlbumDetailTabContentGap = 12.dp
private val AlbumDetailTabCollapseOvershoot = 10.dp
private const val AlbumDetailInitialIntroDurationMs = 1200L

private val DlsiteElasticResizeSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow
)

internal fun dlsiteElasticItemModifier(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
): Modifier {
    return if (enabled) {
        modifier.animateContentSize(animationSpec = DlsiteElasticResizeSpring)
    } else {
        modifier
    }
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
    val introSessionKey = remember(screenKey) { "intro:${UUID.randomUUID()}" }
    var selectedTab by rememberSaveable(screenKey) {
        mutableIntStateOf(if (albumId != null && albumId > 0) 0 else 1)
    }
    var lastChromeResetTab by rememberSaveable(screenKey) {
        mutableIntStateOf(selectedTab)
    }
    var userSelectedTab by remember(screenKey) { mutableStateOf(false) }
    var initialIntroSettled by remember(screenKey) { mutableStateOf(false) }
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
                    LaunchedEffect(screenKey, initialIntroSettled) {
                        if (initialIntroSettled) return@LaunchedEffect
                        delay(AlbumDetailInitialIntroDurationMs)
                        initialIntroSettled = true
                    }
                    val model = state.model
                    val album = model.displayAlbum
                    val asmrOneTree = model.asmrOneTree
                    val shouldPlayInitialAnimations = !initialIntroSettled && !userSelectedTab
                    val shouldAnimateHeaderIntro = !userSelectedTab
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
                                introSessionKey = introSessionKey,
                                animateIntro = shouldAnimateHeaderIntro,
                                messageManager = viewModel.messageManager
                        )
                    }
                    
                    val tabTitles = remember { listOf("本地", "DL", "DL Play") }
                    LaunchedEffect(
                        selectedTab,
                        model.rjCode,
                        model.dlsiteWorkno,
                        model.hasResolvedInitialDlsiteTarget
                    ) {
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
                                    if (!shouldPlayInitialAnimations) {
                                        EnterTransition.None.togetherWith(ExitTransition.None)
                                    } else if (targetState > initialState) {
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
                                                onPreviewFile = { localPreviewFile = it },
                                                animateIntro = shouldPlayInitialAnimations
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
                                        animateIntro = shouldPlayInitialAnimations,
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
                                        animateIntro = shouldPlayInitialAnimations,
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
    introSessionKey: String,
    animateIntro: Boolean,
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
    val normalizedTitle = album.title.trim()
    val isPlaceholderTitle = normalizedTitle.isBlank() ||
        (normalizedTitle.equals(rj, ignoreCase = true) && album.id <= 0L && album.path.isBlank())
    val headerAnimationScopeKey = remember(introSessionKey) { "albumHeader:$introSessionKey" }
    var headerIntroPlayed by rememberSaveable(headerAnimationScopeKey) { mutableStateOf(false) }
    LaunchedEffect(headerAnimationScopeKey, animateIntro) {
        if (headerIntroPlayed) return@LaunchedEffect
        if (!animateIntro) {
            headerIntroPlayed = true
            return@LaunchedEffect
        }
        delay(700)
        headerIntroPlayed = true
    }
    fun copy(label: String, value: String) {
        val v = value.trim()
        if (v.isBlank()) return
        clipboard.setText(AnnotatedString(v))
        messageManager.showSuccess("$label 已复制")
    }

    val headerContainerModifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(colorScheme.surface.copy(alpha = 0.5f))

    Column(
        modifier = dlsiteElasticItemModifier(
            modifier = headerContainerModifier,
            enabled = animateIntro && !headerIntroPlayed
        )
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
                    AlbumHeaderInfoReveal(
                        revealKey = "$headerAnimationScopeKey:title",
                        delayMillis = 0,
                        enabled = animateIntro,
                        ready = !isPlaceholderTitle
                    ) {
                    Text(
                        text = album.title,
                        modifier = Modifier.clickable { copy("标题", album.title) },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    }
                    val circle = album.circle.trim()
                    if (rj.isNotBlank() || circle.isNotBlank()) {
                        AlbumHeaderInfoReveal(
                            revealKey = "$headerAnimationScopeKey:meta",
                            delayMillis = 40,
                            enabled = animateIntro
                        ) {
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
                }
            }

            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (album.cv.isNotBlank()) {
                    AlbumHeaderInfoReveal(
                        revealKey = "$headerAnimationScopeKey:cv",
                        delayMillis = 80,
                        enabled = animateIntro
                    ) {
                        CvChipsFlow(
                            cvText = album.cv,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            onCvClick = { cv -> copy("CV", cv) },
                        )
                    }
                }

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
                    AlbumHeaderInfoReveal(
                        revealKey = "$headerAnimationScopeKey:tags",
                        delayMillis = 120,
                        enabled = animateIntro
                    ) {
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
                }

                AlbumHeaderInfoReveal(
                    revealKey = "$headerAnimationScopeKey:actions",
                    delayMillis = 160,
                    enabled = animateIntro
                ) {
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
}

@Composable
private fun AlbumHeaderInfoReveal(
    revealKey: String,
    delayMillis: Int = 0,
    enabled: Boolean = true,
    ready: Boolean = true,
    content: @Composable () -> Unit
) {
    var hasPlayed by rememberSaveable(revealKey) { mutableStateOf(false) }
    LaunchedEffect(revealKey, enabled, ready) {
        if (!enabled && !hasPlayed) {
            hasPlayed = true
        }
    }
    if (!enabled || hasPlayed) {
        content()
        return
    }
    if (!ready) {
        content()
        return
    }
    var visible by remember(revealKey) { mutableStateOf(false) }
    LaunchedEffect(revealKey, ready, enabled) {
        visible = false
        if (delayMillis > 0) {
            delay(delayMillis.toLong())
        }
        withFrameNanos { }
        visible = true
        delay(420)
        hasPlayed = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "albumHeaderInfoAlpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 10.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "albumHeaderInfoOffsetY"
    )
    val density = LocalDensity.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 220)) + expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            ),
            expandFrom = Alignment.Top
        ),
        exit = fadeOut(animationSpec = tween(durationMillis = 120)) + shrinkVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            shrinkTowards = Alignment.Top
        )
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha
                translationY = with(density) { offsetY.toPx() }
            }
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
internal fun isVideoPreviewUrl(url: String): Boolean {
    val u = url.substringBefore('#').substringBefore('?').lowercase()
    return u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".webm") || u.endsWith(".m3u8")
}

internal data class PlaylistAddTarget(
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
