package com.asmr.player.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.LayoutInflater
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import com.asmr.player.R
import com.asmr.player.HardwareVolumeOverlay
import com.asmr.player.data.lyrics.lyricsTargetContextFromMediaItem
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
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.smoothScrollToIndex
import com.asmr.player.ui.library.TagAssignDialog
import com.asmr.player.service.AudioOutputRouteKind
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.Formatting
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleIndexFinder
import com.asmr.player.listentogether.ListenTogetherStatus
import com.asmr.player.listentogether.ListenTogetherUiState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class NowPlayingSurfaceMode {
    PLAYER,
    LYRICS
}

private const val VideoProgressUiTickMs = 1_000L

private data class NowPlayingStaticPlayback(
    val isConnected: Boolean,
    val startupRestoreResolved: Boolean,
    val isPlaying: Boolean,
    val playbackState: Int,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val playbackSpeed: Float,
    val playbackPitch: Float,
    val currentMediaItem: MediaItem?,
    val durationMs: Long,
    val audioSessionId: Int
) {
    fun toSnapshot(positionMs: Long): PlaybackSnapshot {
        return PlaybackSnapshot(
            isConnected = isConnected,
            startupRestoreResolved = startupRestoreResolved,
            isPlaying = isPlaying,
            playbackState = playbackState,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            currentMediaItem = currentMediaItem,
            positionMs = positionMs,
            durationMs = durationMs,
            audioSessionId = audioSessionId
        )
    }
}

private data class NowPlayingProgressState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

private fun PlaybackSnapshot.toStaticPlayback(): NowPlayingStaticPlayback {
    return NowPlayingStaticPlayback(
        isConnected = isConnected,
        startupRestoreResolved = startupRestoreResolved,
        isPlaying = isPlaying,
        playbackState = playbackState,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
        playbackSpeed = playbackSpeed,
        playbackPitch = playbackPitch,
        currentMediaItem = currentMediaItem,
        durationMs = durationMs,
        audioSessionId = audioSessionId
    )
}

@Composable
private fun PlaybackProgressContent(
    viewModel: PlayerViewModel,
    isVideo: Boolean,
    content: @Composable (NowPlayingProgressState) -> Unit
) {
    val progressState by remember(viewModel, isVideo) {
        viewModel.playback
            .map { snapshot ->
                val positionMs = if (isVideo) {
                    (snapshot.positionMs / VideoProgressUiTickMs) * VideoProgressUiTickMs
                } else {
                    snapshot.positionMs
                }
                NowPlayingProgressState(positionMs, snapshot.durationMs)
            }
            .distinctUntilChanged()
    }.collectAsState(initial = NowPlayingProgressState())

    content(progressState)
}

