package com.asmr.player

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.rounded.*
import android.media.AudioManager
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.ui.library.LibraryViewModel
import com.asmr.player.ui.player.PlayerViewModel
import com.asmr.player.ui.common.DiscPlaceholderBitmapCache
import com.asmr.player.ui.splash.EaraSplashOverlay
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.ui.platform.LocalContext
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.PrewarmDynamicHuePalette
import com.asmr.player.ui.theme.PrewarmDynamicHuePaletteFromVideoFrame
import androidx.compose.animation.*

import com.asmr.player.ui.player.QueueSheetContent
import com.asmr.player.ui.player.SleepTimerSheetContent

import com.asmr.player.data.local.datastore.SettingsDataStore
import com.asmr.player.data.local.datastore.ThemeBootstrapPreferences
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.PlayerBackgroundMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.data.settings.NowPlayingLyricsSettings
import com.asmr.player.util.MessageManager
import com.asmr.player.ui.common.NonTouchableAppMessageOverlay
import com.asmr.player.ui.common.PlayerModalSheet
import com.asmr.player.ui.common.VisibleAppMessage
import com.asmr.player.ui.common.LocalVisibleAppMessages
import com.asmr.player.ui.theme.HuePalette
import com.asmr.player.ui.theme.DefaultBrandPrimaryDark
import com.asmr.player.ui.theme.DefaultBrandPrimaryLight
import com.asmr.player.ui.theme.deriveHuePalette
import com.asmr.player.ui.theme.neutralPaletteForMode
import com.asmr.player.ui.theme.rememberDynamicHuePalette
import com.asmr.player.ui.theme.rememberDynamicHuePaletteFromVideoFrame
import com.asmr.player.ui.theme.dynamicHueCacheKeyForArtwork
import com.asmr.player.ui.theme.dynamicHueCacheKeyForVideo
import com.asmr.player.ui.theme.peekDynamicHueSeedColor
import com.asmr.player.ui.theme.ThemeTransitionRequest
import com.asmr.player.ui.theme.ThemeTransitionTriggerRequest
import com.asmr.player.ui.theme.LocalThemeTransitionTrigger
import com.asmr.player.ui.theme.ThemeCircularRevealOverlay
import com.asmr.player.ui.theme.captureThemeTransitionBitmap
import com.asmr.player.ui.common.calmVerticalFling
import com.asmr.player.ui.common.OledBurnInProtectionBox
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.playback.AppVolume
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            initialThemeBootstrapPreferences = settingsDataStore.loadThemeBootstrapPreferences()
            if (isFinishing || isDestroyed) return@launch
            val recentAlbumsPanelExpandedInitial = false
            val startRouteFromIntent = intent.getStringExtra("start_route")
            setAppContent(
                recentAlbumsPanelExpandedInitial = recentAlbumsPanelExpandedInitial,
                startRouteFromIntent = startRouteFromIntent
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
    private fun setAppContent(
        recentAlbumsPanelExpandedInitial: Boolean,
        startRouteFromIntent: String?
    ) {
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val context = LocalContext.current
            val playerViewModel: PlayerViewModel = hiltViewModel()
            val libraryViewModel: LibraryViewModel = hiltViewModel()
            val volumeKeyTick by volumeKeyEventTick.asStateFlow().collectAsStateWithLifecycle()
            val themeBootstrap by settingsDataStore.themeBootstrapPreferences
                .collectAsStateWithLifecycle(initialValue = initialThemeBootstrapPreferences)
            val themeMediaSource by remember(playerViewModel) {
                playerViewModel.playback
                    .map { it.currentMediaItem.toThemeMediaSource() }
                    .distinctUntilChanged()
            }.collectAsStateWithLifecycle(initialValue = ThemeMediaSource())
            val playbackRestoreResolved by remember(playerViewModel) {
                playerViewModel.playback
                    .map { it.startupRestoreResolved }
                    .distinctUntilChanged()
            }.collectAsStateWithLifecycle(initialValue = false)
            val systemDark = isSystemInDarkTheme()
            val themePref = themeBootstrap.theme
            val mode = resolveThemeMode(themePref = themePref, systemDark = systemDark)
            LaunchedEffect(mode.isDark, context.applicationContext) {
                withContext(Dispatchers.IO) {
                    DiscPlaceholderBitmapCache.preload(
                        resources = context.applicationContext.resources,
                        darkTheme = mode.isDark
                    )
                }
            }
            val artworkUri = themeMediaSource.artworkUri
            val videoUri = themeMediaSource.videoUri
            val isVideo = themeMediaSource.isVideo
            val globalDynamicHueEnabled = themeBootstrap.dynamicPlayerHueEnabled
            val staticHueArgb = resolveStaticHueArgb(
                themePref = themePref,
                staticHueArgbLight = themeBootstrap.staticHueArgbLight,
                staticHueArgbDark = themeBootstrap.staticHueArgbDark
            )
            val playerBackgroundMode by settingsDataStore.playerBackgroundMode
                .collectAsStateWithLifecycle(initialValue = PlayerBackgroundMode.Theme)
            val coverBackgroundEnabled by settingsDataStore.coverBackgroundEnabled.collectAsStateWithLifecycle(initialValue = true)
            val dynamicFlowingBackgroundEnabled by settingsDataStore.dynamicFlowingBackgroundEnabled
                .collectAsStateWithLifecycle(initialValue = true)
            val coverBackgroundClarity by settingsDataStore.coverBackgroundClarity.collectAsStateWithLifecycle(initialValue = 0.35f)
            val coverPreviewMode by settingsDataStore.coverPreviewMode.collectAsStateWithLifecycle(initialValue = CoverPreviewMode.Disabled)
            val nowPlayingHomeLayoutMode by settingsDataStore.nowPlayingHomeLayoutMode.collectAsStateWithLifecycle(
                initialValue = NowPlayingHomeLayoutMode.Classic
            )
            val nowPlayingHomeLayoutHintDismissed by settingsDataStore.nowPlayingHomeLayoutHintDismissed
                .collectAsStateWithLifecycle<Boolean?>(initialValue = null)
            val nowPlayingLyricsSettings by settingsDataStore.nowPlayingLyricsSettings.collectAsStateWithLifecycle(
                initialValue = NowPlayingLyricsSettings()
            )
            val lyricsPageSettings by settingsDataStore.lyricsPageSettings.collectAsStateWithLifecycle(initialValue = LyricsPageSettings())
            val showMiniPlayerBar by settingsRepository.showMiniPlayerBar.collectAsStateWithLifecycle(initialValue = true)
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
                playbackRestoreResolved,
                activeSourceKey,
                themeBootstrap.lastDynamicHueSourceKey,
                themeBootstrap.lastDynamicHueSeedArgb
            ) {
                resolveBootstrapDynamicHueSeedArgb(
                    dynamicHueEnabled = globalDynamicHueEnabled,
                    playbackRestoreResolved = playbackRestoreResolved,
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
            if (globalDynamicHueEnabled) {
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
                playbackRestoreResolved,
                activeSourceKey,
                themeBootstrap.lastDynamicHueSourceKey,
                themeBootstrap.lastDynamicHueSeedArgb
            ) {
                if (
                    shouldClearPersistedDynamicHueSeed(
                        dynamicHueEnabled = globalDynamicHueEnabled,
                        playbackRestoreResolved = playbackRestoreResolved,
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
            var themeTransitionCapturePending by remember { mutableStateOf(false) }
            val currentMode by rememberUpdatedState(mode)
            val themeTransitionTrigger: (ThemeTransitionTriggerRequest) -> Unit = remember {
                { triggerReq ->
                    if (themeTransition == null && !themeTransitionCapturePending) {
                        themeTransitionCapturePending = true
                        transitionScope.launch {
                            try {
                                val oldMode = currentMode
                                val bitmap = captureThemeTransitionBitmap(this@MainActivity.window)
                                if (themeTransition != null) return@launch
                                themeTransition = ThemeTransitionRequest(
                                    origin = triggerReq.origin,
                                    oldContentBitmap = bitmap,
                                    oldBackgroundColor = neutralPaletteForMode(oldMode).background,
                                    targetIsDark = triggerReq.targetPref == "dark",
                                    token = ++themeTransitionSeq
                                )
                                settingsDataStore.setTheme(triggerReq.targetPref)
                            } finally {
                                themeTransitionCapturePending = false
                            }
                        }
                    }
                }
            }

            CompositionLocalProvider(LocalThemeTransitionTrigger provides themeTransitionTrigger) {
            AsmrPlayerTheme(mode = mode, hue = globalHue) {
                var showSplash by rememberSaveable { mutableStateOf(true) }
                var contentReady by remember { mutableStateOf(false) }
                OledBurnInProtectionBox(
                    backgroundColor = AsmrTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxSize()
                        .calmVerticalFling()
                ) {
                    val visibleMessagesSnapshot = visibleMessages.toList()
                    CompositionLocalProvider(LocalVisibleAppMessages provides visibleMessagesSnapshot) {
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
                        showMiniPlayerBar = showMiniPlayerBar,
                        playerBackgroundMode = playerBackgroundMode,
                        coverBackgroundEnabled = coverBackgroundEnabled,
                        dynamicFlowingBackgroundEnabled = dynamicFlowingBackgroundEnabled,
                        coverBackgroundClarity = coverBackgroundClarity,
                        coverPreviewMode = coverPreviewMode,
                        nowPlayingHomeLayoutMode = nowPlayingHomeLayoutMode,
                        nowPlayingHomeLayoutHintDismissed = nowPlayingHomeLayoutHintDismissed,
                        nowPlayingLyricsSettings = nowPlayingLyricsSettings,
                        lyricsPageSettings = lyricsPageSettings,
                        forceImmersive = showSplash,
                        volumeKeyEventTick = volumeKeyTick
                    )
                    }
                    NonTouchableAppMessageOverlay(messages = visibleMessagesSnapshot)

                    if (showSplash) {
                        EaraSplashOverlay(
                            isReady = contentReady,
                            onFinished = { showSplash = false }
                        )
                    }
                    
                    val activeOverlaySheet = overlaySheet
                    if (activeOverlaySheet != null) {
                        key(activeOverlaySheet) {
                            PlayerModalSheet(onDismissRequest = { overlaySheet = null }) { sheetMaxHeight ->
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

                    themeTransition?.let { request ->
                        val currentToken = request.token
                        ThemeCircularRevealOverlay(
                            request = request,
                            targetReady = mode.isDark == request.targetIsDark,
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
