package com.asmr.player.data.settings

data class FloatingLyricsSettings(
    val color: Int = 0xFFFFFFFF.toInt(),
    val size: Float = 16f,
    val opacity: Float = 0.7f,
    val yOffset: Int = 120,
    val align: Int = 1, // 0:Left, 1:Center, 2:Right
    val touchable: Boolean = true
)
