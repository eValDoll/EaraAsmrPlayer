package com.asmr.player.ui.player

import android.content.res.Configuration
import android.content.Intent
import android.view.KeyEvent as AndroidKeyEvent
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.asmr.player.R
import com.asmr.player.HardwareVolumeOverlay
import com.asmr.player.cache.CachePolicy
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.data.lyrics.lyricsTargetContextFromMediaItem
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.AudioOutputRouteIcon
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.DismissOutsideBoundsOverlay
import com.asmr.player.ui.common.AppVolumeHearingWarningDialog
import com.asmr.player.ui.common.AppVolumeSlider
import com.asmr.player.ui.common.AppVolumeWarningSessionState
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
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private enum class NowPlayingSurfaceMode {
    PLAYER,
    LYRICS
}

private const val VideoProgressUiTickMs = 1_000L
private const val NowPlayingHomeLayoutAnimationDurationMillis = 620
private const val NowPlayingHomeLyricsFadeInDurationMillis = 240
private const val NowPlayingHomeLyricsFadeOutDurationMillis = 160
private val NowPlayingPortraitMaxContentWidth = 600.dp
private val NowPlayingCompactShortScreenHeight = 700.dp
private val NowPlayingClassicAudienceHeight = 18.dp
private val NowPlayingClassicTrackInfoSingleLineHeight = 88.dp
private val NowPlayingClassicTrackInfoExtraTitleLineHeight = 20.dp
private val NowPlayingHomeClassicLyricsReserveHeight = 56.dp
private val NowPlayingHomeExpandedLyricsReserveHeight = 118.dp
private val NowPlayingHomeCompactMinCoverWidth = 180.dp
private val NowPlayingHomeRegularMinCoverWidth = 240.dp
private val NowPlayingHomeClassicRegularMaxCoverWidth = 360.dp
private val NowPlayingHomeClassicLyricsBottomPadding = 6.dp
private val NowPlayingPortraitIdentityMaxWidth = 320.dp
private val NowPlayingLandscapeIdentityHeight = 28.dp
private val NowPlayingLandscapeCoreRowHeight = 82.dp
private val NowPlayingLandscapeProgressTopPadding = 12.dp
private const val NowPlayingHomeClassicCompactCoverScale = 0.92f

internal data class NowPlayingPortraitLayoutMetrics(
    val compact: Boolean,
    val contentHorizontalPadding: Dp,
    val topPadding: Dp,
    val coverVerticalPadding: Dp,
    val audienceHeight: Dp,
    val trackInfoSingleLineHeight: Dp,
    val trackInfoExtraTitleLineHeight: Dp,
    val classicLyricsReserveHeight: Dp,
    val expandedLyricsReserveHeight: Dp,
    val minimumCoverWidth: Dp,
    val expandedLyricsTopPadding: Dp,
    val classicLyricsContainerHeight: Dp,
    val classicLyricsBottomPadding: Dp,
    val bottomPadding: Dp,
    val bottomSectionSpacing: Dp
)

internal fun nowPlayingPortraitLayoutMetrics(
    screenHeight: Dp,
    widthClass: WindowWidthSizeClass
): NowPlayingPortraitLayoutMetrics {
    val compact = widthClass == WindowWidthSizeClass.Compact &&
        screenHeight.isFiniteDp() &&
        screenHeight <= NowPlayingCompactShortScreenHeight
    if (compact) {
        return NowPlayingPortraitLayoutMetrics(
            compact = true,
            contentHorizontalPadding = 20.dp,
            topPadding = 8.dp,
            coverVerticalPadding = 4.dp,
            audienceHeight = 16.dp,
            trackInfoSingleLineHeight = 65.dp,
            trackInfoExtraTitleLineHeight = 15.dp,
            classicLyricsReserveHeight = 46.dp,
            expandedLyricsReserveHeight = 96.dp,
            minimumCoverWidth = 148.dp,
            expandedLyricsTopPadding = 8.dp,
            classicLyricsContainerHeight = 48.dp,
            classicLyricsBottomPadding = 2.dp,
            bottomPadding = 8.dp,
            bottomSectionSpacing = 2.dp
        )
    }
    return NowPlayingPortraitLayoutMetrics(
        compact = false,
        contentHorizontalPadding = 24.dp,
        topPadding = 24.dp,
        coverVerticalPadding = if (widthClass == WindowWidthSizeClass.Compact) 16.dp else 32.dp,
        audienceHeight = NowPlayingClassicAudienceHeight,
        trackInfoSingleLineHeight = NowPlayingClassicTrackInfoSingleLineHeight,
        trackInfoExtraTitleLineHeight = NowPlayingClassicTrackInfoExtraTitleLineHeight,
        classicLyricsReserveHeight = NowPlayingHomeClassicLyricsReserveHeight,
        expandedLyricsReserveHeight = NowPlayingHomeExpandedLyricsReserveHeight,
        minimumCoverWidth = nowPlayingHomeMinCoverWidth(widthClass),
        expandedLyricsTopPadding = 14.dp,
        classicLyricsContainerHeight = 58.dp,
        classicLyricsBottomPadding = NowPlayingHomeClassicLyricsBottomPadding,
        bottomPadding = 16.dp,
        bottomSectionSpacing = 4.dp
    )
}

