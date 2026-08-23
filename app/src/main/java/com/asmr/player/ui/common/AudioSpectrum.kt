package com.asmr.player.ui.common

import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.util.UnstableApi
import com.asmr.player.playback.StereoSpectrumBus
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val AudioSpectrumPointCount = 56
private const val AudioSpectrumIdleFrameIntervalNs = 33_000_000L

@Composable
@UnstableApi
internal fun HorizontalStereoSpectrum(
    lineColor: Color,
    intensity: Float = 1f,
    modifier: Modifier = Modifier
) {
    val renderer = remember { HorizontalStereoSpectrumRenderer() }
    val frameNanos = rememberAudioSpectrumFrameNanos()

    Canvas(modifier = modifier) {
        renderer.draw(
            scope = this,
            frameNanos = frameNanos.value,
            playbackActive = StereoSpectrumBus.playbackActive,
            lineColor = lineColor.toArgb(),
            intensity = intensity
        )
    }
}

@Composable
@UnstableApi
private fun rememberAudioSpectrumFrameNanos(): State<Long> {
    val frameNanos = remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        StereoSpectrumBus.registerCaptureConsumer()
        onDispose { StereoSpectrumBus.unregisterCaptureConsumer() }
    }

    LaunchedEffect(Unit) {
        var lastIdleFrameNanos = 0L
        while (isActive) {
            withFrameNanos { currentFrameNanos ->
                if (
                    StereoSpectrumBus.playbackActive ||
                    currentFrameNanos - lastIdleFrameNanos >= AudioSpectrumIdleFrameIntervalNs
                ) {
                    lastIdleFrameNanos = currentFrameNanos
                    frameNanos.longValue = currentFrameNanos
                }
            }
        }
    }

    return frameNanos
}

@UnstableApi
private class HorizontalStereoSpectrumRenderer {
    private val sourceLeft = FloatArray(StereoSpectrumBus.DefaultBinCount)
    private val sourceRight = FloatArray(StereoSpectrumBus.DefaultBinCount)
    private val spectrum = FloatArray(AudioSpectrumPointCount)
    private val smoothedSpectrum = FloatArray(AudioSpectrumPointCount)
    private val envelope = FloatArray(AudioSpectrumPointCount)
    private val upperY = FloatArray(AudioSpectrumPointCount)
    private val lowerY = FloatArray(AudioSpectrumPointCount)
    private val upperPath = Path()
    private val lowerPath = Path()
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var lastFrameNanos = 0L
    private var phase = 0f
    private var adaptivePeak = 0.12f

    fun draw(
        scope: DrawScope,
        frameNanos: Long,
        playbackActive: Boolean,
        lineColor: Int,
        intensity: Float
    ) {
        val previousFrame = lastFrameNanos
        val deltaSeconds = if (previousFrame == 0L || frameNanos == 0L) {
            1f / 60f
        } else {
            ((frameNanos - previousFrame).coerceIn(1_000_000L, 50_000_000L) /
                1_000_000_000f)
        }
        lastFrameNanos = frameNanos
        phase = (phase + deltaSeconds * if (playbackActive) 0.48f else 0.16f) %
            (PI.toFloat() * 2f)

        updateEnvelope(playbackActive, deltaSeconds)
        buildPaths(
            widthPx = scope.size.width,
            heightPx = scope.size.height,
            density = scope.density,
            playbackActive = playbackActive
        )

        linePaint.color = lineColor
        val resolvedIntensity = intensity.coerceIn(0f, 1f)
        scope.drawIntoCanvas { canvas ->
            linePaint.strokeWidth = scope.density * if (playbackActive) 1.35f else 0.90f
            linePaint.alpha = ((if (playbackActive) 138 else 62) * resolvedIntensity).roundToInt()
            canvas.nativeCanvas.drawPath(upperPath, linePaint)

            linePaint.strokeWidth = scope.density * if (playbackActive) 0.82f else 0.62f
            linePaint.alpha = ((if (playbackActive) 76 else 32) * resolvedIntensity).roundToInt()
            canvas.nativeCanvas.drawPath(lowerPath, linePaint)
        }
    }

