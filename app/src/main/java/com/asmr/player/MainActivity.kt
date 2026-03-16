package com.asmr.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
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
import com.asmr.player.ui.library.LibraryFilterScreen
import com.asmr.player.ui.library.LibraryScreen
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.library.BulkPhase
import com.asmr.player.ui.player.MiniPlayer
import com.asmr.player.ui.player.NowPlayingScreen
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.player.LyricsPage
import com.asmr.player.ui.sidepanel.LocalRightPanelExpandedState
import com.asmr.player.ui.common.rememberDominantColorCenterWeighted
import com.asmr.player.ui.downloads.DownloadsScreen
import com.asmr.player.ui.downloads.DownloadsViewModel
import com.asmr.player.ui.downloads.DownloadItemState
import com.asmr.player.ui.dlsite.DlsiteLoginScreen
import com.asmr.player.ui.playlists.PlaylistDetailScreen
import com.asmr.player.ui.playlists.PlaylistPickerScreen
import com.asmr.player.ui.playlists.PlaylistsScreen
import com.asmr.player.ui.playlists.SystemPlaylistScreen
import com.asmr.player.ui.search.SearchScreen
import com.asmr.player.ui.search.SearchViewModel
import com.asmr.player.domain.model.SearchSource
import com.asmr.player.ui.settings.SettingsScreen
import com.asmr.player.ui.common.glassMenu
import com.asmr.player.ui.drawer.DrawerStatusViewModel
import com.asmr.player.ui.drawer.StatisticsViewModel
import com.asmr.player.ui.drawer.SiteStatus
import com.asmr.player.ui.drawer.SiteStatusType
import com.asmr.player.ui.nav.AppNavigator
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.player.MiniPlayerOverlayHeight
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.painterResource

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import com.asmr.player.ui.player.QueueSheetContent
import com.asmr.player.ui.player.SleepTimerSheetContent

import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.util.MessageManager
import com.asmr.player.ui.common.NonTouchableAppMessageOverlay
import com.asmr.player.ui.common.VisibleAppMessage
import com.asmr.player.ui.theme.HuePalette
import com.asmr.player.ui.theme.PlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.ui.theme.DefaultBrandPrimaryDark
import com.asmr.player.ui.theme.DefaultBrandPrimaryLight
import com.asmr.player.ui.theme.deriveHuePalette
import com.asmr.player.ui.theme.neutralPaletteForMode
import com.asmr.player.ui.theme.rememberDynamicHuePalette
import com.asmr.player.ui.theme.rememberDynamicHuePaletteFromVideoFrame
import com.asmr.player.ui.theme.dynamicPageContainerColor
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.media3.common.MediaItem

