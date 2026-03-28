package com.asmr.player.ui.theme

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.asmr.player.data.settings.BackgroundEffectType

internal enum class BackgroundEffectSurface {
    AppPage,
    NowPlayingPlayer,
    NowPlayingLyrics
}

internal fun shouldRenderBackgroundEffect(
    surface: BackgroundEffectSurface,
    effectEnabled: Boolean,
    coverBackgroundEnabled: Boolean
): Boolean {
    if (!effectEnabled) return false
    return when (surface) {
        BackgroundEffectSurface.AppPage -> true
        BackgroundEffectSurface.NowPlayingPlayer,
        BackgroundEffectSurface.NowPlayingLyrics -> !coverBackgroundEnabled
    }
}

@Composable
internal fun AppBackgroundLayer(
    effectEnabled: Boolean,
    effectType: BackgroundEffectType,
    baseBackgroundAlpha: Float = 0.88f,
    tintAlpha: Float = 0.16f,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme

    Box(modifier = modifier.background(colorScheme.background.copy(alpha = baseBackgroundAlpha))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.primarySoft.copy(alpha = tintAlpha))
        )

        if (effectEnabled) {
            when (effectType) {
                BackgroundEffectType.Flow -> FlowBackgroundEffect(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun FlowBackgroundEffect(modifier: Modifier = Modifier) {
    val colorScheme = AsmrTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "appBackgroundFlow")
    val leadDriftDurationMs = 7_600
    val supportDriftDurationMs = 9_000
    val verticalWaveDurationMs = 5_800
    val diagonalWaveDurationMs = 6_400
    val orbitDurationMs = 8_800
    val counterOrbitDurationMs = 7_800
    val sparkPulseDurationMs = 4_200
    val leadDrift by transition.animateFloat(
        initialValue = -0.18f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = leadDriftDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundLeadDrift"
    )
    val supportDrift by transition.animateFloat(
        initialValue = 1.1f,
        targetValue = -0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = supportDriftDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundSupportDrift"
    )
    val verticalWave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = verticalWaveDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundVerticalWave"
    )
    val diagonalWave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = diagonalWaveDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundDiagonalWave"
    )
    val orbitWave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = orbitDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundOrbitWave"
    )
    val counterOrbitWave by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = counterOrbitDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundCounterOrbitWave"
    )
    val sparkPulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = sparkPulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundSparkPulse"
    )

    val leadGlowColor = if (colorScheme.isDark) {
        colorScheme.primaryStrong.copy(alpha = 0.17f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.14f)
    }
    val supportGlowColor = if (colorScheme.isDark) {
        colorScheme.primarySoft.copy(alpha = 0.14f)
    } else {
        colorScheme.primarySoft.copy(alpha = 0.11f)
    }
    val ribbonStart = if (colorScheme.isDark) {
        colorScheme.primaryStrong.copy(alpha = 0.10f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.10f)
    }
    val ribbonEnd = if (colorScheme.isDark) {
        colorScheme.primarySoft.copy(alpha = 0.08f)
    } else {
        colorScheme.primarySoft.copy(alpha = 0.08f)
    }
    val accentGlowColor = if (colorScheme.isDark) {
        colorScheme.primaryStrong.copy(alpha = 0.10f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.16f)
    }
    val veilColor = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.02f)
    } else {
        Color.White.copy(alpha = 0.07f)
    }
    val leadRadiusFactor = if (colorScheme.isDark) 0.82f else 0.72f
    val supportRadiusFactor = if (colorScheme.isDark) 0.92f else 0.78f
    val accentRadiusFactor = if (colorScheme.isDark) 0.58f else 0.46f

    Canvas(modifier = modifier) {
        val minDimension = size.minDimension
        val width = size.width
        val height = size.height
        val leadCenter = Offset(
            x = width * (leadDrift + 0.12f * (counterOrbitWave - 0.5f) + 0.05f * (sparkPulse - 0.5f)),
            y = height * (0.12f + 0.64f * orbitWave + 0.12f * (verticalWave - 0.5f))
        )
        val supportCenter = Offset(
            x = width * (supportDrift + 0.14f * (verticalWave - 0.5f) - 0.05f * (sparkPulse - 0.5f)),
            y = height * (0.84f - 0.58f * counterOrbitWave + 0.10f * (diagonalWave - 0.5f))
        )
        val accentCenter = Offset(
            x = width * (0.14f + 0.72f * diagonalWave),
            y = height * (0.18f + 0.58f * verticalWave + 0.04f * (sparkPulse - 0.5f))
        )

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    ribbonStart,
                    Color.Transparent,
                    ribbonEnd
                ),
                start = Offset(
                    x = width * (-0.18f + 0.82f * counterOrbitWave),
                    y = height * (-0.10f + 0.42f * orbitWave)
                ),
                end = Offset(
                    x = width * (1.12f - 0.68f * diagonalWave),
                    y = height * (1.08f - 0.38f * counterOrbitWave)
                )
            )
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(leadGlowColor, Color.Transparent),
                center = leadCenter,
                radius = minDimension * leadRadiusFactor
            ),
            radius = minDimension * leadRadiusFactor,
            center = leadCenter
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(supportGlowColor, Color.Transparent),
                center = supportCenter,
                radius = minDimension * supportRadiusFactor
            ),
            radius = minDimension * supportRadiusFactor,
            center = supportCenter
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentGlowColor, Color.Transparent),
                center = accentCenter,
                radius = minDimension * accentRadiusFactor
            ),
            radius = minDimension * accentRadiusFactor,
            center = accentCenter
        )

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(veilColor, Color.Transparent)
            )
        )
    }
}
