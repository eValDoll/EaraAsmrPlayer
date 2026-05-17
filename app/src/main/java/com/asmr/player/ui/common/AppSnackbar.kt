package com.asmr.player.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    val shape = RoundedCornerShape(16.dp)
    val containerColor = MaterialTheme.colorScheme.surface
        .copy(alpha = if (appColors.isDark) 0.94f else 0.96f)
        .compositeOver(appColors.background)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(
        alpha = if (appColors.isDark) 0.34f else 0.24f
    )
    val countTextColor = if (appColors.isDark) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.92f)
    } else {
        appColors.textSecondary
    }
    val iconTint = accentColor.copy(alpha = if (appColors.isDark) 0.92f else 0.78f)
    val progressColor = accentColor.copy(alpha = if (appColors.isDark) 0.88f else 0.72f)
    val progressTrackColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (appColors.isDark) 0.10f else 0.06f
    ).compositeOver(containerColor)
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

    Box(
        modifier = modifier
            .widthIn(max = 288.dp)
            .clip(shape)
            .background(containerColor, shape)
            .drawWithContent {
                drawContent()
                val barInset = 1.dp.toPx()
                val barHeight = 1.5.dp.toPx()
                val barWidth = (size.width - barInset * 2f).coerceAtLeast(0f)
                if (barWidth <= 0f) return@drawWithContent

                val barTop = (size.height - barInset - barHeight).coerceAtLeast(0f)
                val barCorner = CornerRadius(x = barHeight, y = barHeight)
                drawRoundRect(
                    color = progressTrackColor,
                    topLeft = Offset(x = barInset, y = barTop),
                    size = Size(width = barWidth, height = barHeight),
                    cornerRadius = barCorner
                )

                val progressWidth = (barWidth * progress.value.coerceIn(0f, 1f)).coerceAtLeast(0f)
                if (progressWidth > 0f) {
                    drawRoundRect(
                        color = progressColor,
                        topLeft = Offset(x = barInset, y = barTop),
                        size = Size(width = progressWidth, height = barHeight),
                        cornerRadius = barCorner
                    )
                }
            }
            .border(1.dp, borderColor, shape)
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            androidx.compose.material3.Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    letterSpacing = 0.1.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 228.dp)
            )
            if (count > 1) {
                androidx.compose.material3.Text(
                    text = "x$count",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.sp
                    ),
                    color = countTextColor
                )
            }
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
                durationMs = 2_000L
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
