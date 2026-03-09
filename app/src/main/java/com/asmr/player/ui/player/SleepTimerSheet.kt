package com.asmr.player.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.asmr.player.ui.theme.AsmrTheme
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheetContent(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val colorScheme = AsmrTheme.colorScheme
    val endAtMs by viewModel.sleepTimerEndAtMs.collectAsState()
    val lastMin by viewModel.sleepTimerLastDurationMin.collectAsState()

    var remainingMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(endAtMs) {
        while (true) {
            val rem = (endAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
            remainingMs = rem
            if (endAtMs <= 0L || rem <= 0L) break
            delay(1000L)
        }
    }

    val isActive = remainingMs > 0L
    val remainingText = if (isActive) {
        "剩余 ${formatSleepTimerRemaining(remainingMs)}"
    } else {
        "未设置定时关闭"
    }

    var showCustomDialog by remember { mutableStateOf(false) }
    var customMinutesText by remember(lastMin) { mutableStateOf(lastMin.toString()) }

    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "定时关闭",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = colorScheme.textSecondary
            )
            TextButton(onClick = onDismiss) {
                Text("完成")
            }
        }

        Text(
            text = remainingText,
            modifier = Modifier.padding(horizontal = 20.dp),
            color = colorScheme.textPrimary,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        val presets = remember { listOf(5, 10, 15, 30, 60) }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.surface.copy(alpha = 0.5f),
            contentColor = colorScheme.onSurface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                presets.forEachIndexed { index, min ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSleepTimerMinutes(min)
                                onDismiss()
                            },
                        headlineContent = { Text("${min}分钟") },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = colorScheme.textPrimary,
                            supportingColor = colorScheme.textSecondary
                        )
                    )
                    if (index != presets.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = dividerColor
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = dividerColor
                )

                ListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomDialog = true },
                    headlineContent = { Text("自定义") },
                    supportingContent = { Text("输入分钟数") },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = colorScheme.textPrimary,
                        supportingColor = colorScheme.textSecondary
                    )
                )

                if (isActive) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = dividerColor
                    )
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.cancelSleepTimer()
                                onDismiss()
                            },
                        headlineContent = { Text("取消定时") },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = colorScheme.textPrimary,
                            supportingColor = colorScheme.textSecondary
                        )
                    )
                }
            }
        }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("自定义时长") },
            text = {
                OutlinedTextField(
                    value = customMinutesText,
                    onValueChange = { customMinutesText = it.filter { ch -> ch.isDigit() }.take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("输入分钟数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val minutes = customMinutesText.toIntOrNull() ?: 0
                        if (minutes > 0) {
                            viewModel.setSleepTimerMinutes(minutes)
                            onDismiss()
                        }
                        showCustomDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun formatSleepTimerRemaining(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
