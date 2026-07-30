package com.asmr.player.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.asmr.player.data.local.db.entities.ListeningSessionEntity
import com.asmr.player.ui.common.AsmrAsyncImage
import com.asmr.player.ui.common.LocalBottomOverlayPadding
import com.asmr.player.ui.common.albumCoverImageModel
import com.asmr.player.ui.common.collectAsStateWhileActive
import com.asmr.player.ui.theme.AsmrColorScheme
import com.asmr.player.ui.theme.AsmrTheme
import com.asmr.player.util.ListeningDay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * "ASMR 看板"：用户的本地收听记录 / 日记面板。
 *
 * 由原"DLsite 登录"页扩展而来：登录能力弱化为二级入口（顶部按钮），
 * 主体是收听热度图（贡献图）+ 汇总统计，点击某天可查看当日的垂直时间线。
 */
@Composable
fun ListeningCalendarScreen(
    windowSizeClass: WindowSizeClass,
    isActive: Boolean = true,
    isDataActive: Boolean = isActive,
    onOpenDlsiteLogin: () -> Unit,
    onOpenAlbum: (ListeningSessionEntity) -> Unit,
    viewModel: ListeningCalendarViewModel = hiltViewModel()
) {
    val summary by viewModel.summary.collectAsStateWhileActive(isDataActive)
    val summaryArtwork by viewModel.summaryArtwork.collectAsStateWhileActive(isDataActive)
    val summaryComparison by viewModel.summaryComparison.collectAsStateWhileActive(isDataActive)
    val comparisonGranularity by viewModel.comparisonGranularity.collectAsStateWhileActive(isDataActive)
    val heatmap by viewModel.heatmap.collectAsStateWhileActive(isDataActive)
    val availableYears by viewModel.availableYears.collectAsStateWhileActive(isDataActive)
    val selectedYear by viewModel.selectedYear.collectAsStateWhileActive(isDataActive)
    val selectedDate by viewModel.selectedDate.collectAsStateWhileActive(isDataActive)
    val selectedSessions by viewModel.selectedSessions.collectAsStateWhileActive(isDataActive)
    val isDlsiteLoggedIn by viewModel.isDlsiteLoggedIn.collectAsStateWhileActive(isDataActive)
    val colorScheme = AsmrTheme.colorScheme
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact
    val bottomOverlayPadding = LocalBottomOverlayPadding.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        viewModel.refreshDlsiteLoginState()
    }

    DisposableEffect(lifecycleOwner, viewModel, isActive) {
        val observer = LifecycleEventObserver { _, event ->
            if (isActive && event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDlsiteLoginState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
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
                .padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = bottomOverlayPadding + 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DlsiteAccountRow(
                onOpenDlsiteLogin = onOpenDlsiteLogin,
                isLoggedIn = isDlsiteLoggedIn,
                colorScheme = colorScheme
            )

            SummaryCards(
                summary = summary,
                artwork = summaryArtwork,
                comparison = summaryComparison,
                selectedGranularity = comparisonGranularity,
                colorScheme = colorScheme,
                onGranularitySelected = viewModel::selectComparisonGranularity
            )

            HeatmapSection(
                days = heatmap,
                selectedDate = selectedDate,
                availableYears = availableYears,
                selectedYear = selectedYear,
                colorScheme = colorScheme,
                onYearSelected = { viewModel.selectYear(it) },
                onDayClick = { date ->
                    viewModel.selectDate(date)
                }
            )

            selectedDate?.let { date ->
                DayTimeline(
                    date = date,
                    sessions = selectedSessions,
                    colorScheme = colorScheme,
                    onOpenAlbum = { session ->
                        viewModel.selectDate(null)
                        onOpenAlbum(session)
                    }
                )
            }
        }
    }
}

@Composable
private fun DlsiteAccountRow(
    onOpenDlsiteLogin: () -> Unit,
    isLoggedIn: Boolean,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "DLsite 账号",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                DlsiteLoginStatusTag(isLoggedIn = isLoggedIn, colorScheme = colorScheme)
            }
            Text(
                text = "用于「已购」搜索鉴权",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textSecondary
            )
        }
        SmallCalendarButton(
            label = if (isLoggedIn) "管理" else "登录",
            colorScheme = colorScheme,
            minWidth = 58.dp,
            onClick = onOpenDlsiteLogin
        )
    }
}

