package com.asmr.player.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.asmr.player.data.local.db.entities.ListeningSessionEntity
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.StableWindowInsets
import com.asmr.player.ui.common.albumCoverImageModel
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.ListeningDay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Eara 日历"：用户的本地收听记录 / 日记面板。
 *
 * 由原"DLsite 登录"页扩展而来：登录能力弱化为二级入口（顶部按钮），
 * 主体是收听热度图（贡献图）+ 汇总统计，点击某天可查看当日的垂直时间线。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningCalendarScreen(
    windowSizeClass: WindowSizeClass,
    onOpenDlsiteLogin: () -> Unit,
    onOpenAlbum: (albumId: Long, rjCode: String) -> Unit,
    viewModel: ListeningCalendarViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsState()
    val heatmap by viewModel.heatmap.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedSessions by viewModel.selectedSessions.collectAsState()
    val colorScheme = AsmrTheme.colorScheme
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(StableWindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = (if (isCompact) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            })
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DlsiteAccountRow(onOpenDlsiteLogin = onOpenDlsiteLogin, colorScheme = colorScheme)

            SummaryCards(summary = summary, colorScheme = colorScheme)

            HeatmapSection(
                days = heatmap,
                colorScheme = colorScheme,
                onDayClick = { viewModel.selectDate(it) }
            )
        }
    }

    if (selectedDate != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectDate(null) },
            sheetState = sheetState
        ) {
            DayTimeline(
                date = selectedDate!!,
                sessions = selectedSessions,
                colorScheme = colorScheme,
                onOpenAlbum = { albumId, rjCode ->
                    scope.launch { sheetState.hide() }
                    viewModel.selectDate(null)
                    onOpenAlbum(albumId, rjCode)
                }
            )
        }
    }
}

@Composable
private fun DlsiteAccountRow(
    onOpenDlsiteLogin: () -> Unit,
    colorScheme: AsmrColorScheme
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colorScheme.textSecondary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "DLsite 账号",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.textPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "用于「已购」搜索鉴权",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textSecondary
            )
        }
        OutlinedButton(onClick = onOpenDlsiteLogin) {
            Text("管理")
        }
    }
}

@Composable
private fun SummaryCards(
    summary: ListeningSummary,
    colorScheme: AsmrColorScheme
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "累计收听",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCell(
                icon = Icons.Rounded.AccessTime,
                label = "总时长",
                value = formatDurationLong(summary.totalDurationMs),
                colorScheme = colorScheme,
                modifier = Modifier.weight(1f)
            )
            SummaryCell(
                icon = Icons.Rounded.Audiotrack,
                label = "音轨",
                value = "${summary.totalTrackCount}",
                colorScheme = colorScheme,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCell(
                icon = Icons.Rounded.EventAvailable,
                label = "活跃天数",
                value = "${summary.activeDayCount}",
                colorScheme = colorScheme,
                modifier = Modifier.weight(1f)
            )
            SummaryCell(
                icon = Icons.Rounded.CloudDownload,
                label = "流量",
                value = formatTraffic(summary.totalTrafficBytes),
                colorScheme = colorScheme,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    colorScheme: AsmrColorScheme,
    modifier: Modifier = Modifier
) {
    val isDark = colorScheme.isDark
    val containerColor = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(
                if (isDark) {
                    Modifier.border(BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)), shape)
                } else Modifier
            )
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colorScheme.textSecondary
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
}

@Composable
private fun HeatmapSection(
    days: List<HeatmapDay>,
    colorScheme: AsmrColorScheme,
    onDayClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "收听热度",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary
        )
        if (days.isEmpty()) {
            Text(
                text = "还没有收听记录，去听点什么吧～",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textSecondary
            )
        } else {
            val columns = remember(days) { buildHeatmapColumns(days) }
            val listState = rememberLazyListState()
            // 默认滚动到最新（最右侧）。
            LaunchedEffect(columns.size) {
                if (columns.isNotEmpty()) listState.scrollToItem(columns.size - 1)
            }

            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CELL_GAP)
            ) {
                items(columns.size) { columnIndex ->
                    Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                        columns[columnIndex].forEach { day ->
                            HeatmapCell(day = day, colorScheme = colorScheme, onDayClick = onDayClick)
                        }
                    }
                }
            }

            HeatmapLegend(colorScheme = colorScheme)
        }
    }
}

