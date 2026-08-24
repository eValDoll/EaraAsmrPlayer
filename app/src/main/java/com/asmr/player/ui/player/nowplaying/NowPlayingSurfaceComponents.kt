package com.asmr.player.ui.player

import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import com.asmr.player.R
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AudioOutputRouteIcon
import com.asmr.player.ui.common.HorizontalStereoSpectrum
import com.asmr.player.ui.common.DismissOutsideBoundsOverlay
import com.asmr.player.ui.common.AppVolumeHearingWarningDialog
import com.asmr.player.ui.common.AppVolumeSlider
import com.asmr.player.ui.common.AppVolumeWarningSessionState
import com.asmr.player.ui.common.AsmrImageLoadingPlaceholder
import com.asmr.player.playback.AppVolume
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.ui.common.EqualizerPanel
import com.asmr.player.ui.common.rememberProtectedAppVolumeChangeState
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun PlayerSurfaceHeader(
    title: String,
    isLandscape: Boolean,
    onNavigateUp: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowQueue: () -> Unit,
    onManualBindLyrics: (() -> Unit)? = null,
    navigationEnabled: Boolean,
    showTitle: Boolean = true,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val headerShadow = remember(colorScheme.isDark) {
        if (colorScheme.isDark) {
            Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(0f, 2f), blurRadius = 4f)
        } else {
            Shadow(color = Color.Black.copy(alpha = 0.15f), offset = Offset(0f, 1f), blurRadius = 2f)
        }
    }
    val dividerColor = colorScheme.onSurface.copy(
        alpha = if (colorScheme.isDark) 0.16f else 0.10f
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
        IconButton(onClick = onNavigateUp, enabled = navigationEnabled) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(if (isLandscape) 24.dp else 28.dp),
                tint = colorScheme.onSurface
            )
        }
        if (showTitle) {
            Text(
                text = title.ifBlank { "未播放" },
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = if (isLandscape) 14.sp else 16.sp,
                    shadow = headerShadow
                ),
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorScheme.textPrimary
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onManualBindLyrics != null) {
                IconButton(onClick = onManualBindLyrics) {
                    Icon(
                        painter = painterResource(R.drawable.ic_manual_subtitle_import),
                        contentDescription = "手动绑定歌词",
                        modifier = Modifier.size(if (isLandscape) 20.dp else 22.dp),
                        tint = colorScheme.onSurface
                    )
                }
            }
            IconButton(onClick = onShowSleepTimer) {
                Icon(
                    Icons.Rounded.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(if (isLandscape) 20.dp else 22.dp),
                    tint = colorScheme.onSurface
                )
            }
            IconButton(onClick = onShowQueue) {
                Icon(
                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(if (isLandscape) 22.dp else 24.dp),
                    tint = colorScheme.onSurface
                )
            }
        }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                thickness = 0.5.dp,
                color = dividerColor
            )
        }
    }
}

@Composable
internal fun NowPlayingLyricsSurface(
    isLandscape: Boolean,
    playbackPositionMs: Long,
    lyrics: List<SubtitleEntry>,
    lyricColors: LyricReadableColors,
    accentColor: Color,
    spectrumColor: Color,
    onAccentColor: Color,
    lyricsPageSettings: LyricsPageSettings,
    onSeekTo: (Long) -> Unit,
    onTimelinePlay: ((Long) -> Unit)? = null,
    onAddLyrics: (() -> Unit)? = null,
    interactionEnabled: Boolean = true,
    stableFocusAnchor: Boolean = false,
    expandedHomeVisualEffects: Boolean = false,
    lyricItemOuterHorizontalPadding: Dp = if (isLandscape) 10.dp else 14.dp,
    lyricItemInnerHorizontalPadding: Dp = if (isLandscape) 8.dp else 10.dp,
    contentKey: String? = null,
    contentVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = tween(durationMillis = if (contentVisible) 220 else 120),
        label = "nowPlayingLyricsSurfaceAlpha"
    )
    val effectiveInteractionEnabled = interactionEnabled && contentVisible
    BoxWithConstraints(
        modifier = modifier.graphicsLayer {
            alpha = surfaceAlpha
        }
    ) {
        if (isLandscape) {
            HorizontalStereoSpectrum(
                lineColor = spectrumColor,
                intensity = 0.72f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (maxWidth >= 840.dp) 112.dp else 88.dp)
                    .align(Alignment.Center)
            )
        }
        if (lyrics.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "暂无歌词",
                    style = MaterialTheme.typography.titleMedium,
                    color = AsmrTheme.colorScheme.textSecondary
                )
                if (onAddLyrics != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onAddLyrics,
                        enabled = effectiveInteractionEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = onAccentColor
                        )
                    ) {
                        Text("添加歌词")
                    }
                }
            }
        } else {
            AppleLyricsView(
                lyrics = lyrics,
                currentPosition = playbackPositionMs,
                onSeekTo = onSeekTo,
                onTimelinePlay = onTimelinePlay,
                showPlaybackTimeline = true,
                colors = lyricColors,
                modifier = Modifier.fillMaxSize(),
                isLandscape = isLandscape,
                settings = lyricsPageSettings,
                interactionEnabled = effectiveInteractionEnabled,
                stableFocusAnchor = stableFocusAnchor,
                expandedHomeVisualEffects = expandedHomeVisualEffects,
                itemOuterHorizontalPadding = lyricItemOuterHorizontalPadding,
                itemInnerHorizontalPadding = lyricItemInnerHorizontalPadding,
                contentKey = contentKey,
                contentVisible = contentVisible
            )
        }
    }
}

