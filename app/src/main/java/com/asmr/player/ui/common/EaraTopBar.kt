package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.AsmrTheme

internal fun resolveMainPageBackgroundColor(colorScheme: AsmrColorScheme): Color {
    return if (colorScheme.isDark) {
        colorScheme.background
    } else {
        colorScheme.primarySoft.copy(alpha = 0.16f).compositeOver(colorScheme.background)
    }
}

@Composable
internal fun EaraTopBarContainer(
    overlay: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    if (overlay) {
        Box(modifier = modifier.fillMaxWidth(), content = content)
        return
    }

    val colorScheme = AsmrTheme.colorScheme
    val pageBackground = resolveMainPageBackgroundColor(colorScheme)
    val tonalTop = colorScheme.primarySoft
        .copy(alpha = if (colorScheme.isDark) 0.44f else 0.38f)
        .compositeOver(pageBackground)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(tonalTop, pageBackground)
                )
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
