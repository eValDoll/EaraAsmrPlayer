package com.asmr.player.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun AsmrShimmerPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 6,
) {
    val colorScheme = AsmrTheme.colorScheme
    val isLight = remember(colorScheme) { colorScheme.surface.luminance() > 0.5f }
    val baseColor: Color = remember(colorScheme) {
        if (isLight) colorScheme.surfaceVariant else colorScheme.surfaceVariant.copy(alpha = 0.80f)
    }
    val highlightColor: Color = remember(colorScheme) {
        if (isLight) {
            colorScheme.surface
        } else {
            colorScheme.onSurface.copy(alpha = 0.12f).compositeOver(colorScheme.surfaceVariant)
        }
    }

    val transition = rememberInfiniteTransition(label = "asmrShimmer")
    val shimmerT = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing)
        ),
        label = "asmrShimmerT"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val band = w * 0.75f
                val t = shimmerT.value
                val edge = 0.12f
                val fade = when {
                    t < edge -> (t / edge)
                    t > (1f - edge) -> ((1f - t) / edge)
                    else -> 1f
                }.coerceIn(0f, 1f)
                val startX = (t * (w + band)) - band
                val endX = startX + band
                val brush = Brush.linearGradient(
                    colors = listOf(baseColor, highlightColor.copy(alpha = highlightColor.alpha * fade), baseColor),
                    start = Offset(startX, 0f),
                    end = Offset(endX, h),
                )
                onDrawBehind {
                    drawRect(color = baseColor)
                    drawRect(brush = brush)
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
