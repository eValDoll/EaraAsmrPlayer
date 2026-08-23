package com.asmr.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.asmr.player.ui.theme.AsmrTheme

@Immutable
internal data class LyricReadableColors(
    val activeText: Color,
    val inactiveText: Color,
    val accentEmphasis: Color,
    val activeContainer: Color
)

@Composable
internal fun rememberLyricReadableColors(
    accentColor: Color
): LyricReadableColors {
    val colorScheme = AsmrTheme.colorScheme
    return remember(accentColor, colorScheme) {
        val emphasisAlpha = if (colorScheme.isDark) 0.82f else 0.68f
        val containerAlpha = if (colorScheme.isDark) 0.26f else 0.16f
        val containerBase = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.80f else 0.88f)
        val activeText = themedLyricTextColor(colorScheme.isDark)

        LyricReadableColors(
            activeText = activeText,
            inactiveText = activeText.copy(alpha = 0.58f),
            accentEmphasis = accentColor.copy(alpha = emphasisAlpha),
            activeContainer = accentColor.copy(alpha = containerAlpha).compositeOver(containerBase)
        )
    }
}

internal fun themedLyricTextColor(isDark: Boolean): Color =
    if (isDark) Color.White else Color.Black
