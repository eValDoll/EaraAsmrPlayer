package com.asmr.player.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
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
    accentColor: Color,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float
): LyricReadableColors {
    val colorScheme = AsmrTheme.colorScheme
    return remember(accentColor, coverBackgroundEnabled, coverBackgroundClarity, colorScheme) {
        val emphasisAlpha = if (colorScheme.isDark) 0.82f else 0.68f
        val containerAlpha = if (colorScheme.isDark) 0.26f else 0.16f
        val containerBase = colorScheme.surface.copy(alpha = if (colorScheme.isDark) 0.80f else 0.88f)
        val backdropEstimate = estimateLyricBackdropColor(
            accentColor = accentColor,
            backgroundColor = colorScheme.background,
            coverBackgroundEnabled = coverBackgroundEnabled,
            coverBackgroundClarity = coverBackgroundClarity,
            isDark = colorScheme.isDark
        )

        val activeText = if (coverBackgroundEnabled) {
            pickReadableLyricTextColor(
                backdrop = backdropEstimate,
                fallback = colorScheme.textPrimary
            )
        } else {
            colorScheme.textPrimary
        }
        val inactiveAlpha = if (coverBackgroundEnabled) {
            if (activeText.luminance() > 0.5f) 0.60f else 0.54f
        } else if (colorScheme.isDark) {
            0.54f
        } else {
            0.50f
        }
        val inactiveText = activeText.copy(alpha = inactiveAlpha)

        LyricReadableColors(
            activeText = activeText,
            inactiveText = inactiveText,
            accentEmphasis = accentColor.copy(alpha = emphasisAlpha),
            activeContainer = accentColor.copy(alpha = containerAlpha).compositeOver(containerBase)
        )
    }
}

private fun estimateLyricBackdropColor(
    accentColor: Color,
    backgroundColor: Color,
    coverBackgroundEnabled: Boolean,
    coverBackgroundClarity: Float,
    isDark: Boolean
): Color {
    if (!coverBackgroundEnabled) return backgroundColor

    val style = coverArtworkBackdropStyle(
        clarity = coverBackgroundClarity,
        isDark = isDark
    )
    var estimated = accentColor
    estimated = backgroundColor.copy(alpha = style.overlayAlpha).compositeOver(estimated)
    estimated = accentColor.copy(alpha = style.tintAlpha).compositeOver(estimated)
    if (style.scrimAlpha > 0f) {
        estimated = Color.Black.copy(alpha = style.scrimAlpha).compositeOver(estimated)
    }
    return estimated
}

private fun pickReadableLyricTextColor(
    backdrop: Color,
    fallback: Color
): Color {
    val lightCandidate = Color(0xFFF7FAFC)
    val darkCandidate = Color(0xFF111418)
    val backdropArgb = backdrop.toArgb()
    val lightContrast = ColorUtils.calculateContrast(lightCandidate.toArgb(), backdropArgb)
    val darkContrast = ColorUtils.calculateContrast(darkCandidate.toArgb(), backdropArgb)
    val best = if (lightContrast >= darkContrast) lightCandidate else darkCandidate
    val bestContrast = maxOf(lightContrast, darkContrast)
    return if (bestContrast >= 4.5) best else fallback
}
