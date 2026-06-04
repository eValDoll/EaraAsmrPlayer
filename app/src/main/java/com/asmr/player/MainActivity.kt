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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CloudDownload
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
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
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.PrewarmDynamicHuePalette
import com.asmr.player.ui.theme.PrewarmDynamicHuePaletteFromVideoFrame
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import com.asmr.player.ui.player.QueueSheetContent
import com.asmr.player.ui.player.SleepTimerSheetContent
import com.asmr.player.ui.player.MiniPlayerDisplayMode

import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.local.datastore.ThemeBootstrapPreferences
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
import com.asmr.player.ui.theme.dynamicHueCacheKeyForArtwork
import com.asmr.player.ui.theme.dynamicHueCacheKeyForVideo
import com.asmr.player.ui.theme.peekDynamicHueSeedColor
import com.asmr.player.ui.theme.ThemeTransitionRequest
import com.asmr.player.ui.theme.ThemeTransitionTriggerRequest
import com.asmr.player.ui.theme.LocalThemeTransitionTrigger
import com.asmr.player.ui.theme.ThemeCircularRevealOverlay
import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.runBlocking

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

private const val MONOCHROME_THEME_SENTINEL = 0x01000000

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

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var listeningTracker: com.asmr.player.hotlistening.ListeningTracker

    private lateinit var initialThemeBootstrapPreferences: ThemeBootstrapPreferences

    private val volumeKeyEventTick = MutableStateFlow(0L)
    private var volumeKeyEventSeq = 0L

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialThemeBootstrapPreferences = runBlocking {
            settingsDataStore.loadThemeBootstrapPreferences()
        }
        val recentAlbumsPanelExpandedInitial = false
        val startRouteFromIntent = intent.getStringExtra("start_route")
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val context = LocalContext.current
            val playerViewModel: PlayerViewModel = hiltViewModel()
            val libraryViewModel: LibraryViewModel = hiltViewModel()
            val volumeKeyTick by volumeKeyEventTick.asStateFlow().collectAsState()
            var themeBootstrap by remember {
                mutableStateOf(initialThemeBootstrapPreferences)
            }
            LaunchedEffect(settingsDataStore) {
                settingsDataStore.themeBootstrapPreferences.collect { value ->
                    themeBootstrap = value
                }
            }
            val themeMediaSource by remember(playerViewModel) {
                playerViewModel.playback
                    .map { it.currentMediaItem.toThemeMediaSource() }
                    .distinctUntilChanged()
            }.collectAsState(initial = ThemeMediaSource())
            val playbackSnapshot by playerViewModel.playback.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val themePref = themeBootstrap.theme
            val mode = resolveThemeMode(themePref = themePref, systemDark = systemDark)
            val artworkUri = themeMediaSource.artworkUri
            val videoUri = themeMediaSource.videoUri
            val isVideo = themeMediaSource.isVideo
            val globalDynamicHueEnabled = themeBootstrap.dynamicPlayerHueEnabled
            val staticHueArgb = resolveStaticHueArgb(
                themePref = themePref,
                staticHueArgbLight = themeBootstrap.staticHueArgbLight,
                staticHueArgbDark = themeBootstrap.staticHueArgbDark
            )
            val coverBackgroundEnabled by settingsDataStore.coverBackgroundEnabled.collectAsState(initial = true)
            val coverBackgroundClarity by settingsDataStore.coverBackgroundClarity.collectAsState(initial = 0.35f)
            val coverPreviewMode by settingsDataStore.coverPreviewMode.collectAsState(initial = CoverPreviewMode.Disabled)
            val lyricsPageSettings by settingsDataStore.lyricsPageSettings.collectAsState(initial = LyricsPageSettings())
            val showMiniPlayerBar by settingsRepository.showMiniPlayerBar.collectAsState(initial = true)
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

            val staticHue: HuePalette? = remember(staticHueArgb, mode, neutral) {
                staticHueArgb?.let { argb ->
                    deriveHuePalette(
                        primary = if (argb == MONOCHROME_THEME_SENTINEL) {
                            if (mode.isDark) Color(0xFFAFB8C2) else Color(0xFF5D6670)
                        } else {
                            Color(argb)
                        },
                        mode = mode,
                        neutral = neutral,
                        fallbackOnPrimary = if (mode.isDark) Color.White else Color.Black,
                        forceMonochrome = argb == MONOCHROME_THEME_SENTINEL
                    )
                }
            }
            val activeSourceKey = remember(themeMediaSource) {
                themeMediaSource.dynamicThemeSourceKey()
            }
            val bootstrapDynamicHueSeedArgb = remember(
                globalDynamicHueEnabled,
                playbackSnapshot.startupRestoreResolved,
                activeSourceKey,
                themeBootstrap.lastDynamicHueSourceKey,
                themeBootstrap.lastDynamicHueSeedArgb
            ) {
                resolveBootstrapDynamicHueSeedArgb(
                    dynamicHueEnabled = globalDynamicHueEnabled,
                    playbackRestoreResolved = playbackSnapshot.startupRestoreResolved,
                    currentSourceKey = activeSourceKey,
                    persistedSourceKey = themeBootstrap.lastDynamicHueSourceKey,
                    persistedSeedArgb = themeBootstrap.lastDynamicHueSeedArgb
                )
            }
            val bootstrapDynamicHue: HuePalette? = remember(bootstrapDynamicHueSeedArgb, mode, neutral) {
                bootstrapDynamicHueSeedArgb?.let { argb ->
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
            val dynamicFallbackHue = remember(baseStaticHue, bootstrapDynamicHue) {
                bootstrapDynamicHue ?: baseStaticHue
            }
            if (isVideo && artworkUri == null) {
                PrewarmDynamicHuePaletteFromVideoFrame(
                    videoUri = videoUri,
                    fallbackHue = dynamicFallbackHue
                )
            } else {
                PrewarmDynamicHuePalette(
                    artworkModel = artworkUri,
                    fallbackHue = dynamicFallbackHue
                )
            }
            val globalHue = if (globalDynamicHueEnabled) {
                val state = if (isVideo && artworkUri == null) {
                    rememberDynamicHuePaletteFromVideoFrame(
                        videoUri = videoUri,
                        mode = mode,
                        neutral = neutral,
                        fallbackHue = dynamicFallbackHue,
                        transitionDurationMs = 0,
                        cachedTransitionDurationMs = 0
                    )
                } else {
                    rememberDynamicHuePalette(
                        artworkModel = artworkUri,
                        mode = mode,
                        neutral = neutral,
                        fallbackHue = dynamicFallbackHue,
                        transitionDurationMs = 0,
                        cachedTransitionDurationMs = 0
                    )
                }
                state.value
            } else baseStaticHue

            val dynamicHueCacheKey = remember(isVideo, artworkUri, videoUri) {
                if (isVideo && artworkUri == null) {
                    dynamicHueCacheKeyForVideo(videoUri)
                } else {
                    dynamicHueCacheKeyForArtwork(artworkUri)
                }
            }
            val persistableDynamicHueSeedArgb = remember(globalDynamicHueEnabled, dynamicHueCacheKey, globalHue) {
                if (!globalDynamicHueEnabled) {
                    null
                } else {
                    peekDynamicHueSeedColor(dynamicHueCacheKey)?.toArgb()
                }
            }

            LaunchedEffect(globalDynamicHueEnabled, activeSourceKey, persistableDynamicHueSeedArgb) {
                if (globalDynamicHueEnabled && activeSourceKey != null && persistableDynamicHueSeedArgb != null) {
                    settingsDataStore.setLastDynamicHueSeed(
                        sourceKey = activeSourceKey,
                        argb = persistableDynamicHueSeedArgb
                    )
                }
            }

            LaunchedEffect(
                globalDynamicHueEnabled,
                playbackSnapshot.startupRestoreResolved,
                activeSourceKey,
                themeBootstrap.lastDynamicHueSourceKey,
                themeBootstrap.lastDynamicHueSeedArgb
            ) {
                if (
                    shouldClearPersistedDynamicHueSeed(
                        dynamicHueEnabled = globalDynamicHueEnabled,
                        playbackRestoreResolved = playbackSnapshot.startupRestoreResolved,
                        currentSourceKey = activeSourceKey,
                        persistedSourceKey = themeBootstrap.lastDynamicHueSourceKey,
                        persistedSeedArgb = themeBootstrap.lastDynamicHueSeedArgb
                    )
                ) {
                    settingsDataStore.clearLastDynamicHueSeed()
                }
            }

            var overlaySheet by remember { mutableStateOf<OverlaySheet?>(null) }

            val visibleMessages = remember { mutableStateListOf<VisibleAppMessage>() }
            val dismissJobs = remember { linkedMapOf<Long, kotlinx.coroutines.Job>() }
            var messageSeq by remember { mutableLongStateOf(0L) }

            LaunchedEffect(Unit) {
                val maxVisible = 5
                val maxRetained = maxVisible + 1
                val exitAnimationMs = 220L

                fun hideMessage(id: Long) {
                    val index = visibleMessages.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        visibleMessages[index] = visibleMessages[index].copy(isVisible = false)
                    }
                }

                fun removeImmediately(id: Long) {
                    dismissJobs.remove(id)?.cancel()
                    visibleMessages.removeAll { it.id == id }
                }

                fun removeAfterExit(id: Long, cancelExistingJob: Boolean = true) {
                    hideMessage(id)
                    if (cancelExistingJob) {
                        dismissJobs.remove(id)?.cancel()
                    } else {
                        dismissJobs.remove(id)
                    }
                    dismissJobs[id] = launch {
                        delay(exitAnimationMs)
                        visibleMessages.removeAll { it.id == id }
                        dismissJobs.remove(id)
                    }
                }

                fun trimRetainedMessages() {
                    while (visibleMessages.size >= maxRetained) {
                        val removed = visibleMessages.lastOrNull { !it.isVisible }
                            ?: visibleMessages.lastOrNull()
                            ?: break
                        removeImmediately(removed.id)
                    }
                }

                fun prepareForIncomingMessage() {
                    trimRetainedMessages()
                    if (visibleMessages.count { it.isVisible } >= maxVisible) {
                        val removed = visibleMessages.lastOrNull { it.isVisible } ?: return
                        removeAfterExit(removed.id)
                    }
                    trimRetainedMessages()
                }

                messageManager.messages.collect { appMessage ->
                    if (!messageManager.tryConsume(appMessage.id)) return@collect
                    val now = System.currentTimeMillis()
                    if ((now - appMessage.createdAtMs) > 10_000L) return@collect
                    val normalized = appMessage.message.trim()
                    if (normalized.isBlank()) return@collect
                    val displayMs = appMessage.durationMs.coerceIn(1200L, 3600L)

                    val id = ++messageSeq
                    prepareForIncomingMessage()
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
                        removeAfterExit(id, cancelExistingJob = false)
                    }
                }
            }

            val transitionScope = rememberCoroutineScope()
            var themeTransitionSeq by remember { mutableLongStateOf(0L) }
            var themeTransition by remember { mutableStateOf<ThemeTransitionRequest?>(null) }
            val currentMode by rememberUpdatedState(mode)
            val themeTransitionTrigger: (ThemeTransitionTriggerRequest) -> Unit = remember {
                { triggerReq ->
                    if (themeTransition == null) {
                        val bitmap = runCatching {
                            val w = this@MainActivity.window.decorView.width
                            val h = this@MainActivity.window.decorView.height
                            if (w > 0 && h > 0) {
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bmp)
                                this@MainActivity.window.decorView.draw(canvas)
                                bmp.asImageBitmap()
                            } else null
                        }.getOrNull()
                        val bgColor = neutralPaletteForMode(currentMode).background
                        themeTransition = ThemeTransitionRequest(
                            origin = triggerReq.origin,
                            oldContentBitmap = bitmap,
                            oldBackgroundColor = bgColor,
                            token = ++themeTransitionSeq
                        )
                        transitionScope.launch {
                            settingsDataStore.setTheme(triggerReq.targetPref)
                        }
                    }
                }
            }

            CompositionLocalProvider(LocalThemeTransitionTrigger provides themeTransitionTrigger) {
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
                        messageManager = messageManager,
                        listeningTracker = listeningTracker,
                        recentAlbumsPanelExpandedInitial = recentAlbumsPanelExpandedInitial,
                        startRouteFromIntent = startRouteFromIntent,
                        onShowQueue = { overlaySheet = OverlaySheet.Queue },
                        onShowSleepTimer = { overlaySheet = OverlaySheet.SleepTimer },
                        onContentReady = { contentReady = true },
                        visibleMessages = visibleMessagesSnapshot,
                        showMiniPlayerBar = showMiniPlayerBar,
                        coverBackgroundEnabled = coverBackgroundEnabled,
                        coverBackgroundClarity = coverBackgroundClarity,
                        coverPreviewMode = coverPreviewMode,
                        lyricsPageSettings = lyricsPageSettings,
                        forceImmersive = showSplash,
                        volumeKeyEventTick = volumeKeyTick
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

                    themeTransition?.let { request ->
                        val currentToken = request.token
                        ThemeCircularRevealOverlay(
                            request = request,
                            onAnimationEnd = {
                                if (themeTransition?.token == currentToken) {
                                    themeTransition = null
                                }
                            }
                        )
                    }
                }
            }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isVolumeKey = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if (!isVolumeKey) return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
            val delta = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) AppVolume.StepPercent else -AppVolume.StepPercent
            lifecycleScope.launch {
                settingsRepository.adjustAppVolumePercent(delta)
            }
            volumeKeyEventSeq += 1L
            volumeKeyEventTick.value = volumeKeyEventSeq
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        syncAppVolumePercentFromSystem()
    }

    private fun syncAppVolumePercentFromSystem() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxSystemVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val currentAppVolumePercent = AppVolume.percentFromSystemVolume(
            systemVolume = currentSystemVolume,
            maxSystemVolume = maxSystemVolume
        )
        lifecycleScope.launch {
            settingsRepository.syncAppVolumePercentFromSystem(currentAppVolumePercent)
        }
    }
}
