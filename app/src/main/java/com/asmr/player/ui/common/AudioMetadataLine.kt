package com.asmr.player.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.AudioQuality

@Composable
internal fun AudioMetadataLine(
    text: String = "",
    trailingText: String = "",
    source: AudioSource? = null,
    quality: AudioQuality? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = AsmrTheme.colorScheme.textTertiary,
) {
    if (text.isBlank() && trailingText.isBlank() && source == null && quality == null) return
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (source != null) {
            Icon(
                imageVector = if (source == AudioSource.Online) Icons.Rounded.CloudQueue else Icons.Rounded.Smartphone,
                contentDescription = if (source == AudioSource.Online) "在线" else "本地",
                tint = color,
                modifier = Modifier.size(14.dp),
            )
        }
        // ListItem uses these baselines to distinguish one supporting line from multiple lines.
        if (quality != null) AudioQualityBadge(quality, Modifier.alignByBaseline())
        if (text.isNotBlank()) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (trailingText.isNotBlank()) {
            Text(
                text = trailingText,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
private fun AudioQualityBadge(quality: AudioQuality, modifier: Modifier = Modifier) {
    val colors = AsmrTheme.colorScheme
    val surface = colors.surface
    val dark = colors.isDark
    val tint = when (quality) {
        AudioQuality.HQ -> if (dark) Color(0xFFCCD8E5) else Color(0xFF526477)
        AudioQuality.SQ -> if (dark) Color(0xFFEBC779) else Color(0xFF8C601B)
    }
    Text(
        text = quality.name,
        color = tint,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            lineHeight = 12.sp,
            letterSpacing = 0.4.sp,
            fontWeight = FontWeight.Bold,
        ),
        maxLines = 1,
        modifier = modifier
            .drawWithCache {
                val radius = CornerRadius(3.dp.toPx())
                val stroke = 0.5.dp.toPx()
                val body = lerp(surface, tint, if (dark) 0.22f else 0.14f)
                val brush = Brush.verticalGradient(
                    0f to lerp(body, Color.White, if (dark) 0.14f else 0.46f),
                    0.44f to body,
                    0.52f to lerp(body, tint, 0.12f),
                    1f to lerp(body, Color.White, if (dark) 0.06f else 0.2f),
                )
                onDrawBehind {
                    drawRoundRect(brush, cornerRadius = radius)
                    drawRoundRect(
                        color = tint.copy(alpha = if (dark) 0.5f else 0.4f),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = radius,
                        style = Stroke(stroke),
                    )
                }
            }
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
