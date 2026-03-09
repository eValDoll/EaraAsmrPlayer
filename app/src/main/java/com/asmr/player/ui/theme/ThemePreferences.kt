package com.asmr.player.ui.theme

data class ThemePreferences(
    val mode: ThemeMode,
    val dynamicPlayerHueEnabled: Boolean,
    val staticHueArgb: Int?
)

