package com.asmr.player.ui.player

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.SubtitleEntry
import com.asmr.player.util.SubtitleIndexFinder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
internal fun AppleLyricsView(
    lyrics: List<SubtitleEntry>,
    currentPosition: Long,
    onSeekTo: (Long) -> Unit,
    onOpenLyrics: () -> Unit = {},
    colors: LyricReadableColors,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false,
    settings: LyricsPageSettings = LyricsPageSettings()
) {
    val indexFinder = remember(lyrics) { SubtitleIndexFinder(lyrics) }
    val activeIndex = remember(currentPosition, indexFinder) {
        indexFinder.findActiveIndex(currentPosition)
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val itemOuterVerticalPadding = 0.dp
    val itemInnerVerticalPadding = if (isLandscape) 2.dp else 3.dp
    val itemOuterHorizontalPadding = if (isLandscape) 10.dp else 14.dp
    val itemInnerHorizontalPadding = if (isLandscape) 8.dp else 10.dp
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
    var previousActiveIndex by remember { mutableIntStateOf(activeIndex) }
    var autoFocusSuspended by remember { mutableStateOf(false) }
    var lastUserScrollAt by remember { mutableLongStateOf(0L) }
    var pendingAnimatedRefocus by remember { mutableStateOf(false) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) {
                    autoFocusSuspended = true
                    lastUserScrollAt = SystemClock.uptimeMillis()
                    pendingAnimatedRefocus = false
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
        val targetWindowRange = remember(lyrics.size, activeIndex, effectiveVisibleLines) {
            targetLyricsWindowRange(
                totalCount = lyrics.size,
                activeIndex = activeIndex,
                visibleItemCount = effectiveVisibleLines
            )
        }
        val itemTextMaxWidthPx = with(density) {
            (maxWidth - itemOuterHorizontalPadding * 2 - itemInnerHorizontalPadding * 2).toPx().toInt().coerceAtLeast(1)
        }
        val measurementStyle = remember(baseLyricTextStyle, fontSize, wrappedLineHeight, textAlign) {
            baseLyricTextStyle.copy(
                fontWeight = FontWeight.Bold,
                fontSize = fontSize,
                lineHeight = wrappedLineHeight,
                textAlign = textAlign
            )
        }
        val lyricItemHeightsPx = remember(
            lyrics,
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
            lyrics.map { entry ->
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
        val centeredActiveTopPx = ((viewportLayout.viewportWindowHeightPx - activeItemHeightPx) / 2f).coerceAtLeast(0f)
        val centeredActiveBottomDp = viewportWindowHeightDp / 2f
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

        LaunchedEffect(activeIndex, autoFocusSuspended) {
            if (lyrics.isEmpty() || activeIndex < 0 || autoFocusSuspended) return@LaunchedEffect

            val targetScrollOffset = -centeredActiveTopPx.roundToInt()
            var didReposition = false
            val indexDelta = activeIndex - previousActiveIndex
            val shouldPlayWave = indexDelta != 0 && indexDelta.absoluteValue <= 2
            val movementDeltaPx = if (shouldPlayWave) {
                accumulatedCenterShiftPx(
                    lyricItemHeightsPx = lyricItemHeightsPx,
                    fromIndex = previousActiveIndex,
                    toIndex = activeIndex,
                    nominalItemHeightPx = nominalItemHeightPx
                )
            } else {
                0f
            }
            val affectedIndices = if (shouldPlayWave) {
                (
                    listState.layoutInfo.visibleItemsInfo.map { it.index } +
                        targetLyricsWindowRange(
                            totalCount = lyrics.size,
                            activeIndex = activeIndex,
                            visibleItemCount = effectiveVisibleLines
                        ).toList()
                    ).distinct().sorted()
            } else {
                emptyList()
            }

            if (shouldPlayWave) {
                affectedIndices.forEach { index ->
                    val anim = itemOffsets.getOrPut(index) { Animatable(0f) }
                    anim.snapTo(movementDeltaPx)
                }
            }

            if (listState.layoutInfo.visibleItemsInfo.any { it.index == activeIndex }) {
                val activeItemInfo = listState.layoutInfo.visibleItemsInfo.first { it.index == activeIndex }
                val activeTopPx = activeItemInfo.offset.toFloat()
                val isPinnedAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (!(isPinnedAtTop && activeTopPx <= centeredActiveTopPx)) {
                    val desiredTopPx = centeredActiveTopPx
                    if (kotlin.math.abs(activeTopPx - desiredTopPx) > 1f) {
                        if (pendingAnimatedRefocus) {
                            listState.animateScrollToItem(activeIndex, targetScrollOffset)
                        } else {
                            listState.scrollToItem(activeIndex, targetScrollOffset)
                        }
                        didReposition = true
                    }
                }
            } else {
                if (pendingAnimatedRefocus) {
                    listState.animateScrollToItem(activeIndex, targetScrollOffset)
                } else {
                    listState.scrollToItem(activeIndex, targetScrollOffset)
                }
                didReposition = true
            }

            if (didReposition && shouldPlayWave) {
                val firstVisible = affectedIndices.minOrNull() ?: 0
                affectedIndices.forEach { index ->
                    val anim = itemOffsets[index] ?: return@forEach
                    val rowDelay = (index - firstVisible) * 40
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
            previousActiveIndex = activeIndex
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewportWindowHeightDp)
                .offset(y = viewportTopOffsetDp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    bottom = centeredActiveBottomDp
                )
            ) {
                itemsIndexed(
                    items = lyrics,
                    key = { _, entry -> entry.startMs },
                    contentType = { _, _ -> "appleLyricLine" }
                ) { index, entry ->
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
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
                                this.alpha = alpha
                            }
                            .clickable {
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

private fun accumulatedCenterShiftPx(
    lyricItemHeightsPx: List<Float>,
    fromIndex: Int,
    toIndex: Int,
    nominalItemHeightPx: Float
): Float {
    if (fromIndex == toIndex) return 0f
    val step = if (toIndex > fromIndex) 1 else -1
    var distancePx = 0f
    var currentIndex = fromIndex
    while (currentIndex != toIndex) {
        val nextIndex = currentIndex + step
        val currentHeight = lyricItemHeightsPx.getOrNull(currentIndex) ?: nominalItemHeightPx
        val nextHeight = lyricItemHeightsPx.getOrNull(nextIndex) ?: nominalItemHeightPx
        distancePx += (currentHeight + nextHeight) / 2f * step
        currentIndex = nextIndex
    }
    return distancePx
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
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = style.copy(shadow = effectiveShadow),
        color = color,
        textAlign = textAlign
    )
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
