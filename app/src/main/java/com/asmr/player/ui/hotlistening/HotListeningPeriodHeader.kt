package com.asmr.player.ui.hotlistening

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
    val periods = remember { listOf("day" to "过去一天", "week" to "过去一周", "month" to "过去一月") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HotListeningPeriodHeaderHeight)
            .interruptScrollableFlingOnPointerDown(onStopScroll)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            periods.forEach { (period, label) ->
                val selected = selectedPeriod == period
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(32.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onPeriodSelected(period) })
                        .padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                        textDecoration = if (selected) TextDecoration.Underline else TextDecoration.None,
                        color = if (selected) colors.primaryStrong else colors.onSurface,
                        maxLines = 1,
                    )
                }
            }
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
