package com.asmr.player.ui.common.reorderable

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.detectReorderAfterLongPress(state: ReorderableState<*>) =
    this.then(
        Modifier.pointerInput(Unit) {
            while (true) {
                val down = awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                }
                awaitLongPressOrCancellation(down)?.also {
                    state.interactions.trySend(StartDrag(down.id))
                }
            }
        }
    )
