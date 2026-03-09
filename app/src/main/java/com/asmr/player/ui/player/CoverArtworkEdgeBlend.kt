package com.asmr.player.ui.player

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.common.AsmrAsyncImage

@Composable
fun CoverArtworkEdgeBlend(
    artworkModel: Any?,
    blendColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val blurDp = 32.dp
    val blurModifier = remember(blurDp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
    val normalizedArtworkModel = remember(artworkModel) {
        when (artworkModel) {
            is String -> artworkModel.trim().takeIf { it.isNotEmpty() }
            else -> artworkModel
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(shape)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        AsmrAsyncImage(
            model = normalizedArtworkModel,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.12f
                    scaleY = 1.12f
                    alpha = 0.82f
                }
                .then(blurModifier),
            contentScale = ContentScale.Crop,
            placeholder = {},
            loading = {},
        )

        Box(modifier = Modifier.fillMaxSize().background(blendColor.copy(alpha = 0.06f)))

        AsmrAsyncImage(
            model = normalizedArtworkModel,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val fadeX = (0.20f).coerceIn(0.02f, 0.45f)
                    val fadeY = (0.20f).coerceIn(0.02f, 0.45f)
                    val horiz = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            fadeX to Color.Black,
                            (1f - fadeX) to Color.Black,
                            1.00f to Color.Transparent
                        )
                    )
                    val vert = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            fadeY to Color.Black,
                            (1f - fadeY) to Color.Black,
                            1.00f to Color.Transparent
                        )
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(horiz, blendMode = BlendMode.DstIn)
                        drawRect(vert, blendMode = BlendMode.DstIn)
                    }
                },
            contentScale = ContentScale.Crop,
            placeholderCornerRadius = cornerRadius.value.toInt(),
        )
    }
}