private enum class OverlaySheet {
    Queue,
    SleepTimer
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var messageManager: MessageManager

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recentAlbumsPanelExpandedInitial = false
        val startRouteFromIntent = intent.getStringExtra("start_route")
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val context = LocalContext.current
            val playerViewModel: PlayerViewModel = hiltViewModel()
            val libraryViewModel: LibraryViewModel = hiltViewModel()
            val themeMediaSource by remember(playerViewModel) {
                playerViewModel.playback
                    .map { it.currentMediaItem.toThemeMediaSource() }
                    .distinctUntilChanged()
            }.collectAsState(initial = ThemeMediaSource())
            val systemDark = isSystemInDarkTheme()
            val themePref by settingsDataStore.theme.collectAsState(initial = "system")
            val mode = when (themePref.lowercase()) {
                "light" -> ThemeMode.Light
                "soft_dark" -> ThemeMode.SoftDark
                "dark" -> ThemeMode.Dark
                "system" -> if (systemDark) ThemeMode.Dark else ThemeMode.Light
                else -> if (systemDark) ThemeMode.Dark else ThemeMode.Light
            }
            val artworkUri = themeMediaSource.artworkUri
            val videoUri = themeMediaSource.videoUri
            val isVideo = themeMediaSource.isVideo
            val globalDynamicHueEnabled by settingsDataStore.dynamicPlayerHueEnabled.collectAsState(initial = false)
            val staticHueArgb by settingsDataStore.staticHueArgb.collectAsState(initial = null)
            val coverBackgroundEnabled by settingsDataStore.coverBackgroundEnabled.collectAsState(initial = true)
            val coverBackgroundClarity by settingsDataStore.coverBackgroundClarity.collectAsState(initial = 0.35f)
            val coverMotionEnabled by settingsDataStore.coverMotionEnabled.collectAsState(initial = false)
            val neutral = remember(mode) { neutralPaletteForMode(mode) }
            val cacheManager = remember(context.applicationContext) {
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.asmr.player.cache.ImageCacheEntryPoint::class.java
                ).imageCacheManager()
            }

            LaunchedEffect(artworkUri) {
                val uri = artworkUri ?: return@LaunchedEffect
                runCatching {
                    cacheManager.loadImage(
                        model = uri,
                        size = androidx.compose.ui.unit.IntSize(512, 512)
                    )
                }
            }

            if (isVideo && artworkUri == null) {
                PrewarmVideoFrameDominantColorCenterWeighted(
                    videoUri = videoUri,
                    defaultColor = neutral.background
                )
            } else {
                PrewarmDominantColorCenterWeighted(
                    model = artworkUri,
                    defaultColor = neutral.background
                )
            }
            val staticHue: HuePalette? = remember(staticHueArgb, mode, neutral) {
                staticHueArgb?.let { argb ->
                    deriveHuePalette(
                        primary = Color(argb),
                        mode = mode,
                        neutral = neutral,
                        fallbackOnPrimary = if (mode.isDark) Color.White else Color.Black
                    )
                }
            }
            val baseStaticHue = remember(mode, neutral, staticHue) {
                staticHue ?: deriveHuePalette(
                    primary = if (mode.isDark) DefaultBrandPrimaryDark else DefaultBrandPrimaryLight,
                    mode = mode,
                    neutral = neutral,
                    fallbackOnPrimary = if (mode.isDark) Color.White else Color.Black
                )
            }
            val globalHue = if (globalDynamicHueEnabled) {
                val state = if (isVideo && artworkUri == null) {
                    rememberDynamicHuePaletteFromVideoFrame(
                        videoUri = videoUri,
                        mode = mode,
                        neutral = neutral,
                        fallbackHue = baseStaticHue,
                        transitionDurationMs = 0,
                        cachedTransitionDurationMs = 0
                    )
                } else {
                    rememberDynamicHuePalette(
                        artworkModel = artworkUri,
                        mode = mode,
                        neutral = neutral,
                        fallbackHue = baseStaticHue,
                        transitionDurationMs = 0,
                        cachedTransitionDurationMs = 0
                    )
                }
                state.value
            } else baseStaticHue

            var overlaySheet by remember { mutableStateOf<OverlaySheet?>(null) }

            val visibleMessages = remember { mutableStateListOf<VisibleAppMessage>() }
            val dismissJobs = remember { linkedMapOf<Long, kotlinx.coroutines.Job>() }
            var messageSeq by remember { mutableLongStateOf(0L) }

            LaunchedEffect(Unit) {
                val maxVisible = 8

                messageManager.messages.collect { appMessage ->
                    val now = System.currentTimeMillis()
                    if ((now - appMessage.createdAtMs) > 10_000L) return@collect
                    val normalized = appMessage.message.trim()
                    if (normalized.isBlank()) return@collect
                    val displayMs = appMessage.durationMs.coerceIn(1500L, 4500L)

                    val id = ++messageSeq
                    visibleMessages.add(
                        0,
                        VisibleAppMessage(
                            id = id,
                            renderId = id,
                            key = id.toString(),
                            message = normalized,
                            type = appMessage.type,
                            count = 1,
                            durationMs = displayMs
                        )
                    )
                    dismissJobs[id] = launch {
                        delay(displayMs)
                        visibleMessages.removeAll { it.id == id }
                        dismissJobs.remove(id)
                    }

                    while (visibleMessages.size > maxVisible) {
                        val removed = visibleMessages.removeLast()
                        dismissJobs.remove(removed.id)?.cancel()
                    }
                }
            }

            AsmrPlayerTheme(mode = mode, hue = globalHue) {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                var contentReady by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxSize()) {
                    val visibleMessagesSnapshot = visibleMessages.toList()
                    MainContainer(
                        windowSizeClass = windowSizeClass,
                        playerViewModel = playerViewModel,
                        libraryViewModel = libraryViewModel,
                        settingsDataStore = settingsDataStore,
                        recentAlbumsPanelExpandedInitial = recentAlbumsPanelExpandedInitial,
                        startRouteFromIntent = startRouteFromIntent,
                        onShowQueue = { overlaySheet = OverlaySheet.Queue },
                        onShowSleepTimer = { overlaySheet = OverlaySheet.SleepTimer },
                        onContentReady = { contentReady = true },
                        visibleMessages = visibleMessagesSnapshot,
                        mode = mode,
                        globalDynamicHueEnabled = globalDynamicHueEnabled,
                        coverBackgroundEnabled = coverBackgroundEnabled,
                        coverBackgroundClarity = coverBackgroundClarity,
                        coverMotionEnabled = coverMotionEnabled,
                        forceImmersive = showSplash,
                        baseStaticHue = baseStaticHue
                    )

                    if (showSplash) {
                        EaraSplashOverlay(
                            isReady = contentReady,
                            onFinished = { showSplash = false }
                        )
                    }
                    
                    val overlayConfiguration = LocalConfiguration.current
                    val activeOverlaySheet = overlaySheet
                    if (activeOverlaySheet != null) {
                        val sheetMaxHeight = overlayConfiguration.screenHeightDp.dp * 3 / 4
                        key(
                            activeOverlaySheet,
                            overlayConfiguration.screenWidthDp,
                            overlayConfiguration.screenHeightDp
                        ) {
                            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                            ModalBottomSheet(
                                onDismissRequest = { overlaySheet = null },
                                sheetState = sheetState,
                                containerColor = MaterialTheme.colorScheme.background,
                                contentColor = MaterialTheme.colorScheme.onBackground
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = sheetMaxHeight)
                                ) {
                                    when (activeOverlaySheet) {
                                        OverlaySheet.Queue -> QueueSheetContent(
                                            viewModel = playerViewModel,
                                            onDismiss = { overlaySheet = null },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = sheetMaxHeight)
                                        )

                                        OverlaySheet.SleepTimer -> SleepTimerSheetContent(
                                            viewModel = playerViewModel,
                                            onDismiss = { overlaySheet = null },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = sheetMaxHeight)
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

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
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
    mode: ThemeMode,
    globalDynamicHueEnabled: Boolean,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    coverMotionEnabled: Boolean,
    forceImmersive: Boolean,
    baseStaticHue: HuePalette
) {
    val navController = rememberNavController()
    val navigator = remember(navController) { AppNavigator(navController) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val initialDestination = remember(startRouteFromIntent) {
        if (startRouteFromIntent == "search") "search" else "library"
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        onContentReady()
    }
    var blockNavTouches by remember { mutableStateOf(false) }
    var lastRouteForTouchBlock by remember { mutableStateOf(currentRoute) }
    var touchBlockSeq by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val hasCurrentMediaItem by remember(playerViewModel) {
        playerViewModel.playback
            .map { it.currentMediaItem != null }
            .distinctUntilChanged()
    }.collectAsState(initial = false)
    val drawerStatusViewModel: DrawerStatusViewModel = hiltViewModel()
    val statisticsViewModel: StatisticsViewModel = hiltViewModel()
    val bulkProgress by libraryViewModel.bulkProgress.collectAsState()
    var showManualRjDialog by remember { mutableStateOf(false) }
    var manualRjInput by remember { mutableStateOf("") }
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 使用 smallestScreenWidthDp 判定是否为手机 (一般 < 600dp 为手机)
    val isPhone = configuration.smallestScreenWidthDp < 600
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val immersivePlayer = forceImmersive || currentRoute == "now_playing" || currentRoute == "lyrics"

    LaunchedEffect(currentRoute) {
        val last = lastRouteForTouchBlock
        val seq = ++touchBlockSeq
        if (last != null && currentRoute != null && last != currentRoute) {
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
        lastRouteForTouchBlock = currentRoute
    }

    val defaultSystemUi = remember(activity) {
        activity?.let { act ->
            val controller = WindowInsetsControllerCompat(act.window, act.window.decorView)
            DefaultSystemUiState(
                statusBarColor = act.window.statusBarColor,
                navigationBarColor = act.window.navigationBarColor,
                lightStatusBars = controller.isAppearanceLightStatusBars,
                lightNavigationBars = controller.isAppearanceLightNavigationBars
            )
        }
    }

    DisposableEffect(activity, immersivePlayer) {
        val act = activity ?: return@DisposableEffect onDispose { }
        val window = act.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // 始终由应用控制系统栏区域绘制，避免 fitsSystemWindows 切换导致的布局跳动
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (immersivePlayer) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            defaultSystemUi?.let { ui ->
                window.statusBarColor = ui.statusBarColor
                window.navigationBarColor = ui.navigationBarColor
                controller.isAppearanceLightStatusBars = ui.lightStatusBars
                controller.isAppearanceLightNavigationBars = ui.lightNavigationBars
            }
        }
        onDispose {
            val act2 = activity ?: return@onDispose
            val window2 = act2.window
            val controller2 = WindowInsetsControllerCompat(window2, window2.decorView)
            // 退出时保持 false，交给 Compose 处理 padding
            WindowCompat.setDecorFitsSystemWindows(window2, false)
            controller2.show(WindowInsetsCompat.Type.systemBars())
            defaultSystemUi?.let { ui ->
                window2.statusBarColor = ui.statusBarColor
                window2.navigationBarColor = ui.navigationBarColor
                controller2.isAppearanceLightStatusBars = ui.lightStatusBars
                controller2.isAppearanceLightNavigationBars = ui.lightNavigationBars
            }
        }
    }

    // 屏幕旋转管理逻辑
    LaunchedEffect(currentRoute, isPhone) {
        activity?.let { act ->
            if (isPhone) {
                if (currentRoute == "now_playing" || currentRoute == "lyrics") {
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

    val colorScheme = AsmrTheme.colorScheme
    val materialColorScheme = MaterialTheme.colorScheme
    val dynamicContainerColor = dynamicPageContainerColor(colorScheme)
    val surfaceColor = colorScheme.surface
    
    val topBarContainerColor = Color.Transparent
    val topBarContentColor = colorScheme.onSurface
    
    val drawerContainerColor = if (colorScheme.isDark) Color(0xFF121212) else Color.White

    BackHandler(drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
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
                                        onClick = { navController.navigateSingleTop(route, popUpToRoute = "library") }
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
        val miniPlayerVisible = hasCurrentMediaItem && currentRoute != "now_playing" && currentRoute != "lyrics"
        val rightPanelExpandedFromStore by settingsDataStore.recentAlbumsPanelExpanded.collectAsState(initial = recentAlbumsPanelExpandedInitial)
        val rightPanelExpandedState = remember(settingsDataStore, scope, recentAlbumsPanelExpandedInitial) {
            PersistedBooleanState(initial = recentAlbumsPanelExpandedInitial) { expanded ->
                scope.launch { settingsDataStore.setRecentAlbumsPanelExpanded(expanded) }
            }
        }
        LaunchedEffect(rightPanelExpandedFromStore) {
            rightPanelExpandedState.updateFromStore(rightPanelExpandedFromStore)
        }
        CompositionLocalProvider(
            LocalBottomOverlayPadding provides (if (miniPlayerVisible) MiniPlayerOverlayHeight else 0.dp),
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
                        containerColor = Color.Transparent,
                        contentColor = colorScheme.onBackground,
                        topBar = {
                            if (currentRoute != "now_playing" && currentRoute != "lyrics") {
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
                                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
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
                                            if (currentRoute == "library_filter" || currentRoute?.startsWith("playlist_picker") == true) {
                                                IconButton(onClick = { navController.popBackStack() }) {
                                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                                }
                                            } else {
                                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                                    Icon(Icons.Default.Menu, contentDescription = null)
                                                }
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
                                            } else if (entry != null && currentRoute == "search") {
                                                val searchViewModel: SearchViewModel = hiltViewModel(entry)
                                                val viewMode by searchViewModel.viewMode.collectAsState()
                                                IconButton(onClick = { searchViewModel.setViewMode(if (viewMode == 1) 0 else 1) }) {
                                                    Icon(
                                                        imageVector = if (viewMode == 1) Icons.Default.ViewList else Icons.Default.ViewModule,
                                                        contentDescription = null
                                                    )
                                                }
                                            } else if (entry != null && currentRoute == "downloads") {
                                                val downloadsViewModel: DownloadsViewModel = hiltViewModel(entry)
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
                        }
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = padding.calculateTopPadding())
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = initialDestination,
                                modifier = Modifier.fillMaxSize()
                            ) {

                composable("library") {
                    LibraryScreen(
                        windowSizeClass = windowSizeClass,
                        onAlbumClick = { album ->
                            navigator.openAlbumDetail(
                                albumId = album.id,
                                rj = null
                            )
                        },
                        onPlayTracks = { album, tracks, startTrack ->
                            playerViewModel.playTracks(album, tracks, startTrack)
                            navController.navigateSingleTop("now_playing")
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
                                composable("library_filter") {
                    LibraryFilterScreen(
                        onClose = { navController.popBackStack() },
                        viewModel = libraryViewModel
                    )
                }
                                composable("search") {
                    SearchScreen(
                        windowSizeClass = windowSizeClass,
                        onAlbumClick = { album ->
                            navigator.openAlbumDetail(
                                albumId = album.id,
                                rj = album.rjCode.ifBlank { album.workId }
                            )
                        }
                    )
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
                            playerViewModel.playTracks(album, tracks, startTrack)
                            navController.navigateSingleTop("now_playing")
                        },
                        onPlayMediaItems = { items, startIndex ->
                            playerViewModel.playMediaItems(items, startIndex)
                            navController.navigateSingleTop("now_playing")
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
                            navController.navigateSingleTop("now_playing")
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
                            playerViewModel.playTracks(album, tracks, startTrack)
                            navController.navigateSingleTop("now_playing")
                        },
                        onPlayMediaItems = { items, startIndex ->
                            playerViewModel.playMediaItems(items, startIndex)
                            navController.navigateSingleTop("now_playing")
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
                            navController.navigateSingleTop("now_playing")
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
                            playerViewModel.playTracks(album, tracks, startTrack)
                            navController.navigateSingleTop("now_playing")
                        },
                        onPlayMediaItems = { items, startIndex ->
                            playerViewModel.playMediaItems(items, startIndex)
                            navController.navigateSingleTop("now_playing")
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
                            navController.navigateSingleTop("now_playing")
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
                composable(
                    route = "now_playing",
                    enterTransition = {
                        slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
                    },
                    exitTransition = {
                        slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
                    }
                ) {
                    if (globalDynamicHueEnabled) {
                        NowPlayingScreen(
                            windowSizeClass = windowSizeClass,
                            onBack = { navController.popBackStack() },
                            onOpenLyrics = { navController.navigateSingleTop("lyrics") },
                            onShowQueue = onShowQueue,
                            onShowSleepTimer = onShowSleepTimer,
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
                            viewModel = playerViewModel,
                            coverBackgroundEnabled = coverBackgroundEnabled,
                            coverBackgroundClarity = coverBackgroundClarity,
                            coverMotionEnabled = coverMotionEnabled
                        )
                    } else {
                        NowPlayingScreen(
                            windowSizeClass = windowSizeClass,
                            onBack = { navController.popBackStack() },
                            onOpenLyrics = { navController.navigateSingleTop("lyrics") },
                            onShowQueue = onShowQueue,
                            onShowSleepTimer = onShowSleepTimer,
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
                            viewModel = playerViewModel,
                            coverBackgroundEnabled = coverBackgroundEnabled,
                            coverBackgroundClarity = coverBackgroundClarity,
                            coverMotionEnabled = coverMotionEnabled
                        )
                    }
                }
                composable("lyrics") {
                    if (globalDynamicHueEnabled) {
                        LyricsPage(
                            onBack = { navController.popBackStack() },
                            onSeekTo = { pos -> playerViewModel.seekTo(pos) },
                            playerViewModel = playerViewModel,
                            coverBackgroundEnabled = coverBackgroundEnabled,
                            coverBackgroundClarity = coverBackgroundClarity,
                            coverMotionEnabled = coverMotionEnabled
                        )
                    } else {
                        LyricsPage(
                            onBack = { navController.popBackStack() },
                            onSeekTo = { pos -> playerViewModel.seekTo(pos) },
                            playerViewModel = playerViewModel,
                            coverBackgroundEnabled = coverBackgroundEnabled,
                            coverBackgroundClarity = coverBackgroundClarity,
                            coverMotionEnabled = coverMotionEnabled
                        )
                    }
                }
                composable("playlists") {
                    PlaylistsScreen(
                        windowSizeClass = windowSizeClass,
                        onPlaylistClick = { playlist ->
                            val encoded = URLEncoder.encode(playlist.name, "UTF-8")
                            navController.navigateSingleTop("playlist/${playlist.id}/$encoded")
                        }
                    )
                }
                composable("groups") {
                    com.asmr.player.ui.groups.AlbumGroupsScreen(
                        windowSizeClass = windowSizeClass,
                        onGroupClick = { group ->
                            val encoded = encodeRouteArg(group.name)
                            navController.navigateSingleTop("group/${group.id}/$encoded")
                        }
                    )
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
                            navController.navigateSingleTop("now_playing")
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
                            navController.navigateSingleTop("now_playing")
                        }
                    )
                }
                composable("playlist_system/{type}") { backStackEntry ->
                    val type = backStackEntry.arguments?.getString("type").orEmpty()
                    SystemPlaylistScreen(
                        windowSizeClass = windowSizeClass,
                        type = type,
                        onPlayAll = { items, startItem ->
                            playerViewModel.playPlaylistItems(items, startItem)
                            navController.navigateSingleTop("now_playing")
                        }
                    )
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
                    SettingsScreen(
                        windowSizeClass = windowSizeClass,
                        libraryViewModel = libraryViewModel
                    )
                }
                composable("downloads") {
                    DownloadsScreen(windowSizeClass = windowSizeClass)
                }
                composable("dlsite_login") {
                    DlsiteLoginScreen(
                        windowSizeClass = windowSizeClass,
                        onDone = { navController.popBackStack() }
                    )
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

            NonTouchableAppMessageOverlay(messages = visibleMessages)
        }

        }

        if (miniPlayerVisible) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                val miniWidth = (maxWidth - reservedRight).coerceAtLeast(0.dp)
                val miniAlignment = Alignment.BottomStart
                Box(
                    modifier = Modifier
                        .align(miniAlignment)
                        .padding(start = 24.dp) // 增加左侧外边距
                        .width(miniWidth - 24.dp) // 宽度相应减小
                ) {
                    MiniPlayer(
                        onClick = {
                            if (currentRoute != "now_playing") {
                                navController.navigateSingleTop("now_playing")
                            }
                        },
                        onOpenQueue = onShowQueue
                    )
                }
            }
        }
    }
}

}

}

@Stable
private class PersistedBooleanState(
    initial: Boolean,
    private val save: (Boolean) -> Unit
) : MutableState<Boolean> {
    private var backing by mutableStateOf(initial)

    override var value: Boolean
        get() = backing
        set(value) {
            if (backing == value) return
            backing = value
            save(value)
        }

    override fun component1(): Boolean = value

    override fun component2(): (Boolean) -> Unit = { value = it }

    fun updateFromStore(value: Boolean) {
        backing = value
    }
}

@Composable
private fun DrawerNavCardItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val unselectedColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)
    val selectedColor = if (isDark) Color(0xFF2A2A2A) else Color.White
    val containerColor = if (selected) selectedColor else unselectedColor
    val selectedContentColor = colorScheme.primaryStrong
    val contentColor = if (selected) selectedContentColor else colorScheme.textPrimary
    val elevation = if (selected || isDark) 0.dp else 2.dp
    val shape = RoundedCornerShape(18.dp)

    ElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDark) {
                    Modifier.border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = shape
                    )
                } else Modifier
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val iconTint = if (selected) selectedContentColor else colorScheme.textSecondary
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = iconTint
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DrawerSiteStatusFooter(
    viewModel: DrawerStatusViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val dlsite by viewModel.dlsite.collectAsState()
    val asmr by viewModel.asmr.collectAsState()
    val site by viewModel.asmrOneSite.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DrawerSiteRow(
            name = "dlsite.com",
            status = dlsite,
            onTest = { viewModel.testDlsite() }
        )

        DrawerSiteRow(
            status = asmr,
            onTest = { viewModel.testAsmrOne() },
            nameContent = {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { expanded = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "asmr-$site",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textPrimary,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colorScheme.textSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf(100, 200, 300).forEach { opt ->
                            val selected = opt == site
                            DropdownMenuItem(
                                text = { Text(opt.toString()) },
                                onClick = {
                                    viewModel.setAsmrOneSite(opt)
                                    expanded = false
                                },
                                leadingIcon = {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = colorScheme.primary
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(18.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun DrawerSiteRow(
    status: SiteStatus,
    onTest: () -> Unit,
    name: String? = null,
    nameContent: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val dotColor = when (status.type) {
        SiteStatusType.Ok -> Color(0xFF2E7D32) // 绿色
        SiteStatusType.Fail -> Color(0xFFC62828) // 红色
        SiteStatusType.Testing -> Color(0xFFF9A825) // 黄色
        SiteStatusType.Unknown -> colorScheme.onSurface.copy(alpha = 0.35f)
    }
    val statusIcon = when (status.type) {
        SiteStatusType.Ok -> Icons.Default.Check
        SiteStatusType.Fail -> Icons.Default.Close
        SiteStatusType.Testing -> Icons.Default.Refresh
        SiteStatusType.Unknown -> null
    }
    
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(status.type) {
        if (status.type == SiteStatusType.Testing) {
            while (true) {
                rotationAngle = (rotationAngle + 10f) % 360f
                kotlinx.coroutines.delay(50)
            }
        } else {
            rotationAngle = 0f
        }
    }
    
    val shape = RoundedCornerShape(16.dp)
    val elevation = if (isDark) 0.dp else 1.dp
    val containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDark) {
                    Modifier.border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = shape
                    )
                } else Modifier
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            if (name != null) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.textPrimary,
                    maxLines = 1
                )
            } else if (nameContent != null) {
                nameContent()
            }

            Spacer(modifier = Modifier.weight(1f))
            if (statusIcon != null) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            rotationZ = rotationAngle
                        },
                    tint = dotColor
                )
            }
            FilledTonalButton(
                onClick = onTest,
                modifier = Modifier.height(30.dp).widthIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorScheme.primarySoft,
                    contentColor = colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "测试", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            if (trailing != null) {
                Box(modifier = Modifier.height(32.dp)) {
                    trailing()
                }
            }
        }
    }
}

@Composable
private fun DailyStatisticsFooter(
    viewModel: StatisticsViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.todayStats.collectAsState()
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val shape = RoundedCornerShape(16.dp)
    val elevation = if (isDark) 0.dp else 1.dp
    val containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDark) {
                    Modifier.border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = shape
                    )
                } else Modifier
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "今日收听统计",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textSecondary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(
                    icon = Icons.Default.AccessTime,
                    label = "时长",
                    value = formatStatsDuration(stats?.listeningDurationMs ?: 0L)
                )
                StatItem(
                    icon = Icons.Default.Audiotrack,
                    label = "音轨",
                    value = "${stats?.trackCount ?: 0}"
                )
                StatItem(
                    icon = Icons.Default.CloudDownload,
                    label = "流量",
                    value = formatStatsTraffic(stats?.networkTrafficBytes ?: 0L)
                )
            }
        }
    }
}

