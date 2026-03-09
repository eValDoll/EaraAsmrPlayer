package com.asmr.player.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.playback.PlaybackSnapshot
import com.asmr.player.ui.common.EqualizerPanel
import com.asmr.player.ui.common.rememberComputedDominantColorCenterWeighted
import com.asmr.player.ui.common.rememberComputedVideoFrameDominantColorCenterWeighted
import com.asmr.player.ui.common.DiscPlaceholder
import com.asmr.player.ui.common.smoothScrollToIndex
import com.asmr.player.ui.library.TagAssignDialog
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
@androidx.media3.common.util.UnstableApi
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun NowPlayingScreen(
    windowSizeClass: WindowSizeClass,
    onBack: () -> Unit,
    onOpenLyrics: () -> Unit,
    onShowQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onOpenPlaylistPicker: (mediaId: String, uri: String, title: String, artist: String, artworkUri: String) -> Unit,
    viewModel: PlayerViewModel,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
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
        rememberComputedDominantColorCenterWeighted(model = metadata?.artworkUri, defaultColor = colorScheme.background)
    }
    val targetAccentColor = if (coverBackgroundEnabled) {
        dominantColorResult.color ?: colorScheme.primary
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

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (!isVideo) {
            val backgroundArtwork = remember(metadata?.artworkUri) {
                metadata?.artworkUri?.takeUnless { u ->
                    val s = u.toString()
                    u.scheme.equals("android.resource", ignoreCase = true) ||
                        s.contains("ic_placeholder", ignoreCase = true)
                }
            }
            CoverArtworkBackground(
                artworkModel = backgroundArtwork,
                enabled = coverBackgroundEnabled,
                clarity = coverBackgroundClarity,
                overlayBaseColor = colorScheme.background,
                tintBaseColor = accentColor,
                isDark = colorScheme.isDark
            )
        }

        val layoutState = remember(useSplitLayout, isPhoneLandscape) { useSplitLayout to isPhoneLandscape }

        AnimatedContent(
            targetState = layoutState,
            transitionSpec = {
                val enter = fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 60)) +
                    scaleIn(animationSpec = tween(durationMillis = 220, delayMillis = 60), initialScale = 0.98f)
                val exit = fadeOut(animationSpec = tween(durationMillis = 160)) +
                    scaleOut(animationSpec = tween(durationMillis = 160), targetScale = 1.02f)
                enter togetherWith exit
            },
            label = "nowPlayingLayout"
        ) { (split, phoneLandscape) ->
        if (split) {
            // --- 平板端横屏布局 (左右分栏) ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 顶部工具栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = onBack) {
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
                        Box(modifier = Modifier.widthIn(max = 420.dp).aspectRatio(if (isVideo) videoAspectRatio else 1f)) {
                            ArtworkBox(
                                isVideo = isVideo,
                                metadata = metadata,
                                viewModel = viewModel,
                                onOpenLyrics = onOpenLyrics,
                                edgeBlendEnabled = false,
                                edgeBlendColor = if (coverBackgroundEnabled) accentColor else colorScheme.background,
                                videoBackdropColor = videoBackdropColor
                            )
                        }
                        
                        key(item?.mediaId) {
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

                    // 右侧：信息与控制区
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 艺术家 (标题已移动到 header)
                        Text(
                            text = metadata?.artist?.toString().orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!isVideo) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
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
                                    onOpenLyrics = onOpenLyrics,
                                    activeColor = accentColor,
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
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        PlaybackControls(
                            playback = playback,
                            isFavorite = isFavorite,
                            viewModel = viewModel,
                            onShowPlaylistPicker = {
                                val current = playback.currentMediaItem ?: return@PlaybackControls
                                val md = current.mediaMetadata
                                onOpenPlaylistPicker(
                                    current.mediaId,
                                    current.localConfiguration?.uri?.toString().orEmpty(),
                                    md.title?.toString().orEmpty(),
                                    md.artist?.toString().orEmpty(),
                                    md.artworkUri?.toString().orEmpty()
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
                            primaryColor = accentColor,
                            onPrimaryColor = onAccentColor
                        )
                    }
                }
            }
        } else if (phoneLandscape) {
            // --- 手机端横屏布局 (特殊适配) ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 顶部：返回、标题和队列按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = onBack) {
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
                                .aspectRatio(if (isVideo) videoAspectRatio else 1f),
                            contentAlignment = Alignment.Center
                        ) {
                            ArtworkBox(
                                isVideo = isVideo,
                                metadata = metadata,
                                viewModel = viewModel,
                                onOpenLyrics = onOpenLyrics,
                                edgeBlendEnabled = false,
                                edgeBlendColor = if (coverBackgroundEnabled) accentColor else colorScheme.background,
                                videoBackdropColor = videoBackdropColor
                            )
                        }

                        key(item?.mediaId) {
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

                    // 右侧：歌词 + 控制
                    Column(
                        modifier = Modifier.weight(0.6f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isVideo) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
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
                                    onOpenLyrics = onOpenLyrics,
                                    activeColor = accentColor,
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
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        PlaybackControls(
                            playback = playback,
                            isFavorite = isFavorite,
                            viewModel = viewModel,
                            onShowPlaylistPicker = {
                                val current = playback.currentMediaItem ?: return@PlaybackControls
                                val md = current.mediaMetadata
                                onOpenPlaylistPicker(
                                    current.mediaId,
                                    current.localConfiguration?.uri?.toString().orEmpty(),
                                    md.title?.toString().orEmpty(),
                                    md.artist?.toString().orEmpty(),
                                    md.artworkUri?.toString().orEmpty()
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
                            primaryColor = accentColor,
                            onPrimaryColor = onAccentColor
                        )
                    }
                }
            }
        } else {
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
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
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
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    // 封面
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f) // 让封面占据剩余可用空间，自动收缩
                            .padding(vertical = if (widthClass == WindowWidthSizeClass.Compact) 16.dp else 32.dp),
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
                                onOpenLyrics = onOpenLyrics,
                                edgeBlendEnabled = false,
                                edgeBlendColor = if (coverBackgroundEnabled) accentColor else colorScheme.background,
                                videoBackdropColor = videoBackdropColor
                            )
                        }
                    }

                    if (!isVideo) {
                        SingleLineLyrics(
                            lyrics = lyricsState.lyrics,
                            currentPosition = playback.positionMs,
                            onOpenLyrics = onOpenLyrics,
                            accentColor = accentColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    key(item?.mediaId) {
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

                    PlaybackControls(
                        playback = playback,
                        isFavorite = isFavorite,
                        viewModel = viewModel,
                        onShowPlaylistPicker = {
                            val current = playback.currentMediaItem ?: return@PlaybackControls
                            val md = current.mediaMetadata
                            onOpenPlaylistPicker(
                                current.mediaId,
                                current.localConfiguration?.uri?.toString().orEmpty(),
                                md.title?.toString().orEmpty(),
                                md.artist?.toString().orEmpty(),
                                md.artworkUri?.toString().orEmpty()
                            )
                        },
                        onShowEqualizer = { showEqualizer = true },
                        onManageTags = {
                            val mediaId = item?.mediaId.orEmpty()
                            val fallback = metadata?.title?.toString().orEmpty()
                            tagViewModel.openForMediaId(mediaId, fallback)
                        },
                        sliceUiState = sliceUiState,
                        primaryColor = accentColor,
                        onPrimaryColor = onAccentColor
                    )

                    VolumeControl(modifier = Modifier.fillMaxWidth(), accentColor = accentColor)
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
                onDismissRequest = { showSliceSheet = false },
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
                        selectedSliceId = sliceUiState.selectedSliceId,
                        activeColor = accentColor,
                        inactiveColor = accentColor.copy(alpha = 0.18f),
                        onSeekTo = { viewModel.seekTo(it) },
                        onSelectSlice = { viewModel.selectSlice(it) },
                        onLongPressSlice = { id ->
                            viewModel.selectSlice(id)
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
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = true),
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
                                            .clickable { viewModel.selectSlice(slice.id) }
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
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = 32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkBox(
    isVideo: Boolean,
    metadata: androidx.media3.common.MediaMetadata?,
    viewModel: PlayerViewModel,
    onOpenLyrics: () -> Unit,
    edgeBlendEnabled: Boolean,
    edgeBlendColor: Color,
    videoBackdropColor: Color
) {
    val shape = RoundedCornerShape(28.dp)
    val hasArtwork = metadata?.artworkUri != null
    Box(
        modifier = Modifier
            .fillMaxSize()
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
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        DiscPlaceholder(modifier = Modifier.fillMaxSize(), cornerRadius = 28)
                    }
                } else {
                    AsmrAsyncImage(
                        model = metadata?.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholderCornerRadius = 28,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    playback: PlaybackSnapshot,
    isFavorite: Boolean,
    viewModel: PlayerViewModel,
    onShowPlaylistPicker: () -> Unit,
    onShowEqualizer: () -> Unit,
    onManageTags: () -> Unit,
    sliceUiState: SliceUiState,
    modifier: Modifier = Modifier,
    showActionRow: Boolean = true,
    bottomPadding: Dp = 0.dp,
    primaryColor: Color = AsmrTheme.colorScheme.primary,
    onPrimaryColor: Color = AsmrTheme.colorScheme.onPrimary
) {
    val colorScheme = AsmrTheme.colorScheme
    val currentMediaId = playback.currentMediaItem?.mediaId
    var optimisticIsPlaying by remember { mutableStateOf<Boolean?>(null) }
    var stableMediaId by remember { mutableStateOf<String?>(currentMediaId) }
    val isPlayingEffective = optimisticIsPlaying ?: playback.isPlaying

    LaunchedEffect(currentMediaId) {
        if (currentMediaId != null && currentMediaId != stableMediaId) {
            stableMediaId = currentMediaId
            optimisticIsPlaying = null
        } else if (stableMediaId == null && currentMediaId != null) {
            stableMediaId = currentMediaId
        }
    }

    LaunchedEffect(optimisticIsPlaying) {
        if (optimisticIsPlaying != null) {
            delay(1_500)
            optimisticIsPlaying = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(if (showActionRow) 20.dp else 12.dp)
    ) {
        if (showActionRow) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleFavorite() }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "喜欢",
                        tint = if (isFavorite) Color.Red else colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onShowPlaylistPicker) {
                    Icon(
                        Icons.Outlined.PlaylistAdd,
                        contentDescription = "添加到播放列表",
                        tint = colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                val floatingEnabled by viewModel.floatingLyricsEnabled.collectAsState()
                IconButton(onClick = { viewModel.toggleFloatingLyrics() }) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "悬浮歌词",
                        tint = if (floatingEnabled) primaryColor else colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onManageTags) {
                    Icon(
                        imageVector = Icons.Default.Label,
                        contentDescription = "标签管理",
                        tint = colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = onShowEqualizer) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "均衡器",
                        tint = colorScheme.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                val sliceEnabled = sliceUiState.sliceModeEnabled
                val targetBg = if (sliceEnabled) primaryColor.copy(alpha = 0.18f) else Color.Transparent
                val bg by animateColorAsState(targetValue = targetBg, animationSpec = tween(240, easing = FastOutSlowInEasing), label = "sliceModeBg")
                val baseTint = colorScheme.onSurface.copy(alpha = 0.8f)
                val tint by animateColorAsState(
                    targetValue = if (sliceEnabled) primaryColor else baseTint,
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    label = "sliceModeTint"
                )
                Surface(
                    color = bg,
                    contentColor = tint,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    IconButton(onClick = { viewModel.toggleSliceMode() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_segment),
                            contentDescription = "切片播放",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 第二行：核心控制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (showActionRow) {
                Arrangement.SpaceBetween
            } else {
                Arrangement.spacedBy(25.dp, Alignment.CenterHorizontally)
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.cyclePlayMode() }) {
                val icon = when {
                    playback.shuffleEnabled -> Icons.Default.Shuffle
                    playback.repeatMode == Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                }
                Icon(
                    icon,
                    contentDescription = "播放模式",
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }

            IconButton(onClick = { viewModel.previous() }) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            val playButtonCorner by animateDpAsState(
                targetValue = if (isPlayingEffective) 24.dp else 36.dp,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "playButtonCorner"
            )
            val playButtonInteractionSource = remember { MutableInteractionSource() }
            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(playButtonCorner),
                color = primaryColor,
                contentColor = onPrimaryColor
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = playButtonInteractionSource,
                            indication = null
                        ) {
                            optimisticIsPlaying = !(optimisticIsPlaying ?: playback.isPlaying)
                            viewModel.togglePlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isPlayingEffective,
                        transitionSpec = {
                            (fadeIn(tween(durationMillis = 120)) + scaleIn(tween(durationMillis = 120), initialScale = 0.9f)) togetherWith
                                (fadeOut(tween(durationMillis = 90)) + scaleOut(tween(durationMillis = 90), targetScale = 1.05f))
                        },
                        label = "play_pause_icon"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "播放/暂停",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            IconButton(onClick = { viewModel.next() }) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = { viewModel.seekForward10s() }) {
                Icon(
                    Icons.Default.FastForward,
                    contentDescription = "快进10秒",
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
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
private fun rememberPlayerVideoAspectRatio(player: Player?, default: Float = 16f / 9f): Float {
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

@Composable
private fun SingleLineLyrics(
    lyrics: List<SubtitleEntry>,
    currentPosition: Long,
    onOpenLyrics: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val sortedLyrics = remember(lyrics) {
        var last = Long.MIN_VALUE
        var sorted = true
        for (i in lyrics.indices) {
            val v = lyrics[i].startMs
            if (v < last) {
                sorted = false
                break
            }
            last = v
        }
        if (sorted) lyrics else lyrics.sortedBy { it.startMs }
    }
    val indexFinder = remember(sortedLyrics) { SubtitleIndexFinder(sortedLyrics) }
    val activeIndex = remember(currentPosition, indexFinder, sortedLyrics) {
        if (sortedLyrics.isEmpty()) return@remember -1
        val idx = indexFinder.findActiveIndex(currentPosition)
        if (idx >= 0) idx else 0
    }
    val currentLine = remember(sortedLyrics, activeIndex) {
        sortedLyrics.getOrNull(activeIndex)
    }
    val currentText = remember(currentLine) {
        val raw = currentLine?.text
        if (raw == null) {
            "暂无歌词"
        } else {
            normalizeSingleLineText(raw).ifBlank { " " }
        }
    }
    val lineDuration = remember(currentLine) {
        if (currentLine != null) (currentLine.endMs - currentLine.startMs).coerceAtLeast(0L) else 0L
    }

    Column(
        modifier = modifier
            .clickable { onOpenLyrics() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedLyricLine(
            text = currentText,
            durationMs = lineDuration,
            color = accentColor,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AnimatedLyricLine(
    text: String,
    durationMs: Long,
    color: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = slideInVertically(animationSpec = tween(600)) { it } + fadeIn(animationSpec = tween(600)),
                initialContentExit = slideOutVertically(animationSpec = tween(600)) { -it } + fadeOut(animationSpec = tween(600)),
                sizeTransform = SizeTransform(clip = false)
            )
        },
        label = "lyricLine"
    ) { target ->
        SlowMarqueeText(
            text = target,
            durationMs = durationMs,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = fontWeight,
            modifier = modifier
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SlowMarqueeText(
    text: String,
    durationMs: Long,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val singleLine = remember(text) { normalizeSingleLineText(text) }
    val content = singleLine.ifBlank { " " }
    val shadow = if (AsmrTheme.colorScheme.isDark) {
        Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 4f)
    } else {
        Shadow(color = Color.Black.copy(alpha = 0.15f), offset = Offset(0f, 1f), blurRadius = 2f)
    }
    
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val textLayoutResult = remember(content, style, fontWeight) {
        textMeasurer.measure(
            text = androidx.compose.ui.text.AnnotatedString(content),
            style = style.copy(fontWeight = fontWeight)
        )
    }
    val textWidth = remember(textLayoutResult) { textLayoutResult.size.width }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = constraints.maxWidth
        val velocity = remember(textWidth, containerWidth, durationMs) {
            if (textWidth <= containerWidth) {
                30.dp // Default slow velocity if no scroll needed
            } else {
                val distancePx = (textWidth - containerWidth).toFloat()
                // Target time: Duration - 1s (buffer)
                val targetTimeSeconds = (durationMs - 1000).coerceAtLeast(2000) / 1000f
                // Velocity = Distance / Time. basicMarquee expects Dp (implicitly Dp/s).
                // We need to convert px/s to dp/s.
                // 1 dp = density * px. 1 px = 1/density dp.
                // Wait, velocity in basicMarquee is Dp. It means Dp per second.
                // So: velocityDp = (distancePx / density) / timeSeconds
                // Actually we can't easily access density here inside remember block without LocalDensity
                // But we can do it outside.
                null // Return null to signal calculation needed
            }
        }
        
        val density = LocalDensity.current
        val finalVelocity = remember(velocity, density, textWidth, containerWidth, durationMs) {
             velocity ?: run {
                 val distancePx = (textWidth - containerWidth).toFloat()
                 val targetTimeSeconds = (durationMs - 1000).coerceAtLeast(2000) / 1000f
                 val distanceDp = with(density) { distancePx.toDp() }
                 // BasicMarquee velocity is in Dp/s.
                 // We want to cover 'distanceDp' in 'targetTimeSeconds'.
                 // BUT basicMarquee scrolls the WHOLE content width + gap?
                 // No, basicMarquee scrolls until the end is visible, then repeats.
                 // The distance it travels is (ContentWidth - ContainerWidth) per cycle?
                 // Actually basicMarquee behavior:
                 // It scrolls the content.
                 // If velocity is 50.dp, it moves 50dp per second.
                 // We want to finish the scroll in `targetTimeSeconds`.
                 // So velocity = distanceDp / targetTimeSeconds.
                 // We add a small buffer to velocity to ensure it finishes.
                 (distanceDp / targetTimeSeconds)
             }
        }

        Text(
            text = content,
            style = style.copy(
                fontWeight = fontWeight,
                shadow = shadow
            ),
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                velocity = finalVelocity.coerceAtLeast(10.dp) // Minimum speed
            )
        )
    }
}

private fun normalizeSingleLineText(text: String): String {
    return text
        .replace('\uFEFF', ' ')
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace('\t', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun VolumeControl(modifier: Modifier = Modifier, accentColor: Color) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVolume)) }
    var lastNonZeroVolume by remember { mutableIntStateOf(volume.coerceAtLeast(1)) }
    var lastInteractionAt by remember { mutableLongStateOf(0L) }

    fun refreshVolumeFromSystem() {
        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVolume)
        if (volume > 0) lastNonZeroVolume = volume
    }

    fun setVolume(newVolume: Int) {
        val v = newVolume.coerceIn(0, maxVolume)
        volume = v
        if (v > 0) lastNonZeroVolume = v
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
    }

    LaunchedEffect(expanded, lastInteractionAt) {
        if (!expanded) return@LaunchedEffect
        val snapshot = lastInteractionAt
        delay(3_000)
        if (expanded && lastInteractionAt == snapshot) {
            expanded = false
        }
    }

    val colorScheme = AsmrTheme.colorScheme
    val isMuted = volume == 0
    val icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp

    AnimatedContent(
        targetState = expanded,
        transitionSpec = {
            fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(120))
        },
        label = "volume_control"
    ) { isExpanded ->
        if (!isExpanded) {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .combinedClickable(
                        onClick = {
                            refreshVolumeFromSystem()
                            if (volume > 0) {
                                setVolume(0)
                            } else {
                                setVolume(lastNonZeroVolume.coerceIn(1, maxVolume))
                            }
                        },
                        onLongClick = {
                            refreshVolumeFromSystem()
                            expanded = true
                            lastInteractionAt = SystemClock.elapsedRealtime()
                        }
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "长按调整音量",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.textTertiary
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                    tint = accentColor,
                        modifier = Modifier
                            .size(20.dp)
                            .combinedClickable(
                                onClick = {
                                    refreshVolumeFromSystem()
                                    if (volume > 0) {
                                        setVolume(0)
                                    } else {
                                        setVolume(lastNonZeroVolume.coerceIn(1, maxVolume))
                                    }
                                    lastInteractionAt = SystemClock.elapsedRealtime()
                                },
                                onLongClick = {}
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "音量",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.textTertiary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${(volume.toFloat() / maxVolume.toFloat() * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.textTertiary
                    )
                }

                Slider(
                    value = volume.toFloat(),
                    onValueChange = { v ->
                        val newVol = v.roundToInt().coerceIn(0, maxVolume)
                        if (newVol != volume) {
                            setVolume(newVol)
                        }
                        lastInteractionAt = SystemClock.elapsedRealtime()
                    },
                    valueRange = 0f..maxVolume.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = accentColor.copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}

// AdaptiveLyricsView has been replaced by AppleLyricsView

@Composable
private fun PlayerProgress(
    positionMs: Long,
    durationMs: Long,
    sliceUiState: SliceUiState,
    onSeekTo: (Long) -> Unit,
    onCutPressed: () -> Unit,
    onScrubbingChanged: (Boolean) -> Unit,
    onSelectSlice: (Long?) -> Unit,
    onLongPressSlice: (Long) -> Unit,
    onUpdateSliceRange: (sliceId: Long, startMs: Long, endMs: Long) -> Unit,
    activeColor: Color,
    inactiveColor: Color
) {
    val colorScheme = AsmrTheme.colorScheme
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = positionMs.coerceIn(0L, safeDuration.takeIf { it > 0 } ?: Long.MAX_VALUE)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var pendingSeekMs by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(safePosition, pendingSeekMs) {
        if (pendingSeekMs >= 0L && abs(safePosition - pendingSeekMs) <= 500L) {
            pendingSeekMs = -1L
        }
    }

    val effectivePosition = if (!isDragging && pendingSeekMs >= 0L && safeDuration > 0L) {
        pendingSeekMs.coerceIn(0L, safeDuration)
    } else {
        safePosition
    }
    val safeFraction = remember(effectivePosition, safeDuration) {
        if (safeDuration > 0L) (effectivePosition.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    }
    val rangeDuration = safeDuration
    val sliderValue = if (isDragging) dragFraction else safeFraction
    val displayPosition = if (isDragging && rangeDuration > 0L) {
        (sliderValue.toDouble() * rangeDuration.toDouble()).roundToLong().coerceIn(0L, rangeDuration)
    } else {
        effectivePosition
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SliceScrubbableSeekBar(
                enabled = rangeDuration > 0L,
                fraction = sliderValue.coerceIn(0f, 1f),
                positionMs = effectivePosition,
                rangeDurationMs = rangeDuration,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                slices = sliceUiState.slices,
                tempStartMs = sliceUiState.tempStartMs,
                selectedSliceId = sliceUiState.selectedSliceId,
                onSelectSlice = onSelectSlice,
                onLongPressSlice = onLongPressSlice,
                onEditCommit = onUpdateSliceRange,
                onGestureActiveChanged = onScrubbingChanged,
                modifier = Modifier.weight(1f),
                onScrubStart = { f ->
                    onScrubbingChanged(true)
                    isDragging = true
                    dragFraction = f
                },
                onScrub = { f ->
                    isDragging = true
                    dragFraction = f
                },
                onScrubStop = { f ->
                    if (rangeDuration > 0L) {
                        val seekMs = (f.toDouble() * rangeDuration.toDouble()).roundToLong().coerceIn(0L, rangeDuration)
                        pendingSeekMs = seekMs
                        onSeekTo(seekMs)
                    }
                    isDragging = false
                    onScrubbingChanged(false)
                }
            )
            IconButton(onClick = onCutPressed, enabled = rangeDuration > 0L) {
                Icon(
                    imageVector = Icons.Outlined.ContentCut,
                    contentDescription = "Cut",
                    tint = if (sliceUiState.tempStartMs != null) activeColor else colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Formatting.formatTrackTime(displayPosition),
                modifier = Modifier.widthIn(min = 45.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textTertiary,
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                Formatting.formatTrackTime(rangeDuration),
                modifier = Modifier.widthIn(min = 45.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textTertiary,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun SliceScrubbableSeekBar(
    enabled: Boolean,
    fraction: Float,
    positionMs: Long,
    rangeDurationMs: Long,
    activeColor: Color,
    inactiveColor: Color,
    slices: List<com.asmr.player.domain.model.Slice>,
    tempStartMs: Long?,
    selectedSliceId: Long?,
    onSelectSlice: (Long?) -> Unit,
    onLongPressSlice: (Long) -> Unit,
    onEditCommit: (sliceId: Long, startMs: Long, endMs: Long) -> Unit,
    onGestureActiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onScrubStart: (Float) -> Unit,
    onScrub: (Float) -> Unit,
    onScrubStop: (Float) -> Unit
) {
    val f = fraction.coerceIn(0f, 1f)
    var lastFraction by remember(f) { mutableFloatStateOf(f) }

    val thumbRadius = 8.dp
    val trackHeight = 4.dp
    val density = LocalDensity.current
    val tooltipTextSizePx = remember(density) { with(density) { 12.sp.toPx() } }
    val tooltipPadX = remember(density) { with(density) { 8.dp.toPx() } }
    val tooltipPadY = remember(density) { with(density) { 6.dp.toPx() } }
    val tooltipRadius = remember(density) { with(density) { 10.dp.toPx() } }
    val tooltipTextPaint = remember(tooltipTextSizePx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tooltipTextSizePx
        }
    }

    // 荧光效果 Paint
    val blurRadius = remember(density) { with(density) { 6.dp.toPx() } }
    val glowPaint = remember(blurRadius) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
            maskFilter = android.graphics.BlurMaskFilter(blurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
            // 使用 SCREEN 混合模式，使光晕叠加后变亮，产生发光感
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
        }
    }
    val corePaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
        }
    }
    val androidPath = remember { android.graphics.Path() }

    val selectedSlice = remember(selectedSliceId, slices) {
        val id = selectedSliceId ?: return@remember null
        slices.firstOrNull { it.id == id }
    }
    var editStartMs by remember(selectedSlice?.id, selectedSlice?.startMs) {
        mutableLongStateOf(selectedSlice?.startMs ?: 0L)
    }
    var editEndMs by remember(selectedSlice?.id, selectedSlice?.endMs) {
        mutableLongStateOf(selectedSlice?.endMs ?: 0L)
    }
    var tooltipMs by remember { mutableLongStateOf(-1L) }
    var tooltipX by remember { mutableFloatStateOf(0f) }
    var tooltipY by remember { mutableFloatStateOf(0f) }

    val inSlice = remember(positionMs, slices) { slices.any { positionMs >= it.startMs && positionMs < it.endMs } }
    val infinite = rememberInfiniteTransition(label = "slice_breath")
    val breathAlpha by infinite.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "slice_breath_alpha"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled, rangeDurationMs, slices, selectedSliceId) {
                if (!enabled) return@pointerInput
                val thumbRadiusPx = thumbRadius.toPx()
                detectTapGestures(
                    onTap = { offset ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        val nf = if (endX > startX) {
                            ((offset.x - startX) / (endX - startX)).coerceIn(0f, 1f)
                        } else 0f
                        onScrubStart(nf)
                        onScrubStop(nf)
                    },
                    onLongPress = { offset ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        val nf = if (endX > startX) {
                            ((offset.x - startX) / (endX - startX)).coerceIn(0f, 1f)
                        } else 0f
                        if (rangeDurationMs <= 0L) return@detectTapGestures
                        val ms = (nf.toDouble() * rangeDurationMs.toDouble()).roundToLong().coerceIn(0L, rangeDurationMs)
                        val hit = slices
                            .asSequence()
                            .filter { s -> ms >= s.startMs && ms <= s.endMs }
                            .maxByOrNull { it.id }
                        if (hit != null) {
                            onSelectSlice(hit.id)
                            onLongPressSlice(hit.id)
                        }
                    }
                )
            }
            .pointerInput(enabled, rangeDurationMs, slices, selectedSliceId) {
                if (!enabled) return@pointerInput
                val thumbRadiusPx = thumbRadius.toPx()
                val hitRadiusPx = 16.dp.toPx()
                var mode = 0
                detectDragGestures(
                    onDragStart = { offset ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        fun toFraction(x: Float): Float {
                            return if (endX > startX) ((x - startX) / (endX - startX)).coerceIn(0f, 1f) else 0f
                        }
                        val nf = toFraction(offset.x)
                        val sel = selectedSlice
                        mode = 0
                        if (sel != null && rangeDurationMs > 0L) {
                            val selStartX = startX + (endX - startX) * (sel.startMs.toFloat() / rangeDurationMs.toFloat()).coerceIn(0f, 1f)
                            val selEndX = startX + (endX - startX) * (sel.endMs.toFloat() / rangeDurationMs.toFloat()).coerceIn(0f, 1f)
                            mode = when {
                                kotlin.math.abs(offset.x - selStartX) <= hitRadiusPx -> 1
                                kotlin.math.abs(offset.x - selEndX) <= hitRadiusPx -> 2
                                else -> 0
                            }
                        }
                        onGestureActiveChanged(true)
                        if (mode == 0) {
                            lastFraction = nf
                            onScrubStart(nf)
                        } else {
                            tooltipMs = if (mode == 1) editStartMs else editEndMs
                            tooltipX = offset.x
                            tooltipY = offset.y
                        }
                    },
                    onDrag = { change, _ ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        val nf = if (endX > startX) {
                            ((change.position.x - startX) / (endX - startX)).coerceIn(0f, 1f)
                        } else 0f
                        lastFraction = nf
                        if (mode == 0) {
                            onScrub(nf)
                        } else if (rangeDurationMs > 0L) {
                            val ms = (nf.toDouble() * rangeDurationMs.toDouble()).roundToLong().coerceIn(0L, rangeDurationMs)
                            if (mode == 1) {
                                editStartMs = ms
                            } else {
                                editEndMs = ms
                            }
                            tooltipMs = ms
                            tooltipX = change.position.x
                            tooltipY = change.position.y
                        }
                    },
                    onDragCancel = {
                        tooltipMs = -1L
                        mode = 0
                        onGestureActiveChanged(false)
                        onScrubStop(lastFraction)
                    },
                    onDragEnd = {
                        val sel = selectedSlice
                        if (mode == 0) {
                            onScrubStop(lastFraction)
                        } else if (sel != null) {
                            val start = editStartMs
                            val end = editEndMs
                            if (end > start) {
                                onEditCommit(sel.id, start, end)
                            } else {
                                editStartMs = sel.startMs
                                editEndMs = sel.endMs
                            }
                        }
                        tooltipMs = -1L
                        mode = 0
                        onGestureActiveChanged(false)
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val trackHeightPx = trackHeight.toPx()
        val thumbRadiusPx = thumbRadius.toPx()
        val centerY = height / 2f
        val startX = thumbRadiusPx
        val endX = (width - thumbRadiusPx).coerceAtLeast(startX)
        val x = if (endX > startX) {
            startX + (endX - startX) * f
        } else {
            startX
        }

        val sliceAlpha = if (inSlice) breathAlpha else 0.20f
        // 切片高度调整：原为 trackHeightPx * 3.2f，现减小以收窄
        val sliceHeightPx = (trackHeightPx * 1.8f).coerceAtLeast(trackHeightPx + 1f)
        val sliceTop = centerY - sliceHeightPx / 2f
        val sliceCorner = sliceHeightPx / 2f

        if (rangeDurationMs > 0L) {
            val span = (endX - startX).coerceAtLeast(1f)
            for (s in slices) {
                val sf = (s.startMs.toFloat() / rangeDurationMs.toFloat()).coerceIn(0f, 1f)
                val ef = (s.endMs.toFloat() / rangeDurationMs.toFloat()).coerceIn(0f, 1f)
                val sx = startX + span * sf
                val ex = startX + span * ef
                val w = (ex - sx).coerceAtLeast(1f)
                val isSelected = s.id == selectedSliceId
                val a = if (isSelected) (sliceAlpha + 0.15f).coerceAtMost(0.50f) else sliceAlpha

                // 1. 绘制切片区域的光晕背景 (荧光)
                drawIntoCanvas { canvas ->
                    glowPaint.color = activeColor.toArgb()
                    // 提高 Alpha 并利用 SCREEN 模式叠加
                    glowPaint.alpha = 200
                    val rect = android.graphics.RectF(sx, sliceTop, sx + w, sliceTop + sliceHeightPx)
                    canvas.nativeCanvas.drawRoundRect(rect, sliceCorner, sliceCorner, glowPaint)
                    // 叠加第二层增强亮度
                    glowPaint.alpha = 100
                    canvas.nativeCanvas.drawRoundRect(rect, sliceCorner, sliceCorner, glowPaint)
                }

                // 2. 绘制切片实体
                val brush = Brush.horizontalGradient(
                    colors = listOf(
                        activeColor.copy(alpha = (a * 0.9f).coerceIn(0f, 1f)),
                        activeColor.copy(alpha = (a * 1.2f).coerceAtMost(1f))
                    ),
                    startX = sx,
                    endX = ex
                )
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(sx, sliceTop),
                    size = Size(w, sliceHeightPx),
                    cornerRadius = CornerRadius(sliceCorner, sliceCorner)
                )

                // 三角形参数 (缩小尺寸)
                val tipY = (centerY - trackHeightPx * 0.55f).coerceAtLeast(14.dp.toPx())
                val markerW = (trackHeightPx * 2.8f).coerceAtLeast(8f)
                val markerH = (trackHeightPx * 2.4f).coerceAtLeast(7f)
                val baseY = tipY - markerH
                
                // 绘制起止三角形
                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    // 核心颜色改回 activeColor
                    val colorInt = activeColor.toArgb()
                    
                    val pulse = ((breathAlpha - 0.18f) / 0.10f).coerceIn(0f, 1f)
                    val glowAlpha = (180 + 75 * pulse).toInt().coerceIn(0, 255)
                    
                    glowPaint.color = colorInt
                    glowPaint.alpha = glowAlpha
                    corePaint.color = colorInt
                    corePaint.alpha = 255

                    // Start Triangle
                    androidPath.reset()
                    androidPath.moveTo(sx, tipY)
                    androidPath.lineTo(sx - markerW / 2f, baseY)
                    androidPath.lineTo(sx + markerW / 2f, baseY)
                    androidPath.close()
                    // 绘制多层光晕
                    nativeCanvas.drawPath(androidPath, glowPaint)
                    nativeCanvas.drawPath(androidPath, glowPaint) 
                    // 绘制核心
                    nativeCanvas.drawPath(androidPath, corePaint)

                    // End Triangle
                    androidPath.reset()
                    androidPath.moveTo(ex, tipY)
                    androidPath.lineTo(ex - markerW / 2f, baseY)
                    androidPath.lineTo(ex + markerW / 2f, baseY)
                    androidPath.close()
                    // 绘制多层光晕
                    nativeCanvas.drawPath(androidPath, glowPaint)
                    nativeCanvas.drawPath(androidPath, glowPaint)
                    // 绘制核心
                    nativeCanvas.drawPath(androidPath, corePaint)
                }
            }

            val tmp = tempStartMs
            if (tmp != null) {
                val tf = (tmp.toFloat() / rangeDurationMs.toFloat()).coerceIn(0f, 1f)
                val tx = startX + span * tf
                
                val tipY = (centerY - trackHeightPx * 0.55f).coerceAtLeast(14.dp.toPx())
                val markerW = (trackHeightPx * 2.8f).coerceAtLeast(8f)
                val markerH = (trackHeightPx * 2.4f).coerceAtLeast(7f)
                val baseY = tipY - markerH

                drawIntoCanvas { canvas ->
                    val nativeCanvas = canvas.nativeCanvas
                    
                    val colorInt = activeColor.toArgb()
                    glowPaint.color = colorInt
                    glowPaint.alpha = 255 
                    corePaint.color = colorInt
                    corePaint.alpha = 255
                    
                    androidPath.reset()
                    androidPath.moveTo(tx, tipY)
                    androidPath.lineTo(tx - markerW / 2f, baseY)
                    androidPath.lineTo(tx + markerW / 2f, baseY)
                    androidPath.close()
                    
                    nativeCanvas.drawPath(androidPath, glowPaint)
                    nativeCanvas.drawPath(androidPath, glowPaint)
                    nativeCanvas.drawPath(androidPath, corePaint)
                }
            }
        }

        drawLine(
            color = inactiveColor,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = trackHeightPx,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = if (inSlice) activeColor else activeColor.copy(alpha = 0.85f),
            start = Offset(startX, centerY),
            end = Offset(x, centerY),
            strokeWidth = trackHeightPx,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawCircle(
            color = activeColor,
            radius = thumbRadiusPx,
            center = Offset(x, centerY)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = (thumbRadiusPx * 0.45f).coerceAtLeast(1f),
            center = Offset(x, centerY)
        )

        if (tooltipMs >= 0L) {
            val t = Formatting.formatTrackTime(tooltipMs)
            val textWidth = tooltipTextPaint.measureText(t).coerceAtLeast(1f)
            val boxW = textWidth + tooltipPadX * 2f
            val boxH = tooltipTextSizePx + tooltipPadY * 2f
            val bx = (tooltipX - boxW / 2f).coerceIn(0f, width - boxW)
            val by = (centerY - sliceHeightPx / 2f - boxH - 6.dp.toPx()).coerceAtLeast(0f)
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(bx, by),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(tooltipRadius, tooltipRadius)
            )
            drawIntoCanvas { canvas ->
                tooltipTextPaint.color = android.graphics.Color.WHITE
                canvas.nativeCanvas.drawText(
                    t,
                    bx + tooltipPadX,
                    by + tooltipPadY + tooltipTextSizePx * 0.82f,
                    tooltipTextPaint
                )
            }
        }
    }
}

@Composable
private fun SliceOverviewBar(
    positionMs: Long,
    durationMs: Long,
    slices: List<com.asmr.player.domain.model.Slice>,
    selectedSliceId: Long?,
    activeColor: Color,
    inactiveColor: Color,
    onSeekTo: (Long) -> Unit,
    onSelectSlice: (Long?) -> Unit,
    onLongPressSlice: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = positionMs.coerceIn(0L, safeDuration.takeIf { it > 0 } ?: Long.MAX_VALUE)
    val fraction = remember(safePosition, safeDuration) {
        if (safeDuration > 0L) (safePosition.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
    }
    val thumbRadius = 7.dp
    val trackHeight = 3.dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(safeDuration, slices) {
                if (safeDuration <= 0L) return@pointerInput
                val thumbRadiusPx = thumbRadius.toPx()
                detectTapGestures(
                    onTap = { offset ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        val nf = if (endX > startX) ((offset.x - startX) / (endX - startX)).coerceIn(0f, 1f) else 0f
                        val ms = (nf.toDouble() * safeDuration.toDouble()).roundToLong().coerceIn(0L, safeDuration)
                        onSeekTo(ms)
                    },
                    onLongPress = { offset ->
                        val w = size.width.toFloat()
                        val startX = thumbRadiusPx
                        val endX = (w - thumbRadiusPx).coerceAtLeast(startX)
                        val nf = if (endX > startX) ((offset.x - startX) / (endX - startX)).coerceIn(0f, 1f) else 0f
                        val ms = (nf.toDouble() * safeDuration.toDouble()).roundToLong().coerceIn(0L, safeDuration)
                        val hit = slices
                            .asSequence()
                            .filter { s -> ms >= s.startMs && ms <= s.endMs }
                            .maxByOrNull { it.id }
                        if (hit != null) {
                            onSelectSlice(hit.id)
                            onLongPressSlice(hit.id)
                        }
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        val trackHeightPx = trackHeight.toPx()
        val thumbRadiusPx = thumbRadius.toPx()
        val centerY = height / 2f
        val startX = thumbRadiusPx
        val endX = (width - thumbRadiusPx).coerceAtLeast(startX)
        val span = (endX - startX).coerceAtLeast(1f)
        val x = startX + span * fraction

        if (safeDuration > 0L) {
            val sliceHeightPx = (trackHeightPx * 3.0f).coerceAtLeast(trackHeightPx + 2f)
            val top = centerY - sliceHeightPx / 2f
            val corner = sliceHeightPx / 2f
            for (s in slices) {
                val sf = (s.startMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                val ef = (s.endMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                val sx = startX + span * sf
                val ex = startX + span * ef
                val w = (ex - sx).coerceAtLeast(1f)
                val selected = s.id == selectedSliceId
                val a = if (selected) 0.32f else 0.20f
                val brush = Brush.horizontalGradient(
                    colors = listOf(
                        activeColor.copy(alpha = a * 0.65f),
                        activeColor.copy(alpha = a)
                    ),
                    startX = sx,
                    endX = ex
                )
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(sx, top),
                    size = Size(w, sliceHeightPx),
                    cornerRadius = CornerRadius(corner, corner)
                )
            }
        }

        drawLine(
            color = inactiveColor,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = trackHeightPx,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = activeColor,
            start = Offset(startX, centerY),
            end = Offset(x, centerY),
            strokeWidth = trackHeightPx,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawCircle(
            color = activeColor,
            radius = thumbRadiusPx,
            center = Offset(x, centerY)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = (thumbRadiusPx * 0.45f).coerceAtLeast(1f),
            center = Offset(x, centerY)
        )
    }
}

@Composable
private fun SliceTimeEditDialog(
    title: String,
    durationMs: Long,
    currentMs: Long,
    initialMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val boundedInitial = if (safeDuration > 0L) initialMs.coerceIn(0L, safeDuration) else initialMs.coerceAtLeast(0L)
    var selectedMs by remember(boundedInitial) { mutableLongStateOf(boundedInitial) }
    var fraction by remember(boundedInitial, safeDuration) {
        mutableFloatStateOf(if (safeDuration > 0L) (boundedInitial.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f) else 0f)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = Formatting.formatTrackTime(selectedMs),
                    style = MaterialTheme.typography.titleLarge,
                    color = AsmrTheme.colorScheme.onSurface
                )

                if (safeDuration > 0L) {
                    Slider(
                        value = fraction,
                        onValueChange = { f ->
                            fraction = f.coerceIn(0f, 1f)
                            selectedMs = (fraction.toDouble() * safeDuration.toDouble()).roundToLong().coerceIn(0L, safeDuration)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = AsmrTheme.colorScheme.primary,
                            activeTrackColor = AsmrTheme.colorScheme.primary,
                            inactiveTrackColor = AsmrTheme.colorScheme.primary.copy(alpha = 0.18f)
                        )
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = AsmrTheme.colorScheme.primary,
                        trackColor = AsmrTheme.colorScheme.primary.copy(alpha = 0.18f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val v = (selectedMs - 5_000L).coerceAtLeast(0L)
                            selectedMs = if (safeDuration > 0L) v.coerceAtMost(safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("-5s") }
                    OutlinedButton(
                        onClick = {
                            val v = (selectedMs - 1_000L).coerceAtLeast(0L)
                            selectedMs = if (safeDuration > 0L) v.coerceAtMost(safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("-1s") }
                    OutlinedButton(
                        onClick = {
                            val v = (selectedMs - 200L).coerceAtLeast(0L)
                            selectedMs = if (safeDuration > 0L) v.coerceAtMost(safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("-0.2s") }
                    OutlinedButton(
                        onClick = {
                            val v = selectedMs + 200L
                            selectedMs = if (safeDuration > 0L) v.coerceIn(0L, safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("+0.2s") }
                    OutlinedButton(
                        onClick = {
                            val v = selectedMs + 1_000L
                            selectedMs = if (safeDuration > 0L) v.coerceIn(0L, safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("+1s") }
                    OutlinedButton(
                        onClick = {
                            val v = selectedMs + 5_000L
                            selectedMs = if (safeDuration > 0L) v.coerceIn(0L, safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("+5s") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            val v = currentMs.coerceAtLeast(0L)
                            selectedMs = if (safeDuration > 0L) v.coerceAtMost(safeDuration) else v
                            if (safeDuration > 0L) fraction = (selectedMs.toDouble() / safeDuration.toDouble()).toFloat().coerceIn(0f, 1f)
                        }
                    ) { Text("设为当前") }
                    TextButton(
                        onClick = {
                            selectedMs = 0L
                            fraction = 0f
                        }
                    ) { Text("设为 0") }
                    if (safeDuration > 0L) {
                        TextButton(
                            onClick = {
                                selectedMs = safeDuration
                                fraction = 1f
                            }
                        ) { Text("设为末尾") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedMs)
                }
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
