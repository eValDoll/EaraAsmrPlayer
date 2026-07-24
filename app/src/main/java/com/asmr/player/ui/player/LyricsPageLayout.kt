package com.asmr.player.ui.player

import androidx.compose.ui.text.style.TextAlign
import com.asmr.player.data.settings.LyricsPageSettings
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

internal data class LyricsViewportLayout(
    val nominalItemHeightPx: Float,
    val viewportWindowHeightPx: Float,
    val viewportTopOffsetPx: Float
)

internal data class LyricVisibleItemFrame(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int
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
    val canUseMeasuredWindow = settings.displayAreaMode in 1..3
    val resolvedWindowHeightPx = if (canUseMeasuredWindow && measuredWindowHeightPx > 0f) {
        measuredWindowHeightPx.coerceAtMost(requestedWindowHeightPx)
    } else {
        requestedWindowHeightPx
    }
    val viewportWindowHeightPx = resolvedWindowHeightPx
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

internal fun centeredLyricFocusTop(
    viewportWindowHeightPx: Float,
    activeItemHeightPx: Float,
    nominalItemHeightPx: Float,
    stableFocusAnchor: Boolean
): Float {
    val focusHeightPx = if (stableFocusAnchor) nominalItemHeightPx else activeItemHeightPx
    return ((viewportWindowHeightPx - focusHeightPx) / 2f).coerceAtLeast(0f)
}

internal fun lyricTextAlign(align: Int): TextAlign = when (align) {
    0 -> TextAlign.Start
    2 -> TextAlign.End
    else -> TextAlign.Center
}

internal fun centeredLyricIndexForTimeline(
    visibleItems: List<LyricVisibleItemFrame>,
    viewportCenterPx: Float,
    totalCount: Int
): Int {
    if (totalCount <= 0 || !viewportCenterPx.isFinite()) return -1
    return visibleItems
        .asSequence()
        .filter { item -> item.index in 0 until totalCount && item.sizePx > 0 }
        .minWithOrNull(
            compareBy<LyricVisibleItemFrame> { item ->
                abs(item.offsetPx + item.sizePx / 2f - viewportCenterPx)
            }.thenBy { item -> item.index }
        )
        ?.index
        ?: -1
}
