package com.asmr.player.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.PI
import kotlin.math.sin

private data class LogoBarSpec(
    val x: Float,
    val startY: Float,
    val endY: Float
)

private val LogoBars = listOf(
    LogoBarSpec(x = 2f, startY = 10f, endY = 13f),
    LogoBarSpec(x = 6f, startY = 6f, endY = 17f),
    LogoBarSpec(x = 10f, startY = 3f, endY = 21f),
    LogoBarSpec(x = 14f, startY = 8f, endY = 15f),
    LogoBarSpec(x = 18f, startY = 5f, endY = 18f),
    LogoBarSpec(x = 22f, startY = 10f, endY = 13f)
)

@Composable
fun EaraLogoLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    glowColor: Color = Color.Unspecified,
    showGlow: Boolean = true
) {
    val colorScheme = AsmrTheme.colorScheme
    val localContentColor = LocalContentColor.current
    val resolvedTint = when {
        tint != Color.Unspecified -> tint
        localContentColor != Color.Unspecified -> localContentColor
        else -> colorScheme.primary
    }.copy(alpha = 1f)
    val resolvedTrackColor = if (trackColor != Color.Unspecified) {
        trackColor
    } else {
        resolvedTint.copy(alpha = if (colorScheme.isDark) 0.26f else 0.18f)
    }
    val resolvedGlowColor = if (glowColor != Color.Unspecified) {
        glowColor
    } else if (resolvedTint.approxLuminance() > 0.7f) {
        resolvedTint
    } else {
        colorScheme.primarySoft
    }

    val infinite = rememberInfiniteTransition(label = "eara_logo_loading")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (showGlow && size > 20.dp) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.92f)
                    .graphicsLayer(
                        alpha = if (colorScheme.isDark) 0.14f + glowPulse * 0.18f else 0.10f + glowPulse * 0.12f,
                        scaleX = 0.86f + glowPulse * 0.18f,
                        scaleY = 0.86f + glowPulse * 0.18f
                    )
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                resolvedGlowColor.copy(alpha = if (colorScheme.isDark) 0.44f else 0.26f),
                                resolvedGlowColor.copy(alpha = 0f)
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize(if (size <= 18.dp) 0.94f else 0.82f)
                .graphicsLayer(alpha = 0.86f + glowPulse * 0.14f)
        ) {
            drawEaraLogoBars(
                phase = wavePhase,
                tint = resolvedTint,
                trackColor = resolvedTrackColor
            )
        }
    }
}

private fun DrawScope.drawEaraLogoBars(
    phase: Float,
    tint: Color,
    trackColor: Color
) {
    val viewportSize = 24f
    val scale = size.minDimension / viewportSize
    val offsetX = (size.width - viewportSize * scale) / 2f
    val offsetY = (size.height - viewportSize * scale) / 2f
    val strokeWidth = 2f * scale

    LogoBars.forEachIndexed { index, bar ->
        val x = offsetX + bar.x * scale
        val baseStartY = offsetY + bar.startY * scale
        val baseEndY = offsetY + bar.endY * scale

        drawLine(
            color = trackColor,
            start = Offset(x, baseStartY),
            end = Offset(x, baseEndY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        val activity = waveAmount(phase = phase, index = index)
        val centerY = (bar.startY + bar.endY) / 2f
        val halfHeight = (bar.endY - bar.startY) / 2f
        val animatedHalfHeight = halfHeight * lerp(0.72f, 1.18f, activity)
        val animatedColor = tint.copy(alpha = lerp(0.56f, 1f, activity))

        drawLine(
            color = animatedColor,
            start = Offset(x, offsetY + (centerY - animatedHalfHeight) * scale),
            end = Offset(x, offsetY + (centerY + animatedHalfHeight) * scale),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun waveAmount(
    phase: Float,
    index: Int
): Float {
    val shifted = (phase + index * 0.12f) % 1f
    val radians = shifted * 2f * PI.toFloat() - (PI.toFloat() / 2f)
    return ((sin(radians.toDouble()).toFloat() + 1f) / 2f).coerceIn(0f, 1f)
}

private fun lerp(
    start: Float,
    end: Float,
    fraction: Float
): Float = start + (end - start) * fraction

private fun Color.approxLuminance(): Float {
    return (0.2126f * red + 0.7152f * green + 0.0722f * blue).coerceIn(0f, 1f)
}