@Composable
internal fun ArtworkBox(
    isVideo: Boolean,
    metadata: androidx.media3.common.MediaMetadata?,
    viewModel: PlayerViewModel,
    videoPlayerCoordinator: NowPlayingVideoPlayerCoordinator,
    renderVideoSurface: Boolean,
    videoFullscreen: Boolean,
    onOpenVideoFullscreen: () -> Unit,
    onOpenLyrics: () -> Unit,
    edgeBlendEnabled: Boolean,
    edgeBlendColor: Color,
    videoBackdropColor: Color,
    artworkAlignment: Alignment = Alignment.Center,
    artworkContentScale: ContentScale = ContentScale.Crop,
    artworkCornerRadius: Dp = 28.dp,
    artworkLoadAtOriginalSize: Boolean = false,
    dragPreviewEnabled: Boolean = false,
    dragPreviewState: CoverDragPreviewState? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(artworkCornerRadius)
    val placeholderCornerRadius = artworkCornerRadius.value.roundToInt()
    Box(
        modifier = modifier
            .fillMaxSize()
            .coverDragPreviewGesture(
                enabled = dragPreviewEnabled && dragPreviewState != null,
                state = dragPreviewState ?: CoverDragPreviewState(),
                minPointers = 2
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isVideo) {
            val player = viewModel.playerOrNull()
            if (!videoFullscreen && renderVideoSurface) {
                NowPlayingVideoPlayer(
                    player = player,
                    coordinator = videoPlayerCoordinator,
                    fullscreen = false,
                    onToggleFullscreen = onOpenVideoFullscreen,
                    backdropColor = videoBackdropColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(videoBackdropColor)
                        .clipToBounds()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(videoBackdropColor))
            }
        } else {
            if (edgeBlendEnabled) {
                val artwork = metadata?.artworkUri
                if (artwork != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onOpenLyrics() }
                    ) {
                        CoverArtworkEdgeBlend(
                            artworkModel = artwork,
                            blendColor = edgeBlendColor,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = artworkCornerRadius,
                            artworkAlignment = artworkAlignment
                        )
                    }
                }
            } else {
                AsmrAsyncImage(
                    model = metadata?.artworkUri,
                    contentDescription = null,
                    contentScale = artworkContentScale,
                    alignment = artworkAlignment,
                    placeholderCornerRadius = placeholderCornerRadius,
                    placeholder = {},
                    loading = { modifier ->
                        AsmrImageLoadingPlaceholder(
                            modifier = modifier,
                            cornerRadius = placeholderCornerRadius,
                            indicatorSize = 36.dp
                        )
                    },
                    empty = {},
                    peekAnySizeForInitial = true,
                    retainPainterDuringReload = true,
                    loadAtOriginalSize = artworkLoadAtOriginalSize,
                    loadWhenSizeStableForMillis = 120L,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .clickable { onOpenLyrics() },
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class NowPlayingVideoPlayerCoordinator {
    private var currentView: PlayerView? = null
    private var currentPlayer: Player? = null

    fun attach(view: PlayerView, player: Player?) {
        if (currentView === view && currentPlayer === player) return

        val previousView = currentView
        val previousPlayer = currentPlayer
        if (previousPlayer !== player) {
            if (previousPlayer != null && previousView != null) {
                PlayerView.switchTargetView(previousPlayer, previousView, null)
            } else {
                previousView?.player = null
            }
            currentView = null
            currentPlayer = player
        }

        if (player != null) {
            PlayerView.switchTargetView(player, currentView, view)
        } else {
            view.player = null
        }
        currentView = view
    }

    fun detach(view: PlayerView) {
        if (currentView !== view) return
        val player = currentPlayer
        if (player != null) {
            PlayerView.switchTargetView(player, view, null)
        } else {
            view.player = null
        }
        currentView = null
    }

    fun rebindCurrentSurface(view: PlayerView, player: Player?) {
        if (player == null || currentView !== view || currentPlayer !== player) return

        // PlayerView 可能在 Surface 尚未创建时就完成首次绑定。横竖屏布局切换后，
        // 等新 Surface 真正可用再重绑一次，只迁移视频输出，不改变播放状态。
        view.player = null
        view.player = player
    }

    fun release() {
        currentView?.let(::detach)
        currentPlayer = null
    }
}

@Composable
internal fun NowPlayingFullscreenVideo(
    player: Player?,
    coordinator: NowPlayingVideoPlayerCoordinator,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    NowPlayingVideoPlayer(
        player = player,
        coordinator = coordinator,
        fullscreen = true,
        onToggleFullscreen = onDismiss,
        backdropColor = Color.Black,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun NowPlayingVideoPlayer(
    player: Player?,
    coordinator: NowPlayingVideoPlayerCoordinator,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    backdropColor: Color,
    modifier: Modifier = Modifier
) {
    val playerViewHolder = remember { arrayOfNulls<PlayerView>(1) }
    val surfaceCallbackHolder = remember { arrayOfNulls<SurfaceHolder.Callback>(1) }
    val latestPlayer by rememberUpdatedState(player)
    DisposableEffect(coordinator) {
        onDispose {
            playerViewHolder[0]?.let { view ->
                (view.videoSurfaceView as? SurfaceView)?.holder?.let { holder ->
                    surfaceCallbackHolder[0]?.let(holder::removeCallback)
                }
                coordinator.detach(view)
            }
            surfaceCallbackHolder[0] = null
            playerViewHolder[0] = null
        }
    }

    Box(
        modifier = modifier
            .background(if (fullscreen) backdropColor else Color.Transparent)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val view = LayoutInflater.from(context)
                    .inflate(R.layout.view_now_playing_video_player, null, false) as PlayerView
                view.apply {
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setKeepContentOnPlayerReset(true)
                    setBackgroundColor(backdropColor.toArgb())
                    setShutterBackgroundColor(backdropColor.toArgb())
                }
                playerViewHolder[0] = view
                coordinator.attach(view, player)
                (view.videoSurfaceView as? SurfaceView)?.holder?.let { holder ->
                    var initialSurfaceCreated = false
                    val callback = object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            if (initialSurfaceCreated) return
                            initialSurfaceCreated = true
                            coordinator.rebindCurrentSurface(view, latestPlayer)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                    }
                    surfaceCallbackHolder[0] = callback
                    holder.addCallback(callback)
                }
                view
            },
            update = { view ->
                view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                view.setBackgroundColor(backdropColor.toArgb())
                view.setShutterBackgroundColor(backdropColor.toArgb())
                coordinator.attach(view, player)
            }
        )

        IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
        ) {
            Icon(
                imageVector = if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                contentDescription = if (fullscreen) "退出全屏" else "全屏",
                tint = Color.White
            )
        }
    }
}

@Composable
internal fun rememberPlayerVideoAspectRatio(player: Player?, default: Float = 16f / 9f): Float {
    var ratio by remember(player) { mutableFloatStateOf(default) }

    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }

        fun update(videoSize: VideoSize) {
            val w = videoSize.width
            val h = videoSize.height
            val pixelRatio = videoSize.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f
            val computed = if (w > 0 && h > 0) (w.toFloat() * pixelRatio) / h.toFloat() else default
            ratio = computed.coerceIn(0.5f, 3.0f)
        }

        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                update(videoSize)
            }
        }

        update(player.videoSize)
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    return ratio
}

