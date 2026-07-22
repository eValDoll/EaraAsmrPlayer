package com.asmr.player.ui.player

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.ui.common.rememberCalmScrollableFlingBehavior
import com.asmr.player.ui.common.thinScrollbar
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.Formatting
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleIndexFinder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class LyricFocusVisualEffect(
    val blurDp: Float,
    val dispersionProgress: Float,
    val dispersionOffsetXDp: Float,
    val dispersionOffsetYDp: Float
)

private val NoLyricFocusVisualEffect = LyricFocusVisualEffect(
    blurDp = 0f,
    dispersionProgress = 0f,
    dispersionOffsetXDp = 0f,
    dispersionOffsetYDp = 0f
)

internal fun lyricFocusVisualEffectForLine(
    index: Int,
    activeIndex: Int,
    enabled: Boolean
): LyricFocusVisualEffect {
    if (!enabled || activeIndex < 0 || index == activeIndex) return NoLyricFocusVisualEffect
    val distance = abs(index - activeIndex).coerceAtMost(4)
    val progress = (distance / 4f).coerceIn(0.18f, 1f)
    val direction = if (index < activeIndex) -1f else 1f
    return LyricFocusVisualEffect(
        blurDp = 0.25f + progress * 2.15f,
        dispersionProgress = progress,
        dispersionOffsetXDp = 0.45f + progress * 1.35f,
        dispersionOffsetYDp = direction * (0.2f + progress * 0.8f)
    )
}

internal fun lyricContentKey(lyrics: List<SubtitleEntry>, contentKey: String? = null): Int {
    var result = contentKey?.hashCode() ?: 0
    result = 31 * result + lyrics.size
    lyrics.forEach { entry ->
        result = 31 * result + entry.startMs.hashCode()
        result = 31 * result + entry.endMs.hashCode()
        result = 31 * result + entry.text.hashCode()
    }
    return result
}

internal fun lyricDisplayActiveIndex(activeIndex: Int, totalCount: Int): Int {
    if (totalCount <= 0) return -1
    return activeIndex.coerceIn(0, totalCount - 1)
}

