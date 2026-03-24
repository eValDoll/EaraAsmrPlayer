package com.asmr.player.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
internal fun DismissOutsideBoundsOverlay(
    targetBoundsInRoot: Rect?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bounds = targetBoundsInRoot ?: return
    if (!bounds.isSpecified()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        if (maxWidthPx <= 0f || maxHeightPx <= 0f) return@BoxWithConstraints

        val leftPx = bounds.left.coerceIn(0f, maxWidthPx)
        val topPx = bounds.top.coerceIn(0f, maxHeightPx)
        val rightPx = bounds.right.coerceIn(leftPx, maxWidthPx)
        val bottomPx = bounds.bottom.coerceIn(topPx, maxHeightPx)
        val middleHeightPx = (bottomPx - topPx).coerceAtLeast(0f)
        val interactionSource = remember { MutableInteractionSource() }

        @Composable
        fun DismissRegion(
            xPx: Float,
            yPx: Float,
            widthPx: Float,
            heightPx: Float
        ) {
            if (widthPx <= 0.5f || heightPx <= 0.5f) return
            Box(
                modifier = Modifier
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .width(with(density) { widthPx.toDp() })
                    .height(with(density) { heightPx.toDp() })
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        DismissRegion(
            xPx = 0f,
            yPx = 0f,
            widthPx = maxWidthPx,
            heightPx = topPx
        )
        DismissRegion(
            xPx = 0f,
            yPx = topPx,
            widthPx = leftPx,
            heightPx = middleHeightPx
        )
        DismissRegion(
            xPx = rightPx,
            yPx = topPx,
            widthPx = maxWidthPx - rightPx,
            heightPx = middleHeightPx
        )
        DismissRegion(
            xPx = 0f,
            yPx = bottomPx,
            widthPx = maxWidthPx,
            heightPx = maxHeightPx - bottomPx
        )
    }
}

private fun Rect.isSpecified(): Boolean {
    return left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()
}
