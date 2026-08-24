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
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.outlined.LabelOff
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PlaybackControls(
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
    actionRowModifier: Modifier = Modifier,
    coreControlsModifier: Modifier = Modifier,
    landscapeControls: Boolean = false,
    primaryColor: Color = AsmrTheme.colorScheme.primary,
    onPrimaryColor: Color = AsmrTheme.colorScheme.onPrimary,
    compactLayout: Boolean = false
) {
    val actionButtonSize = if (compactLayout) 40.dp else 48.dp
    val actionIconSize = if (compactLayout) 22.dp else 24.dp
    val coreButtonSize = when {
        landscapeControls && compactLayout -> 40.dp
        landscapeControls -> 52.dp
        compactLayout -> 40.dp
        else -> 48.dp
    }
    val modeIconSize = when {
        landscapeControls && compactLayout -> 28.dp
        landscapeControls -> 30.dp
        compactLayout -> 24.dp
        else -> 28.dp
    }
    val skipIconSize = when {
        landscapeControls && compactLayout -> 36.dp
        landscapeControls -> 38.dp
        compactLayout -> 30.dp
        else -> 36.dp
    }
    val playButtonSize = when {
        landscapeControls && compactLayout -> 68.dp
        landscapeControls -> 76.dp
        compactLayout -> 60.dp
        else -> 72.dp
    }
    val playIconSize = when {
        landscapeControls && compactLayout -> 36.dp
        landscapeControls -> 40.dp
        compactLayout -> 30.dp
        else -> 36.dp
    }
    val currentMediaId = playback.currentMediaItem?.mediaId
    var optimisticIsPlaying by remember { mutableStateOf<Boolean?>(null) }
    var stableMediaId by remember { mutableStateOf<String?>(currentMediaId) }
    val isPlayingEffective = optimisticIsPlaying ?: playback.playWhenReady

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

    val controlsContainerModifier = modifier
        .fillMaxWidth()
        .padding(bottom = bottomPadding)
    val landscapeActionSpacing = if (compactLayout) 2.dp else 4.dp
    val landscapeCoreSpacing = if (compactLayout) 2.dp else 10.dp
    val landscapeCoreEndPadding = if (compactLayout) 0.dp else 16.dp

    if (landscapeControls && showActionRow) {
        BoxWithConstraints(modifier = controlsContainerModifier) {
            val adaptiveCoreSpacing = if (compactLayout) {
                phoneLandscapeCoreButtonSpacing(
                    availableWidth = maxWidth,
                    actionButtonSize = actionButtonSize,
                    actionSpacing = landscapeActionSpacing,
                    coreButtonSize = coreButtonSize,
                    playButtonSize = playButtonSize
                )
            } else {
                landscapeCoreSpacing
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.then(actionRowModifier),
                    horizontalArrangement = Arrangement.spacedBy(landscapeActionSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaybackActionButtons(
                        playback = playback,
                        isFavorite = isFavorite,
                        viewModel = viewModel,
                        onShowPlaylistPicker = onShowPlaylistPicker,
                        onShowEqualizer = onShowEqualizer,
                        onManageTags = onManageTags,
                        sliceUiState = sliceUiState,
                        actionButtonSize = actionButtonSize,
                        actionIconSize = actionIconSize,
                        landscapeControls = true,
                        primaryColor = primaryColor
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .then(coreControlsModifier)
                        .padding(end = landscapeCoreEndPadding),
                    horizontalArrangement = Arrangement.spacedBy(adaptiveCoreSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaybackCoreButtons(
                        playback = playback,
                        viewModel = viewModel,
                        isPlaying = isPlayingEffective,
                        onTogglePlay = {
                            optimisticIsPlaying = !(optimisticIsPlaying ?: playback.playWhenReady)
                            viewModel.togglePlayPause()
                        },
                        compactLayout = compactLayout,
                        coreButtonSize = coreButtonSize,
                        modeIconSize = modeIconSize,
                        skipIconSize = skipIconSize,
                        playButtonSize = playButtonSize,
                        playIconSize = playIconSize,
                        primaryColor = primaryColor,
                        onPrimaryColor = onPrimaryColor
                    )
                }
            }
        }
    } else {
        Column(
            modifier = controlsContainerModifier,
            verticalArrangement = Arrangement.spacedBy(
                if (showActionRow) {
                    if (compactLayout) 8.dp else 20.dp
                } else {
                    12.dp
                }
            )
        ) {
            if (showActionRow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (compactLayout) 8.dp else 16.dp)
                        .then(actionRowModifier),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaybackActionButtons(
                        playback = playback,
                        isFavorite = isFavorite,
                        viewModel = viewModel,
                        onShowPlaylistPicker = onShowPlaylistPicker,
                        onShowEqualizer = onShowEqualizer,
                        onManageTags = onManageTags,
                        sliceUiState = sliceUiState,
                        actionButtonSize = actionButtonSize,
                        actionIconSize = actionIconSize,
                        landscapeControls = false,
                        primaryColor = primaryColor
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(coreControlsModifier)
                    .padding(end = if (landscapeControls) landscapeCoreEndPadding else 0.dp),
                horizontalArrangement = when {
                    landscapeControls -> Arrangement.spacedBy(
                        space = landscapeCoreSpacing,
                        alignment = Alignment.End
                    )
                    showActionRow -> Arrangement.SpaceBetween
                    else -> Arrangement.spacedBy(25.dp, Alignment.CenterHorizontally)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaybackCoreButtons(
                    playback = playback,
                    viewModel = viewModel,
                    isPlaying = isPlayingEffective,
                    onTogglePlay = {
                        optimisticIsPlaying = !(optimisticIsPlaying ?: playback.playWhenReady)
                        viewModel.togglePlayPause()
                    },
                    compactLayout = compactLayout,
                    coreButtonSize = coreButtonSize,
                    modeIconSize = modeIconSize,
                    skipIconSize = skipIconSize,
                    playButtonSize = playButtonSize,
                    playIconSize = playIconSize,
                    primaryColor = primaryColor,
                    onPrimaryColor = onPrimaryColor
                )
            }
        }
    }
}

internal fun phoneLandscapeCoreButtonSpacing(
    availableWidth: Dp,
    actionButtonSize: Dp,
    actionSpacing: Dp,
    coreButtonSize: Dp,
    playButtonSize: Dp
): Dp {
    if (!availableWidth.value.isFinite()) return 2.dp

    val actionButtonsWidth = actionButtonSize.value * 3f + actionSpacing.value * 2f
    val coreButtonsWidth = coreButtonSize.value * 4f + playButtonSize.value
    val freeWidth = availableWidth.value - actionButtonsWidth - coreButtonsWidth
    return (freeWidth / 4f).coerceIn(2f, 18f).dp
}

@Composable
private fun PlaybackActionButtons(
    playback: PlaybackSnapshot,
    isFavorite: Boolean,
    viewModel: PlayerViewModel,
    onShowPlaylistPicker: () -> Unit,
    onShowEqualizer: () -> Unit,
    onManageTags: () -> Unit,
    sliceUiState: SliceUiState,
    actionButtonSize: Dp,
    actionIconSize: Dp,
    landscapeControls: Boolean,
    primaryColor: Color
) {
    val colorScheme = AsmrTheme.colorScheme
    IconButton(
        onClick = { viewModel.toggleFavorite() },
        modifier = Modifier.size(actionButtonSize)
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isFavorite) "取消收藏" else "收藏",
            tint = if (isFavorite) Color.Red else colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.size(actionIconSize)
        )
    }

    IconButton(
        onClick = onShowPlaylistPicker,
        modifier = Modifier.size(actionButtonSize)
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.PlaylistAdd,
            contentDescription = "添加到播放列表",
            tint = colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.size(actionIconSize)
        )
    }

    if (!landscapeControls) {
        val isOnlineMedia = playback.currentMediaItem.isOnlineMedia()
        IconButton(
            onClick = {
                if (isOnlineMedia) viewModel.showOnlineTagManageUnsupported() else onManageTags()
            },
            modifier = Modifier.size(actionButtonSize)
        ) {
            Icon(
                imageVector = if (isOnlineMedia) Icons.AutoMirrored.Outlined.LabelOff else Icons.AutoMirrored.Rounded.Label,
                contentDescription = "标签管理",
                tint = colorScheme.onSurface.copy(alpha = if (isOnlineMedia) 0.38f else 0.8f),
                modifier = Modifier.size(actionIconSize)
            )
        }
    }

    IconButton(
        onClick = onShowEqualizer,
        modifier = Modifier.size(actionButtonSize)
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = "音效面板",
            tint = colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.size(actionIconSize)
        )
    }

    if (!landscapeControls) {
        val sliceEnabled = sliceUiState.sliceModeEnabled
        val baseTint = colorScheme.onSurface.copy(alpha = 0.8f)
        val tint by animateColorAsState(
            targetValue = if (sliceEnabled) primaryColor else baseTint,
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            label = "sliceModeTint"
        )
        IconButton(
            onClick = { viewModel.toggleSliceMode() },
            modifier = Modifier.size(actionButtonSize)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_segment),
                contentDescription = "切片播放",
                tint = tint,
                modifier = Modifier.size(actionIconSize)
            )
        }
    }
}

