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

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun PlayerSurfaceHeader(
    title: String,
    isLandscape: Boolean,
    onNavigateUp: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowQueue: () -> Unit,
    navigationEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val headerShadow = if (colorScheme.isDark) {
        Shadow(color = Color.Black.copy(alpha = 0.5f), offset = Offset(0f, 2f), blurRadius = 4f)
    } else {
        Shadow(color = Color.Black.copy(alpha = 0.15f), offset = Offset(0f, 1f), blurRadius = 2f)
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
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(if (isLandscape) 24.dp else 28.dp),
                tint = colorScheme.onSurface
            )
        }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShowSleepTimer) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    modifier = Modifier.size(if (isLandscape) 20.dp else 22.dp),
                    tint = colorScheme.onSurface
                )
            }
            IconButton(onClick = onShowQueue) {
                Icon(
                    Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(if (isLandscape) 22.dp else 24.dp),
                    tint = colorScheme.onSurface
                )
            }
        }
        }
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            thickness = 0.5.dp,
            color = dividerColor
        )
    }
}

@Composable
internal fun NowPlayingLyricsSurface(
    isLandscape: Boolean,
    playbackPositionMs: Long,
    lyrics: List<SubtitleEntry>,
    lyricColors: LyricReadableColors,
    lyricsPageSettings: LyricsPageSettings,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AppleLyricsView(
            lyrics = lyrics,
            currentPosition = playbackPositionMs,
            onSeekTo = onSeekTo,
            colors = lyricColors,
            modifier = Modifier.fillMaxSize(),
            isLandscape = isLandscape,
            settings = lyricsPageSettings
        )
    }
}

@Composable
internal fun ArtworkBox(
    isVideo: Boolean,
    metadata: androidx.media3.common.MediaMetadata?,
    viewModel: PlayerViewModel,
    onOpenLyrics: () -> Unit,
    edgeBlendEnabled: Boolean,
    edgeBlendColor: Color,
    videoBackdropColor: Color,
    artworkAlignment: Alignment = Alignment.Center,
    dragPreviewEnabled: Boolean = false,
    dragPreviewState: CoverDragPreviewState? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    val hasArtwork = metadata?.artworkUri != null
    Box(
        modifier = modifier
            .fillMaxSize()
            .coverDragPreviewGesture(
                enabled = dragPreviewEnabled && dragPreviewState != null,
                state = dragPreviewState ?: CoverDragPreviewState(),
                minPointers = 2
            )
            .clip(shape)
            .background(if (isVideo) videoBackdropColor else Color.Transparent)
            .then(if (hasArtwork && !edgeBlendEnabled) Modifier.shadow(12.dp, shape) else Modifier)
    ) {
        if (isVideo) {
            var fullscreen by rememberSaveable { mutableStateOf(false) }
            val player = viewModel.playerOrNull()
            if (!fullscreen) {
                NowPlayingVideoPlayer(
                    player = player,
                    fullscreen = false,
                    onToggleFullscreen = { fullscreen = true },
                    viewKey = "inline",
                    backdropColor = videoBackdropColor,
                    modifier = Modifier.fillMaxSize().clipToBounds()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(videoBackdropColor))
            }
            if (fullscreen) {
                BackHandler { fullscreen = false }
                Dialog(
                    onDismissRequest = { fullscreen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    NowPlayingVideoPlayer(
                        player = player,
                        fullscreen = true,
                        onToggleFullscreen = { fullscreen = false },
                        viewKey = "fullscreen",
                        backdropColor = videoBackdropColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onOpenLyrics() }
            ) {
                if (edgeBlendEnabled) {
                    val artwork = metadata?.artworkUri
                    if (artwork != null) {
                        CoverArtworkEdgeBlend(
                            artworkModel = artwork,
                            blendColor = edgeBlendColor,
                            modifier = Modifier.fillMaxSize(),
                            artworkAlignment = artworkAlignment
                        )
                    } else {
                        DiscPlaceholder(modifier = Modifier.fillMaxSize(), cornerRadius = 28)
                    }
                } else {
                    AsmrAsyncImage(
                        model = metadata?.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = artworkAlignment,
                        placeholderCornerRadius = 28,
                        retainPainterDuringReload = true,
                        loadWhenSizeStableForMillis = 120L,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun NowPlayingVideoPlayer(
    player: Player?,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    viewKey: String,
    backdropColor: Color,
    modifier: Modifier = Modifier
) {
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    DisposableEffect(viewKey) {
        onDispose {
            playerView?.player = null
            playerView = null
        }
    }

    Box(
        modifier = modifier
            .background(if (fullscreen) backdropColor else Color.Transparent)
    ) {
        key(viewKey) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val pv = LayoutInflater.from(context)
                        .inflate(R.layout.view_now_playing_video_player, null, false) as PlayerView
                    pv.also {
                        pv.useController = false
                        pv.player = player
                        pv.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        pv.setShutterBackgroundColor(backdropColor.toArgb())
                        playerView = pv
                    }
                },
                update = { view ->
                    if (view.player !== player) view.player = player
                }
            )
        }

        IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
        ) {
            Icon(
                imageVector = if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
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

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

