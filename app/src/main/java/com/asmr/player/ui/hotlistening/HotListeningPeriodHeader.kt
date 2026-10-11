package com.asmr.player.ui.hotlistening

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asmr.player.hotlistening.HotListeningSortMode
import com.asmr.player.ui.common.interruptScrollableFlingOnPointerDown
import com.asmr.player.ui.theme.AsmrTheme

internal val HotListeningPeriodHeaderHeight = 40.dp

@Composable
internal fun HotListeningPeriodHeader(
    selectedPeriod: String,
    selectedSortMode: HotListeningSortMode,
    onPeriodSelected: (String) -> Unit,
    onSortSelected: (HotListeningSortMode) -> Unit,
    onStopScroll: () -> Unit,
) {
    val colors = AsmrTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HotListeningPeriodHeaderHeight)
            .interruptScrollableFlingOnPointerDown(onStopScroll)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            HotListeningPeriodSelector(selectedPeriod, onPeriodSelected)
        }
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onSortSelected(selectedSortMode.nextMode) },
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedSortMode.toggleLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Icon(
                imageVector = Icons.Rounded.FilterList,
                contentDescription = null,
                tint = colors.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun HotListeningPeriodSelector(
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit,
) {
    val colors = AsmrTheme.colorScheme
    val periods = remember { listOf("day" to "天", "week" to "周", "month" to "月") }
    val selectedLabel = periods.firstOrNull { it.first == selectedPeriod }?.second ?: "天"
    var expanded by remember { mutableStateOf(false) }
    val menuShape = RoundedCornerShape(8.dp)
    val menuContainer = if (colors.isDark) colors.surfaceVariant else colors.surface

    Box {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clickable(role = Role.DropdownList) { expanded = !expanded }
                .semantics {
                    contentDescription = "排行时间范围"
                    stateDescription = "$selectedLabel，${if (expanded) "已展开" else "已折叠"}"
                }
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Leaderboard,
                contentDescription = null,
                tint = colors.primaryStrong,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = colors.primary,
                surface = menuContainer,
                surfaceContainer = menuContainer,
                onSurface = colors.textPrimary,
            ),
            shapes = MaterialTheme.shapes.copy(extraSmall = menuShape),
        ) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(120.dp)
                    .background(menuContainer, menuShape)
                    .border(
                        width = 0.5.dp,
                        color = colors.primary.copy(alpha = if (colors.isDark) 0.28f else 0.20f),
                        shape = menuShape,
                    ),
            ) {
                periods.forEach { (period, label) ->
                    val isSelected = selectedPeriod == period
                    DropdownMenuItem(
                        text = { Text(label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
                        onClick = {
                            expanded = false
                            if (!isSelected) onPeriodSelected(period)
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = if (isSelected) colors.primaryStrong else colors.textPrimary,
                            trailingIconColor = colors.primaryStrong,
                        ),
                        modifier = Modifier
                            .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .semantics { selected = isSelected },
                    )
                }
            }
        }
    }
}

private val HotListeningSortMode.toggleLabel: String
    get() = when (this) {
        HotListeningSortMode.PlayCount -> "次数"
        HotListeningSortMode.ListenDuration -> "时长"
    }

private val HotListeningSortMode.nextMode: HotListeningSortMode
    get() = when (this) {
        HotListeningSortMode.PlayCount -> HotListeningSortMode.ListenDuration
        HotListeningSortMode.ListenDuration -> HotListeningSortMode.PlayCount
    }