private fun Modifier.fitVideoPreviewAspectRatio(
    aspectRatio: Float,
    maxWidth: Dp = Dp.Unspecified
): Modifier {
    val boundedModifier = if (maxWidth != Dp.Unspecified) {
        this.widthIn(max = maxWidth)
    } else {
        this
    }
    return boundedModifier.layout { measurable, constraints ->
        val ratio = aspectRatio
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(0.5f, 3f)
            ?: (16f / 9f)
        val hasBoundedWidth = constraints.maxWidth != androidx.compose.ui.unit.Constraints.Infinity
        val hasBoundedHeight = constraints.maxHeight != androidx.compose.ui.unit.Constraints.Infinity
        if (!hasBoundedWidth && !hasBoundedHeight) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }

        val availableWidth = when {
            hasBoundedWidth -> constraints.maxWidth
            hasBoundedHeight -> (constraints.maxHeight * ratio).roundToInt()
            else -> constraints.minWidth
        }.coerceAtLeast(1)
        val availableHeight = when {
            hasBoundedHeight -> constraints.maxHeight
            hasBoundedWidth -> (constraints.maxWidth / ratio).roundToInt()
            else -> constraints.minHeight
        }.coerceAtLeast(1)

        var width = availableWidth
        var height = (width / ratio).roundToInt().coerceAtLeast(1)
        if (height > availableHeight) {
            height = availableHeight
            width = (height * ratio).roundToInt().coerceAtLeast(1)
        }

        val placeable = measurable.measure(
            androidx.compose.ui.unit.Constraints.fixed(
                width = width.coerceAtMost(availableWidth),
                height = height.coerceAtMost(availableHeight)
            )
        )
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
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

internal sealed interface ListenTogetherAudiencePresentation {
    data class Status(val text: String) : ListenTogetherAudiencePresentation

    data class Audience(val companionCount: Int) : ListenTogetherAudiencePresentation
}

internal fun resolveListenTogetherAudiencePresentation(
    state: ListenTogetherUiState
): ListenTogetherAudiencePresentation? = when {
    !state.available && state.status == ListenTogetherStatus.Unsupported ->
        ListenTogetherAudiencePresentation.Status("当前音频无法参与一起听")
    state.listenerCount != null ->
        ListenTogetherAudiencePresentation.Audience(
            companionCount = (state.listenerCount - 1).coerceAtLeast(0)
        )
    state.available ->
        ListenTogetherAudiencePresentation.Audience(companionCount = 0)
    else ->
        null
}

private fun <S> AnimatedContentTransitionScope<S>.listenTogetherInlineTransform(): ContentTransform {
    val enter = fadeIn(
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundEnterDurationMs,
            easing = LinearOutSlowInEasing
        )
    ) + slideInVertically(
        initialOffsetY = { fullHeight -> fullHeight / 3 },
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
        targetOffsetY = { fullHeight -> -(fullHeight / 3) },
        animationSpec = tween(
            durationMillis = NowPlayingMotionSpec.PlayerForegroundExitDurationMs,
            easing = FastOutLinearInEasing
        )
    )
    return enter togetherWith exit using SizeTransform(clip = false)
}

private fun AnimatedContentTransitionScope<Int>.listenTogetherCounterTransform(): ContentTransform {
    val direction = if (targetState >= initialState) 1 else -1
    val enter = fadeIn(
        animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
    ) + slideInVertically(
        initialOffsetY = { fullHeight -> direction * fullHeight },
        animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
    )
    val exit = fadeOut(
        animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)
    ) + slideOutVertically(
        targetOffsetY = { fullHeight -> -direction * fullHeight },
        animationSpec = tween(durationMillis = 140, easing = FastOutLinearInEasing)
    )
    return enter togetherWith exit using SizeTransform(clip = false)
}

