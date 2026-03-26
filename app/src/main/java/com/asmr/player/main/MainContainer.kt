package com.asmr.player

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CloudDownload
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.asmr.player.ui.library.AlbumDetailScreen
import com.asmr.player.ui.library.AlbumDetailUiState
import com.asmr.player.ui.library.AlbumDetailViewModel
import com.asmr.player.ui.library.CloudSyncSelectionDialog
import com.asmr.player.ui.library.LibraryFilterScreen
import com.asmr.player.ui.library.LibraryScreen
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.library.BulkPhase
import com.asmr.player.ui.player.MiniPlayer
import com.asmr.player.ui.player.NowPlayingMotionLayout
import com.asmr.player.ui.player.NowPlayingMotionSpec
import com.asmr.player.ui.player.NowPlayingScreen
import com.asmr.player.ui.player.PlayerSharedBackdrop
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.player.rememberCoverDragPreviewState
import com.asmr.player.ui.player.rememberCoverMotionState
import com.asmr.player.ui.sidepanel.LocalRightPanelExpandedState
import com.asmr.player.ui.common.rememberDominantColorCenterWeighted
import com.asmr.player.ui.downloads.DownloadsScreen
import com.asmr.player.ui.downloads.DownloadsViewModel
import com.asmr.player.ui.downloads.DownloadItemState
import com.asmr.player.ui.dlsite.DlsiteLoginScreen
import com.asmr.player.ui.dlsite.DlsiteLoginViewModel
import com.asmr.player.ui.groups.AlbumGroupsViewModel
import com.asmr.player.ui.playlists.PlaylistDetailScreen
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsScreen
import com.asmr.player.ui.playlists.PlaylistsViewModel
import com.asmr.player.ui.playlists.SystemPlaylistScreen
import com.asmr.player.ui.search.SearchScreen
import com.asmr.player.ui.search.SearchViewModel
import com.asmr.player.domain.model.SearchSource
import com.asmr.player.ui.settings.SettingsScreen
import com.asmr.player.ui.settings.SettingsViewModel
import com.asmr.player.ui.common.glassMenu
import com.asmr.player.ui.drawer.DrawerStatusViewModel
import com.asmr.player.ui.drawer.StatisticsViewModel
import com.asmr.player.ui.drawer.SiteStatus
import com.asmr.player.ui.drawer.SiteStatusType
import com.asmr.player.ui.nav.AppNavigator
import com.asmr.player.ui.nav.BottomChrome
import com.asmr.player.ui.nav.Routes
import com.asmr.player.ui.nav.bottomChromeNavItems
import com.asmr.player.ui.nav.bottomChromeOverlayHeight
import com.asmr.player.ui.nav.isPrimaryRoute
import com.asmr.player.ui.nav.resolvePrimaryPagerRoutes
import com.asmr.player.ui.nav.resolvePrimaryRoute
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.splash.EaraSplashOverlay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.common.PrewarmDominantColorCenterWeighted
import com.asmr.player.ui.common.PrewarmVideoFrameDominantColorCenterWeighted
import androidx.compose.ui.draw.blur
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import com.asmr.player.ui.player.QueueSheetContent
import com.asmr.player.ui.player.SleepTimerSheetContent
import com.asmr.player.ui.player.MiniPlayerDisplayMode

import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.util.MessageManager
import com.asmr.player.ui.common.NonTouchableAppMessageOverlay
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.VisibleAppMessage
import com.asmr.player.ui.theme.HuePalette
import com.asmr.player.ui.theme.PlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.ui.theme.DefaultBrandPrimaryDark
import com.asmr.player.ui.theme.DefaultBrandPrimaryLight
import com.asmr.player.ui.theme.deriveHuePalette
import kotlin.math.roundToInt
import com.asmr.player.ui.theme.neutralPaletteForMode
import com.asmr.player.ui.theme.rememberDynamicHuePalette
import com.asmr.player.ui.theme.rememberDynamicHuePaletteFromVideoFrame
import com.asmr.player.ui.theme.dynamicPageContainerColor
import com.asmr.player.ui.common.AppVolumeHearingWarningDialog
import com.asmr.player.ui.common.AppVolumeWarningSessionState
import com.asmr.player.ui.common.rememberAppVolumeWarningSessionState
import com.asmr.player.ui.common.rememberCurrentAudioOutputRouteKind
import com.asmr.player.ui.common.rememberProtectedAppVolumeChangeState
import com.asmr.player.ui.common.AudioOutputRouteIcon
import com.asmr.player.ui.common.DismissOutsideBoundsOverlay
import com.asmr.player.service.AudioOutputRouteKind
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.media3.common.MediaItem
import androidx.lifecycle.lifecycleScope
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.playback.AppVolume
import com.asmr.player.ui.common.AppVolumeVerticalSlider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PlaylistPickerRequest(
    val mediaId: String,
    val uri: String,
    val title: String,
    val artist: String,
    val artworkUri: String,
    val albumId: Long,
    val trackId: Long,
    val rjCode: String
)