@Composable
private fun HeatmapCell(
    day: HeatmapDay?,
    colorScheme: AsmrColorScheme,
    onDayClick: (String) -> Unit
) {
    val shape = RoundedCornerShape(3.dp)
    if (day == null) {
        Spacer(modifier = Modifier.size(CELL_SIZE))
    } else {
        val color = heatColorForLevel(day.level, colorScheme)
        Box(
            modifier = Modifier
                .size(CELL_SIZE)
                .clip(shape)
                .background(color)
                .then(
                    if (day.level == 0) {
                        Modifier.border(BorderStroke(0.5.dp, colorScheme.onSurface.copy(alpha = 0.08f)), shape)
                    } else Modifier
                )
                .clickable { onDayClick(day.date) }
        )
    }
}

@Composable
private fun HeatmapLegend(colorScheme: AsmrColorScheme) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "少",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary,
            fontSize = 10.sp
        )
        (0..4).forEach { level ->
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(heatColorForLevel(level, colorScheme))
            )
        }
        Text(
            text = "多",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.textSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun DayTimeline(
    date: String,
    sessions: List<ListeningSessionEntity>,
    colorScheme: AsmrColorScheme,
    onOpenAlbum: (albumId: Long, rjCode: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorScheme.primary
            )
            Text(
                text = formatDateHeader(date),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (sessions.isEmpty()) {
            Text(
                text = "这一天没有收听记录",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.textSecondary,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            sessions.forEachIndexed { index, session ->
                TimelineRow(
                    session = session,
                    isLast = index == sessions.lastIndex,
                    colorScheme = colorScheme,
                    onClick = { onOpenAlbum(session.albumId, session.rjCode) }
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(
    session: ListeningSessionEntity,
    isLast: Boolean,
    colorScheme: AsmrColorScheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 左侧时间线：时间 + 节点 + 连接线
        Column(
            modifier = Modifier.width(52.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatTimeOfDay(session.startAtMs),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textSecondary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(colorScheme.primary)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(colorScheme.onSurface.copy(alpha = 0.12f))
                )
            }
        }

        // 右侧作品简要卡片：封面 + 小字号标题 + cv
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = session.albumId > 0L || session.rjCode.isNotBlank()) { onClick() }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val coverModel = remember(session.id) {
                albumCoverImageModel(
                    coverThumbPath = session.coverThumbPath,
                    coverPath = session.coverPath,
                    coverUrl = session.coverUrl
                )
            }
            AsmrAsyncImage(
                model = coverModel,
                contentDescription = session.title,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title.ifBlank { session.rjCode.ifBlank { "未知作品" } },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = colorScheme.textPrimary,
                    maxLines = 2
                )
                if (session.cv.isNotBlank()) {
                    Text(
                        text = session.cv,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
                Text(
                    text = formatDurationLong(session.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private val CELL_SIZE = 13.dp
private val CELL_GAP = 3.dp

/**
 * 把按日期升序的 [days] 排布成 GitHub 风格的周列：
 * 每一列是一周（7 个格子，周日=0 … 周六=6），首列按第一天的星期做前置留白（null）。
 * 返回列的列表，每列长度固定为 7（不足处以 null 占位）。纯函数，便于测试。
 */
fun buildHeatmapColumns(days: List<HeatmapDay>): List<List<HeatmapDay?>> {
    if (days.isEmpty()) return emptyList()
    val leadingBlanks = ListeningDay.dayOfWeekIndex(days.first().date)
    val cells = ArrayList<HeatmapDay?>(leadingBlanks + days.size)
    repeat(leadingBlanks) { cells.add(null) }
    cells.addAll(days)
    // 尾部补齐到 7 的整数倍
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}

private fun heatColorForLevel(level: Int, colorScheme: AsmrColorScheme): Color {
    val base = colorScheme.primaryStrong
    return when (level) {
        0 -> if (colorScheme.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
        1 -> base.copy(alpha = 0.28f)
        2 -> base.copy(alpha = 0.50f)
        3 -> base.copy(alpha = 0.74f)
        else -> base
    }
}

private fun formatDurationLong(ms: Long): String {
    val totalMinutes = ms / 1000 / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "0m"
    }
}

private fun formatTraffic(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1fG", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1fM", bytes / (1024.0 * 1024))
        bytes >= 1024L -> String.format(Locale.US, "%.1fK", bytes / 1024.0)
        else -> "${bytes}B"
    }
}

private fun formatTimeOfDay(epochMs: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
}

private fun formatDateHeader(date: String): String {
    return runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: return date
        SimpleDateFormat("yyyy 年 M 月 d 日", Locale.getDefault()).format(parsed)
    }.getOrDefault(date)
}
