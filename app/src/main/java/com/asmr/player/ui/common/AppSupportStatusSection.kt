package com.asmr.player.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.ui.drawer.DrawerStatusViewModel
import com.asmr.player.ui.drawer.SiteStatus
import com.asmr.player.ui.drawer.SiteStatusType
import com.asmr.player.ui.drawer.StatisticsViewModel
import com.asmr.player.ui.theme.AsmrTheme

@Composable
fun AppSupportStatusSection(
    modifier: Modifier = Modifier,
    statisticsViewModel: StatisticsViewModel = hiltViewModel(),
    drawerStatusViewModel: DrawerStatusViewModel = hiltViewModel()
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DailyStatisticsSection(viewModel = statisticsViewModel)
        SiteStatusSection(viewModel = drawerStatusViewModel)
    }
}

@Composable
fun DailyStatisticsSection(
    viewModel: StatisticsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val stats by viewModel.todayStats.collectAsState()
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val shape = RoundedCornerShape(16.dp)
    val elevation = if (isDark) 0.dp else 1.dp
    val containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isDark) {
                    Modifier.border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = shape
                    )
                } else {
                    Modifier
                }
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "今日收听统计",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(
                    icon = Icons.Default.AccessTime,
                    label = "时长",
                    value = formatStatsDuration(stats?.listeningDurationMs ?: 0L)
                )
                StatItem(
                    icon = Icons.Default.Audiotrack,
                    label = "音轨",
                    value = "${stats?.trackCount ?: 0}"
                )
                StatItem(
                    icon = Icons.Default.CloudDownload,
                    label = "流量",
                    value = formatStatsTraffic(stats?.networkTrafficBytes ?: 0L)
                )
            }
        }
    }
}

@Composable
fun SiteStatusSection(
    viewModel: DrawerStatusViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val colorScheme = AsmrTheme.colorScheme
    val dlsite by viewModel.dlsite.collectAsState()
    val asmr by viewModel.asmr.collectAsState()
    val site by viewModel.asmrOneSite.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "站点状态测试",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary
        )
        SiteStatusRow(
            name = "dlsite.com",
            status = dlsite,
            onTest = viewModel::testDlsite
        )
        SiteStatusRow(
            status = asmr,
            onTest = viewModel::testAsmrOne,
            nameContent = {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { expanded = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "asmr-$site",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.textPrimary,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = colorScheme.textSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        listOf(100, 200, 300).forEach { option ->
                            val selected = option == site
                            DropdownMenuItem(
                                text = { Text(option.toString()) },
                                onClick = {
                                    viewModel.setAsmrOneSite(option)
                                    expanded = false
                                },
                                leadingIcon = {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = colorScheme.primary
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun SiteStatusRow(
    status: SiteStatus,
    onTest: () -> Unit,
    name: String? = null,
    nameContent: (@Composable RowScope.() -> Unit)? = null
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val dotColor = when (status.type) {
        SiteStatusType.Ok -> Color(0xFF2E7D32)
        SiteStatusType.Fail -> Color(0xFFC62828)
        SiteStatusType.Testing -> Color(0xFFF9A825)
        SiteStatusType.Unknown -> colorScheme.onSurface.copy(alpha = 0.35f)
    }
    val statusIcon = when (status.type) {
        SiteStatusType.Ok -> Icons.Default.Check
        SiteStatusType.Fail -> Icons.Default.Close
        SiteStatusType.Testing -> Icons.Default.Refresh
        SiteStatusType.Unknown -> null
    }

    var rotationAngle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(status.type) {
        if (status.type == SiteStatusType.Testing) {
            while (true) {
                rotationAngle = (rotationAngle + 10f) % 360f
                kotlinx.coroutines.delay(50)
            }
        } else {
            rotationAngle = 0f
        }
    }

    val shape = RoundedCornerShape(16.dp)
    val elevation = if (isDark) 0.dp else 1.dp
    val containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDark) {
                    Modifier.border(
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = shape
                    )
                } else {
                    Modifier
                }
            ),
        shape = shape,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            if (name != null) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.textPrimary,
                    maxLines = 1
                )
            } else if (nameContent != null) {
                nameContent()
            }

            Spacer(modifier = Modifier.weight(1f))

            if (statusIcon != null) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = rotationAngle },
                    tint = dotColor
                )
            }

            FilledTonalButton(
                onClick = onTest,
                modifier = Modifier
                    .height(30.dp)
                    .widthIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                    containerColor = colorScheme.primarySoft,
                    contentColor = colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "测试", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    val colorScheme = AsmrTheme.colorScheme
    val isDark = colorScheme.isDark
    val iconBackground = if (isDark) Color(0xFF1E1E1E) else Color.White

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (isDark) {
                        Modifier
                    } else {
                        Modifier.shadow(elevation = 1.dp, shape = CircleShape, clip = false)
                    }
                )
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colorScheme.textSecondary
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = colorScheme.textPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary,
            fontSize = 10.sp
        )
    }
}

private fun formatStatsDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "${hours}h${minutes % 60}m"
    } else {
        "${minutes}m"
    }
}

private fun formatStatsTraffic(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1fG", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024 * 1024 -> String.format("%.1fM", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format("%.1fK", bytes / 1024.0)
        else -> "${bytes}B"
    }
}