@Composable
internal fun AppleLyricsView(
    lyrics: List<SubtitleEntry>,
    currentPosition: Long,
    onSeekTo: (Long) -> Unit,
    onOpenLyrics: () -> Unit = {},
    onTimelinePlay: ((Long) -> Unit)? = null,
    showPlaybackTimeline: Boolean = false,
    colors: LyricReadableColors,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    settings: LyricsPageSettings = LyricsPageSettings(),
    interactionEnabled: Boolean = true,
    stableFocusAnchor: Boolean = false,
    itemOuterHorizontalPadding: Dp = if (isLandscape) 10.dp else 14.dp,
    itemInnerHorizontalPadding: Dp = if (isLandscape) 8.dp else 10.dp,
    contentKey: String? = null,
    contentVisible: Boolean = true,
    expandedHomeVisualEffects: Boolean = false
) {
    var displayedLyrics by remember { mutableStateOf(lyrics) }
    var displayedContentKey by remember { mutableStateOf(contentKey) }
    var lyricsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(lyrics, contentKey) {
        if (lyrics == displayedLyrics && contentKey == displayedContentKey) {
            lyricsVisible = true
            return@LaunchedEffect
        }
        lyricsVisible = false
        delay(120)
        displayedLyrics = lyrics
        displayedContentKey = contentKey
        lyricsVisible = true
    }

    val renderedLyrics = displayedLyrics
    val renderedContentKey = displayedContentKey
    val lyricsContentKey = remember(renderedLyrics, renderedContentKey) {
        lyricContentKey(renderedLyrics, renderedContentKey)
    }
    val indexFinder = remember(renderedLyrics) { SubtitleIndexFinder(renderedLyrics) }
    val playbackActiveIndex = remember(currentPosition, indexFinder) {
        indexFinder.findActiveIndex(currentPosition)
    }
    val activeIndex = remember(playbackActiveIndex, renderedLyrics.size) {
        lyricDisplayActiveIndex(playbackActiveIndex, renderedLyrics.size)
    }
    val listState = remember(lyricsContentKey) {
        LazyListState(firstVisibleItemIndex = activeIndex.coerceAtLeast(0))
    }
    var pendingInitialFocus by remember(lyricsContentKey) { mutableStateOf(true) }
    val lyricsRenderVisible = contentVisible && lyricsVisible
    val effectiveInteractionEnabled = interactionEnabled && lyricsRenderVisible
    val lyricsContentAlpha by animateFloatAsState(
        targetValue = if (lyricsRenderVisible && !pendingInitialFocus) 1f else 0f,
        animationSpec = tween(durationMillis = if (lyricsRenderVisible) 220 else 120),
        label = "lyricsContentAlpha"
    )
    val density = LocalDensity.current
    val itemOuterVerticalPadding = 0.dp
    val itemInnerVerticalPadding = if (isLandscape) 2.dp else 3.dp
    val fontSize = settings.fontSizeSp.sp
    val wrappedLineHeight = (settings.fontSizeSp * 1.2f).sp
    val fontSizePx = with(density) { fontSize.toPx() }
    val itemSpacingPx = fontSizePx * 0.2f * settings.lineHeightMultiplier.coerceAtLeast(0.1f)
    val nominalItemHeightPx = with(density) {
        wrappedLineHeight.toPx() + itemInnerVerticalPadding.toPx() * 2f + itemOuterVerticalPadding.toPx() * 2f + itemSpacingPx
    }
    val strokeWidthPx = with(density) { settings.strokeWidthSp.sp.toPx() }
    val textAlign = remember(settings.align) { lyricTextAlign(settings.align) }
    val baseLyricTextStyle = MaterialTheme.typography.titleLarge
    val itemOffsets = remember { mutableStateMapOf<Int, Animatable<Float, AnimationVector1D>>() }
    val textMeasurer = rememberTextMeasurer()
    var autoFocusSuspended by remember { mutableStateOf(false) }
    var lastUserScrollAt by remember { mutableLongStateOf(0L) }
    var pendingAnimatedRefocus by remember { mutableStateOf(false) }
    var pendingSmoothFocusIndex by remember { mutableIntStateOf(-1) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    autoFocusSuspended = true
                    lastUserScrollAt = SystemClock.uptimeMillis()
                    pendingAnimatedRefocus = false
                    pendingSmoothFocusIndex = -1
                }
                return Offset.Zero
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val estimatedViewportHeightPx = when (settings.displayAreaMode) {
            1, 2, 3 -> viewportHeightPx * 0.25f
            else -> viewportHeightPx
        }
        val effectiveVisibleLines = calculateRuntimeMaxVisibleLines(
            viewportHeightPx = estimatedViewportHeightPx,
            lineBlockHeightPx = nominalItemHeightPx
        ).coerceAtLeast(1) + 1
        val targetWindowRange = remember(renderedLyrics.size, activeIndex, effectiveVisibleLines) {
            targetLyricsWindowRange(
                totalCount = renderedLyrics.size,
                activeIndex = activeIndex,
                visibleItemCount = effectiveVisibleLines
            )
        }
        val itemTextMaxWidthPx = with(density) {
            (maxWidth - itemOuterHorizontalPadding * 2 - itemInnerHorizontalPadding * 2).toPx().toInt().coerceAtLeast(1)
        }
        val measurementStyle = remember(baseLyricTextStyle, fontSize, wrappedLineHeight, textAlign) {
            baseLyricTextStyle.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                lineHeight = wrappedLineHeight,
                textAlign = textAlign
            )
        }
        val lyricItemHeightsPx = remember(
            renderedLyrics,
            itemTextMaxWidthPx,
            measurementStyle,
            nominalItemHeightPx,
            itemSpacingPx,
            itemInnerVerticalPadding,
            itemOuterVerticalPadding,
            density
        ) {
            val innerVerticalPaddingPx = with(density) { itemInnerVerticalPadding.toPx() }
            val outerVerticalPaddingPx = with(density) { itemOuterVerticalPadding.toPx() }
            renderedLyrics.map { entry ->
                measuredLyricItemHeight(
                    entry = entry,
                    textMeasurer = textMeasurer,
                    measurementStyle = measurementStyle,
                    maxTextWidthPx = itemTextMaxWidthPx,
                    nominalItemHeightPx = nominalItemHeightPx,
                    innerVerticalPaddingPx = innerVerticalPaddingPx,
                    outerVerticalPaddingPx = outerVerticalPaddingPx,
                    itemSpacingPx = itemSpacingPx
                )
            }
        }
        val measuredWindowHeightPx = remember(
            targetWindowRange,
            lyricItemHeightsPx,
            nominalItemHeightPx
        ) {
            measuredWindowHeight(
                targetRange = targetWindowRange,
                lyricItemHeightsPx = lyricItemHeightsPx,
                nominalItemHeightPx = nominalItemHeightPx
            )
        }
        val cumulativeItemOffsetsPx = remember(lyricItemHeightsPx) {
            buildLyricItemOffsets(lyricItemHeightsPx)
        }
        val activeItemHeightPx = remember(
            activeIndex,
            lyricItemHeightsPx,
            nominalItemHeightPx
        ) {
            lyricItemHeightsPx.getOrNull(activeIndex) ?: nominalItemHeightPx
        }
        val waveDurationMillis = 500
        val viewportLayout = remember(settings, viewportHeightPx, nominalItemHeightPx, measuredWindowHeightPx) {
            buildLyricsViewportLayout(
                settings = settings,
                viewportHeightPx = viewportHeightPx,
                nominalItemHeightPx = nominalItemHeightPx,
                measuredWindowHeightPx = measuredWindowHeightPx
            )
        }
        val viewportWindowHeightDp = with(density) { viewportLayout.viewportWindowHeightPx.toDp() }
        val viewportTopOffsetDp = with(density) { viewportLayout.viewportTopOffsetPx.toDp() }
        val centeredActiveBottomDp = viewportWindowHeightDp / 2f
        val edgeFadeHeightPx = if (expandedHomeVisualEffects) {
            with(density) { 40.dp.toPx() }
        } else {
            0f
        }
        val edgeFadeModifier = if (expandedHomeVisualEffects) {
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    if (size.height <= 0f) {
                        return@drawWithCache onDrawWithContent { drawContent() }
                    }
                    val fadeHeight = edgeFadeHeightPx
                        .coerceAtMost(size.height * 0.42f)
                        .coerceAtLeast(1f)
                    val fadeStop = (fadeHeight / size.height).coerceIn(0.01f, 0.48f)
                    val shoulder = (fadeStop * 0.42f).coerceIn(0.005f, fadeStop)
                    val brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            shoulder to Color.White.copy(alpha = 0.42f),
                            fadeStop to Color.White,
                            (1f - fadeStop) to Color.White,
                            (1f - shoulder) to Color.White.copy(alpha = 0.42f),
                            1f to Color.Transparent
                        )
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = brush, blendMode = BlendMode.DstIn)
                    }
                }
        } else {
            Modifier
        }
        val timelineCenterInViewportPx = (viewportHeightPx / 2f - viewportLayout.viewportTopOffsetPx)
            .coerceIn(0f, viewportLayout.viewportWindowHeightPx)
        val maxWaveOffsetPx = viewportLayout.viewportWindowHeightPx * 0.22f
        val timelineTargetIndex by remember(renderedLyrics.size, timelineCenterInViewportPx, listState) {
            derivedStateOf {
                centeredLyricIndexForTimeline(
                    visibleItems = listState.layoutInfo.visibleItemsInfo.map { item ->
                        LyricVisibleItemFrame(
                            index = item.index,
                            offsetPx = item.offset,
                            sizePx = item.size
                        )
                    },
                    viewportCenterPx = timelineCenterInViewportPx,
                    totalCount = renderedLyrics.size
                )
            }
        }
        val timelineTargetPositionMs = renderedLyrics.getOrNull(timelineTargetIndex)?.startMs
        val timelineVisible = showPlaybackTimeline && renderedLyrics.isNotEmpty() && lyricsRenderVisible && autoFocusSuspended
        LaunchedEffect(lastUserScrollAt, autoFocusSuspended) {
            if (autoFocusSuspended) {
                val scheduledAt = lastUserScrollAt
                delay(2000)
                if (autoFocusSuspended && lastUserScrollAt == scheduledAt && !listState.isScrollInProgress) {
                    autoFocusSuspended = false
                    pendingAnimatedRefocus = true
                }
            }
        }

        LaunchedEffect(lyricsContentKey) {
            pendingSmoothFocusIndex = -1
            autoFocusSuspended = false
            pendingAnimatedRefocus = false
            itemOffsets.clear()
        }

        LaunchedEffect(
            activeIndex,
            autoFocusSuspended,
            lyricItemHeightsPx,
            viewportLayout,
            pendingSmoothFocusIndex,
            stableFocusAnchor,
            lyricsContentKey,
            lyricsRenderVisible
        ) {
            if (!lyricsRenderVisible) return@LaunchedEffect
            if (renderedLyrics.isEmpty() || activeIndex < 0 || autoFocusSuspended) {
                if (pendingInitialFocus && (renderedLyrics.isEmpty() || activeIndex < 0)) {
                    pendingInitialFocus = false
                }
                return@LaunchedEffect
            }

            var didReposition = false
            var repositionDeltaPx: Float? = null
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val activeItemInfo = visibleItems.firstOrNull { it.index == activeIndex }
            val activeTopPx = activeItemInfo?.offset?.toFloat()
            val activeFocusHeightPx = if (stableFocusAnchor) {
                nominalItemHeightPx
            } else {
                activeItemInfo?.size?.toFloat() ?: activeItemHeightPx
            }
            val centeredActiveTopPx = centeredLyricFocusTop(
                viewportWindowHeightPx = viewportLayout.viewportWindowHeightPx,
                activeItemHeightPx = activeFocusHeightPx,
                nominalItemHeightPx = nominalItemHeightPx,
                stableFocusAnchor = false
            )
            val targetScrollOffset = -centeredActiveTopPx.roundToInt()
            val isPinnedAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            val isInitialFocus = pendingInitialFocus
            val shouldUseSmoothFocus = !pendingInitialFocus && (
                stableFocusAnchor ||
                    pendingAnimatedRefocus ||
                    pendingSmoothFocusIndex == activeIndex
                )
            val defaultAffectedIndices = (
                visibleItems.map { it.index } +
                    targetWindowRange.toList()
                ).distinct().sorted()

            if (activeTopPx != null) {
                if (!(isPinnedAtTop && activeTopPx <= centeredActiveTopPx)) {
                    val desiredTopPx = centeredActiveTopPx
                    if (kotlin.math.abs(activeTopPx - desiredTopPx) > 1f) {
                        repositionDeltaPx = activeTopPx - desiredTopPx
                        if (shouldUseSmoothFocus) {
                            listState.animateLyricFocusToIndex(
                                index = activeIndex,
                                anchorOffsetPx = targetScrollOffset,
                                cumulativeItemOffsetsPx = cumulativeItemOffsetsPx,
                                viewportHeightPx = viewportLayout.viewportWindowHeightPx
                            )
                        } else {
                            listState.scrollToItem(activeIndex, targetScrollOffset)
                        }
                        didReposition = true
                    }
                }
            } else {
                if (shouldUseSmoothFocus) {
                    listState.animateLyricFocusToIndex(
                        index = activeIndex,
                        anchorOffsetPx = targetScrollOffset,
                        cumulativeItemOffsetsPx = cumulativeItemOffsetsPx,
                        viewportHeightPx = viewportLayout.viewportWindowHeightPx
                    )
                } else {
                    listState.scrollToItem(activeIndex, targetScrollOffset)
                }
                didReposition = true
            }

            val resolvedWave = if (didReposition && !shouldUseSmoothFocus && !isInitialFocus) {
                repositionDeltaPx?.coerceIn(-maxWaveOffsetPx, maxWaveOffsetPx)
            } else {
                null
            }

            if (resolvedWave != null) {
                resetLyricWaveOffsets(
                    itemOffsets = itemOffsets,
                    indices = defaultAffectedIndices
                )
                defaultAffectedIndices.forEach { index ->
                    val anim = itemOffsets.getOrPut(index) { Animatable(0f) }
                    anim.snapTo(resolvedWave)
                }
            } else {
                resetLyricWaveOffsets(
                    itemOffsets = itemOffsets,
                    indices = defaultAffectedIndices
                )
            }

            if (didReposition && resolvedWave != null) {
                val firstVisible = defaultAffectedIndices.minOrNull() ?: 0
                defaultAffectedIndices.forEach { index ->
                    val anim = itemOffsets[index] ?: return@forEach
                    val rowDelay = ((index - firstVisible) * 40).coerceAtLeast(0)
                    launch {
                        anim.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = waveDurationMillis,
                                delayMillis = rowDelay,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                }
            }

            pendingAnimatedRefocus = false
            if (pendingSmoothFocusIndex == activeIndex) {
                pendingSmoothFocusIndex = -1
            }
            pendingInitialFocus = false
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewportWindowHeightDp)
                .offset(y = viewportTopOffsetDp)
                .then(
                    if (expandedHomeVisualEffects) {
                        Modifier.graphicsLayer { clip = false }
                    } else {
                        Modifier.clip(RoundedCornerShape(12.dp))
                    }
                )
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(edgeFadeModifier)
                    .then(if (expandedHomeVisualEffects) Modifier.graphicsLayer { clip = false } else Modifier)
                    .graphicsLayer { alpha = lyricsContentAlpha }
                    .then(if (effectiveInteractionEnabled) Modifier.nestedScroll(nestedScrollConnection) else Modifier)
                    .thinScrollbar(listState),
                flingBehavior = rememberCalmScrollableFlingBehavior(),
                userScrollEnabled = effectiveInteractionEnabled,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    bottom = centeredActiveBottomDp
                )
            ) {
                itemsIndexed(
                    items = renderedLyrics,
                    key = { index, entry -> lyricItemKey(index, entry) },
                    contentType = { _, _ -> "appleLyricLine" }
                ) { index, entry ->
                    val reservedItemHeightDp = with(density) {
                        (lyricItemHeightsPx.getOrNull(index) ?: nominalItemHeightPx).toDp()
                    }
                    val isActive = index == activeIndex
                    val scale by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = tween(400),
                        label = "lyricScale"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isActive) 1f else if (AsmrTheme.colorScheme.isDark) 0.76f else 0.72f,
                        animationSpec = tween(400),
                        label = "lyricAlpha"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isActive) colors.activeText else colors.inactiveText,
                        animationSpec = tween(400),
                        label = "lyricColor"
                    )
                    val shadow = if (isActive) {
                        Shadow(
                            color = colors.accentEmphasis.copy(alpha = if (AsmrTheme.colorScheme.isDark) 0.40f else 0.24f),
                            offset = Offset.Zero,
                            blurRadius = if (isLandscape) 14f else 18f
                        )
                    } else {
                        null
                    }
                    val focusEffect = lyricFocusVisualEffectForLine(
                        index = index,
                        activeIndex = activeIndex,
                        enabled = expandedHomeVisualEffects
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(reservedItemHeightDp)
                            .padding(
                                start = itemOuterHorizontalPadding,
                                top = itemOuterVerticalPadding,
                                end = itemOuterHorizontalPadding,
                                bottom = itemOuterVerticalPadding + with(density) { itemSpacingPx.toDp() }
                            )
                            .graphicsLayer {
                                this.translationY = itemOffsets[index]?.value ?: 0f
                                this.scaleX = scale
                                this.scaleY = scale
                                this.alpha = alpha * (1f - focusEffect.dispersionProgress * 0.08f)
                            }
                            .then(
                                if (focusEffect.blurDp > 0f) {
                                    Modifier.blur(focusEffect.blurDp.dp)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(enabled = effectiveInteractionEnabled) {
                                pendingSmoothFocusIndex = if (activeIndex >= 0 && activeIndex != index) index else -1
                                autoFocusSuspended = false
                                pendingAnimatedRefocus = false
                                onSeekTo(entry.startMs)
                                onOpenLyrics()
                            }
                            .padding(horizontal = itemInnerHorizontalPadding, vertical = itemInnerVerticalPadding)
                    ) {
                        val shadowColor = remember(color) { lyricShadowColor(color) }
                        LyricLineText(
                            text = entry.text,
                            color = color,
                            shadowColor = shadowColor,
                            strokeWidthPx = strokeWidthPx,
                            dispersionProgress = focusEffect.dispersionProgress,
                            dispersionOffsetX = focusEffect.dispersionOffsetXDp.dp,
                            dispersionOffsetY = focusEffect.dispersionOffsetYDp.dp,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                fontSize = fontSize,
                                lineHeight = wrappedLineHeight,
                                textAlign = textAlign,
                                shadow = shadow
                            ),
                            textAlign = textAlign
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = timelineVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 140)),
            exit = fadeOut(animationSpec = tween(durationMillis = 180)),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(
                    start = if (isLandscape) 12.dp else 18.dp,
                    end = if (isLandscape) 8.dp else 14.dp
                )
        ) {
            LyricsPlaybackTimeline(
                targetPositionMs = timelineTargetPositionMs,
                colors = colors,
                isLandscape = isLandscape,
                onPlayClick = { targetMs ->
                    if (timelineTargetIndex in renderedLyrics.indices) {
                        pendingSmoothFocusIndex = timelineTargetIndex
                    }
                    autoFocusSuspended = false
                    pendingAnimatedRefocus = false
                    (onTimelinePlay ?: onSeekTo)(targetMs)
                }
            )
        }
    }
}

