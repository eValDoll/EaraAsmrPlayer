package com.asmr.player.ui

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
internal fun testWindowSizeClass(): WindowSizeClass {
    return WindowSizeClass.calculateFromSize(DpSize(390.dp, 844.dp))
}
