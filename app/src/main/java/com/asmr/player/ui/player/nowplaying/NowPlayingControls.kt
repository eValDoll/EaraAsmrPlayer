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
                    .padding(horizontal = 16.dp)
                    .then(actionRowModifier),
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
            modifier = Modifier
                .fillMaxWidth()
                .then(coreControlsModifier),
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


@Composable
internal fun SingleLineLyrics(
    lyrics: List<SubtitleEntry>,
    currentPosition: Long,
    onOpenLyrics: () -> Unit,
    colors: LyricReadableColors,
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedLyricLine(
            text = currentText,
            durationMs = lineDuration,
            colors = colors,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.accentEmphasis.copy(alpha = if (AsmrTheme.colorScheme.isDark) 0.72f else 0.56f))
        )
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AnimatedLyricLine(
    text: String,
    durationMs: Long,
    colors: LyricReadableColors,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = slideInVertically(animationSpec = tween(600)) { it } + fadeIn(animationSpec = tween(600)),
                initialContentExit = slideOutVertically(animationSpec = tween(600)) { -it } + fadeOut(animationSpec = tween(600)),
                sizeTransform = null
            )
        },
        label = "lyricLine"
    ) { target ->
        SlowMarqueeText(
            text = target,
            durationMs = durationMs,
            style = MaterialTheme.typography.titleMedium,
            colors = colors,
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
    colors: LyricReadableColors,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    val singleLine = remember(text) { normalizeSingleLineText(text) }
    val content = singleLine.ifBlank { " " }
    val colorScheme = AsmrTheme.colorScheme
    val shadow = Shadow(
        color = colors.accentEmphasis.copy(alpha = if (colorScheme.isDark) 0.28f else 0.18f),
        offset = Offset.Zero,
        blurRadius = if (colorScheme.isDark) 14f else 10f
    )
    
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
            color = colors.activeText,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp)
                .basicMarquee(
                    iterations = Int.MAX_VALUE,
                    velocity = finalVelocity.coerceAtLeast(10.dp)
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
internal fun VolumeControl(
    modifier: Modifier = Modifier,
    accentColor: Color,
    viewModel: PlayerViewModel,
    hardwareVolumeEventTick: Long,
    audioOutputRouteKind: AudioOutputRouteKind,
    warningSessionState: AppVolumeWarningSessionState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val volume by viewModel.appVolumePercent.collectAsState()
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
                    .padding(vertical = 6.dp)
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
                    AudioOutputRouteIcon(
                        routeKind = audioOutputRouteKind,
                        isMuted = isMuted,
                        tint = accentColor,
                        modifier = Modifier
                            .size(20.dp)
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
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "音量",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.textTertiary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${AppVolume.clampPercent(volume)}%",
                        style = MaterialTheme.typography.bodySmall,
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

