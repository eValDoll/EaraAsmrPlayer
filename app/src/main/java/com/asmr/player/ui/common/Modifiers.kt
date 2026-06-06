package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

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

@Composable
fun Modifier.clearFocusOnTapOutside(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    return pointerInput(focusManager, keyboardController) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val start = down.position
            val touchSlop = viewConfiguration.touchSlop
            val touchSlopSquared = touchSlop * touchSlop
            var isTap = true
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (isTap) {
                    isTap = event.changes.none { change ->
                        val delta = change.position - start
                        delta.x * delta.x + delta.y * delta.y > touchSlopSquared
                    }
                }
            } while (event.changes.any { it.pressed })
            if (isTap) {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        }
    }
}