private fun targetLyricsWindowRange(
    totalCount: Int,
    activeIndex: Int,
    visibleItemCount: Int
): IntRange {
    if (totalCount <= 0 || visibleItemCount <= 0) return IntRange.EMPTY
    val safeActiveIndex = activeIndex.coerceIn(0, totalCount - 1)
    val itemsAbove = (visibleItemCount - 1) / 2
    val itemsBelow = visibleItemCount - 1 - itemsAbove
    var start = (safeActiveIndex - itemsAbove).coerceAtLeast(0)
    var end = (safeActiveIndex + itemsBelow).coerceAtMost(totalCount - 1)
    val missing = visibleItemCount - (end - start + 1)
    if (missing > 0) {
        start = (start - missing).coerceAtLeast(0)
        end = (start + visibleItemCount - 1).coerceAtMost(totalCount - 1)
    }
    return start..end
}

private fun measuredWindowHeight(
    targetRange: IntRange,
    lyricItemHeightsPx: List<Float>,
    nominalItemHeightPx: Float
): Float {
    if (targetRange.isEmpty()) return 0f
    return targetRange.sumOf { index ->
        lyricItemHeightsPx.getOrNull(index)?.toDouble() ?: nominalItemHeightPx.toDouble()
    }.toFloat()
}

private fun buildLyricItemOffsets(
    lyricItemHeightsPx: List<Float>
): List<Float> {
    if (lyricItemHeightsPx.isEmpty()) return emptyList()
    val offsets = ArrayList<Float>(lyricItemHeightsPx.size)
    var cumulative = 0f
    lyricItemHeightsPx.forEach { height ->
        offsets += cumulative
        cumulative += height
    }
    return offsets
}

