package com.asmr.player.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

@Composable
fun PlayerTheme(
    artworkModel: Any?,
    mode: ThemeMode,
    dynamicHueEnabled: Boolean,
    staticHue: HuePalette? = null,
    content: @Composable () -> Unit
) {
    val neutral = remember(mode) { neutralPaletteForMode(mode) }
    val baseStaticHue = remember(mode, neutral, staticHue) {
        staticHue ?: deriveHuePalette(
            primary = if (mode.isDark) DefaultBrandPrimaryDark else DefaultBrandPrimaryLight,
            mode = mode,
            neutral = neutral,
            fallbackOnPrimary = if (mode.isDark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Black
        )
    }

    if (!dynamicHueEnabled) {
        AsmrPlayerTheme(mode = mode, hue = baseStaticHue, content = content)
        return
    }

    val dynamicHue by rememberDynamicHuePalette(
        artworkModel = artworkModel,
        mode = mode,
        neutral = neutral,
        fallbackHue = baseStaticHue
    )
    AsmrPlayerTheme(mode = mode, hue = dynamicHue, content = content)
}

