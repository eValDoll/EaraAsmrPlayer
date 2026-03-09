package com.asmr.player.ui.sidepanel

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@Composable
fun LandscapeSidePanelsHost(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    centerMaxWidth: Dp = 720.dp,
    maxSideWidth: Dp = 320.dp,
    minSideContentWidth: Dp = 140.dp,
    sidePadding: Dp = 12.dp,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val wantSidePanels = !isCompact &&
        configuration.smallestScreenWidthDp >= 600 &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val extraPerSide = (maxWidth - centerMaxWidth) / 2f
        val sideColumnWidth = extraPerSide.coerceAtMost(maxSideWidth)
        val minSideColumnWidth = minSideContentWidth + sidePadding
        val show = wantSidePanels && sideColumnWidth >= minSideColumnWidth
        if (!show) {
            content()
            return@BoxWithConstraints
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(sideColumnWidth)
                    .fillMaxHeight()
                    .padding(start = sidePadding, top = sidePadding, bottom = sidePadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                left()
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                content()
            }
            Column(
                modifier = Modifier
                    .width(sideColumnWidth)
                    .fillMaxHeight()
                    .padding(end = sidePadding, top = sidePadding, bottom = sidePadding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                right()
            }
        }
    }
}