private suspend fun androidx.compose.foundation.lazy.LazyListState.animateLyricFocusToIndex(
    index: Int,
    anchorOffsetPx: Int,
    cumulativeItemOffsetsPx: List<Float>,
    viewportHeightPx: Float
) {
    if (index !in cumulativeItemOffsetsPx.indices) return
    runCatching {
        val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull()
        val currentScrollPx = if (firstVisible != null) {
            cumulativeItemOffsetsPx.getOrNull(firstVisible.index).orElseZero() - firstVisible.offset
        } else {
            cumulativeItemOffsetsPx.getOrNull(firstVisibleItemIndex).orElseZero() + firstVisibleItemScrollOffset
        }
        val targetScrollPx = cumulativeItemOffsetsPx[index] + anchorOffsetPx
        val totalDistancePx = targetScrollPx - currentScrollPx
        if (abs(totalDistancePx) <= 1f) return@runCatching

        val durationMillis = focusScrollDurationMillis(
            distancePx = totalDistancePx,
            viewportHeightPx = viewportHeightPx
        )
        val durationNanos = durationMillis * 1_000_000L
        val startTime = withFrameNanos { it }
        var previousOffsetPx = 0f

        while (true) {
            val frameTime = withFrameNanos { it }
            val rawFraction = ((frameTime - startTime).toDouble() / durationNanos.toDouble()).toFloat()
            val fraction = rawFraction.coerceIn(0f, 1f)
            val easedOffsetPx = totalDistancePx * FastOutSlowInEasing.transform(fraction)
            val deltaPx = easedOffsetPx - previousOffsetPx
            previousOffsetPx = easedOffsetPx
            if (abs(deltaPx) > 0f) {
                scrollBy(deltaPx)
            }
            if (fraction >= 1f) break
        }

        val remainingPx = totalDistancePx - previousOffsetPx
        if (abs(remainingPx) > 0.5f) {
            scrollBy(remainingPx)
        }
    }.onFailure {
        runCatching { scrollToItem(index = index, scrollOffset = anchorOffsetPx) }
    }
}

