package com.asmr.player.ui.player

import androidx.compose.ui.text.style.TextAlign
import com.asmr.player.data.settings.LyricsPageSettings
import kotlin.math.floor
import kotlin.math.max

internal data class LyricsViewportLayout(
    val nominalItemHeightPx: Float,
    val viewportWindowHeightPx: Float,
    val viewportTopOffsetPx: Float
)

internal fun buildLyricsViewportLayout(
    settings: LyricsPageSettings,
    viewportHeightPx: Float,
    nominalItemHeightPx: Float,
    measuredWindowHeightPx: Float = 0f
): LyricsViewportLayout {
    val quarterHeightPx = (viewportHeightPx * 0.25f).coerceAtLeast(nominalItemHeightPx)
    val requestedWindowHeightPx = when (settings.displayAreaMode) {
        1, 2, 3 -> quarterHeightPx
        else -> viewportHeightPx
    }
    val viewportWindowHeightPx = maxOf(requestedWindowHeightPx, measuredWindowHeightPx.coerceAtMost(requestedWindowHeightPx))
        .coerceAtMost(viewportHeightPx)
        .coerceAtLeast(nominalItemHeightPx)
    val viewportTopOffsetPx = when (settings.displayAreaMode) {
        1 -> 0f
        2 -> ((viewportHeightPx - viewportWindowHeightPx) / 2f).coerceAtLeast(0f)
        3 -> (viewportHeightPx - viewportWindowHeightPx).coerceAtLeast(0f)
        else -> 0f
    }
    return LyricsViewportLayout(
        nominalItemHeightPx = nominalItemHeightPx,
        viewportWindowHeightPx = viewportWindowHeightPx,
        viewportTopOffsetPx = viewportTopOffsetPx
    )
}

internal fun calculateRuntimeMaxVisibleLines(
    viewportHeightPx: Float,
    lineBlockHeightPx: Float
): Int {
    if (viewportHeightPx <= 0f || lineBlockHeightPx <= 0f) return 1
    return max(1, floor(viewportHeightPx / lineBlockHeightPx).toInt())
}

internal fun lyricTextAlign(align: Int): TextAlign = when (align) {
    0 -> TextAlign.Start
    2 -> TextAlign.End
    else -> TextAlign.Center
}
