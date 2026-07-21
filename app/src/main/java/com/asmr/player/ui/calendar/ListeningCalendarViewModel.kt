package com.asmr.player.ui.calendar

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.db.entities.ListeningSessionEntity
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.repository.ListeningRecordRepository
import com.asmr.player.data.repository.StatisticsRepository
import com.asmr.player.util.ListeningDay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val listeningRecordRepository: ListeningRecordRepository,
    @ApplicationContext context: Context
) : ViewModel() {

    private val authStore = DlsiteAuthStore(context)
    private val authPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshDlsiteLoginState()
    }
    private val currentDate = ListeningDay.currentDate()
    private val currentYear = yearOf(currentDate) ?: 0

    init {
        authStore.registerListener(authPreferenceListener)
    }

    /** 今日收听统计，按 [ListeningDay] 的凌晨 5 点重置口径。 */
    val summary: StateFlow<ListeningSummary> =
        statisticsRepository.observeTodayStats()
            .map { stat ->
                if (stat == null) {
                    ListeningSummary()
                } else {
                    ListeningSummary(
                        totalDurationMs = stat.listeningDurationMs,
                        totalTrackCount = stat.trackCount,
                        totalTrafficBytes = stat.networkTrafficBytes,
                        activeDayCount = if (stat.listeningDurationMs > 0L) 1 else 0
                    )
                }
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
                        ListeningDay.datesBetween(startDate, endDate).map { date ->
                            val duration = byDate[date]?.listeningDurationMs ?: 0L
                            HeatmapDay(date = date, durationMs = duration, level = levelFor(duration))
                        }
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDate = MutableStateFlow<String?>(currentDate)
    val selectedDate: StateFlow<String?> = _selectedDate

    private val _isDlsiteLoggedIn = MutableStateFlow(authStore.isLoggedIn())
    val isDlsiteLoggedIn: StateFlow<Boolean> = _isDlsiteLoggedIn

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

    fun refreshDlsiteLoginState() {
        _isDlsiteLoggedIn.value = authStore.isLoggedIn()
    }

    fun selectYear(year: Int) {
        if (year <= 0 || year > currentYear) return
        if (_selectedYear.value == year) return
        _selectedDate.value = if (year == currentYear) currentDate else null
        _selectedYear.value = year
    }

    override fun onCleared() {
        authStore.unregisterListener(authPreferenceListener)
        super.onCleared()
    }

    companion object {
        /**
         * 把某日收听时长映射到 0..4 的固定强度分级。
         * 0 表示无收听；1..4 分别对应 1-9、10-59、60-120、121+ 分钟。
         */
        fun levelFor(durationMs: Long): Int {
            if (durationMs <= 0L) return 0
            val totalMinutes = (durationMs + MILLIS_PER_MINUTE - 1L) / MILLIS_PER_MINUTE
            return when {
                totalMinutes <= 9L -> 1
                totalMinutes <= 59L -> 2
                totalMinutes <= 120L -> 3
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

        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