internal fun nowPlayingClassicTrackInfoHeight(
    titleLineCount: Int,
    metrics: NowPlayingPortraitLayoutMetrics? = null
): Dp {
    val extraLineCount = titleLineCount.coerceIn(1, 2) - 1
    val singleLineHeight = metrics?.trackInfoSingleLineHeight ?: NowPlayingClassicTrackInfoSingleLineHeight
    val extraTitleLineHeight = metrics?.trackInfoExtraTitleLineHeight
        ?: NowPlayingClassicTrackInfoExtraTitleLineHeight
    return singleLineHeight + extraTitleLineHeight * extraLineCount
}

internal fun nowPlayingHomeCoverWidth(
    expanded: Boolean,
    availableWidth: Dp,
    availableHeight: Dp = Dp.Unspecified,
    widthClass: WindowWidthSizeClass,
    contentHorizontalPadding: Dp,
    coverAspectRatio: Float = 1f,
    topPadding: Dp = if (expanded) 0.dp else 24.dp,
    coverVerticalPadding: Dp = if (expanded) 0.dp else if (widthClass == WindowWidthSizeClass.Compact) 16.dp else 32.dp,
    identityHeight: Dp = if (expanded) {
        0.dp
    } else {
        NowPlayingClassicAudienceHeight + nowPlayingClassicTrackInfoHeight(titleLineCount = 1)
    },
    lyricsReserveHeight: Dp = if (expanded) NowPlayingHomeExpandedLyricsReserveHeight else NowPlayingHomeClassicLyricsReserveHeight,
    minimumCoverWidth: Dp = nowPlayingHomeMinCoverWidth(widthClass)
): Dp {
    val fullWidth = availableWidth.coerceAtLeast(1.dp)
    val widthBound = if (expanded) {
        fullWidth
    } else {
        val paddedWidth = (fullWidth - contentHorizontalPadding * 2).coerceAtLeast(1.dp)
        if (widthClass == WindowWidthSizeClass.Compact) {
            paddedWidth * NowPlayingHomeClassicCompactCoverScale
        } else {
            paddedWidth.coerceAtMost(NowPlayingHomeClassicRegularMaxCoverWidth)
        }
    }
    if (!availableHeight.isFiniteDp()) return widthBound

    val safeAspectRatio = coverAspectRatio
        .takeIf { it.isFinite() && it > 0f }
        ?.coerceIn(0.5f, 3f)
        ?: 1f
    val reservedHeight = topPadding +
        coverVerticalPadding * 2 +
        identityHeight +
        lyricsReserveHeight
    val heightLimitedWidth = ((availableHeight - reservedHeight).coerceAtLeast(1.dp) * safeAspectRatio)
        .coerceAtLeast(minimumCoverWidth.coerceAtMost(widthBound))
    return widthBound.coerceAtMost(heightLimitedWidth)
}

private fun nowPlayingHomeMinCoverWidth(widthClass: WindowWidthSizeClass): Dp {
    return if (widthClass == WindowWidthSizeClass.Compact) {
        NowPlayingHomeCompactMinCoverWidth
    } else {
        NowPlayingHomeRegularMinCoverWidth
    }
}

private fun Dp.isFiniteDp(): Boolean = value.isFinite()

private data class NowPlayingStaticPlayback(
    val isConnected: Boolean,
    val startupRestoreResolved: Boolean,
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
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
            playWhenReady = playWhenReady,
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
        playWhenReady = playWhenReady,
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
    val initialProgressState = remember(viewModel, isVideo) {
        val snapshot = viewModel.playback.value
        val positionMs = if (isVideo) {
            (snapshot.positionMs / VideoProgressUiTickMs) * VideoProgressUiTickMs
        } else {
            snapshot.positionMs
        }
        NowPlayingProgressState(positionMs, snapshot.durationMs)
    }
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
    }.collectAsStateWithLifecycle(initialValue = initialProgressState)

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

