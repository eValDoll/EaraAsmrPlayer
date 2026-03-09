package com.asmr.player.ui.sidepanel

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

import com.asmr.player.ui.common.ActionButton

@Composable
fun LandscapeRightPanelHost(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    minContentWidth: Dp = 560.dp,
    maxPanelWidth: Dp = 420.dp,
    minPanelWidth: Dp = 300.dp,
    panelPadding: Dp = 12.dp,
    topPanel: @Composable () -> Unit,
    bottomPanel: (@Composable () -> Unit)? = null,
    bottomHeightFraction: Float = 0.3f,
    minBottomHeight: Dp = 220.dp,
    content: @Composable (
        contentModifier: Modifier,
        hasRightPanel: Boolean,
        rightPanelToggle: (@Composable (modifier: Modifier) -> Unit)?
    ) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val wantPanel = !isCompact &&
        configuration.smallestScreenWidthDp >= 600 &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val externalExpandedState = LocalRightPanelExpandedState.current
    var internalExpanded by rememberSaveable { mutableStateOf(true) }
    val expanded = externalExpandedState?.value ?: internalExpanded
    val setExpanded: (Boolean) -> Unit = remember(externalExpandedState) {
        { v ->
            if (externalExpandedState != null) {
                externalExpandedState.value = v
            } else {
                internalExpanded = v
            }
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val panelWidth = (maxWidth - minContentWidth).coerceAtMost(maxPanelWidth)
        val eligible = wantPanel && panelWidth >= minPanelWidth

        if (!eligible) {
            content(Modifier.fillMaxSize(), false, null)
            return@BoxWithConstraints
        }

        val topPad = panelPadding * 0.5f
        val panelVisibilityState = remember { MutableTransitionState(expanded) }
        panelVisibilityState.targetState = expanded
        val keepPanelSpace = panelVisibilityState.currentState || panelVisibilityState.targetState
        val toggle: @Composable (Modifier) -> Unit = { modifier ->
            ActionButton(
                icon = if (expanded) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                onClick = { setExpanded(!expanded) },
                modifier = modifier
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    content(Modifier.fillMaxSize(), true, toggle)
                }

                if (keepPanelSpace) {
                    Box(
                        modifier = Modifier
                            .width(panelWidth)
                            .fillMaxHeight()
                            .padding(end = panelPadding, top = topPad, bottom = panelPadding)
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = panelVisibilityState,
                            enter = slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn(animationSpec = tween(220)),
                            exit = slideOutHorizontally(animationSpec = tween(180)) { it } + fadeOut(animationSpec = tween(180))
                        ) {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val headerHeight = 0.dp
                                val bottomHeight = if (bottomPanel == null || bottomHeightFraction <= 0f) {
                                    0.dp
                                } else {
                                    (maxHeight * bottomHeightFraction)
                                        .coerceAtLeast(minBottomHeight)
                                        .coerceAtMost((maxHeight - headerHeight).coerceAtLeast(0.dp))
                                }
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(headerHeight)
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        topPanel()
                                    }
                                    if (bottomPanel != null && bottomHeight > 0.dp) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(bottomHeight)
                                        ) {
                                            bottomPanel()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
