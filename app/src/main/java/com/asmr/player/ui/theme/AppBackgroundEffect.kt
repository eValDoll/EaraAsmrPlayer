package com.asmr.player.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.data.settings.BackgroundEffectType
import kotlin.math.sin

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
                BackgroundEffectType.Ripple -> RippleBackgroundEffect(
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

@Composable
private fun RippleBackgroundEffect(modifier: Modifier = Modifier) {
    val colorScheme = AsmrTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "appBackgroundRipple")
    val breath by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundRippleBreath"
    )
    val morphA by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundRippleMorphA"
    )
    val morphB by transition.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundRippleMorphB"
    )
    val flowDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundRippleFlowDrift"
    )
    val veilShift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appBackgroundRippleVeilShift"
    )
    val blurPrimary = remember(colorScheme.isDark) {
        rippleBlurModifier(if (colorScheme.isDark) 24.dp else 18.dp)
    }

    val ribbonSpecs = listOf(
        RippleRibbonSpec(
            baseY = -0.04f,
            thickness = 0.20f,
            slope = 0.12f,
            amplitudeA = 0.104f,
            amplitudeB = 0.054f,
            amplitudeC = 0.028f,
            freqA = 0.72f,
            freqB = 1.58f,
            freqC = 3.45f,
            edgeWarp = 0.028f,
            phaseOffset = 0.08f,
            emphasizeStrong = true
        ),
        RippleRibbonSpec(
            baseY = 0.18f,
            thickness = 0.17f,
            slope = -0.08f,
            amplitudeA = 0.082f,
            amplitudeB = 0.044f,
            amplitudeC = 0.025f,
            freqA = 0.90f,
            freqB = 1.92f,
            freqC = 3.84f,
            edgeWarp = 0.024f,
            phaseOffset = 0.31f,
            emphasizeStrong = false
        ),
        RippleRibbonSpec(
            baseY = 0.46f,
            thickness = 0.20f,
            slope = 0.11f,
            amplitudeA = 0.098f,
            amplitudeB = 0.050f,
            amplitudeC = 0.026f,
            freqA = 0.76f,
            freqB = 1.64f,
            freqC = 3.56f,
            edgeWarp = 0.027f,
            phaseOffset = 0.57f,
            emphasizeStrong = true
        ),
        RippleRibbonSpec(
            baseY = 0.72f,
            thickness = 0.18f,
            slope = -0.10f,
            amplitudeA = 0.084f,
            amplitudeB = 0.044f,
            amplitudeC = 0.024f,
            freqA = 0.96f,
            freqB = 1.86f,
            freqC = 4.02f,
            edgeWarp = 0.024f,
            phaseOffset = 0.83f,
            emphasizeStrong = false
        ),
        RippleRibbonSpec(
            baseY = 0.92f,
            thickness = 0.21f,
            slope = 0.07f,
            amplitudeA = 0.100f,
            amplitudeB = 0.048f,
            amplitudeC = 0.026f,
            freqA = 0.68f,
            freqB = 1.46f,
            freqC = 3.24f,
            edgeWarp = 0.028f,
            phaseOffset = 1.06f,
            emphasizeStrong = true
        )
    )

    val strongColor = colorScheme.primaryStrong
    val softColor = colorScheme.primarySoft
    val mistHighlight = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.11f)
    } else {
        Color.White.copy(alpha = 0.16f)
    }
    val ambientStrong = if (colorScheme.isDark) {
        strongColor.copy(alpha = 0.15f)
    } else {
        strongColor.copy(alpha = 0.17f)
    }
    val ambientSoft = if (colorScheme.isDark) {
        softColor.copy(alpha = 0.10f)
    } else {
        softColor.copy(alpha = 0.13f)
    }
    val topVeil = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.026f)
    } else {
        Color.White.copy(alpha = 0.046f)
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize().then(blurPrimary)) {
            val breathAmount = (breath + 1f) * 0.5f
            drawDiffuseRippleLayer(
                ribbonSpecs = ribbonSpecs,
                breath = breathAmount,
                morphA = morphA,
                morphB = morphB,
                flowDrift = flowDrift,
                strongColor = strongColor,
                softColor = softColor,
                mistHighlight = mistHighlight,
                ambientStrong = ambientStrong,
                ambientSoft = ambientSoft,
                alphaScale = if (colorScheme.isDark) 0.92f else 1.0f,
                thicknessScale = 1.08f,
                amplitudeScale = 1.0f,
                phaseNudge = 0f,
                yShift = 0f
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val breathAmountLocal = (breath + 1f) * 0.5f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        topVeil.copy(alpha = topVeil.alpha * (0.96f + 0.04f * (1f - breathAmountLocal))),
                        Color.Transparent,
                        ambientSoft.copy(alpha = ambientSoft.alpha * 0.18f)
                    ),
                    startY = height * (-0.08f + 0.04f * veilShift),
                    endY = height * (1.04f + 0.06f * flowDrift)
                )
            )

            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        ambientStrong.copy(alpha = ambientStrong.alpha * 0.16f),
                        Color.Transparent
                    ),
                    start = Offset(width * (-0.14f + 0.06f * morphB), height * (0.16f - 0.05f * veilShift)),
                    end = Offset(width * (1.08f - 0.04f * morphA), height * (0.96f + 0.04f * flowDrift))
                )
            )
        }
    }
}