@Composable
private fun rememberArtworkAspectRatio(artworkModel: Any?): Float {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) {
        EntryPointAccessors.fromApplication(context, ImageCacheEntryPoint::class.java)
            .imageCacheManager()
    }
    var aspectRatio by remember(artworkModel) { mutableFloatStateOf(1f) }

    LaunchedEffect(artworkModel, manager) {
        val model = artworkModel ?: run {
            aspectRatio = 1f
            return@LaunchedEffect
        }
        runCatching {
            manager.loadImage(model = model, size = null, cachePolicy = CachePolicy.DEFAULT)
        }.onSuccess { image ->
            val width = image.width
            val height = image.height
            if (width > 0 && height > 0) {
                aspectRatio = (width.toFloat() / height.toFloat()).coerceIn(0.25f, 4f)
            }
        }
    }

    return aspectRatio
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
    textShadow: Shadow?,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.labelSmall.copy(shadow = textShadow)
    val displayCount = companionCount.coerceAtLeast(0)

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
    textColor: Color = AsmrTheme.colorScheme.textTertiary,
    textShadow: Shadow? = null,
    pageEntranceSettled: Boolean = true
) {
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
                        style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    is ListenTogetherAudiencePresentation.Audience -> ListenTogetherAudienceCountText(
                        companionCount = target.companionCount,
                        color = textColor,
                        textShadow = textShadow
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

internal data class NowPlayingArtistMeta(
    val circle: String,
    val cvNames: List<String>
)

internal fun parseNowPlayingArtistMeta(artist: String): NowPlayingArtistMeta {
    val normalized = artist.trim()
    if (normalized.isBlank()) return NowPlayingArtistMeta(circle = "", cvNames = emptyList())

    val parts = normalized.split(" / ", limit = 2).map { it.trim() }
    val circle = parts.takeIf { it.size == 2 }?.first().orEmpty()
    val cvText = if (parts.size == 2) parts[1] else normalized
    val cvNames = cvText
        .split(',', '，', '、', '/', '\n', ';', '；', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
    return NowPlayingArtistMeta(circle = circle, cvNames = cvNames)
}

internal fun formatExpandedArtistSummary(artistMeta: NowPlayingArtistMeta): String {
    val cvSummary = artistMeta.cvNames.joinToString("、")
    return listOf(artistMeta.circle, cvSummary)
        .filter { it.isNotBlank() }
        .joinToString(" | ")
}

@Composable
private fun ClassicPlayerIdentity(
    title: String,
    artistMeta: NowPlayingArtistMeta,
    accentColor: Color,
    onTitleLineCountChanged: (Int) -> Unit,
    compactLayout: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val textShadow = remember(colorScheme.isDark) {
        if (colorScheme.isDark) {
            Shadow(
                color = Color.Black.copy(alpha = 0.4f),
                offset = Offset(0f, 1f),
                blurRadius = 2f
            )
        } else {
            Shadow(
                color = Color.Black.copy(alpha = 0.12f),
                offset = Offset(0f, 0.5f),
                blurRadius = 1.5f
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .padding(horizontal = if (compactLayout) 20.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactLayout) 3.dp else 5.dp)
    ) {
        Text(
            text = title.ifBlank { "未播放" },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compactLayout) 13.sp else 18.sp,
                lineHeight = if (compactLayout) 15.sp else 20.sp,
                shadow = textShadow
            ),
            color = colorScheme.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            onTextLayout = { result ->
                onTitleLineCountChanged(result.lineCount.coerceIn(1, 2))
            }
        )
        ClassicArtistRows(
            artistMeta = artistMeta,
            accentColor = accentColor,
            compactLayout = compactLayout,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ClassicArtistRows(
    artistMeta: NowPlayingArtistMeta,
    accentColor: Color,
    compactLayout: Boolean,
    modifier: Modifier = Modifier
) {
    if (artistMeta.circle.isBlank() && artistMeta.cvNames.isEmpty()) return

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactLayout) 6.dp else 10.dp)
    ) {
        if (artistMeta.circle.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NowPlayingClassicArtistChipHeight),
                contentAlignment = Alignment.Center
            ) {
                ClassicArtistChip(
                    label = "社团",
                    value = artistMeta.circle,
                    accentColor = accentColor,
                    emphasized = true,
                    compactLayout = compactLayout
                )
            }
        }
        if (artistMeta.cvNames.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NowPlayingClassicArtistChipHeight)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(
                    if (compactLayout) 5.dp else 7.dp,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                artistMeta.cvNames.forEach { cv ->
                    ClassicArtistChip(
                        label = "CV",
                        value = cv,
                        accentColor = accentColor,
                        emphasized = false,
                        compactLayout = compactLayout
                    )
                }
            }
        }
    }
}

private val NowPlayingClassicArtistChipHeight = 20.dp