@Composable
private fun PlaybackCoreButtons(
    playback: PlaybackSnapshot,
    viewModel: PlayerViewModel,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    compactLayout: Boolean,
    coreButtonSize: Dp,
    modeIconSize: Dp,
    skipIconSize: Dp,
    playButtonSize: Dp,
    playIconSize: Dp,
    primaryColor: Color,
    onPrimaryColor: Color
) {
    val colorScheme = AsmrTheme.colorScheme
    IconButton(
        onClick = { viewModel.cyclePlayMode() },
        modifier = Modifier.size(coreButtonSize)
    ) {
        val icon = when {
            playback.shuffleEnabled -> Icons.Rounded.Shuffle
            playback.repeatMode == Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
            else -> Icons.Rounded.Repeat
        }
        Icon(
            icon,
            contentDescription = "播放模式",
            tint = colorScheme.onSurface,
            modifier = Modifier.size(modeIconSize)
        )
    }

    IconButton(
        onClick = { viewModel.previous() },
        modifier = Modifier.size(coreButtonSize)
    ) {
        Icon(
            Icons.Rounded.SkipPrevious,
            contentDescription = "上一首",
            tint = colorScheme.onSurface,
            modifier = Modifier.size(skipIconSize)
        )
    }

    val playButtonCorner by animateDpAsState(
        targetValue = if (isPlaying) {
            if (compactLayout) 20.dp else 24.dp
        } else {
            if (compactLayout) 30.dp else 36.dp
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "playButtonCorner"
    )
    val playButtonInteractionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = Modifier.size(playButtonSize),
        shape = RoundedCornerShape(playButtonCorner),
        color = primaryColor,
        contentColor = onPrimaryColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = playButtonInteractionSource,
                    indication = null,
                    onClick = onTogglePlay
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn(tween(durationMillis = 120)) + scaleIn(tween(durationMillis = 120), initialScale = 0.9f)) togetherWith
                        (fadeOut(tween(durationMillis = 90)) + scaleOut(tween(durationMillis = 90), targetScale = 1.05f))
                },
                label = "play_pause_icon"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "播放/暂停",
                    modifier = Modifier.size(playIconSize)
                )
            }
        }
    }

    IconButton(
        onClick = { viewModel.next() },
        modifier = Modifier.size(coreButtonSize)
    ) {
        Icon(
            Icons.Rounded.SkipNext,
            contentDescription = "下一首",
            tint = colorScheme.onSurface,
            modifier = Modifier.size(skipIconSize)
        )
    }

    IconButton(
        onClick = { viewModel.seekForward10s() },
        modifier = Modifier.size(coreButtonSize)
    ) {
        Icon(
            Icons.Rounded.FastForward,
            contentDescription = "快进10秒",
            tint = colorScheme.onSurface,
            modifier = Modifier.size(modeIconSize)
        )
    }
}

