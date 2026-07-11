package com.asmr.player.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun AsmrImageLoadingPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 6,
    indicatorSize: Dp = 28.dp,
    showGlow: Boolean = false
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = RoundedCornerShape(cornerRadius.dp)

    Box(
        modifier = modifier.clip(shape),
        contentAlignment = Alignment.Center
    ) {
        EaraLogoLoadingIndicator(
            size = indicatorSize,
            tint = colorScheme.primary,
            showGlow = showGlow
        )
    }
}
