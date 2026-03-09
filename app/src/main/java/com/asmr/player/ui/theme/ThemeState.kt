package com.asmr.player.ui.theme

import androidx.compose.runtime.Immutable

@Immutable
data class ThemeState(
    val mode: ThemeMode,
    val neutral: NeutralPalette,
    val hue: HuePalette
)