private data class NowPlayingLyricsPreviewContent(
    val activeIndex: Int,
    val current: String,
    val currentDurationMs: Long,
    val upcoming: List<NowPlayingUpcomingLyricLine>
)

private data class NowPlayingLyricTrackLine(
    val lyricIndex: Int,
    val text: String,
    val durationMs: Long
)

private data class NowPlayingUpcomingLyricLine(
    val lyricIndex: Int,
    val text: String
)

private data class NowPlayingLyricTrackGeometry(
    val lineTopByIndex: Map<Int, Float>,
    val dividerTopPx: Float,
    val currentHeightPx: Float,
    val totalHeightPx: Float
)

private fun interpolateFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun upcomingLyricLayerAlpha(slot: Int): Float =
    (1f - slot * 0.10f).coerceAtLeast(0.5f)

internal fun shouldAnimateLyricTrackAdvance(previousIndex: Int, nextIndex: Int): Boolean =
    nextIndex == previousIndex + 1

internal data class NowPlayingLyricTypographyMetrics(
    val currentFontSizeSp: Int,
    val currentLineHeightSp: Int,
    val upcomingFontSizeSp: Int,
    val upcomingLineHeightSp: Int
)

internal fun nowPlayingLyricTypographyMetrics(
    largeTypography: Boolean,
    highlightFontSizeSp: Float = 24f
): NowPlayingLyricTypographyMetrics = if (largeTypography) {
    NowPlayingLyricTypographyMetrics(
        currentFontSizeSp = highlightFontSizeSp.roundToInt() + 4,
        currentLineHeightSp = highlightFontSizeSp.roundToInt() + 12,
        upcomingFontSizeSp = 20,
        upcomingLineHeightSp = 27
    )
} else {
    NowPlayingLyricTypographyMetrics(
        currentFontSizeSp = highlightFontSizeSp.roundToInt(),
        currentLineHeightSp = highlightFontSizeSp.roundToInt() + 7,
        upcomingFontSizeSp = 16,
        upcomingLineHeightSp = 23
    )
}

