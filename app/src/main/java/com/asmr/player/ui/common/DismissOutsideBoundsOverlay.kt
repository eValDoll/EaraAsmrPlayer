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
    targetBoundsInRoot: Rect? = null,
    protectedBoundsInRoot: List<Rect> = emptyList(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rawProtectedBounds = remember(targetBoundsInRoot, protectedBoundsInRoot) {
        buildList {
            targetBoundsInRoot
                ?.takeIf { it.isSpecified() }
                ?.let(::add)
            protectedBoundsInRoot
                .filter { it.isSpecified() }
                .forEach(::add)
        }
    }
    if (rawProtectedBounds.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        if (maxWidthPx <= 0f || maxHeightPx <= 0f) return@BoxWithConstraints

        val protectedBounds = rawProtectedBounds
            .mapNotNull { bounds ->
                val leftPx = bounds.left.coerceIn(0f, maxWidthPx)
                val topPx = bounds.top.coerceIn(0f, maxHeightPx)
                val rightPx = bounds.right.coerceIn(leftPx, maxWidthPx)
                val bottomPx = bounds.bottom.coerceIn(topPx, maxHeightPx)
                Rect(
                    left = leftPx,
                    top = topPx,
                    right = rightPx,
                    bottom = bottomPx
                ).takeIf { it.width > 0.5f && it.height > 0.5f }
            }
        if (protectedBounds.isEmpty()) return@BoxWithConstraints

        val yBoundaries = buildSet {
            add(0f)
            add(maxHeightPx)
            protectedBounds.forEach { bounds ->
                add(bounds.top)
                add(bounds.bottom)
            }
        }.toList().sorted()
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

        for (index in 0 until (yBoundaries.size - 1)) {
            val bandTop = yBoundaries[index]
            val bandBottom = yBoundaries[index + 1]
            val bandHeight = bandBottom - bandTop
            if (bandHeight <= 0.5f) continue

            val coveredIntervals = protectedBounds
                .filter { it.bottom > bandTop && it.top < bandBottom }
                .map { HorizontalInterval(left = it.left, right = it.right) }
                .sortedBy { it.left }
                .fold(emptyList<HorizontalInterval>()) { merged, interval ->
                    val last = merged.lastOrNull()
                    when {
                        last == null -> listOf(interval)
                        interval.left <= last.right + 0.5f -> merged.dropLast(1) + HorizontalInterval(
                            left = last.left,
                            right = maxOf(last.right, interval.right)
                        )
                        else -> merged + interval
                    }
                }

            if (coveredIntervals.isEmpty()) {
                DismissRegion(
                    xPx = 0f,
                    yPx = bandTop,
                    widthPx = maxWidthPx,
                    heightPx = bandHeight
                )
                continue
            }

            var gapStart = 0f
            for (interval in coveredIntervals) {
                DismissRegion(
                    xPx = gapStart,
                    yPx = bandTop,
                    widthPx = interval.left - gapStart,
                    heightPx = bandHeight
                )
                gapStart = interval.right
            }
            DismissRegion(
                xPx = gapStart,
                yPx = bandTop,
                widthPx = maxWidthPx - gapStart,
                heightPx = bandHeight
            )
        }
    }
}

private data class HorizontalInterval(
    val left: Float,
    val right: Float
)

private fun Rect.isSpecified(): Boolean {
    return left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()
}
