package com.asmr.player.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.R
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.ui.theme.LocalThemeTransitionTrigger
import com.asmr.player.ui.theme.ThemeTransitionTriggerRequest

@Composable
internal fun EaraBrand(
    appName: String,
    tint: Color,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
) {
    val trigger = LocalThemeTransitionTrigger.current
    val isDark = AsmrTheme.colorScheme.isDark
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }

    val clickModifier = if (trigger != null) {
        Modifier
            .onGloballyPositioned { coordinates ->
                positionInRoot = coordinates.positionInWindow()
            }
            .pointerInput(isDark) {
                detectTapGestures {
                    val origin = Offset(
                        positionInRoot.x + it.x,
                        positionInRoot.y + it.y
                    )
                    val targetPref = if (isDark) "light" else "dark"
                    trigger(
                        ThemeTransitionTriggerRequest(
                            origin = origin,
                            targetPref = targetPref
                        )
                    )
                }
            }
    } else {
        Modifier
    }

    val content: @Composable () -> Unit = {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(38.dp)
        )
        Text(
            text = appName,
            style = if (stacked) {
                MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            },
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (stacked) {
        Column(
            modifier = modifier.fillMaxWidth().heightIn(min = 72.dp)
                .then(clickModifier).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { content() }
    } else {
        Row(
            modifier = modifier.padding(start = 6.dp).height(44.dp)
                .then(clickModifier).padding(start = 2.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    }
}
