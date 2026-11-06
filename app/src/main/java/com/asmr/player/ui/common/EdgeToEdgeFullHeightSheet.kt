package com.asmr.player.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EdgeToEdgeFullHeightSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier.fillMaxHeight(),
    shape: Shape = RectangleShape,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    scrimColor: Color = Color.Black.copy(alpha = 0.32f),
    content: @Composable () -> Unit
) {
    val tablet = LocalTabletLayout.current.isTablet
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.9f
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = (if (tablet) Modifier.heightIn(max = maxHeight) else Modifier).then(modifier).imePadding(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = if (tablet) 560.dp else Dp.Unspecified,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        scrimColor = scrimColor,
        dragHandle = null,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        content()
    }
}
