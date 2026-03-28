package com.asmr.player.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.MessageType

@Composable
fun AppSnackbar(
    messageId: Long,
    message: String,
    type: MessageType,
    count: Int,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val appColors = AsmrTheme.colorScheme
    val (icon, accentColor) = when (type) {
        MessageType.Success -> Icons.Default.CheckCircle to Color(0xFF3E9B63)
        MessageType.Error -> Icons.Default.Error to Color(0xFFD95C4F)
        MessageType.Warning -> Icons.Default.Warning to Color(0xFFD6942A)
        MessageType.Info -> Icons.Default.Info to Color(0xFF2F7FB6)
    }
    val shape: Shape = RoundedCornerShape(18.dp)
    val containerColor = if (appColors.isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
    } else {
        appColors.primarySoft.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface)
    }
    val borderColor = if (appColors.isDark) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
    } else {
        appColors.primary.copy(alpha = 0.16f)
    }
    val countTextColor = if (appColors.isDark) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        appColors.textSecondary
    }
    val progressTrackColor = if (appColors.isDark) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
    } else {
        accentColor.copy(alpha = 0.12f).compositeOver(containerColor)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor, shape)
            .border(1.dp, borderColor, shape)
    ) {
        val progress = remember(messageId) { Animatable(1f) }
        LaunchedEffect(messageId, durationMs) {
            progress.snapTo(1f)
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = durationMs.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    easing = LinearEasing
                )
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp, top = 10.dp, bottom = 10.dp)
                        .width(4.dp)
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor.copy(alpha = if (appColors.isDark) 0.84f else 0.72f))
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                        androidx.compose.material3.Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (count > 1) {
                            androidx.compose.material3.Text(
                                text = "x$count",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = countTextColor
                            )
                        }
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress.value.coerceIn(0f, 1f) },
                color = accentColor.copy(alpha = if (appColors.isDark) 0.85f else 0.72f),
                trackColor = progressTrackColor,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
            )
        }
    }
}

@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
        snackbar = { data ->
            val (message, type) = parseSnackbarMessage(data.visuals.message)
            AppSnackbar(
                messageId = 0L,
                message = message,
                type = type,
                count = 1,
                durationMs = 3_000L
            )
        }
    )
}

private fun parseSnackbarMessage(rawMessage: String): Pair<String, MessageType> {
    return when {
        rawMessage.startsWith("[SUCCESS]") -> rawMessage.removePrefix("[SUCCESS]") to MessageType.Success
        rawMessage.startsWith("[ERROR]") -> rawMessage.removePrefix("[ERROR]") to MessageType.Error
        rawMessage.startsWith("[WARNING]") -> rawMessage.removePrefix("[WARNING]") to MessageType.Warning
        rawMessage.startsWith("[INFO]") -> rawMessage.removePrefix("[INFO]") to MessageType.Info
        else -> rawMessage to MessageType.Info
    }
}
