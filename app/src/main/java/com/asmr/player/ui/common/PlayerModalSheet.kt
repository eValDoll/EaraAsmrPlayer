package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val PlayerModalSheetMaxHeightFraction = 0.75f

internal fun playerModalSheetMaxHeightDp(screenHeightDp: Int): Float =
    screenHeightDp.coerceAtLeast(0) * PlayerModalSheetMaxHeightFraction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerModalSheet(
    onDismissRequest: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    content: @Composable (maxHeight: Dp) -> Unit
) {
    val configuration = LocalConfiguration.current
    val maxHeight = playerModalSheetMaxHeightDp(configuration.screenHeightDp).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
    )

    key(configuration.screenWidthDp, configuration.screenHeightDp) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = containerColor,
            contentColor = contentColor,
            scrimColor = Color.Transparent,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .windowInsetsPadding(StableWindowInsets.navigationBars)
            ) {
                content(maxHeight)
            }
        }
    }
}
