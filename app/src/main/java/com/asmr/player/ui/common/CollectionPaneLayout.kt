package com.asmr.player.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun CollectionPaneLayout(
    hasDetail: Boolean,
    modifier: Modifier = Modifier,
    detail: @Composable () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val layout = LocalTabletLayout.current
    if (layout.twoPane && hasDetail) {
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.width(layout.masterWidth).fillMaxHeight(), content = content)
            VerticalDivider(color = AsmrTheme.colorScheme.textSecondary.copy(alpha = 0.18f))
            TabletPage(
                modifier = Modifier.weight(1f).padding(top = LocalMainHeaderPadding.current),
                embedded = true,
            ) {
                CompositionLocalProvider(LocalMainHeaderPadding provides 0.dp) { detail() }
            }
        }
    } else {
        Box(modifier.fillMaxSize(), content = content)
    }
}
