package com.asmr.player.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import kotlin.math.max

@Composable
fun CoverContentRow(
    coverWidth: Dp,
    minHeight: Dp,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    fillContentHeight: Boolean = false,
    cover: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(
        content = {
            cover()
            content()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val coverWidthPx = coverWidth.roundToPx()
        val spacingPx = spacing.roundToPx()
        val minHeightPx = minHeight.roundToPx()
        val contentMinHeight = if (fillContentHeight) {
            max(constraints.minHeight, max(minHeightPx, coverWidthPx))
                .coerceAtMost(constraints.maxHeight)
        } else {
            0
        }

        val maxWidth = constraints.maxWidth
        val contentMaxWidth = (maxWidth - coverWidthPx - spacingPx).coerceAtLeast(0)

        val contentConstraints = Constraints(
            minWidth = 0,
            maxWidth = contentMaxWidth,
            minHeight = contentMinHeight,
            maxHeight = constraints.maxHeight,
        )
        val contentPlaceable = measurables[1].measure(contentConstraints)

        val desiredHeight = max(minHeightPx, max(coverWidthPx, contentPlaceable.height))
        val finalHeight = desiredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        val coverPlaceable = measurables[0].measure(Constraints.fixed(coverWidthPx, coverWidthPx))

        layout(width = maxWidth, height = finalHeight) {
            val coverY = ((finalHeight - coverWidthPx) / 2).coerceAtLeast(0)
            val contentY = ((finalHeight - contentPlaceable.height) / 2).coerceAtLeast(0)
            coverPlaceable.placeRelative(0, coverY)
            contentPlaceable.placeRelative(coverWidthPx + spacingPx, contentY)
        }
    }
}