internal data class BatchPlaylistPickerRequest(
    val items: List<MediaItem>
)

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
fun MainContainer(
    windowSizeClass: WindowSizeClass,
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    settingsDataStore: SettingsDataStore,
    recentAlbumsPanelExpandedInitial: Boolean,
    startRouteFromIntent: String?,
    onShowQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onContentReady: () -> Unit,
    visibleMessages: List<VisibleAppMessage>,
    showMiniPlayerBar: Boolean,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    coverPreviewMode: CoverPreviewMode,
    lyricsPageSettings: LyricsPageSettings,
    forceImmersive: Boolean,
    volumeKeyEventTick: Long
) {
    val navController = rememberNavController()
    val navigator = remember(navController) { AppNavigator(navController) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentPlaylistSystemType = navBackStackEntry?.arguments?.getString("type")
    val startRoute = remember(startRouteFromIntent) {
        startRouteFromIntent?.trim().orEmpty()
    }
    val initialDestination = remember(startRoute) {
        if (startRoute == Routes.Search) Routes.Search else Routes.Library
    }
    var lastPrimaryRoute by rememberSaveable { mutableStateOf(initialDestination) }
    val currentPrimaryRoute = resolveCurrentPrimaryDestinationRoute(
        currentRoute = currentRoute,
        playlistSystemType = currentPlaylistSystemType
    )
    val activePrimaryRoute = resolvePrimaryRoute(
        currentRoute = currentRoute,
        lastPrimaryRoute = lastPrimaryRoute,
        playlistSystemType = currentPlaylistSystemType
    )
    val bottomNavItems = remember { bottomChromeNavItems() }
    val fixedBottomNavRoutes = remember(bottomNavItems) {
        bottomNavItems.take(3).map { it.route }.toSet()
    }
    val bottomNavRoutes = remember(bottomNavItems) { bottomNavItems.map { it.route }.toSet() }
    val storedMiniPlayerDisplayMode by settingsDataStore.miniPlayerDisplayMode.collectAsState(
        initial = MiniPlayerDisplayMode.CoverOnly.name
    )
    val storedBottomChromePinnedRoute by settingsDataStore.bottomChromePinnedRoute.collectAsState(initial = null)
    var miniPlayerDisplayMode by rememberSaveable { mutableStateOf(MiniPlayerDisplayMode.CoverOnly) }
    var bottomChromePinnedRoute by rememberSaveable { mutableStateOf<String?>(null) }
    val primaryPagerRoutes = remember(bottomNavItems, activePrimaryRoute, bottomChromePinnedRoute) {
        resolvePrimaryPagerRoutes(
            navItems = bottomNavItems,
            activeRoute = activePrimaryRoute,
            preferredPinnedRoute = bottomChromePinnedRoute
        )
    }
    val initialPrimaryPage = remember(initialDestination, primaryPagerRoutes) {
        primaryPagerRoutes.indexOf(initialDestination).takeIf { it >= 0 } ?: 0
    }
    val primaryPagerState = rememberPagerState(
        initialPage = initialPrimaryPage,
        pageCount = { primaryPagerRoutes.size }
    )
    val primaryContentStateHolder = rememberSaveableStateHolder()
    var primaryPagerScrollLocked by remember { mutableStateOf(false) }
    val primaryNavSelectionProgresses by remember(
        primaryPagerState,
        primaryPagerRoutes,
        activePrimaryRoute
    ) {
        derivedStateOf {
            computePrimaryNavSelectionProgresses(
                pagerRoutes = primaryPagerRoutes,
                currentPage = primaryPagerState.currentPage,
                currentPageOffsetFraction = primaryPagerState.currentPageOffsetFraction,
                fallbackRoute = activePrimaryRoute
            )
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        onContentReady()
    }
    LaunchedEffect(navController, startRoute, initialDestination) {
        if (startRoute.isBlank() || startRoute == initialDestination) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        navController.navigateSingleTop(startRoute)
    }
    var blockNavTouches by remember { mutableStateOf(false) }
    var lastRouteForTouchBlock by remember { mutableStateOf(currentPrimaryRoute ?: currentRoute) }
    var touchBlockSeq by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val searchViewModel: SearchViewModel = hiltViewModel()
    val playlistsViewModel: PlaylistsViewModel = hiltViewModel()
    val albumGroupsViewModel: AlbumGroupsViewModel = hiltViewModel()
    val downloadsViewModel: DownloadsViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val dlsiteLoginViewModel: DlsiteLoginViewModel = hiltViewModel()
    val hasCurrentMediaItem by remember(playerViewModel) {
        playerViewModel.playback
            .map { it.currentMediaItem != null }
            .distinctUntilChanged()
    }.collectAsState(initial = false)
    val sharedPlayerPlayback by playerViewModel.playback.collectAsState()
    val drawerStatusViewModel: DrawerStatusViewModel = hiltViewModel()
    val statisticsViewModel: StatisticsViewModel = hiltViewModel()
    val bulkProgress by libraryViewModel.bulkProgress.collectAsState()
    val cloudSyncSelectionDialogState by libraryViewModel.cloudSyncSelectionDialogState.collectAsState()
    val appVolumePercent by playerViewModel.appVolumePercent.collectAsState()
    var showManualRjDialog by remember { mutableStateOf(false) }
    var manualRjInput by remember { mutableStateOf("") }
    var showHardwareVolumeOverlay by remember { mutableStateOf(false) }
    var hardwareVolumeOverlayInteracting by remember { mutableStateOf(false) }
    var hardwareVolumeOverlayHoldTick by remember { mutableLongStateOf(0L) }
    var lastHandledVolumeKeyTick by remember { mutableLongStateOf(0L) }
    var nowPlayingVolumeEventTick by remember { mutableLongStateOf(0L) }
    var lastNonZeroAppVolumePercent by rememberSaveable { mutableIntStateOf(AppVolume.DefaultPercent) }
    var hardwareVolumeOverlayBounds by remember { mutableStateOf<Rect?>(null) }
    var bottomChromeOverflowExpanded by remember { mutableStateOf(false) }
    var bottomChromeOverflowBounds by remember { mutableStateOf<Rect?>(null) }
    var pendingPrimaryNavigationRoute by remember { mutableStateOf<String?>(null) }
    val appVolumeWarningSessionState = rememberAppVolumeWarningSessionState()
    val audioOutputRouteKind = rememberCurrentAudioOutputRouteKind()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 使用 smallestScreenWidthDp 判定是否为手机 (一般 < 600dp 为手机)
    val isPhone = configuration.smallestScreenWidthDp < 600
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var nowPlayingVisible by rememberSaveable { mutableStateOf(false) }
    var nowPlayingBackdropActive by rememberSaveable { mutableStateOf(false) }
    var nowPlayingBackdropExitDurationMs by rememberSaveable {
        mutableIntStateOf(NowPlayingMotionSpec.totalExitDurationMs(NowPlayingMotionLayout.PORTRAIT))
    }
    var nowPlayingPlaylistPickerRequest by remember { mutableStateOf<PlaylistPickerRequest?>(null) }
    var albumBatchPlaylistPickerRequest by remember { mutableStateOf<BatchPlaylistPickerRequest?>(null) }
    val playerImmersive = nowPlayingVisible
    val openNowPlaying = openNowPlaying@{
        if (nowPlayingVisible) return@openNowPlaying
        nowPlayingBackdropActive = true
        nowPlayingVisible = true
    }
    val closeNowPlaying: () -> Unit = {
        nowPlayingPlaylistPickerRequest = null
        albumBatchPlaylistPickerRequest = null
        nowPlayingBackdropActive = false
        nowPlayingVisible = false
    }
    val playerBackdropVisible = nowPlayingVisible
    val sharedPlayerItem = sharedPlayerPlayback.currentMediaItem
    val sharedPlayerUriText = sharedPlayerItem?.localConfiguration?.uri?.toString().orEmpty()
    val sharedPlayerMimeType = sharedPlayerItem?.localConfiguration?.mimeType.orEmpty()
    val sharedPlayerExt = sharedPlayerUriText
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('.', "")
        .lowercase()
    val sharedPlayerIsVideo = sharedPlayerItem?.mediaMetadata?.extras?.getBoolean("is_video") == true ||
        sharedPlayerMimeType.startsWith("video/") ||
        sharedPlayerExt in setOf("mp4", "m4v", "webm", "mkv", "mov")
    val sharedUseDragPreview = playerBackdropVisible &&
        coverBackgroundEnabled &&
        coverPreviewMode == CoverPreviewMode.Drag &&
        !sharedPlayerIsVideo
    val sharedUseMotionPreview = playerBackdropVisible &&
        coverBackgroundEnabled &&
        coverPreviewMode == CoverPreviewMode.Motion &&
        !sharedPlayerIsVideo
    val sharedCoverMotionState = rememberCoverMotionState(
        enabled = sharedUseMotionPreview,
        resetKey = sharedPlayerItem?.mediaId
    )
    val sharedCoverDragPreviewState = rememberCoverDragPreviewState(
        enabled = sharedUseDragPreview,
        resetKey = sharedPlayerItem?.mediaId
    )
    val sharedPlayerBackdropAlignment = when {
        sharedUseDragPreview -> BiasAlignment(
            horizontalBias = sharedCoverDragPreviewState.horizontalBias,
            verticalBias = sharedCoverDragPreviewState.verticalBias
        )
        sharedUseMotionPreview -> BiasAlignment(
            horizontalBias = sharedCoverMotionState.horizontalBias,
            verticalBias = sharedCoverMotionState.verticalBias
        )
        else -> Alignment.Center
    }
    val nowPlayingBackdropAlpha by animateFloatAsState(
        targetValue = if (nowPlayingBackdropActive) 1f else 0f,
        animationSpec = if (nowPlayingBackdropActive) {
            tween(
                durationMillis = 360,
                easing = LinearOutSlowInEasing
            )
        } else {
            keyframes {
                durationMillis = nowPlayingBackdropExitDurationMs
                1f at 0
                1f at (nowPlayingBackdropExitDurationMs * 0.58f).toInt()
                0f at nowPlayingBackdropExitDurationMs using FastOutLinearInEasing
            }
        },
        label = "nowPlayingBackdropAlpha"
    )
    val currentPrimaryRouteState = rememberUpdatedState(currentPrimaryRoute)
    fun openPrimaryRoute(route: String, pagerRoutes: List<String> = primaryPagerRoutes) {
        val targetPage = pagerRoutes.indexOf(route)
        if (targetPage >= 0 && currentPrimaryRoute != null) {
            scope.launch {
                pendingPrimaryNavigationRoute = route
                primaryPagerState.animateScrollToPage(targetPage)
                if (currentPrimaryRouteState.value != route) {
                    navController.navigateSingleTop(route, popUpToRoute = Routes.Library)
                }
            }
        } else {
            pendingPrimaryNavigationRoute = null
            navController.navigateSingleTop(route, popUpToRoute = Routes.Library)
        }
    }

    LaunchedEffect(currentPrimaryRoute, primaryPagerRoutes, pendingPrimaryNavigationRoute) {
        val route = currentPrimaryRoute ?: return@LaunchedEffect
        val pendingRoute = pendingPrimaryNavigationRoute
        if (pendingRoute != null && route != pendingRoute) return@LaunchedEffect
        val targetPage = primaryPagerRoutes.indexOf(route)
        if (targetPage >= 0 && primaryPagerState.currentPage != targetPage) {
            primaryPagerState.scrollToPage(targetPage)
        }
        if (pendingRoute == route) {
            pendingPrimaryNavigationRoute = null
        }
    }

    LaunchedEffect(primaryPagerState, primaryPagerRoutes) {
        snapshotFlow { primaryPagerState.isScrollInProgress }
            .filter { !it }
            .map { primaryPagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val currentPrimary = currentPrimaryRouteState.value ?: return@collect
                val targetRoute = primaryPagerRoutes.getOrNull(page) ?: return@collect
                if (targetRoute != currentPrimary) {
                    navController.navigateSingleTop(targetRoute, popUpToRoute = Routes.Library)
                }
            }
    }

    LaunchedEffect(currentRoute, currentPrimaryRoute) {
        showHardwareVolumeOverlay = false
        hardwareVolumeOverlayInteracting = false
        hardwareVolumeOverlayBounds = null
        bottomChromeOverflowExpanded = false
        bottomChromeOverflowBounds = null
        val normalizedCurrentRoute = currentPrimaryRoute ?: currentRoute
        val last = lastRouteForTouchBlock
        val seq = ++touchBlockSeq
        val isPrimaryPagerSwitch =
            last != null &&
                normalizedCurrentRoute != null &&
                last != normalizedCurrentRoute &&
                last in primaryPagerRoutes &&
                normalizedCurrentRoute in primaryPagerRoutes
        if (last != null && normalizedCurrentRoute != null && last != normalizedCurrentRoute && !isPrimaryPagerSwitch) {
            blockNavTouches = true
            try {
                delay(320)
            } finally {
                if (touchBlockSeq == seq) {
                    blockNavTouches = false
                }
            }
        } else {
            blockNavTouches = false
        }
        lastRouteForTouchBlock = normalizedCurrentRoute
    }

    LaunchedEffect(activePrimaryRoute) {
        if (isPrimaryRoute(activePrimaryRoute)) {
            lastPrimaryRoute = activePrimaryRoute
        }
    }

    LaunchedEffect(storedMiniPlayerDisplayMode) {
        miniPlayerDisplayMode = runCatching {
            MiniPlayerDisplayMode.valueOf(storedMiniPlayerDisplayMode)
        }.getOrElse {
            MiniPlayerDisplayMode.CoverOnly
        }
    }

    LaunchedEffect(storedBottomChromePinnedRoute) {
        bottomChromePinnedRoute = storedBottomChromePinnedRoute
    }

    LaunchedEffect(nowPlayingVisible) {
        if (!nowPlayingVisible) return@LaunchedEffect
        showHardwareVolumeOverlay = false
        hardwareVolumeOverlayInteracting = false
        hardwareVolumeOverlayBounds = null
        bottomChromeOverflowExpanded = false
        bottomChromeOverflowBounds = null
        nowPlayingVolumeEventTick = 0L
    }

    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val topBarContentColor = colorScheme.onSurface
    val drawerContainerColor = if (colorScheme.isDark) Color(0xFF121212) else Color.White

    val defaultSystemUi = remember(activity) {
        activity?.let { act ->
            val controller = WindowInsetsControllerCompat(act.window, act.window.decorView)
            DefaultSystemUiState(
                statusBarColor = act.window.statusBarColor,
                navigationBarColor = act.window.navigationBarColor,
                lightStatusBars = controller.isAppearanceLightStatusBars,
                lightNavigationBars = controller.isAppearanceLightNavigationBars,
                statusBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    act.window.isStatusBarContrastEnforced
                } else {
                    null
                },
                navigationBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    act.window.isNavigationBarContrastEnforced
                } else {
                    null
                }
            )
        }
    }

    DisposableEffect(activity, forceImmersive, playerImmersive, colorScheme.isDark) {
        val act = activity ?: return@DisposableEffect onDispose { }
        val window = act.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // 始终由应用控制系统栏区域绘制，避免 fitsSystemWindows 切换导致的布局跳动
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        if (forceImmersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        } else if (playerImmersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.show(WindowInsetsCompat.Type.statusBars())
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            controller.isAppearanceLightStatusBars = !colorScheme.isDark
            controller.isAppearanceLightNavigationBars = !colorScheme.isDark
        }
        onDispose {
            val window2 = act.window
            val controller2 = WindowInsetsControllerCompat(window2, window2.decorView)
            // 退出时保持 false，交给 Compose 处理 padding
            WindowCompat.setDecorFitsSystemWindows(window2, false)
            controller2.show(WindowInsetsCompat.Type.systemBars())
            defaultSystemUi?.let { ui ->
                window2.statusBarColor = ui.statusBarColor
                window2.navigationBarColor = ui.navigationBarColor
                controller2.isAppearanceLightStatusBars = ui.lightStatusBars
                controller2.isAppearanceLightNavigationBars = ui.lightNavigationBars
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ui.statusBarContrastEnforced?.let { window2.isStatusBarContrastEnforced = it }
                    ui.navigationBarContrastEnforced?.let { window2.isNavigationBarContrastEnforced = it }
                }
            }
        }
    }

    // 屏幕旋转管理逻辑
    LaunchedEffect(nowPlayingVisible, isPhone) {
        activity?.let { act ->
            if (isPhone) {
                if (nowPlayingVisible) {
                    // 手机端在播放页和歌词页允许横屏（遵守系统自动旋转/旋转锁定设置）
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                } else {
                    // 手机端其他页面强制锁定竖屏
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            } else {
                // 平板端始终允许旋转
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            }
        }
    }
    LaunchedEffect(appVolumePercent) {
        if (appVolumePercent > 0) {
            lastNonZeroAppVolumePercent = appVolumePercent
        }
    }

    LaunchedEffect(volumeKeyEventTick) {
        if (volumeKeyEventTick <= 0L) return@LaunchedEffect
        if (volumeKeyEventTick == lastHandledVolumeKeyTick) return@LaunchedEffect
        lastHandledVolumeKeyTick = volumeKeyEventTick
        if (nowPlayingVisible) {
            showHardwareVolumeOverlay = false
            nowPlayingVolumeEventTick = volumeKeyEventTick
            return@LaunchedEffect
        }
        showHardwareVolumeOverlay = true
        hardwareVolumeOverlayHoldTick = volumeKeyEventTick
    }

    LaunchedEffect(showHardwareVolumeOverlay, hardwareVolumeOverlayHoldTick, hardwareVolumeOverlayInteracting, nowPlayingVisible) {
        if (!showHardwareVolumeOverlay) return@LaunchedEffect
        if (nowPlayingVisible) {
            showHardwareVolumeOverlay = false
            hardwareVolumeOverlayBounds = null
            return@LaunchedEffect
        }
        if (hardwareVolumeOverlayInteracting) return@LaunchedEffect
        val snapshot = hardwareVolumeOverlayHoldTick
        delay(2_000)
        if (!hardwareVolumeOverlayInteracting && hardwareVolumeOverlayHoldTick == snapshot) {
            showHardwareVolumeOverlay = false
            hardwareVolumeOverlayBounds = null
        }
    }

    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val drawerGesturesEnabled = false

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(300.dp)
                    .glassMenu(
                        shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
                        baseColor = drawerContainerColor,
                        elevation = if (colorScheme.isDark) 0.dp else 6.dp,
                        isDark = colorScheme.isDark
                    )
            ) {
                ModalDrawerSheet(
                    drawerContainerColor = Color.Transparent,
                    drawerContentColor = colorScheme.onSurface,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val navItems = listOf(
                        Triple(Icons.Default.Home, "本地库", "library"),
                        Triple(Icons.Default.Search, "在线搜索", "search"),
                        Triple(Icons.Default.Favorite, "我的收藏", "playlist_system/favorites"),
                        Triple(Icons.AutoMirrored.Filled.QueueMusic, "我的列表", "playlists"),
                        Triple(Icons.Default.Folder, "我的分组", "groups"),
                        Triple(Icons.Default.Download, "下载管理", "downloads"),
                        Triple(Icons.Default.Settings, "设置", "settings"),
                        Triple(Icons.Default.Person, "DLsite 登录", "dlsite_login")
                    )

                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(46.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp)
                            ) {
                                items(navItems, key = { it.third }) { (icon, label, route) ->
                                    val isAlbumDetailFromSearch =
                                        currentRoute?.startsWith("album_detail_rj") == true ||
                                            currentRoute?.startsWith("album_detail_online") == true
                                    val isAlbumDetailFromLibrary =
                                        currentRoute?.startsWith("album_detail/") == true &&
                                            currentRoute?.startsWith("album_detail_rj") != true
                                    val isSelected = when (route) {
                                        "library" -> currentRoute == route || isAlbumDetailFromLibrary
                                        "search" -> currentRoute == route || isAlbumDetailFromSearch
                                        "groups" -> currentRoute == route ||
                                            currentRoute?.startsWith("group/") == true ||
                                            currentRoute?.startsWith("group_picker") == true
                                        "playlist_system/favorites" -> {
                                            currentRoute == "playlist_system/{type}" &&
                                                navBackStackEntry?.arguments?.getString("type") == "favorites"
                                        }
                                        else -> currentRoute == route
                                    }
                                    DrawerNavCardItem(
                                        icon = icon,
                                        label = label,
                                        selected = isSelected,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        onClick = { openPrimaryRoute(route) }
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(drawerContainerColor, Color.Transparent)
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, drawerContainerColor)
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        DailyStatisticsFooter(statisticsViewModel, modifier = Modifier.padding(horizontal = 18.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        DrawerSiteStatusFooter(drawerStatusViewModel, modifier = Modifier.padding(horizontal = 18.dp))
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }
        }
    ) {
        val miniPlayerVisible = showMiniPlayerBar &&
            hasCurrentMediaItem &&
            !nowPlayingVisible
        val bottomChromeVisible = !nowPlayingVisible
        val rightPanelExpandedFromStore by settingsDataStore.recentAlbumsPanelExpanded.collectAsState(initial = recentAlbumsPanelExpandedInitial)
        val rightPanelExpandedState = remember(settingsDataStore, scope, recentAlbumsPanelExpandedInitial) {
            PersistedBooleanState(initial = recentAlbumsPanelExpandedInitial) { expanded ->
                scope.launch { settingsDataStore.setRecentAlbumsPanelExpanded(expanded) }
            }
        }
        LaunchedEffect(rightPanelExpandedFromStore) {
            rightPanelExpandedState.updateFromStore(rightPanelExpandedFromStore)
        }
        val currentScreenIsPrimary = currentPrimaryRoute != null
        val showBackButton = !currentScreenIsPrimary
        val showPrimaryBrand = currentScreenIsPrimary
        val topBarDividerColor = colorScheme.onSurface.copy(
            alpha = if (colorScheme.isDark) 0.16f else 0.10f
        )
        val useLargeBottomChrome = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact && !isPhone
        CompositionLocalProvider(
            LocalBottomOverlayPadding provides (if (bottomChromeVisible) bottomChromeOverlayHeight(useLargeBottomChrome) else 0.dp),
            LocalRightPanelExpandedState provides rightPanelExpandedState
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.background.copy(alpha = 0.88f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.primarySoft.copy(alpha = 0.16f))
                    )
                    Scaffold(
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = Color.Transparent,
                        contentColor = colorScheme.onBackground,
                        topBar = {
                            Column {
                                    val compactTopBar =
                                        currentRoute == "library" ||
                                            currentRoute == "library_filter" ||
                                            currentRoute == "search" ||
                                            currentRoute == "playlists" ||
                                            currentRoute == "playlist/{playlistId}/{playlistName}" ||
                                            currentRoute == "playlist_system/{type}" ||
                                            currentRoute == "groups" ||
                                            currentRoute == "group/{groupId}/{groupName}" ||
                                            currentRoute == "settings" ||
                                            currentRoute == "downloads" ||
                                            currentRoute == "dlsite_login" ||
                                            currentRoute?.startsWith("album_detail") == true
                                    Spacer(modifier = Modifier.windowInsetsTopHeight(StableWindowInsets.statusBars))
                                    CenterAlignedTopAppBar(
                                        modifier = Modifier.height(if (compactTopBar) 48.dp else 64.dp),
                                        title = {
                                            val entry = navBackStackEntry
                                            val groupName = if (currentRoute == "group/{groupId}/{groupName}") {
                                                decodeRouteArg(entry?.arguments?.getString("groupName").orEmpty())
                                            } else ""
                                            val playlistName = if (currentRoute == "playlist/{playlistId}/{playlistName}") {
                                                decodeRouteArg(entry?.arguments?.getString("playlistName").orEmpty())
                                            } else ""
                                            val systemPlaylistType = if (currentRoute == "playlist_system/{type}") {
                                                entry?.arguments?.getString("type").orEmpty()
                                            } else ""
                                            val appName = stringResource(R.string.app_name)
                                            Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                                Text(
                                                    when {
                                                        currentRoute == "library" -> "本地库"
                                                        currentRoute == "library_filter" -> "筛选"
                                                        currentRoute == "search" -> "在线搜索"
                                                        currentRoute == "playlists" -> "我的列表"
                                                        currentRoute == "playlist/{playlistId}/{playlistName}" ->
                                                            playlistName.ifBlank { "我的列表" }
                                                        currentRoute == "playlist_system/{type}" -> when (systemPlaylistType) {
                                                            "favorites" -> "我的收藏"
                                                            else -> "我的收藏"
                                                        }
                                                        currentRoute == "groups" -> "我的分组"
                                                        currentRoute == "group/{groupId}/{groupName}" ->
                                                            groupName.ifBlank { "我的分组" }
                                                        currentRoute == "settings" -> "设置"
                                                        currentRoute == "downloads" -> "下载管理"
                                                        currentRoute == "dlsite_login" -> "DLsite 登录"
                                                        currentRoute?.startsWith("playlist_picker") == true -> "添加到我的列表"
                                                        currentRoute?.startsWith("album_detail") == true -> "专辑详情"
                                                        else -> appName
                                                    },
                                                    style = if (compactTopBar) {
                                                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                                    } else {
                                                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                                    }
                                                )
                                            }
                                        },
                                        windowInsets = WindowInsets(0, 0, 0, 0),
                                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                            containerColor = Color.Transparent,
                                            titleContentColor = topBarContentColor,
                                            navigationIconContentColor = topBarContentColor,
                                            actionIconContentColor = topBarContentColor
                                        ),
                                        navigationIcon = {
                                            if (showBackButton && navController.previousBackStackEntry != null) {
                                                IconButton(onClick = { navController.popBackStack() }) {
                                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                                }
                                            } else if (showPrimaryBrand) {
                                                PrimaryTopBarBrand(
                                                    appName = stringResource(R.string.app_name),
                                                    tint = topBarContentColor
                                                )
                                            }
                                        },
                                        actions = {
                                            val entry = navBackStackEntry
                                            if (currentRoute == "library") {
                                                val viewMode by libraryViewModel.libraryViewMode.collectAsState()
                                                if (viewMode != null) {
                                                    var viewMenuExpanded by remember { mutableStateOf(false) }
                                                    Box {
                                                        val normalized = (viewMode ?: 0).coerceIn(0, 2)
                                                        val icon = when (normalized) {
                                                            1 -> Icons.Default.GridView
                                                            2 -> Icons.Default.Audiotrack
                                                            else -> Icons.Default.ViewList
                                                        }
                                                        IconButton(onClick = { viewMenuExpanded = true }) {
                                                            Icon(imageVector = icon, contentDescription = "切换视图")
                                                        }
                                                        MaterialTheme(
                                                            colorScheme = materialColorScheme.copy(
                                                                surface = dynamicContainerColor,
                                                                surfaceContainer = dynamicContainerColor
                                                            )
                                                        ) {
                                                            DropdownMenu(
                                                                expanded = viewMenuExpanded,
                                                                onDismissRequest = { viewMenuExpanded = false },
                                                                modifier = Modifier.background(dynamicContainerColor)
                                                            ) {
                                                            DropdownMenuItem(
                                                                text = { Text("专辑列表") },
                                                                onClick = {
                                                                    viewMenuExpanded = false
                                                                    libraryViewModel.setLibraryViewMode(0)
                                                                }
                                                            )
                                                            HorizontalDivider(
                                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                                thickness = 0.5.dp,
                                                                color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("专辑卡片") },
                                                                onClick = {
                                                                    viewMenuExpanded = false
                                                                    libraryViewModel.setLibraryViewMode(1)
                                                                }
                                                            )
                                                            HorizontalDivider(
                                                                modifier = Modifier.padding(horizontal = 8.dp),
                                                                thickness = 0.5.dp,
                                                                color = materialColorScheme.outlineVariant.copy(alpha = 0.3f)
                                                            )
                                                            DropdownMenuItem(
                                                                text = { Text("音轨列表") },
                                                                onClick = {
                                                                    viewMenuExpanded = false
                                                                    libraryViewModel.setLibraryViewMode(2)
                                                                }
                                                            )
                                                            }
                                                        }
                                                    }
                                                }
                                            } else if (currentRoute == "search") {
                                                val viewMode by searchViewModel.viewMode.collectAsState()
                                                IconButton(onClick = { searchViewModel.setViewMode(if (viewMode == 1) 0 else 1) }) {
                                                    Icon(
                                                        imageVector = if (viewMode == 1) Icons.Default.ViewList else Icons.Default.ViewModule,
                                                        contentDescription = null
                                                    )
                                                }
                                            } else if (currentRoute == "downloads") {
                                                val tasks by downloadsViewModel.tasks.collectAsState()
                                                val hasActiveDownloads = remember(tasks) {
                                                    tasks.any { task ->
                                                        task.items.any { it.state == DownloadItemState.RUNNING || it.state == DownloadItemState.ENQUEUED }
                                                    }
                                                }
                                                val hasPausedDownloads = remember(tasks) {
                                                    tasks.any { task ->
                                                        task.items.any { it.state == DownloadItemState.PAUSED }
                                                    }
                                                }
                                                
                                                if (hasActiveDownloads) {
                                                    TextButton(
                                                        onClick = { downloadsViewModel.pauseAll() },
                                                        colors = ButtonDefaults.textButtonColors(contentColor = topBarContentColor)
                                                    ) { Text("全部暂停") }
                                                } else if (hasPausedDownloads) {
                                                    TextButton(
                                                        onClick = { downloadsViewModel.resumeAll() },
                                                        colors = ButtonDefaults.textButtonColors(contentColor = topBarContentColor)
                                                    ) { Text("全部继续") }
                                                }
                                            } else if (entry != null && (
                                                currentRoute?.startsWith("album_detail/{albumId}") == true ||
                                                    currentRoute?.startsWith("album_detail/") == true
                                                )
                                            ) {
                                                val albumDetailViewModel: AlbumDetailViewModel = hiltViewModel(entry)
                                                val detailState by albumDetailViewModel.uiState.collectAsState()
                                                val showManualBind = (detailState as? AlbumDetailUiState.Success)?.model?.let { m ->
                                                    val local = m.localAlbum
                                                    local != null && local.id > 0L
                                                } == true
                                                if (showManualBind) {
                                                    IconButton(
                                                        onClick = {
                                                            val currentRj = (detailState as? AlbumDetailUiState.Success)?.model?.let { m ->
                                                                val local = m.localAlbum
                                                                m.rjCode.trim()
                                                                    .ifBlank { local?.rjCode?.trim().orEmpty() }
                                                                    .ifBlank { local?.workId?.trim().orEmpty() }
                                                            }.orEmpty()
                                                            manualRjInput = currentRj
                                                            showManualRjDialog = true
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = "手动输入RJ号")
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(),
                                        thickness = 0.5.dp,
                                        color = topBarDividerColor
                                    )

                                    val p = bulkProgress
                                    if (currentRoute == "library" && p?.phase == BulkPhase.ScanningLocal) {
                                        if (p.total > 0) {
                                            LinearProgressIndicator(
                                                progress = { p.fraction },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        } else {
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                            }
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = padding.calculateTopPadding())
                        ) {
                            primaryContentStateHolder.SaveableStateProvider("primary_pager") {
                                if (currentPrimaryRoute != null) {
                                    HorizontalPager(
                                        state = primaryPagerState,
                                        modifier = Modifier.fillMaxSize(),
                                        userScrollEnabled = !primaryPagerScrollLocked,
                                        key = { primaryPagerRoutes[it] }
                                    ) { page ->
                                        val route = primaryPagerRoutes[page]
                                        primaryContentStateHolder.SaveableStateProvider("primary_route:$route") {
                                            when (route) {
                                        Routes.Library -> {
                                            LibraryScreen(
                                                windowSizeClass = windowSizeClass,
                                                onAlbumClick = { album ->
                                                    navigator.openAlbumDetail(
                                                        albumId = album.id,
                                                        rj = null
                                                    )
                                                },
                                                onPlayTracks = { album, tracks, startTrack ->
                                                    scope.launch {
                                                        if (playerViewModel.playTracksPrepared(album, tracks, startTrack)) {
                                                            openNowPlaying()
                                                        }
                                                    }
                                                },
                                                onOpenPlaylistPicker = { mediaId, uri, title, artist, artworkUri, albumId, trackId, rjCode ->
                                                    navController.navigateSingleTop(
                                                        "playlist_picker" +
                                                            "?mediaId=${encodeRouteArg(mediaId)}" +
                                                            "&uri=${encodeRouteArg(uri)}" +
                                                            "&title=${encodeRouteArg(title)}" +
                                                            "&artist=${encodeRouteArg(artist)}" +
                                                            "&artworkUri=${encodeRouteArg(artworkUri)}" +
                                                            "&albumId=$albumId" +
                                                            "&trackId=$trackId" +
                                                            "&rjCode=${encodeRouteArg(rjCode)}"
                                                    )
                                                },
                                                onOpenGroupPicker = { albumId ->
                                                    navController.navigateSingleTop("group_picker?albumId=$albumId")
                                                },
                                                onOpenFilterScreen = { navController.navigateSingleTop("library_filter") },
                                                viewModel = libraryViewModel
                                            )
                                        }

                                        Routes.Search -> {
                                            SearchScreen(
                                                windowSizeClass = windowSizeClass,
                                                onAlbumClick = { album ->
                                                    navigator.openAlbumDetail(
                                                        albumId = album.id,
                                                        rj = album.rjCode.ifBlank { album.workId }
                                                    )
                                                },
                                                viewModel = searchViewModel
                                            )
                                        }

                                        "playlist_system/favorites" -> {
                                            SystemPlaylistScreen(
                                                windowSizeClass = windowSizeClass,
                                                type = "favorites",
                                                onPlayAll = { items, startItem ->
                                                    playerViewModel.playPlaylistItems(items, startItem)
                                                    openNowPlaying()
                                                },
                                                viewModel = playlistsViewModel
                                            )
                                        }

                                        "playlists" -> {
                                            PlaylistsScreen(
                                                windowSizeClass = windowSizeClass,
                                                onPlaylistClick = { playlist ->
                                                    val encoded = URLEncoder.encode(playlist.name, "UTF-8")
                                                    navController.navigateSingleTop("playlist/${playlist.id}/$encoded")
                                                },
                                                viewModel = playlistsViewModel
                                            )
                                        }

                                        "groups" -> {
                                            com.asmr.player.ui.groups.AlbumGroupsScreen(
                                                windowSizeClass = windowSizeClass,
                                                onGroupClick = { group ->
                                                    val encoded = encodeRouteArg(group.name)
                                                    navController.navigateSingleTop("group/${group.id}/$encoded")
                                                },
                                                viewModel = albumGroupsViewModel
                                            )
                                        }

                                        "downloads" -> {
                                            DownloadsScreen(
                                                windowSizeClass = windowSizeClass,
                                                viewModel = downloadsViewModel
                                            )
                                        }

                                        "settings" -> {
                                            SettingsScreen(
                                                windowSizeClass = windowSizeClass,
                                                viewModel = settingsViewModel,
                                                libraryViewModel = libraryViewModel,
                                                onHorizontalControlInteractionChanged = { active ->
                                                    primaryPagerScrollLocked = active
                                                }
                                            )
                                        }

                                        "dlsite_login" -> {
                                            DlsiteLoginScreen(
                                                windowSizeClass = windowSizeClass,
                                                onDone = { navController.popBackStack() },
                                                viewModel = dlsiteLoginViewModel
                                            )
                                        }
                                    }
                                    }
                                }
                            }
                            }

                            NavHost(
                                navController = navController,
                                startDestination = initialDestination,
                                enterTransition = { EnterTransition.None },
                                exitTransition = { ExitTransition.None },
                                popEnterTransition = { EnterTransition.None },
                                popExitTransition = { ExitTransition.None },
                                modifier = Modifier.fillMaxSize()
                            ) {

                composable("library") {
                    Box(modifier = Modifier.fillMaxSize())
                }
                                composable("library_filter") {
                    LibraryFilterScreen(
                        onClose = { navController.popBackStack() },
                        viewModel = libraryViewModel
                    )
                }
                                composable("search") {
                    Box(modifier = Modifier.fillMaxSize())
                }
                                composable(
                    route = "album_detail_rj/{rj}",
                    arguments = listOf(navArgument("rj") { defaultValue = "" })
                ) { backStackEntry ->
                    val rj = backStackEntry.arguments?.getString("rj").orEmpty()
                    val refreshToken by backStackEntry.savedStateHandle.getStateFlow("refreshToken", 0L).collectAsState()
                    AlbumDetailScreen(
                        windowSizeClass = windowSizeClass,
                        rjCode = rj,
                        refreshToken = refreshToken,
                        onConsumeRefreshToken = { backStackEntry.savedStateHandle["refreshToken"] = 0L },
                        onPlayTracks = { album, tracks, startTrack ->
                            scope.launch {
                                if (playerViewModel.playTracksPrepared(album, tracks, startTrack)) {
                                    openNowPlaying()
                                }
                            }
                        },
                        onPlayMediaItems = { items, startIndex ->
                            playerViewModel.playMediaItems(items, startIndex)
                            openNowPlaying()
                        },
                        onAddToQueue = { album, track ->
                            playerViewModel.addTrackToQueue(album, track)
                        },
                        onAddMediaItemsToQueue = { items ->
                            playerViewModel.addMediaItemsToQueue(items)
                        },
                        onAddMediaItemsToFavorites = { items ->
                            scope.launch {
                                playlistsViewModel.addItemsToFavorites(items)
                            }
                        },
                        onOpenPlaylistPicker = { mediaId, uri, title, artist, artworkUri, albumId, trackId, rjCode ->
                            navController.navigateSingleTop(
                                "playlist_picker" +
                                    "?mediaId=${encodeRouteArg(mediaId)}" +
                                    "&uri=${encodeRouteArg(uri)}" +
                                    "&title=${encodeRouteArg(title)}" +
                                    "&artist=${encodeRouteArg(artist)}" +
                                    "&artworkUri=${encodeRouteArg(artworkUri)}" +
                                    "&albumId=$albumId" +
                                    "&trackId=$trackId" +
                                    "&rjCode=${encodeRouteArg(rjCode)}"
                            )
                        },
                        onOpenBatchPlaylistPicker = { items ->
                            albumBatchPlaylistPickerRequest = BatchPlaylistPickerRequest(items)
                        },
                        onOpenGroupPicker = { albumId ->
                            navController.navigateSingleTop("group_picker?albumId=$albumId")
                        },
                        onPlayVideo = { title, uriOrPath, artwork, artist ->
                            playerViewModel.playVideo(title, uriOrPath, artwork, artist)
                            openNowPlaying()
                        },
                        onOpenDlsiteLogin = { navController.navigateSingleTop("dlsite_login") },
                        onOpenAlbumByRj = { navigator.openAlbumDetailByRjStacked(it) }
                    )
                }
                composable(
                    route = "album_detail/{albumId}?rjCode={rjCode}",
                    arguments = listOf(
                        navArgument("albumId") { type = NavType.LongType },
                        navArgument("rjCode") { type = NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
                    val rjCode = backStackEntry.arguments?.getString("rjCode")
                    val refreshToken by backStackEntry.savedStateHandle.getStateFlow("refreshToken", 0L).collectAsState()
                    AlbumDetailScreen(
                        windowSizeClass = windowSizeClass,
                        albumId = albumId,
                        rjCode = rjCode,
                        refreshToken = refreshToken,
                        onConsumeRefreshToken = { backStackEntry.savedStateHandle["refreshToken"] = 0L },
                        onPlayTracks = { album, tracks, startTrack ->
                            scope.launch {
                                if (playerViewModel.playTracksPrepared(album, tracks, startTrack)) {
                                    openNowPlaying()
                                }
                            }
                        },
                        onPlayMediaItems = { items, startIndex ->
                            playerViewModel.playMediaItems(items, startIndex)
                            openNowPlaying()
                        },
                        onAddToQueue = { album, track ->
                            playerViewModel.addTrackToQueue(album, track)
                        },
                        onAddMediaItemsToQueue = { items ->
                            playerViewModel.addMediaItemsToQueue(items)
                        },
                        onAddMediaItemsToFavorites = { items ->
                            scope.launch {
                                playlistsViewModel.addItemsToFavorites(items)
                            }
                        },
                        onOpenPlaylistPicker = { mediaId, uri, title, artist, artworkUri, albumId, trackId, rjCode ->
                            navController.navigateSingleTop(
                                "playlist_picker" +
                                    "?mediaId=${encodeRouteArg(mediaId)}" +
                                    "&uri=${encodeRouteArg(uri)}" +
                                    "&title=${encodeRouteArg(title)}" +
                                    "&artist=${encodeRouteArg(artist)}" +
                                    "&artworkUri=${encodeRouteArg(artworkUri)}" +
                                    "&albumId=$albumId" +
                                    "&trackId=$trackId" +
                                    "&rjCode=${encodeRouteArg(rjCode)}"
                            )
                        },
                        onOpenBatchPlaylistPicker = { items ->
                            albumBatchPlaylistPickerRequest = BatchPlaylistPickerRequest(items)
                        },
                        onOpenGroupPicker = { albumId ->
                            navController.navigateSingleTop("group_picker?albumId=$albumId")
                        },
                        onPlayVideo = { title, uriOrPath, artwork, artist ->
                            playerViewModel.playVideo(title, uriOrPath, artwork, artist)
                            openNowPlaying()
                        },
                        onOpenDlsiteLogin = { navController.navigateSingleTop("dlsite_login") },
                        onOpenAlbumByRj = { navigator.openAlbumDetailByRjStacked(it) }
                    )
                }
                composable(
                    route = "album_detail_online/{rj}",
                    arguments = listOf(navArgument("rj") { defaultValue = "" })
                ) { backStackEntry ->
                    val rj = backStackEntry.arguments?.getString("rj").orEmpty()
                    val refreshToken by backStackEntry.savedStateHandle.getStateFlow("refreshToken", 0L).collectAsState()
                    AlbumDetailScreen(
                        windowSizeClass = windowSizeClass,
                        rjCode = rj,
                        refreshToken = refreshToken,
                        onConsumeRefreshToken = { backStackEntry.savedStateHandle["refreshToken"] = 0L },
                        onPlayTracks = { album, tracks, startTrack ->
                            scope.launch {
                                if (playerViewModel.playTracksPrepared(album, tracks, startTrack)) {
                                    openNowPlaying()
                                }
                            }
                        },
                        onPlayMediaItems = { items, startIndex ->
                            playerViewModel.playMediaItems(items, startIndex)
                            openNowPlaying()
                        },
                        onAddToQueue = { album, track ->
                            playerViewModel.addTrackToQueue(album, track)
                        },
                        onOpenPlaylistPicker = { mediaId, uri, title, artist, artworkUri, albumId, trackId, rjCode ->
                            navController.navigateSingleTop(
                                "playlist_picker" +
                                    "?mediaId=${encodeRouteArg(mediaId)}" +
                                    "&uri=${encodeRouteArg(uri)}" +
                                    "&title=${encodeRouteArg(title)}" +
                                    "&artist=${encodeRouteArg(artist)}" +
                                    "&artworkUri=${encodeRouteArg(artworkUri)}" +
                                    "&albumId=$albumId" +
                                    "&trackId=$trackId" +
                                    "&rjCode=${encodeRouteArg(rjCode)}"
                            )
                        },
                        onOpenGroupPicker = { albumId ->
                            navController.navigateSingleTop("group_picker?albumId=$albumId")
                        },
                        onPlayVideo = { title, uriOrPath, artwork, artist ->
                            playerViewModel.playVideo(title, uriOrPath, artwork, artist)
                            openNowPlaying()
                        },
                        onOpenDlsiteLogin = { navController.navigateSingleTop("dlsite_login") },
                        onOpenAlbumByRj = { navigator.openAlbumDetailByRjStacked(it) }
                    )
                }
                composable(
                    route = "album_detail_online/{source}/{workId}",
                    arguments = listOf(
                        navArgument("source") { defaultValue = SearchSource.DLSite.name },
                        navArgument("workId") { defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val workId = backStackEntry.arguments?.getString("workId").orEmpty()
                    LaunchedEffect(workId) {
                        if (workId.isNotBlank()) {
                            navController.navigate("album_detail_online/$workId") {
                                launchSingleTop = true
                                popUpTo("album_detail_online/{source}/{workId}") { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
                }
                composable("playlists") {
                    Box(modifier = Modifier.fillMaxSize())
                }
                composable("groups") {
                    Box(modifier = Modifier.fillMaxSize())
                }
                composable(
                    route = "group/{groupId}/{groupName}",
                    arguments = listOf(
                        navArgument("groupId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("groupName") { defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                    val groupName = decodeRouteArg(backStackEntry.arguments?.getString("groupName").orEmpty())
                    com.asmr.player.ui.groups.AlbumGroupDetailScreen(
                        windowSizeClass = windowSizeClass,
                        groupId = groupId,
                        title = groupName,
                        onPlayMediaItems = { items, startIndex ->
                            playerViewModel.playMediaItems(items, startIndex)
                            openNowPlaying()
                        }
                    )
                }
                composable(
                    route = "group_picker?albumId={albumId}",
                    arguments = listOf(
                        navArgument("albumId") { type = NavType.LongType; defaultValue = 0L }
                    )
                ) { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
                    com.asmr.player.ui.groups.AlbumGroupPickerScreen(
                        windowSizeClass = windowSizeClass,
                        albumId = albumId,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "playlist/{playlistId}/{playlistName}",
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.LongType; defaultValue = 0L },
                        navArgument("playlistName") { defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                    val playlistName = decodeRouteArg(backStackEntry.arguments?.getString("playlistName").orEmpty())
                    PlaylistDetailScreen(
                        windowSizeClass = windowSizeClass,
                        playlistId = playlistId,
                        title = playlistName,
                        onPlayAll = { items, startItem ->
                            playerViewModel.playPlaylistItems(items, startItem)
                            openNowPlaying()
                        }
                    )
                }
                composable("playlist_system/{type}") { backStackEntry ->
                    val type = backStackEntry.arguments?.getString("type").orEmpty()
                    if (type == "favorites") {
                        Box(modifier = Modifier.fillMaxSize())
                    } else {
                        SystemPlaylistScreen(
                            windowSizeClass = windowSizeClass,
                            type = type,
                            onPlayAll = { items, startItem ->
                                playerViewModel.playPlaylistItems(items, startItem)
                                openNowPlaying()
                            },
                            viewModel = playlistsViewModel
                        )
                    }
                }
                composable(
                    route = "playlist_picker?mediaId={mediaId}&uri={uri}&title={title}&artist={artist}&artworkUri={artworkUri}&albumId={albumId}&trackId={trackId}&rjCode={rjCode}",
                    arguments = listOf(
                        navArgument("mediaId") { defaultValue = "" },
                        navArgument("uri") { defaultValue = "" },
                        navArgument("title") { defaultValue = "" },
                        navArgument("artist") { defaultValue = "" },
                        navArgument("artworkUri") { defaultValue = "" },
                        navArgument("albumId") { type = NavType.LongType },
                        navArgument("trackId") { type = NavType.LongType },
                        navArgument("rjCode") { defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val mediaId = decodeRouteArg(backStackEntry.arguments?.getString("mediaId").orEmpty())
                    val uri = decodeRouteArg(backStackEntry.arguments?.getString("uri").orEmpty())
                    val title = decodeRouteArg(backStackEntry.arguments?.getString("title").orEmpty())
                    val artist = decodeRouteArg(backStackEntry.arguments?.getString("artist").orEmpty())
                    val artworkUri = decodeRouteArg(backStackEntry.arguments?.getString("artworkUri").orEmpty())
                    val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
                    val trackId = backStackEntry.arguments?.getLong("trackId") ?: 0L
                    val rjCode = decodeRouteArg(backStackEntry.arguments?.getString("rjCode").orEmpty())
                    PlaylistPickerScreen(
                        windowSizeClass = windowSizeClass,
                        mediaId = mediaId,
                        uri = uri,
                        title = title,
                        artist = artist,
                        artworkUri = artworkUri,
                        albumId = albumId,
                        trackId = trackId,
                        rjCode = rjCode,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    Box(modifier = Modifier.fillMaxSize())
                }
                composable("downloads") {
                    Box(modifier = Modifier.fillMaxSize())
                }
                composable("dlsite_login") {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }

                    if (blockNavTouches) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInteropFilter { true }
                        )
                    }
            }

            if (showManualRjDialog && navBackStackEntry != null &&
                (currentRoute?.startsWith("album_detail/{albumId}") == true || currentRoute?.startsWith("album_detail/") == true)
            ) {
                val albumDetailViewModel: AlbumDetailViewModel = hiltViewModel(navBackStackEntry!!)
                AlertDialog(
                    onDismissRequest = { showManualRjDialog = false },
                    title = { Text("手动绑定 RJ") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("请输入 RJ 号，保存后将自动执行云同步。", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = manualRjInput,
                                onValueChange = { manualRjInput = it },
                                singleLine = true,
                                label = { Text("RJ号（如 RJ123456）") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showManualRjDialog = false
                                albumDetailViewModel.manualSetRjAndSync(manualRjInput)
                            }
                        ) { Text("同步") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showManualRjDialog = false }) { Text("取消") }
                    }
                )
            }

            cloudSyncSelectionDialogState?.let { dialogState ->
                val ignoreAllHandler = if (bulkProgress != null) {
                    { libraryViewModel.ignoreAllCloudSyncSelections() }
                } else {
                    null
                }
                CloudSyncSelectionDialog(
                    state = dialogState,
                    onSelect = libraryViewModel::confirmCloudSyncSelection,
                    onCancel = libraryViewModel::cancelCloudSyncSelection,
                    onIgnoreAll = ignoreAllHandler
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (showHardwareVolumeOverlay) {
                    DismissOutsideBoundsOverlay(
                        targetBoundsInRoot = hardwareVolumeOverlayBounds,
                        onDismiss = {
                            showHardwareVolumeOverlay = false
                            hardwareVolumeOverlayBounds = null
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 18.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    AnimatedVisibility(
                        visible = showHardwareVolumeOverlay,
                        enter = fadeIn(animationSpec = tween(140)) + slideInHorizontally(animationSpec = tween(180)) { it / 3 },
                        exit = fadeOut(animationSpec = tween(160)) + slideOutHorizontally(animationSpec = tween(180)) { it / 3 }
                    ) {
                        HardwareVolumeOverlay(
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                hardwareVolumeOverlayBounds = coordinates.boundsInRoot()
                            },
                            volumePercent = appVolumePercent,
                            audioOutputRouteKind = audioOutputRouteKind,
                            onVolumeChange = {
                                playerViewModel.setAppVolumePercent(it)
                                hardwareVolumeOverlayHoldTick += 1L
                            },
                            onToggleMute = {
                                if (appVolumePercent > 0) {
                                    playerViewModel.setAppVolumePercent(0)
                                } else {
                                    playerViewModel.setAppVolumePercent(
                                        lastNonZeroAppVolumePercent.coerceAtLeast(AppVolume.StepPercent)
                                    )
                                }
                                hardwareVolumeOverlayHoldTick += 1L
                            },
                            onInteractionActiveChanged = { active ->
                                hardwareVolumeOverlayInteracting = active
                                if (!active) {
                                    hardwareVolumeOverlayHoldTick += 1L
                                }
                            },
                            warningSessionState = appVolumeWarningSessionState
                        )
                    }
                }
            }

            NonTouchableAppMessageOverlay(messages = visibleMessages)
        }

        }

        if (bottomChromeVisible) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val isCompactWidth = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
                val canUseRightPanel = !isCompactWidth &&
                    !isPhone &&
                    isLandscape &&
                    (currentRoute == "library" || currentRoute == "search")
                val rightPanelExpanded = rightPanelExpandedState.value
                val rightPanelWidth = (maxWidth - 560.dp).coerceAtMost(420.dp)
                val showRightPanel = canUseRightPanel && rightPanelWidth >= 300.dp
                val reservedRightTarget = if (!showRightPanel) {
                    0.dp
                } else if (rightPanelExpanded) {
                    rightPanelWidth + 12.dp
                } else {
                    36.dp + 12.dp
                }
                val reservedRight by animateDpAsState(
                    targetValue = reservedRightTarget,
                    animationSpec = tween(durationMillis = if (rightPanelExpanded) 220 else 180),
                    label = "miniPlayerReservedRight"
                )
                val chromeWidth = (maxWidth - reservedRight - 48.dp).coerceAtLeast(0.dp)
                if (bottomChromeOverflowExpanded) {
                    DismissOutsideBoundsOverlay(
                        targetBoundsInRoot = bottomChromeOverflowBounds,
                        onDismiss = { bottomChromeOverflowExpanded = false }
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .graphicsLayer { clip = false }
                        .padding(start = 22.dp, bottom = 24.dp)
                        .width(chromeWidth)
                ) {
                    BottomChrome(
                        activeRoute = activePrimaryRoute,
                        selectionProgresses = primaryNavSelectionProgresses,
                        preferredPinnedRoute = bottomChromePinnedRoute,
                        miniPlayerVisible = miniPlayerVisible,
                        miniPlayerDisplayMode = miniPlayerDisplayMode,
                        largeLayout = useLargeBottomChrome,
                        navItems = bottomNavItems,
                        overflowExpanded = bottomChromeOverflowExpanded,
                        onOverflowExpandedChange = { bottomChromeOverflowExpanded = it },
                        onOverflowBoundsChange = { bottomChromeOverflowBounds = it },
                        onMiniPlayerDisplayModeChange = { nextMode ->
                            miniPlayerDisplayMode = nextMode
                            scope.launch { settingsDataStore.setMiniPlayerDisplayMode(nextMode.name) }
                        },
                        onOpenNowPlaying = {
                            if (!nowPlayingVisible) {
                                openNowPlaying()
                            }
                        },
                        onOpenQueue = onShowQueue,
                        onNavigate = { route ->
                            val projectedPagerRoutes = if (route in bottomNavRoutes && route !in fixedBottomNavRoutes) {
                                resolvePrimaryPagerRoutes(
                                    navItems = bottomNavItems,
                                    activeRoute = activePrimaryRoute,
                                    preferredPinnedRoute = route
                                )
                            } else {
                                primaryPagerRoutes
                            }
                            if (route in bottomNavRoutes && route !in fixedBottomNavRoutes) {
                                bottomChromePinnedRoute = route
                                scope.launch { settingsDataStore.setBottomChromePinnedRoute(route) }
                            }
                            openPrimaryRoute(route, projectedPagerRoutes)
                        }
                    )
                }
            }
        }

        if (nowPlayingVisible) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInteropFilter { true }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.background.copy(alpha = 0.92f * nowPlayingBackdropAlpha))
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colorScheme.primarySoft.copy(alpha = 0.22f * nowPlayingBackdropAlpha))
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = nowPlayingBackdropAlpha }
                ) {
                    PlayerSharedBackdrop(
                        playback = sharedPlayerPlayback,
                        enabled = coverBackgroundEnabled,
                        clarity = coverBackgroundClarity,
                        artworkAlignment = sharedPlayerBackdropAlignment
                    )
                }
                NowPlayingScreen(
                    windowSizeClass = windowSizeClass,
                    hardwareVolumeEventTick = nowPlayingVolumeEventTick,
                    onBack = closeNowPlaying,
                    onRouteExitStarted = { exitDurationMs ->
                        nowPlayingBackdropExitDurationMs = exitDurationMs
                        nowPlayingBackdropActive = false
                    },
                    onShowQueue = onShowQueue,
                    onShowSleepTimer = onShowSleepTimer,
                    onOpenPlaylistPicker = { mediaId, uri, title, artist, artworkUri, albumId, trackId, rjCode ->
                        nowPlayingPlaylistPickerRequest = PlaylistPickerRequest(
                            mediaId = mediaId,
                            uri = uri,
                            title = title,
                            artist = artist,
                            artworkUri = artworkUri,
                            albumId = albumId,
                            trackId = trackId,
                            rjCode = rjCode
                        )
                    },
                    viewModel = playerViewModel,
                    coverBackgroundEnabled = coverBackgroundEnabled,
                    coverBackgroundClarity = coverBackgroundClarity,
                    coverPreviewMode = coverPreviewMode,
                    lyricsPageSettings = lyricsPageSettings,
                    audioOutputRouteKind = audioOutputRouteKind,
                    warningSessionState = appVolumeWarningSessionState,
                    renderBackdrop = false,
                    sharedArtworkAlignment = sharedPlayerBackdropAlignment,
                    sharedCoverDragPreviewState = sharedCoverDragPreviewState
                )
                nowPlayingPlaylistPickerRequest?.let { request ->
                    Dialog(
                        onDismissRequest = { nowPlayingPlaylistPickerRequest = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = colorScheme.background.copy(alpha = 0.96f),
                            contentColor = colorScheme.onBackground
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(StableWindowInsets.statusBars)
                                    .windowInsetsPadding(StableWindowInsets.navigationBars)
                            ) {
                                PlaylistPickerScreen(
                                    windowSizeClass = windowSizeClass,
                                    mediaId = request.mediaId,
                                    uri = request.uri,
                                    title = request.title,
                                    artist = request.artist,
                                    artworkUri = request.artworkUri,
                                    albumId = request.albumId,
                                    trackId = request.trackId,
                                    rjCode = request.rjCode,
                                    onBack = { nowPlayingPlaylistPickerRequest = null },
                                    embeddedInDialog = true
                                )
                            }
                        }
                    }
                }
                albumBatchPlaylistPickerRequest?.let { request ->
                    Dialog(
                        onDismissRequest = { albumBatchPlaylistPickerRequest = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = colorScheme.background.copy(alpha = 0.96f),
                            contentColor = colorScheme.onBackground
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(StableWindowInsets.statusBars)
                                    .windowInsetsPadding(StableWindowInsets.navigationBars)
                            ) {
                                PlaylistPickerScreen(
                                    windowSizeClass = windowSizeClass,
                                    items = request.items,
                                    onBack = { albumBatchPlaylistPickerRequest = null },
                                    embeddedInDialog = true
                                )
                            }
                        }
                    }
                }
            }
            if (!nowPlayingVisible) {
                albumBatchPlaylistPickerRequest?.let { request ->
                    Dialog(
                        onDismissRequest = { albumBatchPlaylistPickerRequest = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = colorScheme.background.copy(alpha = 0.96f),
                            contentColor = colorScheme.onBackground
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(StableWindowInsets.statusBars)
                                    .windowInsetsPadding(StableWindowInsets.navigationBars)
                            ) {
                                PlaylistPickerScreen(
                                    windowSizeClass = windowSizeClass,
                                    items = request.items,
                                    onBack = { albumBatchPlaylistPickerRequest = null },
                                    embeddedInDialog = true
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