private fun focusScrollDurationMillis(
    distancePx: Float,
    viewportHeightPx: Float
): Int {
    val normalizedDistance = if (viewportHeightPx > 0f) {
        abs(distancePx) / viewportHeightPx
    } else {
        1f
    }
    return (420 + normalizedDistance * 180f).roundToInt().coerceIn(360, 760)
}

private fun Float?.orElseZero(): Float = this ?: 0f

@Composable
private fun LyricsPlaybackTimeline(
    targetPositionMs: Long?,
    colors: LyricReadableColors,
    isLandscape: Boolean,
    onPlayClick: (Long) -> Unit
) {
    val enabled = targetPositionMs != null
    val buttonSize = if (isLandscape) 34.dp else 40.dp
    val iconSize = if (isLandscape) 19.dp else 22.dp
    val timelineColor = colors.accentEmphasis.copy(alpha = if (enabled) 0.82f else 0.36f)
    val containerColor = colors.activeContainer.copy(alpha = if (enabled) 0.96f else 0.54f)
    val contentColor = colors.activeText.copy(alpha = if (enabled) 0.94f else 0.46f)
    val targetText = targetPositionMs?.let { Formatting.formatTrackTime(it) } ?: "--:--"
    val description = targetPositionMs?.let { "跳转到 ${Formatting.formatTrackTime(it)} 播放" } ?: "跳转播放"
    val density = LocalDensity.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isLandscape) 36.dp else 44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(if (isLandscape) 18.dp else 20.dp)
        ) {
            val strokeWidth = with(density) { 1.dp.toPx() }
            val dash = with(density) { 6.dp.toPx() }
            val gap = with(density) { 5.dp.toPx() }
            drawLine(
                color = timelineColor,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(dash, gap),
                    phase = 0f
                )
            )
        }
        Spacer(modifier = Modifier.width(if (isLandscape) 6.dp else 8.dp))
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = if (enabled) 2.dp else 0.dp
        ) {
            Text(
                text = targetText,
                modifier = Modifier.padding(
                    horizontal = if (isLandscape) 8.dp else 10.dp,
                    vertical = if (isLandscape) 4.dp else 5.dp
                ),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
        }
        Spacer(modifier = Modifier.width(if (isLandscape) 6.dp else 8.dp))
        Surface(
            modifier = Modifier.size(buttonSize),
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = if (enabled) 3.dp else 0.dp
        ) {
            IconButton(
                onClick = {
                    targetPositionMs?.let(onPlayClick)
                },
                enabled = enabled,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = description,
                    modifier = Modifier.size(iconSize),
                    tint = contentColor
                )
            }
        }
    }
}

