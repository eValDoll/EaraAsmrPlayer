package com.asmr.player.ui.common

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable

/**
 * Insets that remain stable while system bars animate in and out.
 */
internal object StableWindowInsets {
    val statusBars: WindowInsets
        @Composable
        @OptIn(ExperimentalLayoutApi::class)
        get() = WindowInsets.statusBarsIgnoringVisibility

    val navigationBars: WindowInsets
        @Composable
        @OptIn(ExperimentalLayoutApi::class)
        get() = WindowInsets.navigationBarsIgnoringVisibility
}