@Composable
private fun StatItem(icon: ImageVector, label: String, value: String) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val iconBackground = if (isDark) Color(0xFF1E1E1E) else Color.White
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(if (isDark) Modifier else Modifier.shadow(elevation = 1.dp, shape = CircleShape, clip = false))
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colorScheme.textSecondary
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.textPrimary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary,
            fontSize = 10.sp
        )
    }
}

private fun formatStatsDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "${hours}h${minutes % 60}m"
    } else {
        "${minutes}m"
    }
}

private fun formatStatsTraffic(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1fG", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format("%.1fM", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format("%.1fK", bytes / 1024.0)
        else -> "${bytes}B"
    }
}

private fun encodeRouteArg(value: String): String = URLEncoder.encode(value, "UTF-8")

private fun decodeRouteArg(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }
    .getOrDefault(value)

private fun NavHostController.navigateSingleTop(route: String, popUpToRoute: String? = null) {
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        if (!popUpToRoute.isNullOrBlank()) {
            popUpTo(popUpToRoute) {
                saveState = true
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmallTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon
    )
}

private data class DefaultSystemUiState(
    val statusBarColor: Int,
    val navigationBarColor: Int,
    val lightStatusBars: Boolean,
    val lightNavigationBars: Boolean
)

private data class ThemeMediaSource(
    val artworkUri: Uri? = null,
    val videoUri: Uri? = null,
    val isVideo: Boolean = false
)

private fun MediaItem?.toThemeMediaSource(): ThemeMediaSource {
    val item = this ?: return ThemeMediaSource()
    val metadata = item.mediaMetadata
    val artworkUri = metadata.artworkUri
    val videoUri = item.localConfiguration?.uri
    val mimeType = item.localConfiguration?.mimeType.orEmpty()
    val uriText = videoUri?.toString().orEmpty()
    val fileExtension = uriText
        .substringBefore('#')
        .substringBefore('?')
        .substringAfterLast('.', "")
        .lowercase()
    val isVideo = metadata.extras?.getBoolean("is_video") == true ||
        mimeType.startsWith("video/") ||
        fileExtension in setOf("mp4", "m4v", "webm", "mkv", "mov")
    return ThemeMediaSource(
        artworkUri = artworkUri,
        videoUri = videoUri,
        isVideo = isVideo
    )
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