@Composable
private fun ClassicArtistChip(
    label: String,
    value: String,
    accentColor: Color,
    emphasized: Boolean,
    compactLayout: Boolean
) {
    val colorScheme = AsmrTheme.colorScheme
    val containerColor = accentColor.copy(alpha = if (emphasized) 0.16f else 0.10f)
    val chipTextStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = if (compactLayout) 11.sp else 12.sp,
        lineHeight = if (compactLayout) 13.sp else 14.sp
    )
    Box(
        modifier = Modifier
            .height(NowPlayingClassicArtistChipHeight)
            .background(containerColor, RoundedCornerShape(999.dp))
            .padding(horizontal = if (compactLayout) 7.dp else 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compactLayout) 3.dp else 4.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.alignByBaseline(),
                style = chipTextStyle.copy(fontWeight = FontWeight.SemiBold),
                color = accentColor.copy(alpha = 0.92f),
                maxLines = 1
            )
            Text(
                text = value,
                modifier = Modifier
                    .widthIn(max = if (emphasized) 220.dp else 180.dp)
                    .alignByBaseline(),
                style = chipTextStyle,
                color = if (emphasized) colorScheme.textPrimary else colorScheme.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpandedPlayerIdentityOverlay(
    title: String,
    artistMeta: NowPlayingArtistMeta,
    listenTogetherState: ListenTogetherUiState,
    pageEntranceSettled: Boolean,
    modifier: Modifier = Modifier
) {
    val artistSummary = remember(artistMeta) { formatExpandedArtistSummary(artistMeta) }
    val overlayShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.72f),
            offset = Offset(0f, 1f),
            blurRadius = 4f
        )
    }
    val scrim = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.44f)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(scrim)
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = NowPlayingPortraitIdentityMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            ListenTogetherAudienceLine(
                state = listenTogetherState,
                modifier = Modifier.fillMaxWidth(),
                accentColor = Color.White.copy(alpha = 0.68f),
                textColor = Color.White.copy(alpha = 0.68f),
                textShadow = overlayShadow,
                pageEntranceSettled = pageEntranceSettled
            )
            Text(
                text = title.ifBlank { "未播放" },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp,
                    shadow = overlayShadow
                ),
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (artistSummary.isNotBlank()) {
                Text(
                    text = artistSummary,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = 16.sp,
                        shadow = overlayShadow
                    ),
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun NowPlayingHomeLayoutSwipeHint(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "nowPlayingHomeLayoutSwipeHint")
    val waveProgress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nowPlayingHomeLayoutSwipeHintProgress"
    )
    val textShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.72f),
            offset = Offset(0f, 1.2f),
            blurRadius = 4f
        )
    }

    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Canvas(
            modifier = Modifier
                .width(44.dp)
                .height(24.dp)
        ) {
            repeat(4) { index ->
                val phase = (waveProgress.value + index * 0.25f) % 1f
                val edgeFade = when {
                    phase < 0.22f -> phase / 0.22f
                    phase > 0.78f -> (1f - phase) / 0.22f
                    else -> 1f
                }.coerceIn(0f, 1f)
                val pulse = edgeFade * edgeFade * (3f - 2f * edgeFade)
                val centerX = size.width / 2f
                val centerY = size.height * (0.88f - phase * 0.70f)
                val halfWidth = size.width * 0.15f
                val halfHeight = size.height * 0.14f
                val color = Color.White.copy(alpha = pulse * 0.66f)
                val shadowColor = Color.Black.copy(alpha = pulse * 0.24f)
                val strokeWidth = 1.45.dp.toPx()
                drawLine(
                    color = shadowColor,
                    start = Offset(centerX - halfWidth, centerY + halfHeight + 1.2f),
                    end = Offset(centerX, centerY - halfHeight + 1.2f),
                    strokeWidth = strokeWidth + 1.2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = shadowColor,
                    start = Offset(centerX + halfWidth, centerY + halfHeight + 1.2f),
                    end = Offset(centerX, centerY - halfHeight + 1.2f),
                    strokeWidth = strokeWidth + 1.2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(centerX - halfWidth, centerY + halfHeight),
                    end = Offset(centerX, centerY - halfHeight),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(centerX + halfWidth, centerY + halfHeight),
                    end = Offset(centerX, centerY - halfHeight),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        Text(
            text = "上滑切换封面排布",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                shadow = textShadow
            ),
            color = Color.White.copy(alpha = 0.86f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
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
    onVideoFullscreenChanged: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onRouteExitStarted: (exitDurationMs: Int) -> Unit = {},
    onShowQueue: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onOpenPlaylistPicker: (MediaItem) -> Unit,
    viewModel: PlayerViewModel,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    coverPreviewMode: CoverPreviewMode,
    nowPlayingHomeLayoutMode: NowPlayingHomeLayoutMode,
    nowPlayingHomeLayoutHintDismissed: Boolean,
    onNowPlayingHomeLayoutModeChange: (NowPlayingHomeLayoutMode) -> Unit,
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
    }.collectAsStateWithLifecycle(initialValue = PlaybackSnapshot().toStaticPlayback())
    val playback = staticPlayback.toSnapshot(positionMs = 0L)
    val resolvedDurationMs by viewModel.resolvedDurationMs.collectAsStateWithLifecycle()
    val sliceUiState by viewModel.sliceUiState.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val listenTogetherUiState by viewModel.listenTogetherUiState.collectAsStateWithLifecycle()
    val lyricsState by lyricsViewModel.uiState.collectAsStateWithLifecycle()
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
    val tagDialog by tagViewModel.dialogState.collectAsStateWithLifecycle()
    val availableTags by tagViewModel.availableTags.collectAsStateWithLifecycle()
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
    var homeLayoutHintDismissedInSession by rememberSaveable { mutableStateOf(false) }
    var homeLayoutLyricsVisible by remember { mutableStateOf(true) }
    var homeLayoutChangeJob by remember { mutableStateOf<Job?>(null) }
    val homeLayoutHintScope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        onDispose {
            homeLayoutChangeJob?.cancel()
        }
    }
    LaunchedEffect(nowPlayingHomeLayoutHintDismissed) {
        if (nowPlayingHomeLayoutHintDismissed) {
            homeLayoutHintDismissedInSession = true
        }
    }
    val changeNowPlayingHomeLayoutMode = remember(
        haptic,
        nowPlayingHomeLayoutMode,
        nowPlayingHomeLayoutHintDismissed,
        homeLayoutHintDismissedInSession,
        homeLayoutHintScope,
        onNowPlayingHomeLayoutModeChange
    ) {
        { mode: NowPlayingHomeLayoutMode ->
            if (mode != nowPlayingHomeLayoutMode) {
                homeLayoutChangeJob?.cancel()
                if (!nowPlayingHomeLayoutHintDismissed && !homeLayoutHintDismissedInSession) {
                    homeLayoutHintScope.launch {
                        delay(NowPlayingHomeLayoutAnimationDurationMillis.toLong())
                        homeLayoutHintDismissedInSession = true
                    }
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                homeLayoutChangeJob = homeLayoutHintScope.launch {
                    homeLayoutLyricsVisible = false
                    delay(NowPlayingHomeLyricsFadeOutDurationMillis.toLong())
                    onNowPlayingHomeLayoutModeChange(mode)
                    delay(NowPlayingHomeLayoutAnimationDurationMillis.toLong())
                    homeLayoutLyricsVisible = true
                }
            }
        }
    }
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
    val videoPlayerCoordinator = remember { NowPlayingVideoPlayerCoordinator() }
    DisposableEffect(videoPlayerCoordinator) {
        onDispose { videoPlayerCoordinator.release() }
    }
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
    var videoFullscreen by rememberSaveable(item?.mediaId) { mutableStateOf(false) }
    val activeVideoFullscreen = videoFullscreen && isVideo
    val latestOnVideoFullscreenChanged = rememberUpdatedState(onVideoFullscreenChanged)
    LaunchedEffect(activeVideoFullscreen) {
        latestOnVideoFullscreenChanged.value(activeVideoFullscreen)
    }
    DisposableEffect(Unit) {
        onDispose { latestOnVideoFullscreenChanged.value(false) }
    }
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

    BackHandler(enabled = !pendingRouteExit && !videoFullscreen) {
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
    val showSharedHeaderTitle = isLandscape || surfaceMode == NowPlayingSurfaceMode.LYRICS
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
            PlayerSurfaceHeader(
                title = sharedHeaderTitle,
                isLandscape = isLandscape,
                onNavigateUp = handleNavigateUp,
                onShowSleepTimer = onShowSleepTimer,
                onShowQueue = onShowQueue,
                onManualBindLyrics = if (surfaceMode == NowPlayingSurfaceMode.LYRICS) openManualLyricsAction else null,
                navigationEnabled = !pendingRouteExit,
                showTitle = showSharedHeaderTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sharedHeaderHorizontalPadding)
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
            val renderVideoSurface = split == layoutState.first &&
                phoneLandscape == layoutState.second
            val motionLayout = when {
                split -> NowPlayingMotionLayout.SPLIT_LANDSCAPE
                phoneLandscape -> NowPlayingMotionLayout.PHONE_LANDSCAPE
                else -> NowPlayingMotionLayout.PORTRAIT
            }

            if (split) {
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
                        Spacer(modifier = Modifier.height(NowPlayingLandscapeIdentityHeight))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .then(coverMotion),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Box(
                                modifier = if (isVideo) {
                                    Modifier.fitVideoPreviewAspectRatio(
                                        aspectRatio = videoAspectRatio,
                                        maxWidth = 420.dp
                                    )
                                } else {
                                    Modifier
                                        .widthIn(max = 420.dp)
                                        .aspectRatio(1f)
                                }
                            ) {
                                ArtworkBox(
                                    isVideo = isVideo,
                                    metadata = metadata,
                                    viewModel = viewModel,
                                    videoPlayerCoordinator = videoPlayerCoordinator,
                                    renderVideoSurface = renderVideoSurface,
                                    videoFullscreen = videoFullscreen,
                                    onOpenVideoFullscreen = { videoFullscreen = true },
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
                                    .height(NowPlayingLandscapeCoreRowHeight)
                                    .then(progressMotion),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Box(modifier = Modifier.padding(top = NowPlayingLandscapeProgressTopPadding)) {
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
                            modifier = Modifier
                                .height(NowPlayingLandscapeIdentityHeight)
                                .then(infoMotion),
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
                                            setRenderStyle(ChannelSpectrumView.RenderStyle.ThinLines)
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
                                            isLandscape = true,
                                            contentKey = lyricsState.contentKey,
                                            contentVisible = !lyricsState.isLoading
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
                                            setRenderStyle(ChannelSpectrumView.RenderStyle.ThinLines)
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
                            modifier = Modifier.height(NowPlayingLandscapeCoreRowHeight),
                            showActionRow = false,
                            coreControlsModifier = controlsMotion,
                            primaryColor = accentColor,
                            onPrimaryColor = onAccentColor
                        )
                    }
                }
            }
        } else if (phoneLandscape) {
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .then(coverMotion),
                            contentAlignment = Alignment.TopCenter
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
                                    videoPlayerCoordinator = videoPlayerCoordinator,
                                    renderVideoSurface = renderVideoSurface,
                                    videoFullscreen = videoFullscreen,
                                    onOpenVideoFullscreen = { videoFullscreen = true },
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
                                    .height(NowPlayingLandscapeCoreRowHeight)
                                    .then(progressMotion),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Box(modifier = Modifier.padding(top = NowPlayingLandscapeProgressTopPadding)) {
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
                                            setRenderStyle(ChannelSpectrumView.RenderStyle.ThinLines)
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
                                            isLandscape = true,
                                            contentKey = lyricsState.contentKey,
                                            contentVisible = !lyricsState.isLoading
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
                                            setRenderStyle(ChannelSpectrumView.RenderStyle.ThinLines)
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
                            modifier = Modifier.height(NowPlayingLandscapeCoreRowHeight),
                            showActionRow = false,
                            coreControlsModifier = controlsMotion,
                            primaryColor = accentColor,
                            onPrimaryColor = onAccentColor
                        )
                    }
                }
            }
        } else {
            val coverMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.COVER)
            val lyricsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.LYRICS)
            val progressMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.PROGRESS)
            val actionRowMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.ACTION_ROW)
            val controlsMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.CONTROLS)
            val volumeMotion = routeTransition.nowPlayingMotionModifier(motionLayout, NowPlayingMotionSlot.VOLUME)
            val expandedHomeLayout = nowPlayingHomeLayoutMode == NowPlayingHomeLayoutMode.Expanded && !isVideo
            val portraitArtistText = metadata?.artist?.toString().orEmpty()
            val portraitArtistMeta = remember(portraitArtistText) {
                parseNowPlayingArtistMeta(portraitArtistText)
            }
            var classicTitleLineCount by remember(playerHeaderTitle) { mutableIntStateOf(1) }
            val portraitScreenHeight = configuration.screenHeightDp.dp
            val portraitLayoutMetrics = remember(portraitScreenHeight, widthClass) {
                nowPlayingPortraitLayoutMetrics(
                    screenHeight = portraitScreenHeight,
                    widthClass = widthClass
                )
            }
            val classicTrackInfoTargetHeight = nowPlayingClassicTrackInfoHeight(
                titleLineCount = classicTitleLineCount,
                metrics = portraitLayoutMetrics
            )
            val homeLayoutSwipeHintAllowed = !nowPlayingHomeLayoutHintDismissed &&
                !homeLayoutHintDismissedInSession &&
                !isVideo
            val portraitContentHorizontalPadding = portraitLayoutMetrics.contentHorizontalPadding
            val homeBezier = remember { CubicBezierEasing(0.20f, 0f, 0f, 1f) }
            val homeLayoutDurationMillis = NowPlayingHomeLayoutAnimationDurationMillis
            val homeFadeInDurationMillis = NowPlayingHomeLyricsFadeInDurationMillis
            val homeFadeOutDurationMillis = NowPlayingHomeLyricsFadeOutDurationMillis
            val expandedHomeLyricsSettings = remember(lyricsPageSettings) {
                lyricsPageSettings.copy(displayAreaMode = 0)
            }
            val portraitContentWidthModifier = if (widthClass == WindowWidthSizeClass.Compact) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
                    .widthIn(max = NowPlayingPortraitMaxContentWidth)
                    .fillMaxWidth()
            }
            val artworkAspectRatio = rememberArtworkAspectRatio(artworkModel)
            val homeLayoutTransition = updateTransition(
                targetState = expandedHomeLayout,
                label = "nowPlayingHomeLayoutMode"
            )
            val showHomeLayoutSwipeHint = homeLayoutSwipeHintAllowed &&
                !homeLayoutTransition.targetState
            val classicAudienceHeight by homeLayoutTransition.animateDp(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeClassicAudienceHeight"
            ) { expanded ->
                if (expanded) 0.dp else portraitLayoutMetrics.audienceHeight
            }
            val classicTrackInfoHeight by homeLayoutTransition.animateDp(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeClassicTrackInfoHeight"
            ) { expanded ->
                if (expanded) 0.dp else classicTrackInfoTargetHeight
            }
            val classicIdentityAlpha by homeLayoutTransition.animateFloat(
                transitionSpec = {
                    tween(
                        durationMillis = if (targetState) homeFadeOutDurationMillis else homeFadeInDurationMillis,
                        easing = if (targetState) FastOutLinearInEasing else LinearOutSlowInEasing
                    )
                },
                label = "nowPlayingHomeClassicIdentityAlpha"
            ) { expanded ->
                if (expanded) 0f else 1f
            }
            val expandedIdentityAlpha by homeLayoutTransition.animateFloat(
                transitionSpec = {
                    tween(
                        durationMillis = if (targetState) homeFadeInDurationMillis else homeFadeOutDurationMillis,
                        easing = if (targetState) LinearOutSlowInEasing else FastOutLinearInEasing
                    )
                },
                label = "nowPlayingHomeExpandedIdentityAlpha"
            ) { expanded ->
                if (expanded) 1f else 0f
            }
            val portraitTopPadding by homeLayoutTransition.animateDp(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeTopPadding"
            ) { expanded ->
                if (expanded) 0.dp else portraitLayoutMetrics.topPadding
            }
            val coverVerticalPadding by homeLayoutTransition.animateDp(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeCoverVerticalPadding"
            ) { expanded ->
                if (expanded) 0.dp else portraitLayoutMetrics.coverVerticalPadding
            }
            val expandedLyricsTopPadding = portraitLayoutMetrics.expandedLyricsTopPadding
            val homeCoverAspectRatio by homeLayoutTransition.animateFloat(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeCoverAspectRatio"
            ) { expanded ->
                if (expanded) artworkAspectRatio else 1f
            }
            val homeCoverCornerRadius by homeLayoutTransition.animateDp(
                transitionSpec = {
                    tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                },
                label = "nowPlayingHomeCoverCornerRadius"
            ) { expanded ->
                if (expanded) 0.dp else 28.dp
            }
            val homeLayoutSettled = homeLayoutTransition.currentState == homeLayoutTransition.targetState
            LaunchedEffect(expandedHomeLayout) {
                if (!homeLayoutSettled) {
                    homeLayoutLyricsVisible = false
                }
            }
            LaunchedEffect(homeLayoutSettled) {
                if (homeLayoutSettled) {
                    homeLayoutLyricsVisible = true
                }
            }
            val expandedLyricsActive = expandedHomeLayout && homeLayoutSettled && homeLayoutLyricsVisible
            val classicLyricsActive = !expandedHomeLayout && homeLayoutSettled && homeLayoutLyricsVisible
            val expandedLyricsAlpha by animateFloatAsState(
                targetValue = if (expandedLyricsActive) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (expandedLyricsActive) homeFadeInDurationMillis else homeFadeOutDurationMillis,
                    easing = if (expandedLyricsActive) LinearOutSlowInEasing else FastOutLinearInEasing
                ),
                label = "nowPlayingHomeExpandedLyricsAlpha"
            )
            val classicLyricsAlpha by animateFloatAsState(
                targetValue = if (classicLyricsActive) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (classicLyricsActive) homeFadeInDurationMillis else homeFadeOutDurationMillis,
                    easing = if (classicLyricsActive) LinearOutSlowInEasing else FastOutLinearInEasing
                ),
                label = "nowPlayingHomeClassicLyricsAlpha"
            )
            val lyricsExpandedInteractionEnabled = expandedLyricsAlpha > 0.5f
            val lyricsClassicInteractionEnabled = classicLyricsAlpha > 0.5f
            // --- 垂直布局 (手机 或 平板竖屏) ---
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds()
                    ) {
                        val portraitTopContentMaxHeight = maxHeight
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = portraitTopPadding),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = portraitContentWidthModifier
                                    .height(classicAudienceHeight)
                                    .clipToBounds()
                                    .graphicsLayer { alpha = classicIdentityAlpha }
                                    .then(coverMotion),
                                contentAlignment = Alignment.Center
                            ) {
                                ListenTogetherAudienceLine(
                                    state = listenTogetherUiState,
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = accentColor,
                                    textColor = colorScheme.textTertiary,
                                    pageEntranceSettled = pageEntranceSettled
                                )
                            }

                            // 封面
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = coverVerticalPadding)
                                    .then(coverMotion)
                                    .nowPlayingHomeLayoutSwipeGesture(
                                        enabled = !isVideo && !pendingRouteExit,
                                        currentMode = nowPlayingHomeLayoutMode,
                                        onModeChange = changeNowPlayingHomeLayoutMode
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                BoxWithConstraints(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val homeCoverWidth by homeLayoutTransition.animateDp(
                                        transitionSpec = {
                                            tween(durationMillis = homeLayoutDurationMillis, easing = homeBezier)
                                        },
                                        label = "nowPlayingHomeCoverWidth"
                                    ) { expanded ->
                                        nowPlayingHomeCoverWidth(
                                            expanded = expanded,
                                            availableWidth = maxWidth,
                                            availableHeight = portraitTopContentMaxHeight,
                                            widthClass = widthClass,
                                            contentHorizontalPadding = portraitContentHorizontalPadding,
                                            coverAspectRatio = if (expanded) artworkAspectRatio else 1f,
                                            topPadding = if (expanded) 0.dp else portraitLayoutMetrics.topPadding,
                                            coverVerticalPadding = if (expanded) 0.dp else portraitLayoutMetrics.coverVerticalPadding,
                                            identityHeight = if (expanded) {
                                                0.dp
                                            } else {
                                                portraitLayoutMetrics.audienceHeight + classicTrackInfoTargetHeight
                                            },
                                            lyricsReserveHeight = if (expanded) {
                                                portraitLayoutMetrics.expandedLyricsReserveHeight
                                            } else {
                                                portraitLayoutMetrics.classicLyricsReserveHeight
                                            },
                                            minimumCoverWidth = portraitLayoutMetrics.minimumCoverWidth
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .then(
                                                if (isVideo) {
                                                    Modifier
                                                        .widthIn(max = if (widthClass == WindowWidthSizeClass.Compact) 1000.dp else 400.dp)
                                                        .fitVideoPreviewAspectRatio(videoAspectRatio)
                                                } else {
                                                    Modifier
                                                        .width(homeCoverWidth)
                                                        .aspectRatio(homeCoverAspectRatio)
                                                }
                                            )
                                            .clip(RoundedCornerShape(homeCoverCornerRadius))
                                    ) {
                                        ArtworkBox(
                                            isVideo = isVideo,
                                            metadata = metadata,
                                            viewModel = viewModel,
                                            videoPlayerCoordinator = videoPlayerCoordinator,
                                            renderVideoSurface = renderVideoSurface,
                                            videoFullscreen = videoFullscreen,
                                            onOpenVideoFullscreen = { videoFullscreen = true },
                                            onOpenLyrics = showLyricsSurface,
                                            edgeBlendEnabled = false,
                                            edgeBlendColor = if (playerArtworkBackdropEnabled) playerThemeColors.backdropTintColor else colorScheme.background,
                                            videoBackdropColor = videoBackdropColor,
                                            artworkAlignment = coverPreviewAlignment,
                                            artworkContentScale = ContentScale.Crop,
                                            artworkCornerRadius = homeCoverCornerRadius,
                                            artworkLoadAtOriginalSize = true,
                                            dragPreviewEnabled = useDragPreview,
                                            dragPreviewState = coverDragPreviewState
                                        )
                                        if (showHomeLayoutSwipeHint) {
                                            NowPlayingHomeLayoutSwipeHint(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 16.dp)
                                            )
                                        }
                                        ExpandedPlayerIdentityOverlay(
                                            title = playerHeaderTitle,
                                            artistMeta = portraitArtistMeta,
                                            listenTogetherState = listenTogetherUiState,
                                            pageEntranceSettled = pageEntranceSettled,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .graphicsLayer { alpha = expandedIdentityAlpha }
                                        )
                                    }
                                }
                            }

                            ClassicPlayerIdentity(
                                title = playerHeaderTitle,
                                artistMeta = portraitArtistMeta,
                                accentColor = playerThemeColors.coverAccentColor,
                                onTitleLineCountChanged = { lineCount ->
                                    if (!expandedHomeLayout && classicTitleLineCount != lineCount) {
                                        classicTitleLineCount = lineCount
                                    }
                                },
                                compactLayout = portraitLayoutMetrics.compact,
                                modifier = portraitContentWidthModifier
                                    .height(classicTrackInfoHeight)
                                    .graphicsLayer { alpha = classicIdentityAlpha }
                                    .then(coverMotion)
                            )

                            if (!isVideo) {
                                Box(
                                    modifier = portraitContentWidthModifier
                                        .weight(1f)
                                        .then(lyricsMotion),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    if (expandedLyricsActive || expandedLyricsAlpha > 0.001f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(top = expandedLyricsTopPadding)
                                                .graphicsLayer { alpha = expandedLyricsAlpha }
                                        ) {
                                            PlaybackProgressContent(viewModel, isVideo) { progress ->
                                                NowPlayingLyricsSurface(
                                                    isLandscape = false,
                                                    playbackPositionMs = progress.positionMs,
                                                    lyrics = lyricsState.lyrics,
                                                    lyricColors = lyricColors,
                                                    accentColor = accentColor,
                                                    onAccentColor = onAccentColor,
                                                    lyricsPageSettings = expandedHomeLyricsSettings,
                                                    onSeekTo = { viewModel.seekTo(it) },
                                                    onTimelinePlay = { targetMs ->
                                                        viewModel.seekTo(targetMs)
                                                        viewModel.play()
                                                    },
                                                    onAddLyrics = openManualLyricsAction,
                                                    interactionEnabled = lyricsExpandedInteractionEnabled,
                                                    stableFocusAnchor = true,
                                                    expandedHomeVisualEffects = true,
                                                    lyricItemOuterHorizontalPadding = 6.dp,
                                                    lyricItemInnerHorizontalPadding = 8.dp,
                                                    contentKey = lyricsState.contentKey,
                                                    contentVisible = !lyricsState.isLoading,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                    if (classicLyricsActive || classicLyricsAlpha > 0.001f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(portraitLayoutMetrics.classicLyricsContainerHeight)
                                                .align(Alignment.BottomCenter)
                                                .padding(horizontal = portraitContentHorizontalPadding)
                                                .padding(bottom = portraitLayoutMetrics.classicLyricsBottomPadding)
                                                .graphicsLayer { alpha = classicLyricsAlpha },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PlaybackProgressContent(viewModel, isVideo) { progress ->
                                                SingleLineLyrics(
                                                    lyrics = lyricsState.lyrics,
                                                    currentPosition = progress.positionMs,
                                                    onOpenLyrics = showLyricsSurface,
                                                    colors = lyricColors,
                                                    interactionEnabled = lyricsClassicInteractionEnabled,
                                                    compactLayout = portraitLayoutMetrics.compact,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Column(
                        modifier = portraitContentWidthModifier
                            .padding(bottom = portraitLayoutMetrics.bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(portraitLayoutMetrics.bottomSectionSpacing)
                    ) {
                        key(item?.mediaId) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = portraitContentHorizontalPadding)
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
                                        inactiveColor = accentColor.copy(alpha = 0.2f),
                                        compactLayout = portraitLayoutMetrics.compact
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
                            modifier = Modifier.padding(horizontal = portraitContentHorizontalPadding),
                            actionRowModifier = actionRowMotion,
                            coreControlsModifier = controlsMotion,
                            primaryColor = accentColor,
                            onPrimaryColor = onAccentColor,
                            compactLayout = portraitLayoutMetrics.compact
                        )

                        VolumeControl(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = portraitContentHorizontalPadding)
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
                            onExpandedChange = { volumeControlExpanded = it },
                            compactLayout = portraitLayoutMetrics.compact
                        )
                    }
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
                        accentColor = accentColor,
                        onAccentColor = onAccentColor,
                        lyricsPageSettings = lyricsPageSettings,
                        onSeekTo = { viewModel.seekTo(it) },
                        onTimelinePlay = { targetMs ->
                            viewModel.seekTo(targetMs)
                            viewModel.play()
                        },
                        onAddLyrics = openManualLyricsAction,
                        contentKey = lyricsState.contentKey,
                        contentVisible = !lyricsState.isLoading,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(routeTransition.nowPlayingMotionModifier(currentMotionLayout, NowPlayingMotionSlot.COVER))
                    )
                }
            }
                }
            }
        }

        if (videoFullscreen && isVideo) {
            BackHandler { videoFullscreen = false }
            NowPlayingFullscreenVideo(
                player = player,
                coordinator = videoPlayerCoordinator,
                onDismiss = { videoFullscreen = false },
                modifier = Modifier.fillMaxSize()
            )
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
                contentColor = colorScheme.onSurface,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = sheetMinHeight)
                        .windowInsetsPadding(WindowInsets.navigationBars)
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
                                .weight(1f, fill = true),
                            flingBehavior = rememberCalmScrollableFlingBehavior(),
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
            val eqSettings by viewModel.sessionEqualizer.collectAsStateWithLifecycle()
            val customPresets by viewModel.customPresets.collectAsStateWithLifecycle()
            val appVolumePercent by viewModel.appVolumePercent.collectAsStateWithLifecycle()
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
                contentColor = colorScheme.onSurface,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.navigationBars)
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
                            .verticalScroll(
                                state = scrollState,
                                flingBehavior = rememberCalmScrollableFlingBehavior()
                            )
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
