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
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.sqrt

private const val ShimmerSweepDurationMillis = 1500
private const val ShimmerPauseDurationMillis = 520
private const val ShimmerCycleDurationMillis =
    ShimmerSweepDurationMillis + ShimmerPauseDurationMillis
private const val ShimmerSweepFraction =
    ShimmerSweepDurationMillis.toFloat() / ShimmerCycleDurationMillis.toFloat()

@Composable
fun rememberAsmrShimmerProgress(): State<Float> {
    val transition = rememberInfiniteTransition(label = "asmrShimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ShimmerCycleDurationMillis, easing = LinearEasing)
        ),
        label = "asmrShimmerT"
    )
}

@Composable
fun AsmrShimmerPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 6,
    sharedProgress: State<Float>? = null,
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

    val shimmerT = sharedProgress ?: rememberAsmrShimmerProgress()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .drawWithCache {
                val w = size.width.coerceAtLeast(1f)
                val h = size.height.coerceAtLeast(1f)
                val diagonal = sqrt((w * w) + (h * h))
                val band = diagonal * 0.78f
                // 原来的底色矩形和透明高光矩形会让每个骨架块每帧提交两条
                // GPU 指令。把高光按相同的 SrcOver 公式预合成进渐变色标，
                // 只画一次即可得到逐像素一致的结果。
                val highlightOverBase = highlightColor
                    .copy(alpha = highlightColor.alpha * 0.96f)
                    .compositeOver(baseColor)
                val shimmerBrush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to baseColor,
                        0.42f to baseColor,
                        0.50f to highlightOverBase,
                        0.58f to baseColor,
                        1.00f to baseColor
                    ),
                    start = Offset.Zero,
                    end = Offset(band, h)
                )
                onDrawBehind {
                    val cycleT = shimmerT.value
                    if (cycleT <= ShimmerSweepFraction) {
                        val progress = (cycleT / ShimmerSweepFraction).coerceIn(0f, 1f)
                        val startX = -band + ((w + band) * progress)
                        translate(left = startX) {
                            drawRect(
                                brush = shimmerBrush,
                                topLeft = Offset(-startX, 0f),
                                size = Size(w, h)
                            )
                        }
                    } else {
                        drawRect(color = baseColor)
                    }
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}