@Composable
private fun DlsiteLoginStatusTag(
    isLoggedIn: Boolean,
    colorScheme: AsmrColorScheme
) {
    val statusColor = if (isLoggedIn) colorScheme.primaryStrong else colorScheme.danger
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(statusColor.copy(alpha = if (colorScheme.isDark) 0.18f else 0.10f))
            .border(BorderStroke(0.6.dp, statusColor.copy(alpha = 0.35f)), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isLoggedIn) "已登录" else "未登录",
            style = MaterialTheme.typography.labelSmall,
            color = statusColor,
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun SummaryCards(
    summary: ListeningSummary,
    artwork: ListeningSummaryArtwork?,
    comparison: ListeningSummaryComparison,
    selectedGranularity: ListeningComparisonGranularity,
    onGranularitySelected: (ListeningComparisonGranularity) -> Unit,
    colorScheme: AsmrColorScheme
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = summaryTitleFor(selectedGranularity),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textSecondary,
                modifier = Modifier.weight(1f)
            )
            ComparisonGranularitySelector(
                selectedGranularity = selectedGranularity,
                colorScheme = colorScheme,
                onGranularitySelected = onGranularitySelected
            )
        }
        SummaryArtworkPanel(
            artwork = artwork,
            colorScheme = colorScheme,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SummaryCell(
                        icon = Icons.Rounded.AccessTime,
                        label = "总时长",
                        value = formatDurationLong(summary.totalDurationMs),
                        comparisonDisplay = summaryComparisonDisplay(SummaryMetric.Duration, comparison, colorScheme),
                        colorScheme = colorScheme,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCell(
                        icon = Icons.Rounded.Audiotrack,
                        label = "音轨",
                        value = "${summary.totalTrackCount}",
                        comparisonDisplay = summaryComparisonDisplay(SummaryMetric.TrackCount, comparison, colorScheme),
                        colorScheme = colorScheme,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SummaryCell(
                        icon = Icons.Rounded.EventAvailable,
                        label = "活跃天数",
                        value = "${summary.activeDayCount}",
                        comparisonDisplay = summaryComparisonDisplay(
                            metric = SummaryMetric.ActiveDayCount,
                            comparison = comparison,
                            colorScheme = colorScheme,
                            selectedGranularity = selectedGranularity
                        ),
                        colorScheme = colorScheme,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryCell(
                        icon = Icons.Rounded.CloudDownload,
                        label = "流量",
                        value = formatTraffic(summary.totalTrafficBytes),
                        comparisonDisplay = summaryComparisonDisplay(SummaryMetric.Traffic, comparison, colorScheme),
                        colorScheme = colorScheme,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SummaryArtworkPanel(
    artwork: ListeningSummaryArtwork?,
    colorScheme: AsmrColorScheme,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val backgroundModel = remember(artwork?.coverThumbPath, artwork?.coverPath, artwork?.coverUrl) {
        artwork?.let {
            albumCoverImageModel(
                coverThumbPath = it.coverThumbPath,
                coverPath = it.coverPath,
                coverUrl = it.coverUrl
            )
        }
    }
    val shape = RoundedCornerShape(16.dp)
    val fallbackColor = if (colorScheme.isDark) {
        colorScheme.surfaceVariant.copy(alpha = 0.36f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = 0.26f)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(fallbackColor)
    ) {
        AnimatedContent(
            targetState = backgroundModel,
            transitionSpec = {
                fadeIn(animationSpec = tween(260))
                    .togetherWith(fadeOut(animationSpec = tween(180)))
            },
            label = "ListeningSummaryArtwork",
            modifier = Modifier.matchParentSize()
        ) { targetModel ->
            if (targetModel != null) {
                AsmrAsyncImage(
                    model = targetModel,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.TopCenter,
                    alpha = if (colorScheme.isDark) 0.54f else 0.62f,
                    placeholder = { _ -> },
                    loading = { _ -> },
                    empty = { _ -> },
                    loadAtOriginalSize = true,
                    peekAnySizeForInitial = true
                )
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    if (colorScheme.isDark) {
                        Color.Black.copy(alpha = 0.22f)
                    } else {
                        colorScheme.surface.copy(alpha = 0.14f)
                    }
                )
        )
        Box(modifier = Modifier.padding(8.dp)) {
            content()
        }
    }
}

@Composable
private fun ComparisonGranularitySelector(
    selectedGranularity: ListeningComparisonGranularity,
    colorScheme: AsmrColorScheme,
    onGranularitySelected: (ListeningComparisonGranularity) -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    val containerColor = if (colorScheme.isDark) {
        colorScheme.surfaceVariant.copy(alpha = 0.34f)
    } else {
        colorScheme.primarySoft.copy(alpha = 0.36f)
    }
    val borderColor = if (colorScheme.isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        colorScheme.primaryStrong.copy(alpha = 0.16f)
    }
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ListeningComparisonGranularity.values().forEach { granularity ->
            val selected = granularity == selectedGranularity
            val selectedColor = colorScheme.primaryStrong.copy(alpha = if (colorScheme.isDark) 0.24f else 0.14f)
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) selectedColor else Color.Transparent)
                    .clickable(
                        role = Role.Button,
                        interactionSource = remember(granularity) { MutableInteractionSource() },
                        indication = null,
                        onClick = { onGranularitySelected(granularity) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = granularity.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) colorScheme.primaryStrong else colorScheme.textSecondary,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SummaryCell(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    comparisonDisplay: SummaryComparisonDisplay? = null,
    colorScheme: AsmrColorScheme,
    modifier: Modifier = Modifier
) {
    val isDark = colorScheme.isDark
    val containerColor = if (isDark) {
        colorScheme.surfaceVariant.copy(alpha = 0.46f)
    } else {
        colorScheme.surface.copy(alpha = 0.48f)
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.16f)
    } else {
        colorScheme.onSurface.copy(alpha = 0.08f)
    }
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .height(72.dp)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        val useStackedLayout = maxWidth < 170.dp
        if (useStackedLayout) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SummaryIcon(icon = icon, colorScheme = colorScheme, size = 15.dp)
                    SummaryValueText(
                        value = value,
                        colorScheme = colorScheme,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SummaryLabelText(
                        label = label,
                        colorScheme = colorScheme,
                        modifier = Modifier.weight(1f)
                    )
                    if (comparisonDisplay != null) {
                        AnimatedComparisonSummary(
                            display = comparisonDisplay,
                            modifier = Modifier.widthIn(min = 48.dp, max = 72.dp)
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                SummaryIcon(icon = icon, colorScheme = colorScheme, size = 17.dp)
                Column(modifier = Modifier.weight(1f)) {
                    SummaryValueText(value = value, colorScheme = colorScheme)
                    SummaryLabelText(label = label, colorScheme = colorScheme)
                }
                if (comparisonDisplay != null) {
                    AnimatedComparisonSummary(
                        display = comparisonDisplay,
                        modifier = Modifier.widthIn(min = 50.dp, max = 74.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    colorScheme: AsmrColorScheme,
    size: Dp
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = colorScheme.textSecondary
    )
}

@Composable
private fun SummaryValueText(
    value: String,
    colorScheme: AsmrColorScheme,
    modifier: Modifier = Modifier
) {
    val fontSize = when {
        value.length >= 8 -> 12.sp
        value.length >= 7 -> 13.sp
        else -> 14.sp
    }
    Text(
        text = value,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            fontFeatureSettings = "tnum"
        ),
        color = colorScheme.textPrimary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier
    )
}

@Composable
private fun SummaryLabelText(
    label: String,
    colorScheme: AsmrColorScheme,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = colorScheme.textSecondary,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        modifier = modifier
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedComparisonSummary(
    display: SummaryComparisonDisplay,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = display,
        transitionSpec = {
            (slideInVertically(animationSpec = tween(220)) { height -> height } + fadeIn(tween(140)))
                .togetherWith(
                    slideOutVertically(animationSpec = tween(220)) { height -> -height } + fadeOut(tween(140))
                )
                .using(SizeTransform(clip = false))
        },
        label = "ListeningSummaryComparison",
        modifier = modifier
    ) { targetDisplay ->
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = targetDisplay.headline,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = if (targetDisplay.headline.length >= 8) 12.sp else 14.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = targetDisplay.color,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth()
            )
            if (targetDisplay.caption.isNotBlank()) {
                Text(
                    text = targetDisplay.caption,
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 11.sp),
                    color = targetDisplay.captionColor,
                    fontSize = 9.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun HeatmapSection(
    days: List<HeatmapDay>,
    selectedDate: String?,
    availableYears: List<Int>,
    selectedYear: Int,
    colorScheme: AsmrColorScheme,
    onYearSelected: (Int) -> Unit,
    onDayClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "收听热度",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.textSecondary,
                modifier = Modifier.weight(1f)
            )
            YearSelector(
                years = availableYears,
                selectedYear = selectedYear,
                colorScheme = colorScheme,
                onYearSelected = onYearSelected
            )
        }
        if (days.isEmpty()) {
            Text(
                text = "还没有收听记录，去听点什么吧～",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.textSecondary
            )
        } else {
            val columns = remember(days) { buildHeatmapColumns(days) }
            val monthLabels = remember(columns) { buildHeatmapMonthLabels(columns) }
            val scrollState = rememberScrollState()
            LaunchedEffect(selectedYear, columns.size) {
                if (columns.isNotEmpty()) {
                    withFrameNanos { }
                    scrollState.scrollTo(scrollState.maxValue)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HeatmapMonthLabelRow(labels = monthLabels, colorScheme = colorScheme)
                Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    columns.forEach { column ->
                        Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                            column.forEach { day ->
                                HeatmapCell(
                                    day = day,
                                    selected = day?.date == selectedDate,
                                    colorScheme = colorScheme,
                                    onDayClick = onDayClick
                                )
                            }
                        }
                    }
                }
            }

            HeatmapLegend(colorScheme = colorScheme)
        }
    }
}

@Composable
private fun YearSelector(
    years: List<Int>,
    selectedYear: Int,
    colorScheme: AsmrColorScheme,
    onYearSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SmallCalendarButton(
            label = "${selectedYear} 年",
            onClick = { expanded = true },
            enabled = years.size > 1,
            colorScheme = colorScheme,
            trailingIcon = true
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            years.forEach { year ->
                DropdownMenuItem(
                    text = { Text("${year} 年") },
                    leadingIcon = if (year == selectedYear) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onYearSelected(year)
                    }
                )
            }
        }
    }
}

@Composable
private fun SmallCalendarButton(
    label: String,
    colorScheme: AsmrColorScheme,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailingIcon: Boolean = false,
    minWidth: Dp = 44.dp
) {
    val shape = RoundedCornerShape(8.dp)
    val contentColor = if (enabled) colorScheme.primaryStrong else colorScheme.textSecondary
    val containerColor = if (enabled) {
        colorScheme.primarySoft.copy(alpha = if (colorScheme.isDark) 0.24f else 0.44f)
    } else {
        colorScheme.surfaceVariant.copy(alpha = if (colorScheme.isDark) 0.32f else 0.56f)
    }
    val borderColor = if (enabled) {
        colorScheme.primaryStrong.copy(alpha = 0.24f)
    } else {
        colorScheme.onSurface.copy(alpha = 0.10f)
    }

    Row(
        modifier = Modifier
            .height(30.dp)
            .widthIn(min = minWidth)
            .clip(shape)
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1
        )
        if (trailingIcon) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor.copy(alpha = 0.82f)
            )
        }
    }
}

@Composable
private fun HeatmapMonthLabelRow(
    labels: List<HeatmapMonthLabel>,
    colorScheme: AsmrColorScheme
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        labels.forEach { label ->
            Box(
                modifier = Modifier
                    .width(heatmapSpanWidth(label.span))
                    .height(16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = label.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    day: HeatmapDay?,
    selected: Boolean,
    colorScheme: AsmrColorScheme,
    onDayClick: (String) -> Unit
) {
    val shape = RoundedCornerShape(3.dp)
    if (day == null) {
        Spacer(modifier = Modifier.size(CELL_SIZE))
    } else {
        val color = heatColorForLevel(day.level, colorScheme)
        val borderStroke = when {
            selected -> BorderStroke(1.4.dp, colorScheme.primary)
            day.level == 0 -> BorderStroke(0.5.dp, colorScheme.onSurface.copy(alpha = 0.08f))
            else -> null
        }
        Box(
            modifier = Modifier
                .size(CELL_SIZE)
                .clip(shape)
                .background(color)
                .then(
                    if (borderStroke != null) Modifier.border(borderStroke, shape) else Modifier
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
    onOpenAlbum: (ListeningSessionEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
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
                    onClick = { onOpenAlbum(session) }
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
    val metaLine = remember(session.circle, session.cv) {
        listOf(session.circle, session.cv)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" / ")
    }
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

        // 右侧作品简要卡片：封面 + 小字号标题 + 社团/CV
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
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
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

internal data class HeatmapMonthLabel(
    val text: String,
    val span: Int
)

internal fun buildHeatmapMonthLabels(columns: List<List<HeatmapDay?>>): List<HeatmapMonthLabel> {
    if (columns.isEmpty()) return emptyList()
    val markers = ArrayList<Pair<Int, String>>()
    columns.forEachIndexed { columnIndex, column ->
        val realDays = column.filterNotNull()
        val firstDay = realDays.firstOrNull() ?: return@forEachIndexed
        val monthStartDay = realDays.firstOrNull { it.date.length >= 10 && it.date.substring(8, 10) == "01" }
        if (columnIndex == 0 || monthStartDay != null) {
            val label = formatMonthLabel((monthStartDay ?: firstDay).date)
            if (label.isNotBlank() && markers.lastOrNull()?.second != label) {
                markers.add(columnIndex to label)
            }
        }
    }
    return markers.mapIndexed { index, marker ->
        val nextColumnIndex = markers.getOrNull(index + 1)?.first ?: columns.size
        HeatmapMonthLabel(
            text = marker.second,
            span = (nextColumnIndex - marker.first).coerceAtLeast(1)
        )
    }
}

private fun heatmapSpanWidth(span: Int): Dp {
    val safeSpan = span.coerceAtLeast(1)
    return CELL_SIZE * safeSpan.toFloat() + CELL_GAP * (safeSpan - 1).toFloat()
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

private data class SummaryComparisonDisplay(
    val headline: String,
    val caption: String,
    val color: Color,
    val captionColor: Color
)

private enum class SummaryMetric {
    Duration,
    TrackCount,
    ActiveDayCount,
    Traffic
}

private fun summaryComparisonDisplay(
    metric: SummaryMetric,
    comparison: ListeningSummaryComparison,
    colorScheme: AsmrColorScheme,
    selectedGranularity: ListeningComparisonGranularity? = null
): SummaryComparisonDisplay {
    if (metric == SummaryMetric.ActiveDayCount && selectedGranularity == ListeningComparisonGranularity.Day) {
        return SummaryComparisonDisplay(
            headline = "-",
            caption = "相比${comparison.referenceLabel}",
            color = colorScheme.textSecondary,
            captionColor = Color.Transparent
        )
    }
    val delta = metricDelta(metric, comparison)
    return SummaryComparisonDisplay(
        headline = formatComparisonHeadline(metric, delta),
        caption = "相比${comparison.referenceLabel}",
        color = comparisonColorFor(metric, comparison, colorScheme),
        captionColor = colorScheme.textSecondary
    )
}

private fun summaryTitleFor(granularity: ListeningComparisonGranularity): String {
    return when (granularity) {
        ListeningComparisonGranularity.Day -> "今日收听"
        ListeningComparisonGranularity.Week -> "本周收听"
        ListeningComparisonGranularity.Month -> "本月收听"
        ListeningComparisonGranularity.Year -> "本年收听"
    }
}

private fun formatComparisonHeadline(
    metric: SummaryMetric,
    delta: ListeningMetricDelta
): String {
    if (abs(delta.difference) < 0.01) {
        return formatComparisonAmount(metric, 0.0)
    }
    val sign = if (delta.difference > 0.0) "+" else "-"
    return "$sign${formatComparisonAmount(metric, abs(delta.difference))}"
}

private fun formatComparisonAmount(metric: SummaryMetric, value: Double): String {
    return when (metric) {
        SummaryMetric.Duration -> formatDurationLong(value.roundToLong())
        SummaryMetric.TrackCount -> "${formatDecimalAmount(value)}个"
        SummaryMetric.ActiveDayCount -> "${formatDecimalAmount(value)}天"
        SummaryMetric.Traffic -> formatTraffic(value.roundToLong())
    }
}

private fun metricDelta(
    metric: SummaryMetric,
    comparison: ListeningSummaryComparison
): ListeningMetricDelta {
    return when (metric) {
        SummaryMetric.Duration -> comparison.durationMs
        SummaryMetric.TrackCount -> comparison.trackCount
        SummaryMetric.ActiveDayCount -> comparison.activeDayCount
        SummaryMetric.Traffic -> comparison.trafficBytes
    }
}

private fun comparisonColorFor(
    metric: SummaryMetric,
    comparison: ListeningSummaryComparison,
    colorScheme: AsmrColorScheme
): Color {
    val difference = metricDelta(metric, comparison).difference
    return when {
        difference > 0.01 -> colorScheme.primaryStrong
        difference < -0.01 -> colorScheme.danger
        else -> colorScheme.textSecondary
    }
}

private fun formatDecimalAmount(value: Double): String {
    val rounded = value.roundToLong()
    return if (abs(value - rounded) < 0.05) {
        rounded.toString()
    } else {
        String.format(Locale.US, "%.1f", value)
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

private fun formatMonthLabel(date: String): String {
    return runCatching {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
            ?: return@runCatching fallbackMonthLabel(date)
        SimpleDateFormat("M月", Locale.getDefault()).format(parsed)
    }.getOrElse { fallbackMonthLabel(date) }
}

private fun fallbackMonthLabel(date: String): String {
    if (date.length < 7) return date
    val month = date.substring(5, 7).trimStart('0').ifBlank { date.substring(5, 7) }
    return "${month}月"
}