@Composable
private fun ListenTogetherAudienceCountText(
    companionCount: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.labelSmall
    var lastNonZeroCount by remember { mutableIntStateOf(0) }

    val displayCount = when {
        companionCount > 0 -> {
            lastNonZeroCount = companionCount
            companionCount
        }
        lastNonZeroCount > 0 -> lastNonZeroCount
        else -> 0
    }

    AnimatedContent(
        targetState = displayCount > 0,
        transitionSpec = { listenTogetherInlineTransform() },
        label = "listenTogetherAudienceMode"
    ) { hasCompanions ->
        if (hasCompanions) {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = displayCount,
                    transitionSpec = { listenTogetherCounterTransform() },
                    label = "listenTogetherAudienceCounter"
                ) { value ->
                    Text(
                        text = value.toString(),
                        style = textStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = color,
                        maxLines = 1
                    )
                }
                Text(
                    text = " 人正在和你一起听",
                    style = textStyle,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = "孤独赏鉴中",
                modifier = modifier,
                style = textStyle,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ListenTogetherAudienceLine(
    state: ListenTogetherUiState,
    modifier: Modifier = Modifier,
    accentColor: Color = AsmrTheme.colorScheme.primary,
    pageEntranceSettled: Boolean = true
) {
    val colorScheme = AsmrTheme.colorScheme
    val presentation = resolveListenTogetherAudiencePresentation(state)

    val displayTarget = if (pageEntranceSettled) presentation else null

    Box(
        modifier = modifier.height(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.animateContentSize(
                animationSpec = tween(
                    durationMillis = NowPlayingMotionSpec.PlayerForegroundEnterDurationMs,
                    easing = LinearOutSlowInEasing
                )
            ),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_users_round),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = accentColor.copy(alpha = 0.82f)
            )
            AnimatedContent(
                targetState = displayTarget,
                transitionSpec = {
                    fadeIn(tween(300, easing = LinearOutSlowInEasing)) togetherWith
                        fadeOut(tween(200, easing = FastOutLinearInEasing))
                },
                label = "listenTogetherAudienceText"
            ) { target ->
                when (target) {
                    is ListenTogetherAudiencePresentation.Status -> Text(
                        text = target.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    is ListenTogetherAudiencePresentation.Audience -> ListenTogetherAudienceCountText(
                        companionCount = target.companionCount,
                        color = colorScheme.textTertiary
                    )
                    null -> {}
                }
            }
        }
    }
}

@Composable
private fun ArtistWithListenTogetherInfo(
    artist: String,
    listenTogetherState: ListenTogetherUiState,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    badgeAlignment: Alignment = Alignment.TopStart,
    textAlignment: Alignment = Alignment.CenterStart,
    accentColor: Color = AsmrTheme.colorScheme.primary,
    pageEntranceSettled: Boolean = true
) {
    Box(modifier = modifier.fillMaxWidth()) {
        ListenTogetherAudienceLine(
            state = listenTogetherState,
            modifier = Modifier
                .align(badgeAlignment)
                .offset(y = (-18).dp),
            accentColor = accentColor,
            pageEntranceSettled = pageEntranceSettled
        )
        Text(
            text = artist,
            modifier = Modifier.align(textAlignment),
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign
        )
    }
}

@Composable
@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
internal fun NowPlayingScreen(
    windowSizeClass: WindowSizeClass,
    hardwareVolumeEventTick: Long,
    onInlineVolumeControlVisibilityChanged: (Boolean) -> Unit = {},
    onEqualizerVisibilityChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onRouteExitStarted: (exitDurationMs: Int) -> Unit = {},
    onShowQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onOpenPlaylistPicker: (MediaItem) -> Unit,
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
    val staticPlayback by remember(viewModel) {
        viewModel.playback
            .map { it.toStaticPlayback() }
            .distinctUntilChanged()
    }.collectAsState(initial = PlaybackSnapshot().toStaticPlayback())
    val playback = staticPlayback.toSnapshot(positionMs = 0L)
    val resolvedDurationMs by viewModel.resolvedDurationMs.collectAsState()
    val sliceUiState by viewModel.sliceUiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val listenTogetherUiState by viewModel.listenTogetherUiState.collectAsState()
    val lyricsState by lyricsViewModel.uiState.collectAsState()
    val item = playback.currentMediaItem
    val metadata = item?.mediaMetadata
    val canBindManualLyrics = lyricsTargetContextFromMediaItem(item) != null
    val colorScheme = AsmrTheme.colorScheme
    val uriText = item?.localConfiguration?.uri?.toString().orEmpty()
    val isOnlineMedia = remember(uriText, item?.mediaId) { item.isOnlineMedia() }
    val artworkModel = remember(metadata?.artworkUri) {
        sanitizeBackdropArtworkModel(metadata?.artworkUri)
    }
    val mimeType = item?.localConfiguration?.mimeType.orEmpty()
    val ext = uriText.substringBefore('#').substringBefore('?').substringAfterLast('.', "").lowercase()
    val isVideo = metadata?.extras?.getBoolean("is_video") == true ||
        mimeType.startsWith("video/") ||
        ext in setOf("mp4", "m4v", "webm", "mkv", "mov")
    
    var showEqualizer by remember { mutableStateOf(false) }
    val tagViewModel: NowPlayingTagViewModel = hiltViewModel()
    val tagDialog by tagViewModel.dialogState.collectAsState()
    val availableTags by tagViewModel.availableTags.collectAsState()
    val playerArtworkBackdropEnabled = coverBackgroundEnabled && !isVideo
    val playerThemeColors = rememberPlayerThemeColors(
        mediaItem = item,
        colorScheme = colorScheme,
        coverBackgroundEnabled = coverBackgroundEnabled,
        artworkBackdropEnabled = playerArtworkBackdropEnabled
    )
    val accentColor = playerThemeColors.accentColor
    val lyricColors = rememberLyricReadableColors(
        accentColor = accentColor,
        backdropTintColor = playerThemeColors.backdropTintColor,
        coverBackgroundEnabled = playerArtworkBackdropEnabled,
        coverBackgroundClarity = coverBackgroundClarity
    )
    val onAccentColor = playerThemeColors.onAccentColor
    val videoBackdropColor = if (isVideo) playerThemeColors.videoBackdropColor else Color.Transparent
    val progressDurationMs = when {
        playback.durationMs > 0L && resolvedDurationMs > 0L -> maxOf(playback.durationMs, resolvedDurationMs)
        playback.durationMs > 0L -> playback.durationMs
        else -> resolvedDurationMs
    }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val lyricsPickerMimeTypes = remember {
        arrayOf(
            "*/*",
            "text/*",
            "application/octet-stream",
            "application/x-subrip",
            "application/lrc",
            "audio/x-lrc"
        )
    }
    val lyricsPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val displayName = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull().orEmpty()
        val extension = displayName.ifBlank { uri.lastPathSegment.orEmpty() }
            .substringAfterLast('.', "")
            .lowercase()
        if (extension !in setOf("lrc", "srt", "vtt")) {
            viewModel.showUnsupportedLyricsFileMessage()
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModel.bindManualLyrics(uri.toString()) {
            lyricsViewModel.refreshCurrentLyrics()
        }
    }
    val openManualLyricsAction: (() -> Unit)? = if (canBindManualLyrics) {
        {
            if (isOnlineMedia) {
                viewModel.showOnlineManualLyricsUnsupported()
            } else {
                lyricsPicker.launch(lyricsPickerMimeTypes)
            }
        }
    } else {
        null
    }
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
    val pageEntranceSettled by remember {
        derivedStateOf {
            routeTransition.currentState && routeTransition.targetState && !routeTransition.isRunning
        }
    }
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
    // Only the portrait player layout renders the inline volume control.
    // Split landscape, phone landscape, and the dedicated lyrics surface should
    // all fall back to the floating hardware volume overlay.
    val usesInlineVolumeControl =
        surfaceMode == NowPlayingSurfaceMode.PLAYER &&
            !useSplitLayout &&
            !isPhoneLandscape
    val latestOnInlineVolumeControlVisibilityChanged by rememberUpdatedState(onInlineVolumeControlVisibilityChanged)
    val latestOnEqualizerVisibilityChanged by rememberUpdatedState(onEqualizerVisibilityChanged)

    SideEffect {
        latestOnInlineVolumeControlVisibilityChanged(usesInlineVolumeControl)
    }

    SideEffect {
        latestOnEqualizerVisibilityChanged(showEqualizer)
    }

    LaunchedEffect(showEqualizer) {
        if (showEqualizer) {
            volumeControlExpanded = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            latestOnInlineVolumeControlVisibilityChanged(false)
            latestOnEqualizerVisibilityChanged(false)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (renderBackdrop && !isVideo) {
            CoverArtworkBackground(
                artworkModel = artworkModel,
                enabled = coverBackgroundEnabled,
                clarity = coverBackgroundClarity,
                overlayBaseColor = colorScheme.background,
                tintBaseColor = playerThemeColors.backdropTintColor,
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
                onManualBindLyrics = if (surfaceMode == NowPlayingSurfaceMode.LYRICS) openManualLyricsAction else null,
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
                                Icons.Rounded.KeyboardArrowDown,
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
                                Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = onShowQueue) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistPlay,
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
                                .then(
                                    if (isVideo) {
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    } else {
                                        Modifier
                                            .widthIn(max = 420.dp)
                                            .aspectRatio(1f)
                                    }
                                )
                                .then(coverMotion),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = if (isVideo) {
                                    Modifier.fitVideoPreviewAspectRatio(
                                        aspectRatio = videoAspectRatio,
                                        maxWidth = 420.dp
                                    )
                                } else {
                                    Modifier.fillMaxSize()
                                }
                            ) {
                                ArtworkBox(
                                    isVideo = isVideo,
                                    metadata = metadata,
                                    viewModel = viewModel,
                                    onOpenLyrics = showLyricsSurface,
                                    edgeBlendEnabled = false,
                                    edgeBlendColor = if (playerArtworkBackdropEnabled) playerThemeColors.backdropTintColor else colorScheme.background,
                                    videoBackdropColor = videoBackdropColor,
                                    artworkAlignment = coverPreviewAlignment,
                                    dragPreviewEnabled = useDragPreview,
                                    dragPreviewState = coverDragPreviewState
                                )
                            }
                        }
                        
                        key(item?.mediaId) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(progressMotion)
                            ) {
                                PlaybackProgressContent(viewModel, isVideo) { progress ->
                                    PlayerProgress(
                                        positionMs = progress.positionMs,
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
                    }

                    // 右侧：信息与控制区
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 艺术家 (标题已移动到 header)
                        ArtistWithListenTogetherInfo(
                            artist = metadata?.artist?.toString().orEmpty(),
                            listenTogetherState = listenTogetherUiState,
                            modifier = Modifier.then(infoMotion),
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.textSecondary,
                            accentColor = accentColor,
                            pageEntranceSettled = pageEntranceSettled
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

                                Box(
                                    modifier = Modifier
                                        .weight(0.70f)
                                        .fillMaxHeight()
                                ) {
                                    PlaybackProgressContent(viewModel, isVideo) { progress ->
                                        AppleLyricsView(
                                            lyrics = lyricsState.lyrics,
                                            currentPosition = progress.positionMs,
                                            onSeekTo = { viewModel.seekTo(it) },
                                            onOpenLyrics = showLyricsSurface,
                                            colors = lyricColors,
                                            modifier = Modifier.fillMaxSize(),
                                            isLandscape = true
                                        )
                                    }
                                }

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
                                onOpenPlaylistPicker(current)
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
                                Icons.Rounded.KeyboardArrowDown,
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
                                Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = onShowQueue) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistPlay,
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
                                .then(coverMotion),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = if (isVideo) {
                                    Modifier.fitVideoPreviewAspectRatio(videoAspectRatio)
                                } else {
                                    Modifier.aspectRatio(1f)
                                }
                            ) {
                                ArtworkBox(
                                    isVideo = isVideo,
                                    metadata = metadata,
                                    viewModel = viewModel,
                                    onOpenLyrics = showLyricsSurface,
                                    edgeBlendEnabled = false,
                                    edgeBlendColor = if (playerArtworkBackdropEnabled) playerThemeColors.backdropTintColor else colorScheme.background,
                                    videoBackdropColor = videoBackdropColor,
                                    artworkAlignment = coverPreviewAlignment,
                                    dragPreviewEnabled = useDragPreview,
                                    dragPreviewState = coverDragPreviewState
                                )
                            }
                        }

                        key(item?.mediaId) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(progressMotion)
                            ) {
                                PlaybackProgressContent(viewModel, isVideo) { progress ->
                                    PlayerProgress(
                                        positionMs = progress.positionMs,
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

                                Box(
                                    modifier = Modifier
                                        .weight(0.72f)
                                        .fillMaxHeight()
                                ) {
                                    PlaybackProgressContent(viewModel, isVideo) { progress ->
                                        AppleLyricsView(
                                            lyrics = lyricsState.lyrics,
                                            currentPosition = progress.positionMs,
                                            onSeekTo = { viewModel.seekTo(it) },
                                            onOpenLyrics = showLyricsSurface,
                                            colors = lyricColors,
                                            modifier = Modifier.fillMaxSize(),
                                            isLandscape = true
                                        )
                                    }
                                }

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
                                onOpenPlaylistPicker(current)
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
                                Icons.Rounded.KeyboardArrowDown,
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
                                    Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            IconButton(onClick = onShowQueue) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // 艺术家
                    ArtistWithListenTogetherInfo(
                        artist = metadata?.artist?.toString().orEmpty(),
                        listenTogetherState = listenTogetherUiState,
                        style = MaterialTheme.typography.bodySmall.copy(
                            shadow = if (colorScheme.isDark) {
                                Shadow(color = Color.Black.copy(alpha = 0.4f), offset = Offset(0f, 1f), blurRadius = 2f)
                            } else {
                                Shadow(color = Color.Black.copy(alpha = 0.12f), offset = Offset(0f, 0.5f), blurRadius = 1.5f)
                            }
                        ),
                        color = colorScheme.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(coverMotion),
                        textAlign = TextAlign.Center,
                        badgeAlignment = Alignment.TopCenter,
                        textAlignment = Alignment.Center,
                        accentColor = accentColor,
                        pageEntranceSettled = pageEntranceSettled
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
                                            .fitVideoPreviewAspectRatio(videoAspectRatio)
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
                                edgeBlendColor = if (playerArtworkBackdropEnabled) playerThemeColors.backdropTintColor else colorScheme.background,
                                videoBackdropColor = videoBackdropColor,
                                artworkAlignment = coverPreviewAlignment,
                                dragPreviewEnabled = useDragPreview,
                                dragPreviewState = coverDragPreviewState
                            )
                        }
                    }

                    if (!isVideo) {
                        PlaybackProgressContent(viewModel, isVideo) { progress ->
                            SingleLineLyrics(
                                lyrics = lyricsState.lyrics,
                                currentPosition = progress.positionMs,
                                onOpenLyrics = showLyricsSurface,
                                colors = lyricColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(lyricsMotion)
                            )
                        }
                    }

                    key(item?.mediaId) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(progressMotion)
                        ) {
                            PlaybackProgressContent(viewModel, isVideo) { progress ->
                                PlayerProgress(
                                    positionMs = progress.positionMs,
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

                    PlaybackControls(
                        playback = playback,
                        isFavorite = isFavorite,
                        viewModel = viewModel,
                        onShowPlaylistPicker = {
                            val current = playback.currentMediaItem ?: return@PlaybackControls
                            onOpenPlaylistPicker(current)
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
                PlaybackProgressContent(viewModel, isVideo) { progress ->
                    NowPlayingLyricsSurface(
                        isLandscape = isLandscape,
                        playbackPositionMs = progress.positionMs,
                        lyrics = lyricsState.lyrics,
                        lyricColors = lyricColors,
                        lyricsPageSettings = lyricsPageSettings,
                        onSeekTo = { viewModel.seekTo(it) },
                        onTimelinePlay = { targetMs ->
                            viewModel.seekTo(targetMs)
                            viewModel.play()
                        },
                        onAddLyrics = openManualLyricsAction,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(routeTransition.nowPlayingMotionModifier(currentMotionLayout, NowPlayingMotionSlot.COVER))
                    )
                }
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

                    PlaybackProgressContent(viewModel, isVideo) { progress ->
                        val highlightedPlaybackSliceId = currentSliceIdForPosition(
                            positionMs = progress.positionMs,
                            slices = sliceUiState.slices,
                            sliceModeEnabled = sliceUiState.sliceModeEnabled
                        )
                        SliceOverviewBar(
                            positionMs = progress.positionMs,
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
                    }

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
                                                imageVector = Icons.Rounded.PlayArrow,
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
                PlaybackProgressContent(viewModel, isVideo) { progress ->
                    SliceTimeEditDialog(
                        title = if (edit.second) "修改起点" else "修改终点",
                        durationMs = progressDurationMs,
                        currentMs = progress.positionMs,
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
                }
            } else {
                timeEditTarget = null
            }
        }

        if (showEqualizer) {
            val eqSettings by viewModel.sessionEqualizer.collectAsState()
            val customPresets by viewModel.customPresets.collectAsState()
            val appVolumePercent by viewModel.appVolumePercent.collectAsState()
            val equalizerFocusRequester = remember { FocusRequester() }
            var showEqualizerVolumeOverlay by remember { mutableStateOf(false) }
            var equalizerVolumeOverlayInteracting by remember { mutableStateOf(false) }
            var equalizerVolumeOverlayHoldTick by remember { mutableLongStateOf(0L) }
            var equalizerVolumeOverlayBounds by remember { mutableStateOf<Rect?>(null) }
            var lastNonZeroEqualizerVolume by remember { mutableIntStateOf(AppVolume.DefaultPercent) }
            LaunchedEffect(equalizerFocusRequester) {
                equalizerFocusRequester.requestFocus()
            }
            LaunchedEffect(appVolumePercent) {
                if (appVolumePercent > 0) {
                    lastNonZeroEqualizerVolume = appVolumePercent
                }
            }
            LaunchedEffect(showEqualizerVolumeOverlay, equalizerVolumeOverlayHoldTick, equalizerVolumeOverlayInteracting) {
                if (!showEqualizerVolumeOverlay) return@LaunchedEffect
                if (equalizerVolumeOverlayInteracting) return@LaunchedEffect
                val snapshot = equalizerVolumeOverlayHoldTick
                delay(2_000)
                if (!equalizerVolumeOverlayInteracting && equalizerVolumeOverlayHoldTick == snapshot) {
                    showEqualizerVolumeOverlay = false
                    equalizerVolumeOverlayBounds = null
                }
            }
            ModalBottomSheet(
                onDismissRequest = { showEqualizer = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colorScheme.surface,
                contentColor = colorScheme.onSurface
            ) {
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .focusRequester(equalizerFocusRequester)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.nativeKeyEvent.keyCode) {
                                AndroidKeyEvent.KEYCODE_VOLUME_UP -> {
                                    viewModel.adjustAppVolumePercent(AppVolume.StepPercent)
                                    showEqualizerVolumeOverlay = true
                                    equalizerVolumeOverlayHoldTick += 1L
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_VOLUME_DOWN -> {
                                    viewModel.adjustAppVolumePercent(-AppVolume.StepPercent)
                                    showEqualizerVolumeOverlay = true
                                    equalizerVolumeOverlayHoldTick += 1L
                                    true
                                }
                                else -> false
                            }
                        }
                ) {
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
                    if (showEqualizerVolumeOverlay) {
                        DismissOutsideBoundsOverlay(
                            targetBoundsInRoot = equalizerVolumeOverlayBounds,
                            onDismiss = {
                                showEqualizerVolumeOverlay = false
                                equalizerVolumeOverlayBounds = null
                            }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = 18.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showEqualizerVolumeOverlay,
                            enter = fadeIn(animationSpec = tween(140)) + slideInHorizontally(animationSpec = tween(180)) { it / 3 },
                            exit = fadeOut(animationSpec = tween(160)) + slideOutHorizontally(animationSpec = tween(180)) { it / 3 }
                        ) {
                            HardwareVolumeOverlay(
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    equalizerVolumeOverlayBounds = coordinates.boundsInRoot()
                                },
                                volumePercent = appVolumePercent,
                                audioOutputRouteKind = audioOutputRouteKind,
                                onVolumeChange = {
                                    viewModel.setAppVolumePercent(it)
                                    equalizerVolumeOverlayHoldTick += 1L
                                },
                                onToggleMute = {
                                    if (appVolumePercent > 0) {
                                        viewModel.setAppVolumePercent(0)
                                    } else {
                                        viewModel.setAppVolumePercent(
                                            lastNonZeroEqualizerVolume.coerceAtLeast(AppVolume.StepPercent)
                                        )
                                    }
                                    equalizerVolumeOverlayHoldTick += 1L
                                },
                                onInteractionActiveChanged = { active ->
                                    equalizerVolumeOverlayInteracting = active
                                    if (!active) {
                                        equalizerVolumeOverlayHoldTick += 1L
                                    }
                                },
                                warningSessionState = warningSessionState
                            )
                        }
                    }
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
