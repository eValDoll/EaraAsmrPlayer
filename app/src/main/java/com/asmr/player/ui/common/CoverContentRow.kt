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

        val maxWidth = constraints.maxWidth
        val contentMaxWidth = (maxWidth - coverWidthPx - spacingPx).coerceAtLeast(0)

        val contentConstraints = Constraints(
            minWidth = 0,
            maxWidth = contentMaxWidth,
            minHeight = 0,
            maxHeight = constraints.maxHeight,
        )
        var contentPlaceable = measurables[1].measure(contentConstraints)

        val desiredHeight = max(minHeightPx, max(coverWidthPx, contentPlaceable.height))
        val finalHeight = desiredHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        if (finalHeight != desiredHeight) {
            contentPlaceable = measurables[1].measure(contentConstraints.copy(maxHeight = finalHeight))
        }

        val coverPlaceable = measurables[0].measure(Constraints.fixed(coverWidthPx, coverWidthPx))

        layout(width = maxWidth, height = finalHeight) {
            val coverY = ((finalHeight - coverWidthPx) / 2).coerceAtLeast(0)
            coverPlaceable.placeRelative(0, coverY)
            contentPlaceable.placeRelative(coverWidthPx + spacingPx, 0)
        }
    }
}
