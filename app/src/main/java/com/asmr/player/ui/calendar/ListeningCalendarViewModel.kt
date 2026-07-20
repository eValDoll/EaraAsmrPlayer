package com.asmr.player.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.db.entities.ListeningSessionEntity
import com.asmr.player.data.repository.ListeningRecordRepository
import com.asmr.player.data.repository.StatisticsRepository
import com.asmr.player.util.ListeningDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import java.util.Locale

/** 热度图中单个格子的数据（一天）。 */
data class HeatmapDay(
    val date: String,
    val durationMs: Long,
    /** 0..4 的强度分级，用于映射颜色深浅。 */
    val level: Int
)

/** 顶部汇总卡片数据。 */
data class ListeningSummary(
    val totalDurationMs: Long = 0L,
    val totalTrackCount: Int = 0,
    val totalTrafficBytes: Long = 0L,
    val activeDayCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListeningCalendarViewModel @Inject constructor(
    private val statisticsRepository: StatisticsRepository,
    private val listeningRecordRepository: ListeningRecordRepository
) : ViewModel() {

    private val currentDate = ListeningDay.currentDate()
    private val currentYear = yearOf(currentDate) ?: 0

    /** 全部历史每日统计，用于汇总。 */
    val summary: StateFlow<ListeningSummary> =
        statisticsRepository.observeAllStats()
            .map { stats ->
                ListeningSummary(
                    totalDurationMs = stats.sumOf { it.listeningDurationMs },
                    totalTrackCount = stats.sumOf { it.trackCount },
                    totalTrafficBytes = stats.sumOf { it.networkTrafficBytes },
                    activeDayCount = stats.count { it.listeningDurationMs > 0L }
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListeningSummary())

    /** 可切换的年份列表，默认包含当前年，按新到旧排序。 */
    val availableYears: StateFlow<List<Int>> =
        statisticsRepository.observeAllStats()
            .map { stats -> availableYearsForDates(stats.map { it.date }, currentYear) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(currentYear))

    private val _selectedYear = MutableStateFlow(currentYear)
    val selectedYear: StateFlow<Int> = _selectedYear

    /** 热度图数据：区间内每一天（补齐无数据的日子）。 */
    val heatmap: StateFlow<List<HeatmapDay>> =
        _selectedYear
            .flatMapLatest { year ->
                val (startDate, endDate) = dateRangeForYear(year, currentDate)
                statisticsRepository.observeStatsBetween(startDate, endDate)
                    .map { stats ->
                        val byDate = stats.associateBy { it.date }
                        val maxDuration = stats.maxOfOrNull { it.listeningDurationMs } ?: 0L
                        ListeningDay.datesBetween(startDate, endDate).map { date ->
                            val duration = byDate[date]?.listeningDurationMs ?: 0L
                            HeatmapDay(date = date, durationMs = duration, level = levelFor(duration, maxDuration))
                        }
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate

    /** 选中日期的会话列表（垂直时间线数据）。 */
    val selectedSessions: StateFlow<List<ListeningSessionEntity>> =
        _selectedDate
            .flatMapLatest { date ->
                if (date == null) {
                    flowOf(emptyList())
                } else {
                    listeningRecordRepository.observeSessionsForDate(date)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(date: String?) {
        _selectedDate.value = date
    }

    fun selectYear(year: Int) {
        if (year <= 0 || year > currentYear) return
        if (_selectedYear.value == year) return
        _selectedDate.value = null
        _selectedYear.value = year
    }

    companion object {
        /**
         * 把某日收听时长映射到 0..4 的强度分级（相对当前区间峰值）。
         * 0 表示无收听；1..4 按峰值分位递增。
         */
        fun levelFor(durationMs: Long, maxDurationMs: Long): Int {
            if (durationMs <= 0L) return 0
            if (maxDurationMs <= 0L) return 1
            val ratio = durationMs.toDouble() / maxDurationMs.toDouble()
            return when {
                ratio <= 0.25 -> 1
                ratio <= 0.50 -> 2
                ratio <= 0.75 -> 3
                else -> 4
            }
        }

        internal fun dateRangeForYear(year: Int, currentDate: String): Pair<String, String> {
            val safeCurrentYear = yearOf(currentDate) ?: year
            val targetYear = year.coerceAtMost(safeCurrentYear)
            val startDate = formatYearDate(targetYear, 1, 1)
            val endDate = if (targetYear == safeCurrentYear) currentDate else formatYearDate(targetYear, 12, 31)
            return startDate to endDate
        }

        internal fun availableYearsForDates(dates: List<String>, currentYear: Int): List<Int> {
            val earliestYear = dates.mapNotNull { yearOf(it) }
                .filter { it <= currentYear }
                .minOrNull()
                ?: currentYear
            return (currentYear downTo earliestYear).toList()
        }

        internal fun yearOf(date: String): Int? {
            return date.take(4).toIntOrNull()
        }

        private fun formatYearDate(year: Int, month: Int, day: Int): String {
            return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
        }
    }
}
