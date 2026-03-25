package com.asmr.player.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.ceil

private data class ThinScrollbarMetrics(
    val offsetFraction: Float,
    val thumbFraction: Float,
)

@Composable
fun Modifier.thinScrollbar(
    state: LazyListState,
    thickness: Dp = 3.dp,
    endPadding: Dp = 3.dp,
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 8.dp,
    minThumbLength: Dp = 32.dp,
): Modifier {
    val metrics = state.thinScrollbarMetrics()
    val canScroll = state.canScrollBackward || state.canScrollForward
    val scrollbarColor = AsmrTheme.colorScheme.textSecondary
    val alpha by animateFloatAsState(
        targetValue = when {
            metrics == null || !canScroll -> 0f
            state.isScrollInProgress -> 0.72f
            else -> 0.34f
        },
        animationSpec = tween(durationMillis = 180),
        label = "lazyListThinScrollbarAlpha"
    )
    return drawThinScrollbar(
        metrics = metrics,
        color = scrollbarColor,
        alpha = alpha,
        thickness = thickness,
        endPadding = endPadding,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        minThumbLength = minThumbLength
    )
}

@Composable
fun Modifier.thinScrollbar(
    state: LazyStaggeredGridState,
    thickness: Dp = 3.dp,
    endPadding: Dp = 3.dp,
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 8.dp,
    minThumbLength: Dp = 32.dp,
): Modifier {
    val metrics = state.thinScrollbarMetrics()
    val canScroll = state.canScrollBackward || state.canScrollForward
    val scrollbarColor = AsmrTheme.colorScheme.textSecondary
    val alpha by animateFloatAsState(
        targetValue = when {
            metrics == null || !canScroll -> 0f
            state.isScrollInProgress -> 0.72f
            else -> 0.34f
        },
        animationSpec = tween(durationMillis = 180),
        label = "lazyGridThinScrollbarAlpha"
    )
    return drawThinScrollbar(
        metrics = metrics,
        color = scrollbarColor,
        alpha = alpha,
        thickness = thickness,
        endPadding = endPadding,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        minThumbLength = minThumbLength
    )
}

@Composable
fun Modifier.thinScrollbar(
    state: ScrollState,
    thickness: Dp = 3.dp,
    endPadding: Dp = 3.dp,
    topPadding: Dp = 8.dp,
    bottomPadding: Dp = 8.dp,
    minThumbLength: Dp = 32.dp,
): Modifier {
    val metrics = state.thinScrollbarMetrics()
    val canScroll = state.canScrollBackward || state.canScrollForward
    val scrollbarColor = AsmrTheme.colorScheme.textSecondary
    val alpha by animateFloatAsState(
        targetValue = when {
            metrics == null || !canScroll -> 0f
            state.isScrollInProgress -> 0.72f
            else -> 0.34f
        },
        animationSpec = tween(durationMillis = 180),
        label = "scrollStateThinScrollbarAlpha"
    )
    return drawThinScrollbar(
        metrics = metrics,
        color = scrollbarColor,
        alpha = alpha,
        thickness = thickness,
        endPadding = endPadding,
        topPadding = topPadding,
        bottomPadding = bottomPadding,
        minThumbLength = minThumbLength
    )
}

private fun Modifier.drawThinScrollbar(
    metrics: ThinScrollbarMetrics?,
    color: androidx.compose.ui.graphics.Color,
    alpha: Float,
    thickness: Dp,
    endPadding: Dp,
    topPadding: Dp,
    bottomPadding: Dp,
    minThumbLength: Dp,
): Modifier {
    return then(
        Modifier.drawWithContent {
            drawContent()
            val resolvedMetrics = metrics ?: return@drawWithContent
            if (alpha <= 0f) return@drawWithContent

            val resolvedColor = color.copy(alpha = alpha)
            val barWidth = thickness.toPx()
            val barX = size.width - endPadding.toPx() - barWidth
            if (barWidth <= 0f || barX < 0f) return@drawWithContent

            val trackTop = topPadding.toPx()
            val trackBottom = size.height - bottomPadding.toPx()
            val trackHeight = (trackBottom - trackTop).coerceAtLeast(0f)
            if (trackHeight <= 0f) return@drawWithContent

            val thumbHeight = (trackHeight * resolvedMetrics.thumbFraction)
                .coerceIn(minThumbLength.toPx(), trackHeight)
            val thumbOffsetY = trackTop + (trackHeight - thumbHeight) * resolvedMetrics.offsetFraction

            drawRoundRect(
                color = resolvedColor,
                topLeft = Offset(barX, thumbOffsetY),
                size = Size(barWidth, thumbHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    )
}

private fun LazyListState.thinScrollbarMetrics(): ThinScrollbarMetrics? {
    val layoutInfo = layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems <= 0 || visibleItems.isEmpty()) return null

    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
        .coerceAtLeast(1f)
    val averageItemSize = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
    val estimatedContentHeight =
        averageItemSize * totalItems + layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
    if (estimatedContentHeight <= viewportHeight) return null

    val estimatedScrollOffset = firstVisibleItemIndex * averageItemSize + firstVisibleItemScrollOffset
    return buildThinScrollbarMetrics(
        scrollOffset = estimatedScrollOffset,
        viewportHeight = viewportHeight,
        contentHeight = estimatedContentHeight
    )
}

private fun LazyStaggeredGridState.thinScrollbarMetrics(): ThinScrollbarMetrics? {
    val layoutInfo = layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo
    if (totalItems <= 0 || visibleItems.isEmpty()) return null

    val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
        .coerceAtLeast(1f)
    val averageItemHeight = visibleItems.sumOf { it.size.height }.toFloat() / visibleItems.size
    val laneCount = visibleItems.map { it.offset.x }.distinct().size.coerceAtLeast(1)
    val estimatedRowCount = ceil(totalItems / laneCount.toFloat())
    val estimatedContentHeight =
        averageItemHeight * estimatedRowCount + layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
    if (estimatedContentHeight <= viewportHeight) return null

    val estimatedScrollOffset =
        (firstVisibleItemIndex / laneCount.toFloat()) * averageItemHeight + firstVisibleItemScrollOffset
    return buildThinScrollbarMetrics(
        scrollOffset = estimatedScrollOffset,
        viewportHeight = viewportHeight,
        contentHeight = estimatedContentHeight
    )
}

private fun ScrollState.thinScrollbarMetrics(): ThinScrollbarMetrics? {
    val viewportHeight = viewportSize.toFloat().coerceAtLeast(1f)
    val contentHeight = viewportHeight + maxValue
    if (contentHeight <= viewportHeight) return null
    return buildThinScrollbarMetrics(
        scrollOffset = value.toFloat(),
        viewportHeight = viewportHeight,
        contentHeight = contentHeight
    )
}

private fun buildThinScrollbarMetrics(
    scrollOffset: Float,
    viewportHeight: Float,
    contentHeight: Float,
): ThinScrollbarMetrics {
    val resolvedContentHeight = contentHeight.coerceAtLeast(viewportHeight)
    val maxScroll = (resolvedContentHeight - viewportHeight).coerceAtLeast(1f)
    val resolvedThumbFraction = (viewportHeight / resolvedContentHeight).coerceIn(0.08f, 1f)
    val resolvedOffsetFraction = (scrollOffset / maxScroll).coerceIn(0f, 1f)
    return ThinScrollbarMetrics(
        offsetFraction = resolvedOffsetFraction,
        thumbFraction = resolvedThumbFraction
    )
}
