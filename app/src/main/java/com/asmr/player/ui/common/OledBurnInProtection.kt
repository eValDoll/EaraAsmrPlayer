package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

internal const val OledBurnInShiftIntervalMillis = 60_000L
internal const val OledBurnInMaxShiftPixels = 2

internal val OledBurnInPixelShiftPattern = listOf(
    IntOffset(0, 0),
    IntOffset(1, 0),
    IntOffset(1, 1),
    IntOffset(0, 1),
    IntOffset(-1, 1),
    IntOffset(-1, 0),
    IntOffset(-1, -1),
    IntOffset(0, -1),
    IntOffset(1, -1),
    IntOffset(2, -1),
    IntOffset(2, 0),
    IntOffset(2, 1),
    IntOffset(2, 2),
    IntOffset(1, 2),
    IntOffset(0, 2),
    IntOffset(-1, 2),
    IntOffset(-2, 2),
    IntOffset(-2, 1),
    IntOffset(-2, 0),
    IntOffset(-2, -1),
    IntOffset(-2, -2),
    IntOffset(-1, -2),
    IntOffset(0, -2),
    IntOffset(1, -2),
    IntOffset(2, -2),
    IntOffset(1, -2),
    IntOffset(1, -1),
    IntOffset(0, -1)
)

@Composable
internal fun OledBurnInProtectionBox(
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val pixelShift = rememberOledBurnInPixelShift()
    Box(modifier = modifier.background(backgroundColor)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = pixelShift.value.x.toFloat()
                    translationY = pixelShift.value.y.toFloat()
                },
            content = content
        )
    }
}

@Composable
internal fun rememberOledBurnInPixelShift(): State<IntOffset> {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(
        initialValue = IntOffset.Zero,
        key1 = lifecycleOwner
    ) {
        try {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var stepIndex = 0
                value = OledBurnInPixelShiftPattern[stepIndex]
                try {
                    while (true) {
                        delay(OledBurnInShiftIntervalMillis)
                        stepIndex = (stepIndex + 1) % OledBurnInPixelShiftPattern.size
                        value = OledBurnInPixelShiftPattern[stepIndex]
                    }
                } finally {
                    value = IntOffset.Zero
                }
            }
        } finally {
            value = IntOffset.Zero
        }
    }
}