    private fun updateEnvelope(playbackActive: Boolean, deltaSeconds: Float) {
        if (!playbackActive) {
            val release = 1f - exp((-deltaSeconds / 0.18f).toDouble()).toFloat()
            for (index in envelope.indices) {
                envelope[index] += (0f - envelope[index]) * release
            }
            adaptivePeak += (0.12f - adaptivePeak) * release
            return
        }

        StereoSpectrumBus.store.copyLatestLeft(sourceLeft)
        StereoSpectrumBus.store.copyLatestRight(sourceRight)
        val sourceScale = sourceLeft.size.toFloat() / spectrum.size.toFloat()
        var framePeak = 0f
        for (index in spectrum.indices) {
            // 将低频能量映射到右侧空白区，避免最活跃的波形被左侧封面遮住。
            val sourceGroupIndex = spectrum.lastIndex - index
            val start = floor(sourceGroupIndex * sourceScale)
                .toInt()
                .coerceIn(0, sourceLeft.lastIndex)
            val endExclusive = floor((sourceGroupIndex + 1) * sourceScale)
                .toInt()
                .coerceIn(start + 1, sourceLeft.size)
            var energy = 0f
            var cursor = start
            while (cursor < endExclusive) {
                energy += max(sourceLeft[cursor], sourceRight[cursor])
                cursor++
            }
            val averaged = energy / (endExclusive - start).toFloat()
            spectrum[index] = if (averaged < 0.012f) 0f else averaged
            framePeak = max(framePeak, spectrum[index])
        }

        smoothedSpectrum[0] = spectrum[0]
        smoothedSpectrum[smoothedSpectrum.lastIndex] = spectrum[spectrum.lastIndex]
        for (index in 1 until spectrum.lastIndex) {
            smoothedSpectrum[index] = (
                spectrum[index - 1] + spectrum[index] * 2f + spectrum[index + 1]
                ) * 0.25f
        }

        val peakTau = if (framePeak > adaptivePeak) 0.035f else 0.32f
        val peakResponse = 1f - exp((-deltaSeconds / peakTau).toDouble()).toFloat()
        adaptivePeak += (framePeak.coerceAtLeast(0.08f) - adaptivePeak) * peakResponse
        val noiseFloor = adaptivePeak * 0.055f
        val usableRange = (adaptivePeak - noiseFloor).coerceAtLeast(0.045f)
        val attack = 1f - exp((-deltaSeconds / 0.040f).toDouble()).toFloat()
        val release = 1f - exp((-deltaSeconds / 0.17f).toDouble()).toFloat()

        for (index in envelope.indices) {
            val normalized = ((smoothedSpectrum[index] - noiseFloor) / usableRange)
                .coerceIn(0f, 1f)
                .pow(0.68f)
            val current = envelope[index]
            val response = if (normalized > current) attack else release
            envelope[index] = current + (normalized - current) * response
        }
    }

    private fun buildPaths(
        widthPx: Float,
        heightPx: Float,
        density: Float,
        playbackActive: Boolean
    ) {
        val centerY = heightPx * 0.52f
        val maximumAmplitude = heightPx * 0.36f
        val fullTurn = PI.toFloat() * 2f
        for (index in 0 until AudioSpectrumPointCount) {
            val progress = index.toFloat() / (AudioSpectrumPointCount - 1).toFloat()
            if (playbackActive) {
                val energy = envelope[index].coerceIn(0f, 1f)
                val drift = sin(phase + progress * fullTurn * 1.35f) * density * 0.45f
                val amplitude = density * 0.6f + energy * maximumAmplitude
                upperY[index] = centerY - amplitude + drift
                lowerY[index] = centerY + amplitude * 0.62f + drift * 0.55f
            } else {
                upperY[index] = centerY +
                    sin(phase + progress * fullTurn * 1.08f) * density * 1.35f +
                    sin(phase * 0.62f + progress * fullTurn * 2.10f) * density * 0.34f
                lowerY[index] = centerY +
                    sin(phase * 0.78f + 1.2f + progress * fullTurn * 0.92f) * density * 0.72f
            }
        }

        buildSmoothPath(upperPath, upperY, widthPx)
        buildSmoothPath(lowerPath, lowerY, widthPx)
    }

    private fun buildSmoothPath(path: Path, yValues: FloatArray, widthPx: Float) {
        path.reset()
        if (yValues.isEmpty()) return
        val step = widthPx / (yValues.size - 1).coerceAtLeast(1).toFloat()
        path.moveTo(0f, yValues[0])
        val tangentScale = 0.82f / 6f
        for (index in 0 until yValues.lastIndex) {
            val previousIndex = (index - 1).coerceAtLeast(0)
            val nextIndex = index + 1
            val followingIndex = (index + 2).coerceAtMost(yValues.lastIndex)
            val x0 = previousIndex * step
            val x1 = index * step
            val x2 = nextIndex * step
            val x3 = followingIndex * step
            val y0 = yValues[previousIndex]
            val y1 = yValues[index]
            val y2 = yValues[nextIndex]
            val y3 = yValues[followingIndex]
            path.cubicTo(
                x1 + (x2 - x0) * tangentScale,
                y1 + (y2 - y0) * tangentScale,
                x2 - (x3 - x1) * tangentScale,
                y2 - (y3 - y1) * tangentScale,
                x2,
                y2
            )
        }
    }
}