private fun DrawScope.drawDiffuseRippleLayer(
    ribbonSpecs: List<RippleRibbonSpec>,
    breath: Float,
    morphA: Float,
    morphB: Float,
    flowDrift: Float,
    strongColor: Color,
    softColor: Color,
    mistHighlight: Color,
    ambientStrong: Color,
    ambientSoft: Color,
    alphaScale: Float,
    thicknessScale: Float,
    amplitudeScale: Float,
    phaseNudge: Float,
    yShift: Float
) {
        val minDimension = size.minDimension
        val width = size.width
        val height = size.height
        val ambientCenterA = Offset(
            x = width * (0.18f + 0.18f * flowDrift + 0.02f * phaseNudge),
            y = height * (0.16f + 0.12f * morphA + yShift)
        )
        val ambientCenterB = Offset(
            x = width * (0.80f - 0.16f * morphB - 0.02f * phaseNudge),
            y = height * (0.78f - 0.14f * flowDrift - yShift)
        )
        val ambientRadiusA = minDimension * 1.10f * (0.96f + 0.12f * breath)
        val ambientRadiusB = minDimension * 1.00f * (0.94f + 0.16f * (1f - breath))

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    ambientStrong.copy(alpha = ambientStrong.alpha * 0.72f * alphaScale),
                    ambientSoft.copy(alpha = ambientSoft.alpha * 0.44f * alphaScale),
                    Color.Transparent
                ),
                start = Offset(
                    x = width * (-0.18f + 0.20f * flowDrift),
                    y = height * (-0.02f - 0.08f * morphB + yShift)
                ),
                end = Offset(
                    x = width * (1.14f - 0.14f * morphA),
                    y = height * (1.04f + 0.10f * flowDrift - yShift)
                )
            )
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ambientStrong.copy(alpha = ambientStrong.alpha * alphaScale), Color.Transparent),
                center = ambientCenterA,
                radius = ambientRadiusA
            ),
            radius = ambientRadiusA,
            center = ambientCenterA
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ambientSoft.copy(alpha = ambientSoft.alpha * alphaScale), Color.Transparent),
                center = ambientCenterB,
                radius = ambientRadiusB
            ),
            radius = ambientRadiusB,
            center = ambientCenterB
        )

        ribbonSpecs.forEachIndexed { index, baseSpec ->
            val spec = baseSpec.copy(
                baseY = baseSpec.baseY + yShift * (1f + index * 0.10f),
                thickness = baseSpec.thickness * thicknessScale,
                slope = baseSpec.slope * (0.96f + 0.10f * amplitudeScale),
                amplitudeA = baseSpec.amplitudeA * amplitudeScale,
                amplitudeB = baseSpec.amplitudeB * amplitudeScale,
                amplitudeC = baseSpec.amplitudeC * (1.04f + 0.10f * amplitudeScale),
                edgeWarp = baseSpec.edgeWarp * (1.08f + 0.12f * amplitudeScale),
                phaseOffset = baseSpec.phaseOffset + phaseNudge * (1f + index * 0.14f)
            )
            val baseColor = if (spec.emphasizeStrong) strongColor else softColor
            val fillAlpha = if (spec.emphasizeStrong) {
                0.17f * alphaScale
            } else {
                0.12f * alphaScale
            }
            val fillPath = buildRippleRibbonFillPath(
                width = width,
                height = height,
                breath = breath,
                morphA = morphA,
                morphB = morphB,
                flowDrift = flowDrift,
                spec = spec
            )
            val highlightAlpha = if (spec.emphasizeStrong) {
                0.032f * alphaScale
            } else {
                0.022f * alphaScale
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        mistHighlight.copy(alpha = highlightAlpha * 0.65f),
                        baseColor.copy(alpha = fillAlpha * 0.52f),
                        baseColor.copy(alpha = fillAlpha * (0.86f + 0.06f * breath)),
                        baseColor.copy(alpha = fillAlpha * 0.60f),
                        Color.Transparent
                    ),
                    startY = height * (spec.baseY - spec.thickness * 2.05f),
                    endY = height * (spec.baseY + spec.thickness * 2.05f)
                )
            )
        }
    }

private data class RippleRibbonSpec(
    val baseY: Float,
    val thickness: Float,
    val slope: Float,
    val amplitudeA: Float,
    val amplitudeB: Float,
    val amplitudeC: Float,
    val freqA: Float,
    val freqB: Float,
    val freqC: Float,
    val edgeWarp: Float,
    val phaseOffset: Float,
    val emphasizeStrong: Boolean
)

