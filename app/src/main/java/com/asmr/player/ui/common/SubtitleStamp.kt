package com.asmr.player.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun SubtitleStamp(
    text: String = "字幕",
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val verticalText = remember(text) {
        text.trim().toCharArray().joinToString("\n")
    }
    Surface(
        color = Color.Transparent,
        contentColor = colorScheme.primary,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.55f)),
        modifier = modifier
            .width(18.dp)
            .height(40.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = verticalText,
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 10.sp),
                color = colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}
