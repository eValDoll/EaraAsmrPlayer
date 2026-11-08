package com.asmr.player.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp

/** 专辑元数据没有权重、行数限制或折叠项；逐项测量一次，保持原有间距与换行。 */
@Composable
internal fun AlbumMetadataFlow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier, measurePolicy = AlbumMetadataFlowMeasurePolicy)
}

private object AlbumMetadataFlowMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val horizontalGap = 8.dp.roundToPx()
        val verticalGap = 3.dp.roundToPx()
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val children = arrayOfNulls<Placeable>(measurables.size)
        val left = IntArray(measurables.size)
        val top = IntArray(measurables.size)
        var lineWidth = 0
        var lineHeight = 0
        var lineItems = 0
        var lineTop = 0
        var widestLine = 0
        for (index in measurables.indices) {
            val child = measurables[index].measure(childConstraints)
            children[index] = child
            val gap = if (lineItems == 0) 0 else horizontalGap
            if (lineItems > 0 && lineWidth.toLong() + gap + child.width > constraints.maxWidth) {
                widestLine = maxOf(widestLine, lineWidth)
                lineTop += lineHeight + verticalGap
                lineWidth = 0
                lineHeight = 0
                lineItems = 0
            }
            if (lineItems > 0) lineWidth += horizontalGap
            left[index] = lineWidth
            top[index] = lineTop
            lineWidth += child.width
            lineHeight = maxOf(lineHeight, child.height)
            lineItems++
        }
        return layout(
            constraints.constrainWidth(maxOf(widestLine, lineWidth)),
            constraints.constrainHeight(lineTop + lineHeight),
        ) {
            for (index in children.indices) {
                checkNotNull(children[index]).placeRelative(left[index], top[index])
            }
        }
    }
}
