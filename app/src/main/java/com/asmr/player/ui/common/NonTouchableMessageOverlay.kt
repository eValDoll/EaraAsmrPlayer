package com.asmr.player.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

internal val AppMessageOverlayAlignment: Alignment = Alignment.TopCenter
internal val AppMessageOverlayTopPadding = EaraMainTopBarHeight + 8.dp

@Composable
fun NonTouchableAppMessageOverlay(
    messages: List<VisibleAppMessage>
) {
    if (messages.isEmpty()) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f),
        contentAlignment = AppMessageOverlayAlignment
    ) {
        AppMessageOverlay(
            messages = messages,
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                    )
                )
                .padding(
                    start = 16.dp,
                    top = AppMessageOverlayTopPadding,
                    end = 16.dp,
                    bottom = 0.dp
                )
        )
    }
}
