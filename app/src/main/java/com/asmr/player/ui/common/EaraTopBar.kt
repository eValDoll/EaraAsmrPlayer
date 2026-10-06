package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.AsmrTheme

internal val EaraMainTopBarHeight = 48.dp

internal fun resolveMainPageBackgroundColor(colorScheme: AsmrColorScheme): Color {
    return if (colorScheme.isDark) {
        colorScheme.background
    } else {
        colorScheme.primarySoft.copy(alpha = 0.16f).compositeOver(colorScheme.background)
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun EaraTopBarContainer(
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val pageBackground = resolveMainPageBackgroundColor(colorScheme)
    val statusBarHeight = StableWindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val tonalTop = colorScheme.primarySoft
        .copy(alpha = if (colorScheme.isDark) 0.44f else 0.38f)
        .compositeOver(pageBackground)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (blurEnabled) {
                    Modifier
                        .drawWithCache {
                            val maskStart = statusBarHeight.toPx() / 2f
                            val maskEnd = statusBarHeight.toPx() + EaraMainTopBarHeight.toPx() * 0.75f
                            val fadeStops = Array(9) { index ->
                                val progress = index / 8f
                                val alpha = 1f - progress * progress * (3f - 2f * progress)
                                progress to tonalTop.copy(alpha = alpha)
                            }
                            val upperMask = Brush.verticalGradient(
                                *fadeStops,
                                startY = maskStart,
                                endY = maskEnd,
                            )
                            onDrawBehind { drawRect(upperMask, size = Size(size.width, maskEnd)) }
                        }
                        .pointerInput(Unit) { }
                        .semantics { testTagsAsResourceId = true }
                        .testTag("main_header_progressive_blur")
                } else {
                    Modifier.background(Brush.verticalGradient(listOf(tonalTop, pageBackground)))
                }
            ),
        content = content
    )
}

@Composable
internal fun EaraTopBarIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        content = content
    )
}