private suspend fun resetLyricWaveOffsets(
    itemOffsets: Map<Int, Animatable<Float, AnimationVector1D>>,
    indices: Collection<Int>
) {
    indices.forEach { index ->
        val anim = itemOffsets[index] ?: return@forEach
        anim.stop()
        if (anim.value != 0f) {
            anim.snapTo(0f)
        }
    }
}

private fun measuredLyricItemHeight(
    entry: SubtitleEntry?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    measurementStyle: TextStyle,
    maxTextWidthPx: Int,
    nominalItemHeightPx: Float,
    innerVerticalPaddingPx: Float,
    outerVerticalPaddingPx: Float,
    itemSpacingPx: Float
): Float {
    if (entry == null) return nominalItemHeightPx
    val textLayout = textMeasurer.measure(
        text = AnnotatedString(entry.text),
        style = measurementStyle,
        constraints = Constraints(maxWidth = maxTextWidthPx)
    )
    return textLayout.size.height +
        innerVerticalPaddingPx * 2f +
        outerVerticalPaddingPx * 2f +
        itemSpacingPx
}

private fun lyricShadowColor(textColor: Color): Color {
    return if (textColor.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.9f)
    } else {
        Color.White.copy(alpha = 0.9f)
    }
}

@Composable
private fun LyricLineText(
    text: String,
    color: Color,
    shadowColor: Color,
    strokeWidthPx: Float,
    dispersionProgress: Float,
    dispersionOffsetX: Dp,
    dispersionOffsetY: Dp,
    style: TextStyle,
    textAlign: TextAlign
) {
    val effectiveShadow = remember(style.shadow, shadowColor, strokeWidthPx) {
        lyricTextShadow(
            baseShadow = style.shadow,
            shadowColor = shadowColor,
            shadowStrengthPx = strokeWidthPx
        )
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        if (dispersionProgress > 0.01f) {
            val ghostStyle = style.copy(shadow = null)
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = -dispersionOffsetX, y = -dispersionOffsetY),
                style = ghostStyle,
                color = Color(0xFFFF5D7A).copy(alpha = 0.16f * dispersionProgress),
                textAlign = textAlign
            )
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = dispersionOffsetX, y = dispersionOffsetY),
                style = ghostStyle,
                color = Color(0xFF42E8FF).copy(alpha = 0.18f * dispersionProgress),
                textAlign = textAlign
            )
        }
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = style.copy(shadow = effectiveShadow),
            color = color,
            textAlign = textAlign
        )
    }
}

private fun lyricTextShadow(
    baseShadow: Shadow?,
    shadowColor: Color,
    shadowStrengthPx: Float
): Shadow? {
    val lyricShadow = if (shadowStrengthPx > 0f) {
        Shadow(
            color = shadowColor.copy(alpha = 0.95f),
            offset = Offset.Zero,
            blurRadius = shadowStrengthPx * 2.4f
        )
    } else {
        null
    }
    return when {
        baseShadow == null -> lyricShadow
        lyricShadow == null -> baseShadow
        else -> Shadow(
            color = lyricShadow.color,
            offset = baseShadow.offset,
            blurRadius = maxOf(baseShadow.blurRadius, lyricShadow.blurRadius)
        )
    }
}
