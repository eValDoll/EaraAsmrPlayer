package com.asmr.player.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import com.asmr.player.R
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AudioOutputRouteIcon
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.common.DismissOutsideBoundsOverlay
import com.asmr.player.ui.common.AppVolumeHearingWarningDialog
import com.asmr.player.ui.common.AppVolumeSlider
import com.asmr.player.ui.common.AppVolumeWarningSessionState
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.playback.AppVolume
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.ui.common.EqualizerPanel
import com.asmr.player.ui.common.rememberProtectedAppVolumeChangeState
import com.asmr.player.ui.common.rememberComputedDominantColorCenterWeighted
import com.asmr.player.ui.common.rememberComputedVideoFrameDominantColorCenterWeighted
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.smoothScrollToIndex
import com.asmr.player.ui.library.TagAssignDialog
import com.asmr.player.service.AudioOutputRouteKind
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.Formatting
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleIndexFinder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class NowPlayingSurfaceMode {
    PLAYER,
    LYRICS
}

private fun AnimatedContentTransitionScope<NowPlayingSurfaceMode>.nowPlayingSurfaceTransform(): ContentTransform {
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundEnterDurationMs,
            easing = LinearOutSlowInEasing
        )
    ) + slideInVertically(
        initialOffsetY = {
            (it * NowPlayingMotionSpec.PlayerForegroundFloatOffsetFraction).roundToInt()
        },
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundEnterDurationMs,
            easing = LinearOutSlowInEasing
        )
    ) + scaleIn(
        initialScale = NowPlayingMotionSpec.PlayerForegroundInitialScale,
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundEnterDurationMs,
            easing = LinearOutSlowInEasing
        )
    )
    val exit = fadeOut(
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundExitDurationMs,
            easing = FastOutLinearInEasing
        )
    ) + slideOutVertically(
        targetOffsetY = {
            (it * NowPlayingMotionSpec.PlayerForegroundSinkOffsetFraction).roundToInt()
        },
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundExitDurationMs,
            easing = FastOutLinearInEasing
        )
    ) + scaleOut(
        targetScale = NowPlayingMotionSpec.PlayerForegroundTargetScale,
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundExitDurationMs,
            easing = FastOutLinearInEasing
        )
    )
    return enter togetherWith exit using SizeTransform(clip = false)
}

