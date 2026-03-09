package com.asmr.player.ui.common

internal const val ArgbWhite: Int = 0xFFFFFFFF.toInt()
internal const val ArgbBlack: Int = 0xFF000000.toInt()

internal fun pickBlackOrWhiteTextColorArgbFromBackgroundLuminance(
    backgroundLuminance: Double?,
    minContrastRatio: Double = 4.5
): Int? {
    val lum = backgroundLuminance ?: return null
    if (!lum.isFinite()) return null
    val clamped = lum.coerceIn(0.0, 1.0)
    val contrastWhite = (1.0 + 0.05) / (clamped + 0.05)
    val contrastBlack = (clamped + 0.05) / 0.05
    val pick = if (contrastWhite >= contrastBlack) ArgbWhite else ArgbBlack
    val contrastPick = maxOf(contrastWhite, contrastBlack)
    return if (contrastPick >= minContrastRatio) pick else null
}