private fun buildRippleRibbonFillPath(
    width: Float,
    height: Float,
    breath: Float,
    morphA: Float,
    morphB: Float,
    flowDrift: Float,
    spec: RippleRibbonSpec,
    segments: Int = 28
): Path {
    val path = Path()

    for (i in 0..segments) {
        val nx = -0.12f + 1.24f * (i.toFloat() / segments.toFloat())
        val x = width * nx
        val centerY = rippleRibbonCenterY(
            height = height,
            nx = nx,
            breath = breath,
            morphA = morphA,
            morphB = morphB,
            flowDrift = flowDrift,
            spec = spec
        )
        val halfThickness = rippleRibbonHalfThickness(
            height = height,
            nx = nx,
            breath = breath,
            morphB = morphB,
            spec = spec
        )
        val edgeWarp = rippleRibbonEdgeWarp(
            height = height,
            nx = nx,
            morphA = morphA,
            morphB = morphB,
            flowDrift = flowDrift,
            spec = spec
        )
        val topY = centerY - halfThickness - edgeWarp
        if (i == 0) {
            path.moveTo(x, topY)
        } else {
            path.lineTo(x, topY)
        }
    }

    for (i in segments downTo 0) {
        val nx = -0.12f + 1.24f * (i.toFloat() / segments.toFloat())
        val x = width * nx
        val centerY = rippleRibbonCenterY(
            height = height,
            nx = nx,
            breath = breath,
            morphA = morphA,
            morphB = morphB,
            flowDrift = flowDrift,
            spec = spec
        )
        val halfThickness = rippleRibbonHalfThickness(
            height = height,
            nx = nx,
            breath = breath,
            morphB = morphB,
            spec = spec
        )
        val edgeWarp = rippleRibbonEdgeWarp(
            height = height,
            nx = nx,
            morphA = morphA,
            morphB = morphB,
            flowDrift = flowDrift,
            spec = spec
        )
        val bottomY = centerY + halfThickness + edgeWarp * 0.78f
        path.lineTo(x, bottomY)
    }

    path.close()
    return path
}

private fun rippleRibbonCenterY(
    height: Float,
    nx: Float,
    breath: Float,
    morphA: Float,
    morphB: Float,
    flowDrift: Float,
    spec: RippleRibbonSpec
): Float {
    val breathScale = 0.92f + 0.18f * breath
    val slowWave = sin((nx * spec.freqA + spec.phaseOffset + morphA * 0.22f) * RIPPLE_TAU)
    val mediumWave = sin(
        (nx * spec.freqB - spec.phaseOffset * 0.64f + morphB * 0.28f + slowWave * 0.18f) * RIPPLE_TAU
    )
    val fineWave = sin(
        (nx * spec.freqC + spec.phaseOffset * 1.18f - flowDrift * 0.40f + mediumWave * 0.16f) * RIPPLE_TAU
    )
    val twistWave = sin(
        (((nx + 0.22f) * (nx + 0.08f)) * (spec.freqB + 0.72f) + morphA * 0.12f - morphB * 0.09f) * RIPPLE_TAU
    )
    val contourOffset = (
        spec.amplitudeA * slowWave +
            spec.amplitudeB * mediumWave +
            spec.amplitudeC * fineWave +
            spec.edgeWarp * twistWave
        ) * breathScale
    val diagonalOffset = spec.slope * (nx - 0.5f)
    val pulseLift = 0.018f * breath * sin(
        (nx * (0.90f + spec.freqA * 0.32f) + spec.phaseOffset + flowDrift * 0.10f) * RIPPLE_TAU
    )
    return height * (spec.baseY + diagonalOffset + contourOffset + pulseLift)
}

private fun rippleRibbonHalfThickness(
    height: Float,
    nx: Float,
    breath: Float,
    morphB: Float,
    spec: RippleRibbonSpec
): Float {
    val envelope = 0.96f + 0.18f * breath
    val unevenPulse = 0.12f * sin(
        (nx * (spec.freqB * 0.62f + 0.84f) + morphB * 0.22f - spec.phaseOffset) * RIPPLE_TAU
    )
    return height * spec.thickness * (envelope + unevenPulse)
}

private fun rippleRibbonEdgeWarp(
    height: Float,
    nx: Float,
    morphA: Float,
    morphB: Float,
    flowDrift: Float,
    spec: RippleRibbonSpec
): Float {
    val edgeWaveA = sin(
        (nx * (spec.freqC * 0.74f + 1.28f) + morphA * 0.26f + spec.phaseOffset * 0.82f) * RIPPLE_TAU
    )
    val edgeWaveB = sin(
        (nx * (spec.freqA * 1.16f + 1.08f) - morphB * 0.18f + flowDrift * 0.10f) * RIPPLE_TAU
    )
    return height * (0.030f * edgeWaveA + 0.014f * edgeWaveB)
}

private fun rippleBlurModifier(blurDp: Dp): Modifier {
    if (blurDp.value <= 0f) return Modifier
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            val blurPx = blurDp.toPx()
            renderEffect = RenderEffect
                .createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        Modifier.blur(blurDp)
    }
}

private const val RIPPLE_TAU = 6.2831855f