internal fun fittingUpcomingLyricCount(
    availableHeightPx: Int,
    contentTopPaddingPx: Int,
    currentHeightPx: Int,
    dividerHeightPx: Int,
    verticalSpacingPx: Int,
    upcomingHeightsPx: List<Int>,
    maxCount: Int
): Int {
    if (availableHeightPx <= 0 || maxCount <= 0) return 0

    var usedHeight = contentTopPaddingPx.coerceAtLeast(0) +
        currentHeightPx.coerceAtLeast(0) +
        verticalSpacingPx.coerceAtLeast(0) +
        dividerHeightPx.coerceAtLeast(0)
    if (usedHeight > availableHeightPx) return 0

    var count = 0
    for (height in upcomingHeightsPx.take(maxCount)) {
        val requiredHeight = verticalSpacingPx.coerceAtLeast(0) + height.coerceAtLeast(0)
        if (usedHeight + requiredHeight > availableHeightPx) break
        usedHeight += requiredHeight
        count++
    }
    return count
}

@Composable
internal fun NowPlayingLyricsPreview(
    lyrics: List<SubtitleEntry>,
    currentPosition: Long,
    onOpenLyrics: () -> Unit,
    colors: LyricReadableColors,
    interactionEnabled: Boolean = true,
    highlightFontSizeSp: Float = 24f,
    compactHeight: Boolean = false,
    tabletLayout: Boolean = false,
    largeTypography: Boolean = tabletLayout,
    upcomingCount: Int? = null,
    centered: Boolean = false,
    currentFontWeight: FontWeight = FontWeight.SemiBold,
    currentMaxLinesOverride: Int? = null,
    upcomingMaxLinesOverride: Int? = null,
    marqueeCurrentLine: Boolean = false,
    contentTopPadding: Dp = 0.dp,
    onCurrentLineAnchorChanged: ((Float) -> Unit)? = null,
    emptyText: String = "当前音频暂无同步歌词",
    modifier: Modifier = Modifier
) {
    val sortedLyrics = remember(lyrics) {
        if (lyrics.zipWithNext().all { (first, second) -> first.startMs <= second.startMs }) {
            lyrics
        } else {
            lyrics.sortedBy { it.startMs }
        }
    }
    val indexFinder = remember(sortedLyrics) { SubtitleIndexFinder(sortedLyrics) }
    val activeIndex = remember(currentPosition, indexFinder, sortedLyrics) {
        if (sortedLyrics.isEmpty()) {
            -1
        } else {
            indexFinder.findActiveIndex(currentPosition).coerceAtLeast(0)
        }
    }
    val candidateLimit = upcomingCount?.coerceAtLeast(0) ?: if (tabletLayout) 8 else 6
    val sourceContent = remember(sortedLyrics, activeIndex, emptyText, candidateLimit) {
        val currentEntry = sortedLyrics.getOrNull(activeIndex)
        val current = currentEntry
            ?.text
            ?.let(::normalizeMultilineText)
            .orEmpty()
            .ifBlank { emptyText }
        val currentDurationMs = currentEntry
            ?.let { (it.endMs - it.startMs).coerceAtLeast(0L) }
            ?: 0L
        val upcoming = (1..candidateLimit).mapNotNull { offset ->
            val lyricIndex = activeIndex + offset
            val text = sortedLyrics
                .getOrNull(lyricIndex)
                ?.text
                ?.let(::normalizeMultilineText)
                ?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            NowPlayingUpcomingLyricLine(
                lyricIndex = lyricIndex,
                text = text
            )
        }
        NowPlayingLyricsPreviewContent(
            activeIndex = activeIndex,
            current = current,
            currentDurationMs = currentDurationMs,
            upcoming = upcoming
        )
    }
    val typographyMetrics = remember(largeTypography, highlightFontSizeSp) {
        nowPlayingLyricTypographyMetrics(largeTypography, highlightFontSizeSp)
    }
    val currentFontSize = typographyMetrics.currentFontSizeSp.sp
    val currentLineHeight = typographyMetrics.currentLineHeightSp.sp
    val currentMaxLines = currentMaxLinesOverride ?: when {
        tabletLayout -> 5
        compactHeight -> 2
        else -> 3
    }
    val upcomingMaxLines = upcomingMaxLinesOverride ?: if (tabletLayout) 2 else 1
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    val verticalSpacing = if (tabletLayout) 12.dp else 8.dp
    val currentStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = currentFontSize,
        lineHeight = currentLineHeight,
        fontWeight = currentFontWeight
    )
    val upcomingStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = typographyMetrics.upcomingFontSizeSp.sp,
        lineHeight = typographyMetrics.upcomingLineHeightSp.sp,
        fontWeight = FontWeight.Normal
    )
    val density = LocalDensity.current
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val contentTopPaddingPx = with(density) { contentTopPadding.roundToPx() }
    val verticalSpacingPx = with(density) { verticalSpacing.roundToPx() }
    val dividerHeightPx = with(density) { 2.dp.roundToPx() }

    BoxWithConstraints(
        modifier = modifier
            .then(
                if (onCurrentLineAnchorChanged != null) {
                    Modifier.onGloballyPositioned { coordinates ->
                        onCurrentLineAnchorChanged(
                            coordinates.boundsInRoot().top + contentTopPaddingPx
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clickable(enabled = interactionEnabled) { onOpenLyrics() }
    ) {
        val visibleUpcomingCount = remember(
            sourceContent,
            upcomingCount,
            currentStyle,
            upcomingStyle,
            currentMaxLines,
            upcomingMaxLines,
            constraints.maxWidth,
            constraints.maxHeight,
            contentTopPaddingPx,
            verticalSpacing,
            density.density,
            density.fontScale
        ) {
            if (upcomingCount != null) {
                candidateLimit
            } else if (
                constraints.maxWidth <= 0 ||
                constraints.maxHeight <= 0 ||
                constraints.maxHeight == androidx.compose.ui.unit.Constraints.Infinity
            ) {
                0
            } else {
                val textConstraints = androidx.compose.ui.unit.Constraints(
                    maxWidth = constraints.maxWidth
                )
                val currentHeightPx = textMeasurer.measure(
                    text = androidx.compose.ui.text.AnnotatedString(sourceContent.current),
                    style = currentStyle,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true,
                    maxLines = currentMaxLines,
                    constraints = textConstraints
                ).size.height
                val upcomingHeightsPx = sourceContent.upcoming.map { lyric ->
                    textMeasurer.measure(
                        text = androidx.compose.ui.text.AnnotatedString(lyric.text),
                        style = upcomingStyle,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true,
                        maxLines = upcomingMaxLines,
                        constraints = textConstraints
                    ).size.height
                }
                fittingUpcomingLyricCount(
                    availableHeightPx = constraints.maxHeight,
                    contentTopPaddingPx = contentTopPaddingPx,
                    currentHeightPx = currentHeightPx,
                    dividerHeightPx = dividerHeightPx,
                    verticalSpacingPx = verticalSpacingPx,
                    upcomingHeightsPx = upcomingHeightsPx,
                    maxCount = candidateLimit
                )
            }
        }
        val content = remember(sourceContent, visibleUpcomingCount) {
            sourceContent.copy(upcoming = sourceContent.upcoming.take(visibleUpcomingCount))
        }
        var settledContent by remember(sortedLyrics) { mutableStateOf(content) }
        var outgoingContent by remember(sortedLyrics) {
            mutableStateOf<NowPlayingLyricsPreviewContent?>(null)
        }
        val trackProgress = remember(sortedLyrics) { Animatable(1f) }

        LaunchedEffect(content) {
            if (content.activeIndex == settledContent.activeIndex) {
                settledContent = content
                outgoingContent = null
                trackProgress.snapTo(1f)
            } else if (!shouldAnimateLyricTrackAdvance(settledContent.activeIndex, content.activeIndex)) {
                settledContent = content
                outgoingContent = null
                trackProgress.snapTo(1f)
            } else {
                trackProgress.snapTo(0f)
                outgoingContent = settledContent
                settledContent = content
                trackProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 440,
                        easing = FastOutSlowInEasing
                    )
                )
                outgoingContent = null
            }
        }

        val fromContent = outgoingContent ?: settledContent
        val toContent = settledContent
        val textConstraints = remember(constraints.maxWidth) {
            androidx.compose.ui.unit.Constraints(maxWidth = constraints.maxWidth.coerceAtLeast(0))
        }

        fun measureTrackGeometry(trackContent: NowPlayingLyricsPreviewContent): NowPlayingLyricTrackGeometry {
            val currentHeightPx = textMeasurer.measure(
                text = androidx.compose.ui.text.AnnotatedString(trackContent.current),
                style = currentStyle,
                overflow = TextOverflow.Ellipsis,
                softWrap = true,
                maxLines = currentMaxLines,
                constraints = textConstraints
            ).size.height.toFloat()
            val lineTopByIndex = linkedMapOf(trackContent.activeIndex to 0f)
            val dividerTopPx = currentHeightPx + verticalSpacingPx
            var cursorPx = dividerTopPx + dividerHeightPx + verticalSpacingPx

            trackContent.upcoming.forEachIndexed { slot, lyric ->
                lineTopByIndex[lyric.lyricIndex] = cursorPx
                val lineHeightPx = textMeasurer.measure(
                    text = androidx.compose.ui.text.AnnotatedString(lyric.text),
                    style = upcomingStyle,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true,
                    maxLines = upcomingMaxLines,
                    constraints = textConstraints
                ).size.height
                cursorPx += lineHeightPx
                if (slot != trackContent.upcoming.lastIndex) {
                    cursorPx += verticalSpacingPx
                }
            }

            return NowPlayingLyricTrackGeometry(
                lineTopByIndex = lineTopByIndex,
                dividerTopPx = dividerTopPx,
                currentHeightPx = currentHeightPx,
                totalHeightPx = cursorPx.coerceAtLeast(dividerTopPx + dividerHeightPx)
            )
        }

        val fromGeometry = remember(
            fromContent,
            currentStyle,
            upcomingStyle,
            currentMaxLines,
            upcomingMaxLines,
            textConstraints,
            verticalSpacingPx,
            dividerHeightPx
        ) {
            measureTrackGeometry(fromContent)
        }
        val toGeometry = remember(
            toContent,
            currentStyle,
            upcomingStyle,
            currentMaxLines,
            upcomingMaxLines,
            textConstraints,
            verticalSpacingPx,
            dividerHeightPx
        ) {
            measureTrackGeometry(toContent)
        }
        fun trackBaseTop(geometry: NowPlayingLyricTrackGeometry): Float {
            if (contentTopPaddingPx > 0) return contentTopPaddingPx.toFloat()
            if (constraints.maxHeight == androidx.compose.ui.unit.Constraints.Infinity) return 0f
            return ((constraints.maxHeight - geometry.totalHeightPx) / 2f).coerceAtLeast(0f)
        }

        val fromBaseTop = trackBaseTop(fromGeometry)
        val toBaseTop = trackBaseTop(toGeometry)
        val fromUpcomingSlots = remember(fromContent) {
            fromContent.upcoming
                .mapIndexed { slot, lyric -> lyric.lyricIndex to slot }
                .toMap()
        }
        val toUpcomingSlots = remember(toContent) {
            toContent.upcoming
                .mapIndexed { slot, lyric -> lyric.lyricIndex to slot }
                .toMap()
        }
        val renderedLines = remember(fromContent, toContent) {
            val lines = linkedMapOf<Int, NowPlayingLyricTrackLine>()
            fun addTrack(trackContent: NowPlayingLyricsPreviewContent) {
                lines[trackContent.activeIndex] = NowPlayingLyricTrackLine(
                    lyricIndex = trackContent.activeIndex,
                    text = trackContent.current,
                    durationMs = trackContent.currentDurationMs
                )
                trackContent.upcoming.forEach { lyric ->
                    lines[lyric.lyricIndex] = NowPlayingLyricTrackLine(
                        lyricIndex = lyric.lyricIndex,
                        text = lyric.text,
                        durationMs = 0L
                    )
                }
            }
            addTrack(fromContent)
            addTrack(toContent)
            lines.values.sortedBy { it.lyricIndex }
        }
        val promotionStartScale = remember(currentFontSize, upcomingStyle.fontSize) {
            (upcomingStyle.fontSize.value / currentFontSize.value).coerceIn(0.55f, 0.9f)
        }
        val dividerAlignment = if (centered) Alignment.TopCenter else Alignment.TopStart

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(dividerAlignment)
                    .width(if (tabletLayout) 48.dp else 40.dp)
                    .height(2.dp)
                    .graphicsLayer {
                        val fraction = trackProgress.value.coerceIn(0f, 1f)
                        translationY = interpolateFloat(
                            fromBaseTop + fromGeometry.dividerTopPx,
                            toBaseTop + toGeometry.dividerTopPx,
                            fraction
                        )
                    }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.accentEmphasis)
            )

            renderedLines.forEach { line ->
                key(line.lyricIndex) {
                    val fromTop = fromGeometry.lineTopByIndex[line.lyricIndex]
                    val toTop = toGeometry.lineTopByIndex[line.lyricIndex]
                    val fromSlot = fromUpcomingSlots[line.lyricIndex]
                    val toSlot = toUpcomingSlots[line.lyricIndex]
                    val wasCurrent = line.lyricIndex == fromContent.activeIndex
                    val becomesCurrent = line.lyricIndex == toContent.activeIndex
                    val isTailCandidate = fromTop == null && toSlot != null
                    val renderAsCurrent = becomesCurrent || (wasCurrent && toTop == null)
                    val movingForward = toContent.activeIndex >= fromContent.activeIndex
                    val outgoingDistance = maxOf(
                        fromGeometry.currentHeightPx + verticalSpacingPx,
                        toGeometry.currentHeightPx + verticalSpacingPx
                    )
                    val startY = when {
                        fromTop != null -> fromBaseTop + fromTop
                        becomesCurrent -> toBaseTop + (toTop ?: 0f) + if (movingForward) outgoingDistance else -outgoingDistance
                        else -> toBaseTop + (toTop ?: 0f)
                    }
                    val endY = when {
                        toTop != null -> toBaseTop + toTop
                        movingForward -> startY - outgoingDistance
                        else -> startY + outgoingDistance
                    }
                    val startAlpha = when {
                        wasCurrent -> 1f
                        fromSlot != null && becomesCurrent -> {
                            val inactiveVisualAlpha = colors.inactiveText.alpha * upcomingLyricLayerAlpha(fromSlot)
                            (inactiveVisualAlpha / colors.activeText.alpha.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                        }
                        fromSlot != null -> upcomingLyricLayerAlpha(fromSlot)
                        else -> 0f
                    }
                    val endAlpha = when {
                        becomesCurrent -> 1f
                        toSlot != null -> upcomingLyricLayerAlpha(toSlot)
                        else -> 0f
                    }
                    val startScale = if (becomesCurrent && fromSlot != null) promotionStartScale else 1f
                    val transformOrigin = if (centered) {
                        TransformOrigin(0.5f, 0f)
                    } else {
                        TransformOrigin(0f, 0f)
                    }
                    val lineModifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val fraction = trackProgress.value.coerceIn(0f, 1f)
                            val alphaFraction = when {
                                isTailCandidate -> ((fraction - 0.46f) / 0.54f).coerceIn(0f, 1f)
                                wasCurrent && toTop == null -> (fraction / 0.72f).coerceIn(0f, 1f)
                                else -> fraction
                            }
                            translationY = interpolateFloat(startY, endY, fraction)
                            alpha = interpolateFloat(startAlpha, endAlpha, alphaFraction)
                            val scale = interpolateFloat(startScale, 1f, fraction)
                            scaleX = scale
                            scaleY = scale
                            this.transformOrigin = transformOrigin
                        }

                    if (renderAsCurrent && marqueeCurrentLine) {
                        SlowMarqueeText(
                            text = line.text,
                            durationMs = line.durationMs,
                            style = currentStyle,
                            colors = colors,
                            fontWeight = currentFontWeight,
                            modifier = lineModifier
                        )
                    } else {
                        Text(
                            text = line.text,
                            style = if (renderAsCurrent) currentStyle else upcomingStyle,
                            color = if (renderAsCurrent) colors.activeText else colors.inactiveText,
                            modifier = lineModifier,
                            textAlign = textAlign,
                            maxLines = if (renderAsCurrent) currentMaxLines else upcomingMaxLines,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}


@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SlowMarqueeText(
    text: String,
    durationMs: Long,
    style: androidx.compose.ui.text.TextStyle,
    colors: LyricReadableColors,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val singleLine = remember(text) { normalizeSingleLineText(text) }
    val content = singleLine.ifBlank { " " }
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
        val density = LocalDensity.current
        val containerWidth = constraints.maxWidth
        val horizontalPaddingPx = with(density) { 10.dp.roundToPx() * 2 }
        val availableWidth = (containerWidth - horizontalPaddingPx).coerceAtLeast(0)
        val needsMarquee = textWidth > availableWidth
        val finalVelocity = remember(textWidth, availableWidth, durationMs, density, needsMarquee) {
            if (!needsMarquee) {
                0.dp
            } else {
                val distancePx = (textWidth - availableWidth).toFloat()
                val targetTimeSeconds = (durationMs - 1000).coerceAtLeast(2000) / 1000f
                val distanceDp = with(density) { distancePx.toDp() }
                (distanceDp / targetTimeSeconds)
            }
        }

        key(content, availableWidth, needsMarquee, finalVelocity) {
            Text(
                text = content,
                style = style.copy(fontWeight = fontWeight),
                color = colors.activeText,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp)
                    .then(
                        if (needsMarquee) {
                            Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                velocity = finalVelocity.coerceAtLeast(10.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
            )
        }
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

private fun normalizeMultilineText(text: String): String {
    return text
        .replace('\uFEFF', ' ')
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace('\t', ' ')
        .lineSequence()
        .map { line -> line.replace(Regex("""[ ]+"""), " ").trim() }
        .joinToString("\n")
        .trim()
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun VolumeControl(
    modifier: Modifier = Modifier,
    accentColor: Color,
    viewModel: PlayerViewModel,
    hardwareVolumeEventTick: Long,
    audioOutputRouteKind: AudioOutputRouteKind,
    warningSessionState: AppVolumeWarningSessionState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    compactLayout: Boolean = false
) {
    val volume by viewModel.appVolumePercent.collectAsStateWithLifecycle()
    var lastNonZeroVolume by remember { mutableIntStateOf(AppVolume.DefaultPercent) }
    var lastInteractionAt by remember { mutableLongStateOf(0L) }
    var hasObservedInitialHardwareVolumeEventTick by remember { mutableStateOf(false) }
    var lastHandledHardwareVolumeEventTick by remember { mutableLongStateOf(0L) }
    val protectedVolumeChangeState = rememberProtectedAppVolumeChangeState(
        warningSessionState = warningSessionState,
        onApplyVolumeChange = { next ->
            if (next > 0) lastNonZeroVolume = next
            viewModel.setAppVolumePercent(next)
        }
    )

    LaunchedEffect(volume) {
        if (volume > 0) lastNonZeroVolume = volume
    }

    LaunchedEffect(hardwareVolumeEventTick) {
        if (!hasObservedInitialHardwareVolumeEventTick) {
            lastHandledHardwareVolumeEventTick = hardwareVolumeEventTick
            hasObservedInitialHardwareVolumeEventTick = true
            return@LaunchedEffect
        }
        if (hardwareVolumeEventTick <= 0L) return@LaunchedEffect
        if (hardwareVolumeEventTick == lastHandledHardwareVolumeEventTick) return@LaunchedEffect
        lastHandledHardwareVolumeEventTick = hardwareVolumeEventTick
        onExpandedChange(true)
        lastInteractionAt = SystemClock.elapsedRealtime()
    }

    fun setVolume(newVolume: Int) {
        val next = AppVolume.clampPercent(newVolume)
        if (next > 0) lastNonZeroVolume = next
        viewModel.setAppVolumePercent(next)
    }

    fun requestVolumeChange(newVolume: Int, source: com.asmr.player.playback.AppVolumeChangeSource) {
        protectedVolumeChangeState.requestVolumeChange(
            currentPercent = volume,
            targetPercent = newVolume,
            source = source
        )
    }

    LaunchedEffect(expanded, lastInteractionAt) {
        if (!expanded) return@LaunchedEffect
        val snapshot = lastInteractionAt
        delay(3_000)
        if (expanded && lastInteractionAt == snapshot) {
            onExpandedChange(false)
        }
    }

    val colorScheme = AsmrTheme.colorScheme
    val isMuted = volume == 0

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
                    .padding(vertical = if (compactLayout) 2.dp else 6.dp)
                    .combinedClickable(
                        onClick = {
                            if (volume > 0) {
                                setVolume(0)
                            } else {
                                setVolume(lastNonZeroVolume.coerceAtLeast(AppVolume.StepPercent))
                            }
                        },
                        onLongClick = {
                            onExpandedChange(true)
                            lastInteractionAt = SystemClock.elapsedRealtime()
                        }
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioOutputRouteIcon(
                    routeKind = audioOutputRouteKind,
                    isMuted = isMuted,
                    tint = accentColor,
                    modifier = Modifier.size(if (compactLayout) 20.dp else 22.dp)
                )
                Spacer(modifier = Modifier.width(if (compactLayout) 8.dp else 10.dp))
                Text(
                    text = "长按调整音量",
                    style = if (compactLayout) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    color = colorScheme.textTertiary
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (compactLayout) 2.dp else 6.dp,
                        bottom = if (compactLayout) 0.dp else 2.dp
                    )
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(if (compactLayout) 4.dp else 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AudioOutputRouteIcon(
                        routeKind = audioOutputRouteKind,
                        isMuted = isMuted,
                        tint = accentColor,
                        modifier = Modifier
                            .size(if (compactLayout) 18.dp else 20.dp)
                            .combinedClickable(
                                onClick = {
                                    if (volume > 0) {
                                        setVolume(0)
                                    } else {
                                        setVolume(lastNonZeroVolume.coerceAtLeast(AppVolume.StepPercent))
                                    }
                                    lastInteractionAt = SystemClock.elapsedRealtime()
                                },
                                onLongClick = {}
                            )
                    )
                    Spacer(modifier = Modifier.width(if (compactLayout) 8.dp else 10.dp))
                    Text(
                        text = "音量",
                        style = if (compactLayout) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        color = colorScheme.textTertiary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${AppVolume.clampPercent(volume)}%",
                        style = if (compactLayout) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        color = colorScheme.textTertiary
                    )
                }

                AppVolumeSlider(
                    valuePercent = volume,
                    onValueChange = { newVol, source ->
                        if (newVol != volume) {
                            requestVolumeChange(newVol, source)
                        }
                        lastInteractionAt = SystemClock.elapsedRealtime()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = accentColor,
                    onInteractionActiveChanged = {
                        lastInteractionAt = SystemClock.elapsedRealtime()
                    }
                )
            }
        }
    }
    AppVolumeHearingWarningDialog(state = protectedVolumeChangeState)
}

// AdaptiveLyricsView has been replaced by AppleLyricsView

