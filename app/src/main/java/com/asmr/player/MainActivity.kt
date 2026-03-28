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
import com.asmr.player.data.settings.BackgroundEffectType
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

    private val volumeKeyEventTick = MutableStateFlow(0L)
    private var volumeKeyEventSeq = 0L

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
            val volumeKeyTick by volumeKeyEventTick.asStateFlow().collectAsState()
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
            val backgroundEffectEnabled by settingsDataStore.backgroundEffectEnabled.collectAsState(initial = false)
            val backgroundEffectType by settingsDataStore.backgroundEffectType.collectAsState(initial = BackgroundEffectType.Flow)
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
                        showMiniPlayerBar = showMiniPlayerBar,
                        backgroundEffectEnabled = backgroundEffectEnabled,
                        backgroundEffectType = backgroundEffectType,
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
}
