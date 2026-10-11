package com.asmr.player.ui.common

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme
import kotlin.math.ceil

internal val LocalMainHeaderPadding = staticCompositionLocalOf { 0.dp }
internal val LocalProgressiveHeaderBlur = staticCompositionLocalOf<ProgressiveHeaderBlurState?> { null }
private val HeaderBlurTransitionHeight = 80.dp

internal fun hasProgressiveMainHeader(route: String?): Boolean = when (route) {
    "library", "search", "hot_listening", "playlist_system/favorites", "playlists", "groups",
    "listening_calendar", "settings" -> true
    else -> false
}

@Stable
internal class ProgressiveHeaderBlurState(
    val source: GraphicsLayer,
    val backdrop: GraphicsLayer,
    val effect: ProgressiveHeaderEffect,
)

@Composable
internal fun rememberProgressiveHeaderBlur(): ProgressiveHeaderBlurState? {
    if (Build.VERSION.SDK_INT < 33 || !LocalView.current.isHardwareAccelerated) {
        return null
    }
    // Drivers that cannot compile AGSL keep the original header and layout.
    val effect = remember { runCatching { ProgressiveHeaderEffect() }.getOrNull() } ?: return null
    val source = rememberGraphicsLayer()
    val backdrop = rememberGraphicsLayer()
    return remember(source, backdrop, effect) { ProgressiveHeaderBlurState(source, backdrop, effect) }
}

private fun Modifier.headerBlurSource(state: ProgressiveHeaderBlurState?): Modifier =
    if (state == null) this else drawWithContent {
        // Keep one display list; the backdrop reuses it without composing or measuring the list again.
        state.source.record { this@drawWithContent.drawContent() }
        drawLayer(state.source)
    }

/** Blurs this container's content at the header without reserving layout space. */
@Composable
internal fun Modifier.progressiveHeaderContent(fadeEndOffset: Dp = 0.dp): Modifier {
    val state = LocalProgressiveHeaderBlur.current ?: return this
    val headerHeight = LocalMainHeaderPadding.current
    val background = resolveMainPageBackgroundColor(AsmrTheme.colorScheme)
    return progressiveHeaderBackdrop(
        state = state,
        background = background,
        height = headerHeight + fadeEndOffset,
    ).headerBlurSource(state)
}

// A state is only created after the API/hardware checks in rememberProgressiveHeaderBlur.
@SuppressLint("NewApi")
private fun Modifier.progressiveHeaderBackdrop(
    state: ProgressiveHeaderBlurState,
    background: Color,
    height: Dp,
): Modifier = drawWithCache {
    val recordingDensity = Density(density, fontScale)
    val recordingLayoutDirection = layoutDirection
    val backdropHeight = height.toPx().coerceAtMost(size.height)
    val blurRadiusPx = 20.dp.toPx()
    val sampleSize = IntSize(
        ceil(size.width).toInt().coerceAtLeast(1),
        // Keep native pixels all the way to the clear edge; scaling aliases moving text.
        ceil(backdropHeight).toInt().coerceAtLeast(1),
    )
    val renderEffect = state.effect.configure(
        width = sampleSize.width.toFloat(),
        height = sampleSize.height.toFloat(),
        radius = blurRadiusPx,
        blurEnd = backdropHeight,
        blurStart = (backdropHeight - HeaderBlurTransitionHeight.toPx()).coerceAtLeast(0f),
    )
    state.backdrop.renderEffect = renderEffect
    state.backdrop.clip = true
    onDrawWithContent {
        drawContent()
        // Use an independent density for nested layer recording; DrawScope itself is mutable.
        state.backdrop.record(recordingDensity, recordingLayoutDirection, sampleSize) {
            drawRect(background)
            drawLayer(state.source)
        }
        clipRect(bottom = backdropHeight) {
            drawLayer(state.backdrop)
        }
    }
}

/** Native-resolution separable blur using Haze's adjacent-pixel Gaussian sampling. */
@RequiresApi(33)
internal class ProgressiveHeaderEffect {
    private val horizontal = RuntimeShader(ProgressiveHeaderBlurShader)
    private val vertical = RuntimeShader(ProgressiveHeaderBlurShader)

    fun configure(
        width: Float,
        height: Float,
        radius: Float,
        blurEnd: Float,
        blurStart: Float,
    ): androidx.compose.ui.graphics.RenderEffect {
        for (shader in arrayOf(horizontal, vertical)) {
            shader.setFloatUniform("bounds", width, height)
            shader.setFloatUniform("radius", radius)
            shader.setFloatUniform("blurStart", blurStart)
            shader.setFloatUniform("blurEnd", blurEnd)
        }
        horizontal.setFloatUniform("direction", 1f, 0f)
        vertical.setFloatUniform("direction", 0f, 1f)
        return RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(vertical, "content"),
            RenderEffect.createRuntimeShaderEffect(horizontal, "content"),
        ).asComposeRenderEffect()
    }
}
