package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import android.os.Build
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

fun Modifier.glassCard(
    shape: Shape,
    baseColor: Color = Color.White.copy(alpha = 0.1f),
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent
) = this
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                baseColor.copy(alpha = baseColor.alpha * 1.2f),
                baseColor
            )
        ),
        shape = shape
    )
    .then(if (borderWidth > 0.dp) Modifier.border(width = borderWidth, color = borderColor, shape = shape) else Modifier)
    .clip(shape)

fun Modifier.glassMenu(
    shape: Shape = RectangleShape,
    baseColor: Color = Color.Black.copy(alpha = 0.4f),
    elevation: Dp = 12.dp,
    isDark: Boolean = false
) = this
    .shadow(elevation = elevation, shape = shape, clip = false)
    .then(
        if (isDark) {
            Modifier.border(width = 1.dp, color = Color.White.copy(alpha = 0.2f), shape = shape)
        } else Modifier
    )
    .background(color = baseColor, shape = shape)
    .clip(shape)
