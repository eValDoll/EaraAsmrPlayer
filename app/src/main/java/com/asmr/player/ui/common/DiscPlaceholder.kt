package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.asmr.player.R
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun DiscPlaceholder(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 6
) {
    val colorScheme = AsmrTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(colorScheme.surface.copy(alpha = 0.6f))
            .padding(6.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_placeholder_disc),
            contentDescription = null,
            tint = colorScheme.textTertiary.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxSize()
        )
    }
}

