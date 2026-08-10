package com.asmr.player.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asmr.player.data.remote.api.AsmrOneEndpoint
import com.asmr.player.ui.drawer.SiteStatus
import com.asmr.player.ui.drawer.SiteStatusType
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.delay

private const val FAST_LATENCY_MAX_MS = 300L
private const val MEDIUM_LATENCY_MAX_MS = 800L

internal data class SiteLatencyDisplay(
    val label: String,
    val activeBars: Int
)

internal fun siteLatencyDisplay(status: SiteStatus): SiteLatencyDisplay {
    val latency = status.latencyMs
    val activeBars = when {
        status.type != SiteStatusType.Ok || latency == null -> 0
        latency <= FAST_LATENCY_MAX_MS -> 3
        latency <= MEDIUM_LATENCY_MAX_MS -> 2
        else -> 1
    }
    val label = when (status.type) {
        SiteStatusType.Ok -> latency?.let { "$it ms" } ?: "— ms"
        SiteStatusType.Fail -> "失败"
        SiteStatusType.Testing -> "测试中"
        SiteStatusType.Unknown -> "未测试"
    }
    return SiteLatencyDisplay(label = label, activeBars = activeBars)
}

@Composable
internal fun AsmrOneSiteSelector(
    selectedSite: Int,
    onSiteSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val triggerShape = RoundedCornerShape(10.dp)
    val triggerContainer = lerp(
        colorScheme.surface,
        colorScheme.primarySoft,
        if (colorScheme.isDark) 0.30f else 0.62f
    )
    val menuContainer = lerp(
        colorScheme.surface,
        colorScheme.primarySoft,
        if (colorScheme.isDark) 0.24f else 0.46f
    )
    val selectedContainer = lerp(
        colorScheme.surface,
        colorScheme.primarySoft,
        if (colorScheme.isDark) 0.52f else 0.88f
    )
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .heightIn(min = 30.dp)
                .clip(triggerShape)
                .background(triggerContainer)
                .border(
                    border = BorderStroke(1.dp, colorScheme.primaryStrong.copy(alpha = 0.22f)),
                    shape = triggerShape
                )
                .clickable(
                    role = Role.Button,
                    onClick = { expanded = true }
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = AsmrOneEndpoint.displayName(selectedSite),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = "选择站点",
                modifier = Modifier.size(18.dp),
                tint = colorScheme.primaryStrong
            )
        }

        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = menuContainer,
                surfaceVariant = menuContainer,
                onSurface = colorScheme.textPrimary,
                onSurfaceVariant = colorScheme.textSecondary
            )
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(80.dp)
                    .background(menuContainer, RoundedCornerShape(10.dp))
            ) {
                AsmrOneEndpoint.options.forEach { option ->
                    val selected = option == selectedSite
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = AsmrOneEndpoint.displayName(option),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) {
                                    colorScheme.primaryStrong
                                } else {
                                    colorScheme.textPrimary
                                },
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            expanded = false
                            onSiteSelected(option)
                        },
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) selectedContainer else Color.Transparent),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun SiteStatusTestRow(
    status: SiteStatus,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
    name: String? = null,
    nameContent: (@Composable () -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val containerColor = lerp(
        colorScheme.surface,
        colorScheme.primarySoft,
        if (colorScheme.isDark) 0.10f else 0.16f
    )

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(
                border = BorderStroke(
                    1.dp,
                    colorScheme.primaryStrong.copy(alpha = if (colorScheme.isDark) 0.20f else 0.12f)
                ),
                shape = shape
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (colorScheme.isDark) 0.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (name != null) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                nameContent?.invoke()
            }

            Spacer(modifier = Modifier.weight(1f))
            SiteLatencyIndicator(status = status)
            FilledTonalButton(
                onClick = onTest,
                enabled = status.type != SiteStatusType.Testing,
                modifier = Modifier
                    .height(30.dp)
                    .widthIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorScheme.primarySoft,
                    contentColor = colorScheme.onPrimaryContainer,
                    disabledContainerColor = colorScheme.primarySoft.copy(alpha = 0.48f),
                    disabledContentColor = colorScheme.onPrimaryContainer.copy(alpha = 0.54f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "测试", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun SiteLatencyIndicator(
    status: SiteStatus,
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val latency = status.latencyMs
    val display = siteLatencyDisplay(status)
    val statusColor = when {
        status.type == SiteStatusType.Fail -> colorScheme.danger
        status.type == SiteStatusType.Testing -> colorScheme.primaryStrong
        status.type != SiteStatusType.Ok || latency == null -> colorScheme.textTertiary
        latency <= FAST_LATENCY_MAX_MS -> if (colorScheme.isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
        latency <= MEDIUM_LATENCY_MAX_MS -> if (colorScheme.isDark) Color(0xFFFFB74D) else Color(0xFFA86100)
        else -> colorScheme.danger
    }

    var rotationAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(status.type) {
        if (status.type == SiteStatusType.Testing) {
            while (true) {
                rotationAngle = (rotationAngle + 10f) % 360f
                delay(50)
            }
        } else {
            rotationAngle = 0f
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (status.type == SiteStatusType.Testing) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = rotationAngle },
                tint = statusColor
            )
        } else {
            SignalBars(
                activeBars = display.activeBars,
                color = statusColor
            )
        }
        Text(
            text = display.label,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontWeight = if (status.type == SiteStatusType.Ok) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun SignalBars(
    activeBars: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(14.dp)
            .semantics {
                contentDescription = if (activeBars == 0) {
                    "无可用信号"
                } else {
                    "信号强度 $activeBars 格"
                }
            },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(5.dp, 9.dp, 13.dp).forEachIndexed { index, barHeight ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (index < activeBars) color else color.copy(alpha = 0.20f)
                    )
            )
        }
    }
}
