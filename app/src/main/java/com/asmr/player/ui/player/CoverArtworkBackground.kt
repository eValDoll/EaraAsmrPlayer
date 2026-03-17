package com.asmr.player.ui.player

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.AsmrAsyncImage
import kotlin.math.pow

@Composable
fun CoverArtworkBackground(
    artworkModel: Any?,
    enabled: Boolean,
    clarity: Float,
    overlayBaseColor: Color,
    tintBaseColor: Color,
    artworkAlignment: Alignment = Alignment.Center,
    isDark: Boolean = true
) {
    if (!enabled) return

    val style = remember(clarity, isDark) {
        coverArtworkBackdropStyle(clarity = clarity, isDark = isDark)
    }

    // Keep the backdrop anchored to the extracted cover hue, but keep the base veil lighter.
    val baseBackdropColor = remember(style.baseTintAlpha, overlayBaseColor, tintBaseColor) {
        tintBaseColor.copy(alpha = style.baseTintAlpha).compositeOver(overlayBaseColor)
    }
    Box(modifier = Modifier.fillMaxSize().background(baseBackdropColor))

    val artworkModifier = remember(style.blurDp) {
        if (style.blurDp.value <= 0f) {
            Modifier
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.graphicsLayer {
                val blurPx = style.blurDp.toPx()
                renderEffect = RenderEffect
                    .createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        } else {
            Modifier.blur(style.blurDp)
        }
    }

    if (artworkModel != null) {
        AsmrAsyncImage(
            model = artworkModel,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().then(artworkModifier),
            contentScale = ContentScale.Crop,
            alignment = artworkAlignment,
            alpha = style.artworkAlpha,
            loading = {},
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(overlayBaseColor.copy(alpha = style.overlayAlpha)))
    Box(modifier = Modifier.fillMaxSize().background(tintBaseColor.copy(alpha = style.tintAlpha)))

    if (style.scrimAlpha > 0f) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = style.scrimAlpha)))
    }
}

internal data class BackdropStyle(
    val blurDp: Dp,
    val baseTintAlpha: Float,
    val artworkAlpha: Float,
    val overlayAlpha: Float,
    val tintAlpha: Float,
    val scrimAlpha: Float
)

internal fun coverArtworkBackdropStyle(
    clarity: Float,
    isDark: Boolean
): BackdropStyle {
    val normalized = clarity.coerceIn(0f, 1f)
    // Lower clarity should change gently; higher clarity should open up faster.
    val reveal = normalized.pow(2.15f)
    return BackdropStyle(
        blurDp = lerpFloat(
            start = if (isDark) 30f else 28f,
            end = 0f,
            fraction = reveal
        ).dp,
        baseTintAlpha = lerpFloat(
            start = if (isDark) 0.64f else 0.52f,
            end = if (isDark) 0.12f else 0.04f,
            fraction = reveal
        ),
        artworkAlpha = lerpFloat(
            start = if (isDark) 0.01f else 0.008f,
            end = if (isDark) 0.96f else 0.985f,
            fraction = reveal
        ),
        overlayAlpha = lerpFloat(
            start = if (isDark) 0.12f else 0.07f,
            end = if (isDark) 0.02f else 0.008f,
            fraction = reveal
        ),
        tintAlpha = lerpFloat(
            start = if (isDark) 0.10f else 0.08f,
            end = if (isDark) 0.01f else 0.004f,
            fraction = reveal
        ),
        scrimAlpha = lerpFloat(
            start = if (isDark) 0.15f else 0.015f,
            end = if (isDark) 0.018f else 0f,
            fraction = reveal
        )
    )
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return start + (end - start) * t
}