@Composable
@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
internal fun NowPlayingScreen(
    windowSizeClass: WindowSizeClass,
    hardwareVolumeEventTick: Long,
    onBack: () -> Unit,
    onRouteExitStarted: (exitDurationMs: Int) -> Unit = {},
    onShowQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onOpenPlaylistPicker: (mediaId: String, uri: String, title: String, artist: String, artworkUri: String, albumId: Long, trackId: Long, rjCode: String) -> Unit,
    viewModel: PlayerViewModel,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    coverPreviewMode: CoverPreviewMode,
    lyricsPageSettings: LyricsPageSettings,
    audioOutputRouteKind: AudioOutputRouteKind,
    warningSessionState: AppVolumeWarningSessionState,
    renderBackdrop: Boolean = true,
    sharedArtworkAlignment: Alignment? = null,
    sharedCoverDragPreviewState: CoverDragPreviewState? = null,
    enableStaggeredRouteEntry: Boolean = true,
    lyricsViewModel: LyricsViewModel = hiltViewModel()
) {
    val playback by viewModel.playback.collectAsState()
    val resolvedDurationMs by viewModel.resolvedDurationMs.collectAsState()
    val sliceUiState by viewModel.sliceUiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val lyricsState by lyricsViewModel.uiState.collectAsState()
    val item = playback.currentMediaItem
    val metadata = item?.mediaMetadata
    val colorScheme = AsmrTheme.colorScheme
    val uriText = item?.localConfiguration?.uri?.toString().orEmpty()
    val artworkModel = remember(metadata?.artworkUri) {
        sanitizeBackdropArtworkModel(metadata?.artworkUri)
    }
    val videoUri = item?.localConfiguration?.uri
    val mimeType = item?.localConfiguration?.mimeType.orEmpty()
    val ext = uriText.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
    val isVideo = metadata?.extras?.getBoolean("is_video") == true ||
        mimeType.startsWith("video/") ||
        ext in setOf("mp4", "m4v", "webm", "mkv", "mov")
    
    var showEqualizer by remember { mutableStateOf(false) }
    val tagViewModel: NowPlayingTagViewModel = hiltViewModel()
    val tagDialog by tagViewModel.dialogState.collectAsState()
    val availableTags by tagViewModel.availableTags.collectAsState()
    val dominantColorResult by if (isVideo) {
        rememberComputedVideoFrameDominantColorCenterWeighted(videoUri = videoUri, defaultColor = colorScheme.background)
    } else {
        rememberComputedDominantColorCenterWeighted(model = artworkModel, defaultColor = colorScheme.background)
    }
    val targetAccentColor = if (coverBackgroundEnabled) {
        dominantColorResult.color ?: colorScheme.primarySoft
    } else {
        colorScheme.primary
    }
    val accentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(
            durationMillis = if (dominantColorResult.fromCache) 260 else 1000,
            easing = FastOutSlowInEasing
        ),
        label = "nowPlayingAccentColor"
    )
    val lyricColors = rememberLyricReadableColors(
        accentColor = accentColor,
        coverBackgroundEnabled = coverBackgroundEnabled,
        coverBackgroundClarity = coverBackgroundClarity
    )
    val onAccentColor = if (accentColor.luminance() > 0.55f) Color.Black else Color.White
    val videoBackdropColor = if (isVideo) {
        if (coverBackgroundEnabled) accentColor else colorScheme.background
    } else {
        Color.Transparent
    }
    val progressDurationMs = when {
        playback.durationMs > 0L && resolvedDurationMs > 0L -> maxOf(playback.durationMs, resolvedDurationMs)
        playback.durationMs > 0L -> playback.durationMs
        else -> resolvedDurationMs
    }

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        viewModel.sliceUiEvents.collect { event ->
            when (event) {
                SliceUiEvent.CutStartMarked -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                SliceUiEvent.CutSliceCreated -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                SliceUiEvent.CutInvalidRange -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    var showSliceSheet by remember { mutableStateOf(false) }
    var timeEditTarget by remember { mutableStateOf<Pair<Long, Boolean>?>(null) }
    val dismissSliceSheet = {
        showSliceSheet = false
        viewModel.selectSlice(null)
    }
    val toggleSelectedSlice = { sliceId: Long ->
        viewModel.selectSlice(if (sliceUiState.selectedSliceId == sliceId) null else sliceId)
    }
    val highlightedPlaybackSliceId = currentSliceIdForPosition(
        positionMs = playback.positionMs,
        slices = sliceUiState.slices,
        sliceModeEnabled = sliceUiState.sliceModeEnabled
    )
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val widthClass = windowSizeClass.widthSizeClass
    val heightClass = windowSizeClass.heightSizeClass
    
    // 手机横屏：高度为 Compact
    val isPhoneLandscape = heightClass == WindowHeightSizeClass.Compact
    // 平板横屏：高度不为 Compact 且处于横屏状态
    val useSplitLayout = heightClass != WindowHeightSizeClass.Compact && isLandscape
    val player = viewModel.playerOrNull()
    val videoAspectRatio = rememberPlayerVideoAspectRatio(player)
    val useDragPreview = coverPreviewMode == CoverPreviewMode.Drag && !isVideo
    val useMotionPreview = coverPreviewMode == CoverPreviewMode.Motion && !isVideo
    val ownsMotionPreview = sharedArtworkAlignment == null
    val ownsDragPreview = sharedCoverDragPreviewState == null
    val localCoverMotionState = rememberCoverMotionState(
        enabled = ownsMotionPreview && useMotionPreview,
        resetKey = item?.mediaId
    )
    val localCoverDragPreviewState = rememberCoverDragPreviewState(
        enabled = ownsDragPreview && useDragPreview,
        resetKey = item?.mediaId
    )
    val coverDragPreviewState = sharedCoverDragPreviewState ?: localCoverDragPreviewState
    val coverPreviewAlignment = sharedArtworkAlignment ?: when {
        useDragPreview -> coverDragPreviewState.toAlignment()
        useMotionPreview -> localCoverMotionState.toAlignment()
        else -> Alignment.Center
    }
    var surfaceMode by rememberSaveable { mutableStateOf(NowPlayingSurfaceMode.PLAYER) }
    val currentMotionLayout = when {
        useSplitLayout -> NowPlayingMotionLayout.SPLIT_LANDSCAPE
        isPhoneLandscape -> NowPlayingMotionLayout.PHONE_LANDSCAPE
        else -> NowPlayingMotionLayout.PORTRAIT
    }
    var routeVisible by remember(enableStaggeredRouteEntry) { mutableStateOf(!enableStaggeredRouteEntry) }
    var pendingRouteExit by remember { mutableStateOf(false) }
    var exitMotionLayout by remember { mutableStateOf<NowPlayingMotionLayout?>(null) }
    val latestOnBack = rememberUpdatedState(onBack)
    val latestOnRouteExitStarted = rememberUpdatedState(onRouteExitStarted)
    val routeTransition = updateTransition(targetState = routeVisible, label = "nowPlayingRouteVisibility")
    val requestClose = remember(pendingRouteExit, currentMotionLayout) {
        {
            if (!pendingRouteExit) {
                exitMotionLayout = currentMotionLayout
                pendingRouteExit = true
                latestOnRouteExitStarted.value(
                    NowPlayingMotionSpec.totalExitDurationMs(currentMotionLayout)
                )
                routeVisible = false
            }
        }
    }

    LaunchedEffect(enableStaggeredRouteEntry) {
        routeVisible = true
    }

    LaunchedEffect(pendingRouteExit, exitMotionLayout) {
        val layout = exitMotionLayout ?: return@LaunchedEffect
        if (!pendingRouteExit) return@LaunchedEffect
        delay(NowPlayingMotionSpec.totalExitDurationMs(layout).toLong())
        latestOnBack.value()
    }

    val showLyricsSurface = remember(isVideo) {
        {
            if (!isVideo) {
                surfaceMode = NowPlayingSurfaceMode.LYRICS
            }
        }
    }
    val handleNavigateUp = {
        if (surfaceMode == NowPlayingSurfaceMode.LYRICS) {
            surfaceMode = NowPlayingSurfaceMode.PLAYER
        } else {
            requestClose()
        }
    }

    BackHandler(enabled = !pendingRouteExit) {
        handleNavigateUp()
    }
    val playerHeaderTitle = metadata?.title?.toString().orEmpty().ifBlank {
        lyricsState.title.ifBlank { "未播放" }
    }
    val lyricsHeaderTitle = lyricsState.title.ifBlank {
        metadata?.title?.toString().orEmpty().ifBlank { "歌词" }
    }

    val sharedHeaderTitle = if (surfaceMode == NowPlayingSurfaceMode.LYRICS) {
        lyricsHeaderTitle
    } else {
        playerHeaderTitle
    }
    val sharedHeaderMotion = routeTransition.nowPlayingMotionModifier(
        currentMotionLayout,
        NowPlayingMotionSlot.HEADER
    )
    val sharedHeaderHorizontalPadding = if (isLandscape) 4.dp else 12.dp
    var volumeControlExpanded by remember { mutableStateOf(false) }
    var volumeControlBounds by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (renderBackdrop && !isVideo) {
            CoverArtworkBackground(
                artworkModel = artworkModel,
                enabled = coverBackgroundEnabled,
                clarity = coverBackgroundClarity,
                overlayBaseColor = colorScheme.background,
                tintBaseColor = accentColor,
                artworkAlignment = coverPreviewAlignment,
                isDark = colorScheme.isDark
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(StableWindowInsets.statusBars))
            PlayerSurfaceHeader(
                title = sharedHeaderTitle,
                isLandscape = isLandscape,
                onNavigateUp = handleNavigateUp,
                onShowSleepTimer = onShowSleepTimer,
                onShowQueue = onShowQueue,
                navigationEnabled = !pendingRouteExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sharedHeaderHorizontalPadding, vertical = 4.dp)
                    .then(sharedHeaderMotion)
            )
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = surfaceMode,
                    transitionSpec = { nowPlayingSurfaceTransform() },
                    label = "nowPlayingSurfaceMode"
                ) { activeSurfaceMode ->
            if (activeSurfaceMode == NowPlayingSurfaceMode.PLAYER) {
                val layoutState = remember(useSplitLayout, isPhoneLandscape) { useSplitLayout to isPhoneLandscape }

                AnimatedContent(
                    targetState = layoutState,
                    transitionSpec = {
                        if (initialState == targetState) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val enter = fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 60)) +
                                scaleIn(animationSpec = tween(durationMillis = 220, delayMillis = 60), initialScale = 0.98f)
                            val exit = fadeOut(animationSpec = tween(durationMillis = 160)) +
                                scaleOut(animationSpec = tween(durationMillis = 160), targetScale = 1.02f)
                            enter togetherWith exit
                        }
                    },
                    label = "nowPlayingLayout"
                ) { (split, phoneLandscape) ->
            val motionLayout = when {
                split -> NowPlayingMotionLayout.SPLIT_LANDSCAPE
                phoneLandscape -> NowPlayingMotionLayout.PHONE_LANDSCAPE
                else -> NowPlayingMotionLayout.PORTRAIT
            }

            if (split) {
                val headerMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.HEADER)
                val coverMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.COVER)
                val progressMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.PROGRESS)
                val infoMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.INFO_PANEL)
                val controlsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.CONTROLS)
            // --- 平板端横屏布局 (左右分栏) ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 顶部工具栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(headerMotion)
                        .requiredHeight(0.dp)
                        .alpha(0f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = requestClose, enabled = !pendingRouteExit) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = metadata?.title?.toString().orEmpty().ifBlank { "未播放" },
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onShowSleepTimer) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = onShowQueue) {
                            Icon(
                                Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // 主内容区：左右分栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：封面/视频区 + 进度条
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 420.dp)
                                .aspectRatio(if (isVideo) videoAspectRatio else 1f)
                                .then(coverMotion)
                        ) {
                            ArtworkBox(
                                isVideo = isVideo,
                                metadata = metadata,
                                viewModel = viewModel,
                                onOpenLyrics = showLyricsSurface,
                                edgeBlendEnabled = false,
                                edgeBlendColor = if (coverBackgroundEnabled) accentColor else colorScheme.background,
                                videoBackdropColor = videoBackdropColor,
                                artworkAlignment = coverPreviewAlignment,
                                dragPreviewEnabled = useDragPreview,
                                dragPreviewState = coverDragPreviewState
                            )
                        }
                        
                        key(item?.mediaId) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(progressMotion)
                            ) {
                                PlayerProgress(
                                    positionMs = playback.positionMs,
                                    durationMs = progressDurationMs,
                                    sliceUiState = sliceUiState,
                                    onSeekTo = { viewModel.seekTo(it) },
                                    onCutPressed = { viewModel.onCutPressed(progressDurationMs) },
                                    onScrubbingChanged = { viewModel.setUserScrubbing(it) },
                                    onSelectSlice = { viewModel.selectSlice(it) },
                                    onLongPressSlice = {
                                        viewModel.selectSlice(it)
                                        showSliceSheet = true
                                    },
                                    onUpdateSliceRange = { sliceId, startMs, endMs ->
                                        viewModel.updateSliceRange(sliceId, startMs, endMs, progressDurationMs)
                                    },
                                    activeColor = accentColor,
                                    inactiveColor = accentColor.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }

                    // 右侧：信息与控制区
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 艺术家 (标题已移动到 header)
                        Text(
                            text = metadata?.artist?.toString().orEmpty(),
                            modifier = Modifier.then(infoMotion),
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!isVideo) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .then(infoMotion),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AndroidView(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(54.dp)
                                        .graphicsLayer { clip = false },
                                    factory = { context ->
                                        ChannelSpectrumView(context).apply {
                                            setChannel(ChannelSpectrumView.Channel.Left)
                                            setBarCount(64)
                                            setBarColor(accentColor.toArgb())
                                        }
                                    },
                                    update = { view ->
                                        view.setBarColor(accentColor.toArgb())
                                    }
                                )

                                AppleLyricsView(
                                    lyrics = lyricsState.lyrics,
                                    currentPosition = playback.positionMs,
                                    onSeekTo = { viewModel.seekTo(it) },
                                    onOpenLyrics = showLyricsSurface,
                                    colors = lyricColors,
                                    modifier = Modifier
                                        .weight(0.70f)
                                        .fillMaxHeight(),
                                    isLandscape = true
                                )

                                AndroidView(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(54.dp)
                                        .graphicsLayer { clip = false },
                                    factory = { context ->
                                        ChannelSpectrumView(context).apply {
                                            setChannel(ChannelSpectrumView.Channel.Right)
                                            setBarCount(64)
                                            setBarColor(accentColor.toArgb())
                                        }
                                    },
                                    update = { view ->
                                        view.setBarColor(accentColor.toArgb())
                                    }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f).then(infoMotion))
                        }

                        PlaybackControls(
                            playback = playback,
                            isFavorite = isFavorite,
                            viewModel = viewModel,
                            onShowPlaylistPicker = {
                                val current = playback.currentMediaItem ?: return@PlaybackControls
                                val md = current.mediaMetadata
                                val extras = md.extras
                                val albumId = extras?.getLong("album_id") ?: 0L
                                val trackId = extras?.getLong("track_id") ?: 0L
                                val rjCode = extras?.getString("rj_code").orEmpty()
                                onOpenPlaylistPicker(
                                    current.mediaId,
                                    current.localConfiguration?.uri?.toString().orEmpty(),
                                    md.title?.toString().orEmpty(),
                                    md.artist?.toString().orEmpty(),
                                    md.artworkUri?.toString().orEmpty(),
                                    albumId,
                                    trackId,
                                    rjCode
                                )
                            },
                            onShowEqualizer = { showEqualizer = true },
                            onManageTags = {
                                val mediaId = item?.mediaId.orEmpty()
                                val fallback = metadata?.title?.toString().orEmpty()
                                tagViewModel.openForMediaId(mediaId, fallback)
                            },
                            sliceUiState = sliceUiState,
                            showActionRow = false,
                            bottomPadding = 40.dp,
                            coreControlsModifier = controlsMotion,
                            primaryColor = accentColor,
                            onPrimaryColor = onAccentColor
                        )
                    }
                }
            }
        } else if (phoneLandscape) {
            val headerMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.HEADER)
            val coverMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.COVER)
            val progressMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.PROGRESS)
            val lyricsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.LYRICS)
            val controlsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.CONTROLS)
            // --- 手机端横屏布局 (特殊适配) ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 顶部：返回、标题和队列按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(headerMotion)
                        .requiredHeight(0.dp)
                        .alpha(0f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = requestClose, enabled = !pendingRouteExit) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = metadata?.title?.toString().orEmpty().ifBlank { "未播放" },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = colorScheme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onShowSleepTimer) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = onShowQueue) {
                            Icon(
                                Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：封面 + 进度条
                    Column(
                        modifier = Modifier.weight(0.4f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(if (isVideo) videoAspectRatio else 1f)
                                .then(coverMotion),
                            contentAlignment = Alignment.Center
                        ) {
                            ArtworkBox(
                                isVideo = isVideo,
                                metadata = metadata,
                                viewModel = viewModel,
                                onOpenLyrics = showLyricsSurface,
                                edgeBlendEnabled = false,
                                edgeBlendColor = if (coverBackgroundEnabled) accentColor else colorScheme.background,
                                videoBackdropColor = videoBackdropColor,
                                artworkAlignment = coverPreviewAlignment,
                                dragPreviewEnabled = useDragPreview,
                                dragPreviewState = coverDragPreviewState
                            )
                        }

                        key(item?.mediaId) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(progressMotion)
                            ) {
                                PlayerProgress(
                                    positionMs = playback.positionMs,
                                    durationMs = progressDurationMs,
                                    sliceUiState = sliceUiState,
                                    onSeekTo = { viewModel.seekTo(it) },
                                    onCutPressed = { viewModel.onCutPressed(progressDurationMs) },
                                    onScrubbingChanged = { viewModel.setUserScrubbing(it) },
                                    onSelectSlice = { viewModel.selectSlice(it) },
                                    onLongPressSlice = {
                                        viewModel.selectSlice(it)
                                        showSliceSheet = true
                                    },
                                    onUpdateSliceRange = { sliceId, startMs, endMs ->
                                        viewModel.updateSliceRange(sliceId, startMs, endMs, progressDurationMs)
                                    },
                                    activeColor = accentColor,
                                    inactiveColor = accentColor.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }

                    // 右侧：歌词 + 控制
                    Column(
                        modifier = Modifier.weight(0.6f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isVideo) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .then(lyricsMotion),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AndroidView(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(44.dp)
                                        .graphicsLayer { clip = false },
                                    factory = { context ->
                                        ChannelSpectrumView(context).apply {
                                            setChannel(ChannelSpectrumView.Channel.Left)
                                            setBarCount(56)
                                            setBarColor(accentColor.toArgb())
                                        }
                                    },
                                    update = { view ->
                                        view.setBarColor(accentColor.toArgb())
                                    }
                                )

                                AppleLyricsView(
                                    lyrics = lyricsState.lyrics,
                                    currentPosition = playback.positionMs,
                                    onSeekTo = { viewModel.seekTo(it) },
                                    onOpenLyrics = showLyricsSurface,
                                    colors = lyricColors,
                                    modifier = Modifier
                                        .weight(0.72f)
                                        .fillMaxHeight(),
                                    isLandscape = true
                                )

                                AndroidView(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(44.dp)
                                        .graphicsLayer { clip = false },
                                    factory = { context ->
                                        ChannelSpectrumView(context).apply {
                                            setChannel(ChannelSpectrumView.Channel.Right)
                                            setBarCount(56)
                                            setBarColor(accentColor.toArgb())
                                        }
                                    },
                                    update = { view ->
                                        view.setBarColor(accentColor.toArgb())
                                    }
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f).then(lyricsMotion))
                        }

                        PlaybackControls(
                            playback = playback,
                            isFavorite = isFavorite,
                            viewModel = viewModel,
                            onShowPlaylistPicker = {
                                val current = playback.currentMediaItem ?: return@PlaybackControls
                                val md = current.mediaMetadata
                                val extras = md.extras
                                val albumId = extras?.getLong("album_id") ?: 0L
                                val trackId = extras?.getLong("track_id") ?: 0L
                                val rjCode = extras?.getString("rj_code").orEmpty()
                                onOpenPlaylistPicker(
                                    current.mediaId,
                                    current.localConfiguration?.uri?.toString().orEmpty(),
                                    md.title?.toString().orEmpty(),
                                    md.artist?.toString().orEmpty(),
                                    md.artworkUri?.toString().orEmpty(),
                                    albumId,
                                    trackId,
                                    rjCode
                                )
                            },
                            onShowEqualizer = { showEqualizer = true },
                            onManageTags = {
                                val mediaId = item?.mediaId.orEmpty()
                                val fallback = metadata?.title?.toString().orEmpty()
                                tagViewModel.openForMediaId(mediaId, fallback)
                            },
                            sliceUiState = sliceUiState,
                            showActionRow = false,
                            bottomPadding = 28.dp,
                            coreControlsModifier = controlsMotion,
                            primaryColor = accentColor,
                            onPrimaryColor = onAccentColor
                        )
                    }
                }
            }
        } else {
            val headerMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.HEADER)
            val coverMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.COVER)
            val lyricsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.LYRICS)
            val progressMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.PROGRESS)
            val actionRowMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.ACTION_ROW)
            val controlsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.CONTROLS)
            val volumeMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.VOLUME)
            // --- 垂直布局 (手机 或 平板竖屏) ---
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = if (widthClass == WindowWidthSizeClass.Compact) {
                        Modifier.fillMaxSize()
                    } else {
                        // 平板竖屏：限制最大宽度
                        Modifier
                            .fillMaxHeight()
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                    }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 顶部导航栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .then(headerMotion)
                            .requiredHeight(0.dp)
                            .alpha(0f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = requestClose, enabled = !pendingRouteExit) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        val textShadow = if (colorScheme.isDark) {
                            Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(0f, 2f), blurRadius = 4f)
                        } else {
                            Shadow(color = Color.Black.copy(alpha = 0.15f), offset = Offset(0f, 1f), blurRadius = 2f)
                        }

                        Text(
                            text = metadata?.title?.toString().orEmpty().ifBlank { "未播放" },
                            modifier = Modifier
                                .weight(1f)
                                .basicMarquee(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                shadow = textShadow
                            ),
                            color = colorScheme.textPrimary,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onShowSleepTimer) {
                                Icon(
                                    Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            IconButton(onClick = onShowQueue) {
                                Icon(
                                    Icons.Default.PlaylistPlay,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // 艺术家
                    Text(
                        text = metadata?.artist?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            shadow = if (colorScheme.isDark) {
                                Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(0f, 1f), blurRadius = 2f)
                            } else {
                                Shadow(color = Color.Black.copy(alpha = 0.12f), offset = Offset(0f, 0.5f), blurRadius = 1.5f)
                            }
                        ),
                        color = colorScheme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(coverMotion),
                        textAlign = TextAlign.Center
                    )

                    // 封面
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f) // 让封面占据剩余可用空间，自动收缩
                            .padding(vertical = if (widthClass == WindowWidthSizeClass.Compact) 16.dp else 32.dp)
                            .then(coverMotion),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .then(
                                    if (isVideo) {
                                        Modifier
                                            .widthIn(max = if (widthClass == WindowWidthSizeClass.Compact) 1000.dp else 400.dp)
                                            .fillMaxWidth()
                                            .aspectRatio(videoAspectRatio)
                                    } else {
                                        Modifier
                                            .fillMaxHeight()
                                            .aspectRatio(1f)
                                            .widthIn(max = if (widthClass == WindowWidthSizeClass.Compact) 1000.dp else 400.dp)
                                    }
                                ) // 平板竖屏限制最大宽度
                        ) {
                            ArtworkBox(
                                isVideo = isVideo,
                                metadata = metadata,
                                viewModel = viewModel,
                                onOpenLyrics = showLyricsSurface,
                                edgeBlendEnabled = false,
                                edgeBlendColor = if (coverBackgroundEnabled) accentColor else colorScheme.background,
                                videoBackdropColor = videoBackdropColor,
                                artworkAlignment = coverPreviewAlignment,
                                dragPreviewEnabled = useDragPreview,
                                dragPreviewState = coverDragPreviewState
                            )
                        }
                    }

                    if (!isVideo) {
                        SingleLineLyrics(
                            lyrics = lyricsState.lyrics,
                            currentPosition = playback.positionMs,
                            onOpenLyrics = showLyricsSurface,
                            colors = lyricColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(lyricsMotion)
                        )
                    }

                    key(item?.mediaId) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(progressMotion)
                        ) {
                            PlayerProgress(
                                positionMs = playback.positionMs,
                                durationMs = progressDurationMs,
                                sliceUiState = sliceUiState,
                                onSeekTo = { viewModel.seekTo(it) },
                                onCutPressed = { viewModel.onCutPressed(progressDurationMs) },
                                onScrubbingChanged = { viewModel.setUserScrubbing(it) },
                                onSelectSlice = { viewModel.selectSlice(it) },
                                onLongPressSlice = {
                                    viewModel.selectSlice(it)
                                    showSliceSheet = true
                                },
                                onUpdateSliceRange = { sliceId, startMs, endMs ->
                                    viewModel.updateSliceRange(sliceId, startMs, endMs, progressDurationMs)
                                },
                                activeColor = accentColor,
                                inactiveColor = accentColor.copy(alpha = 0.2f)
                            )
                        }
                    }

                    PlaybackControls(
                        playback = playback,
                        isFavorite = isFavorite,
                        viewModel = viewModel,
                        onShowPlaylistPicker = {
                            val current = playback.currentMediaItem ?: return@PlaybackControls
                            val md = current.mediaMetadata
                            val extras = md.extras
                            val albumId = extras?.getLong("album_id") ?: 0L
                            val trackId = extras?.getLong("track_id") ?: 0L
                            val rjCode = extras?.getString("rj_code").orEmpty()
                            onOpenPlaylistPicker(
                                current.mediaId,
                                current.localConfiguration?.uri?.toString().orEmpty(),
                                md.title?.toString().orEmpty(),
                                md.artist?.toString().orEmpty(),
                                md.artworkUri?.toString().orEmpty(),
                                albumId,
                                trackId,
                                rjCode
                            )
                        },
                        onShowEqualizer = { showEqualizer = true },
                        onManageTags = {
                            val mediaId = item?.mediaId.orEmpty()
                            val fallback = metadata?.title?.toString().orEmpty()
                            tagViewModel.openForMediaId(mediaId, fallback)
                        },
                        sliceUiState = sliceUiState,
                        actionRowModifier = actionRowMotion,
                        coreControlsModifier = controlsMotion,
                        primaryColor = accentColor,
                        onPrimaryColor = onAccentColor
                    )

                    VolumeControl(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(volumeMotion)
                            .onGloballyPositioned { coordinates ->
                                volumeControlBounds = coordinates.boundsInRoot()
                            },
                        accentColor = accentColor,
                        viewModel = viewModel,
                        hardwareVolumeEventTick = hardwareVolumeEventTick,
                        audioOutputRouteKind = audioOutputRouteKind,
                        warningSessionState = warningSessionState,
                        expanded = volumeControlExpanded,
                        onExpandedChange = { volumeControlExpanded = it }
                    )
                }
            }
            }
            }
            } else {
                NowPlayingLyricsSurface(
                    isLandscape = isLandscape,
                    playbackPositionMs = playback.positionMs,
                    lyrics = lyricsState.lyrics,
                    lyricColors = lyricColors,
                    lyricsPageSettings = lyricsPageSettings,
                    onSeekTo = { viewModel.seekTo(it) },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(routeTransition.nowPlayingMotionModifier(currentMotionLayout, NowPlayingMotionSlot.COVER))
                )
            }
                }
            }
        }

        val dialog = tagDialog
        if (dialog != null) {
            TagAssignDialog(
                title = dialog.title,
                allTags = availableTags,
                inheritedTags = dialog.inheritedTags,
                userTags = dialog.userTags,
                onDismiss = { tagViewModel.dismiss() },
                onApplyUserTags = { tagViewModel.applyUserTags(it) }
            )
        }

        if (showSliceSheet) {
            val sheetMinHeight = (configuration.screenHeightDp.dp * 0.66f).coerceAtLeast(320.dp)
            ModalBottomSheet(
                onDismissRequest = dismissSliceSheet,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colorScheme.surface,
                contentColor = colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = sheetMinHeight)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "切片管理",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.clearSlicesForCurrentTrack() }) {
                            Text("清空")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    SliceOverviewBar(
                        positionMs = playback.positionMs,
                        durationMs = progressDurationMs,
                        slices = sliceUiState.slices,
                        highlightedSliceId = highlightedPlaybackSliceId,
                        selectedSliceId = sliceUiState.selectedSliceId,
                        activeColor = accentColor,
                        inactiveColor = accentColor.copy(alpha = 0.18f),
                        onSeekTo = { viewModel.seekTo(it) },
                        onSelectSlice = { id ->
                            if (id == null) viewModel.selectSlice(null) else toggleSelectedSlice(id)
                        },
                        onLongPressSlice = { id ->
                            toggleSelectedSlice(id)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (sliceUiState.slices.isEmpty()) {
                        Text(
                            text = "暂无切片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.textTertiary,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    } else {
                        val sliceListState = rememberLazyListState()
                        LazyColumn(
                            state = sliceListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true)
                                .thinScrollbar(sliceListState),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(sliceUiState.slices, key = { _, s -> s.id }) { index, slice ->
                                val selected = slice.id == sliceUiState.selectedSliceId
                                val bg = if (selected) accentColor.copy(alpha = 0.12f) else colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = bg,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { toggleSelectedSlice(slice.id) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = (index + 1).toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorScheme.textTertiary,
                                            modifier = Modifier.widthIn(min = 18.dp)
                                        )

                                        TextButton(
                                            onClick = { timeEditTarget = slice.id to true },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(Formatting.formatTrackTime(slice.startMs))
                                        }

                                        Text(
                                            text = "→",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.textTertiary
                                        )

                                        TextButton(
                                            onClick = { timeEditTarget = slice.id to false },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(Formatting.formatTrackTime(slice.endMs))
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        IconButton(onClick = { viewModel.playSlicePreview(slice) }) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "播放切片",
                                                tint = colorScheme.onSurface
                                            )
                                        }

                                        IconButton(onClick = { viewModel.deleteSlice(slice.id) }) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteOutline,
                                                contentDescription = "删除切片",
                                                tint = colorScheme.onSurface.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                            item { Spacer(modifier = Modifier.height(18.dp)) }
                        }
                    }
                }
            }
        }

        val edit = timeEditTarget
        if (edit != null) {
            val slice = sliceUiState.slices.firstOrNull { it.id == edit.first }
            if (slice != null) {
                SliceTimeEditDialog(
                    title = if (edit.second) "修改起点" else "修改终点",
                    durationMs = progressDurationMs,
                    currentMs = playback.positionMs,
                    initialMs = if (edit.second) slice.startMs else slice.endMs,
                    onDismiss = { timeEditTarget = null },
                    onConfirm = { newMs ->
                        if (edit.second) {
                            viewModel.updateSliceRange(slice.id, newMs, slice.endMs, progressDurationMs)
                        } else {
                            viewModel.updateSliceRange(slice.id, slice.startMs, newMs, progressDurationMs)
                        }
                        timeEditTarget = null
                    }
                )
            } else {
                timeEditTarget = null
            }
        }

        if (showEqualizer) {
            val eqSettings by viewModel.sessionEqualizer.collectAsState()
            val customPresets by viewModel.customPresets.collectAsState()
            ModalBottomSheet(
                onDismissRequest = { showEqualizer = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colorScheme.surface,
                contentColor = colorScheme.onSurface
            ) {
                val scrollState = rememberScrollState()
                Box(modifier = Modifier.fillMaxHeight()) {
                    EqualizerPanel(
                        settings = eqSettings,
                        customPresets = customPresets,
                        onSettingsChanged = { viewModel.updateSessionEqualizer(it) },
                        onSavePreset = { name -> viewModel.saveCustomPreset(name, eqSettings) },
                        onDeletePreset = { viewModel.deleteCustomPreset(it) },
                        playbackSpeed = playback.playbackSpeed,
                        playbackPitch = playback.playbackPitch,
                        onPlaybackSpeedChanged = { viewModel.setPlaybackParameters(it, playback.playbackPitch) },
                        onPlaybackPitchChanged = { viewModel.setPlaybackParameters(playback.playbackSpeed, it) },
                        onPlaybackParametersChanged = { speed, pitch -> viewModel.setPlaybackParameters(speed, pitch) },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = 32.dp)
                    )
                }
            }
        }

        if (volumeControlExpanded) {
            DismissOutsideBoundsOverlay(
                targetBoundsInRoot = volumeControlBounds,
                onDismiss = { volumeControlExpanded = false }
            )
        }
    }
}
