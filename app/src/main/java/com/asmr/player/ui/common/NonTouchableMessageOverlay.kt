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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
fun NonTouchableAppMessageOverlay(
    messages: List<VisibleAppMessage>,
    startPadding: Dp = 16.dp,
    bottomPadding: Dp = 80.dp
) {
    if (messages.isEmpty()) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f),
        contentAlignment = Alignment.BottomStart
    ) {
        AppMessageOverlay(
            messages = messages,
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Start + WindowInsetsSides.Bottom
                    )
                )
                .padding(
                    start = startPadding,
                    bottom = bottomPadding + LocalBottomOverlayPadding.current
                )
        )
    }
}
