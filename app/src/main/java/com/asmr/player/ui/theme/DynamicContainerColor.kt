package com.asmr.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

internal fun dynamicPageContainerColor(colorScheme: AsmrColorScheme): Color {
    return colorScheme.primarySoft.copy(alpha = 0.16f).compositeOver(colorScheme.background)
}
