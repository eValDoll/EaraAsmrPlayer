package com.asmr.player.data.settings

data class LyricsPageSettings(
    val fontSizeSp: Float = 21f,
    val strokeWidthSp: Float = 0.1f,
    val lineHeightMultiplier: Float = 1.5f,
    val align: Int = 0, // 0: Left, 1: Center, 2: Right
    val displayAreaMode: Int = 0 // 0: Full, 1: Top Quarter, 2: Middle Quarter, 3: Bottom Quarter
)
