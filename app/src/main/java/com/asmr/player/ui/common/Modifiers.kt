package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

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

fun Modifier.consumeTapThrough(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { change -> change.consume() }
            } while (event.changes.any { it.pressed })
        }
    }

@Composable
fun Modifier.clearFocusOnTapOutside(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    return pointerInput(focusManager, keyboardController) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            val start = down.position
            val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis.toLong()
            val touchSlop = viewConfiguration.touchSlop
            val touchSlopSquared = touchSlop * touchSlop
            var isTap = true
            var isLongPress = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (isTap) {
                    isTap = event.changes.none { change ->
                        val delta = change.position - start
                        delta.x * delta.x + delta.y * delta.y > touchSlopSquared
                    }
                }
                if (!isLongPress) {
                    val activeChange = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                    isLongPress =
                        activeChange != null &&
                            activeChange.uptimeMillis - down.uptimeMillis >= longPressTimeoutMillis
                }
            } while (event.changes.any { it.pressed })
            if (isTap && !isLongPress) {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        }
    }
}
