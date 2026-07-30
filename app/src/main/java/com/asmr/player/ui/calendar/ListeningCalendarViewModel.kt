package com.asmr.player.ui.calendar

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.local.db.dao.AlbumListeningRow
import com.asmr.player.data.local.db.entities.DailyStatEntity
import com.asmr.player.data.local.db.entities.ListeningSessionEntity
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import com.asmr.player.data.repository.ListeningRecordRepository
import com.asmr.player.data.repository.StatisticsRepository
import com.asmr.player.util.ListeningDay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.math.roundToLong

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

enum class ListeningComparisonGranularity(val label: String) {
    Day("日"),
    Week("周"),
    Month("月"),
    Year("年")
}

data class ListeningSummaryArtwork(
    val title: String = "",
    val coverUrl: String = "",
    val coverPath: String = "",
    val coverThumbPath: String = ""
)

data class ListeningMetricDelta(
    val current: Double = 0.0,
    val reference: Double = 0.0
) {
    val difference: Double
        get() = current - reference
}

data class ListeningSummaryComparison(
    val referenceLabel: String = "昨日",
    val durationMs: ListeningMetricDelta = ListeningMetricDelta(),
    val trackCount: ListeningMetricDelta = ListeningMetricDelta(),
    val activeDayCount: ListeningMetricDelta = ListeningMetricDelta(),
    val trafficBytes: ListeningMetricDelta = ListeningMetricDelta()
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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
    private val _comparisonGranularity = MutableStateFlow(ListeningComparisonGranularity.Day)

    init {
        authStore.registerListener(authPreferenceListener)
    }

    /** 当前维度的收听数据，按 [ListeningDay] 的凌晨 5 点重置口径。 */
    val summary: StateFlow<ListeningSummary> =
        combine(
            statisticsRepository.observeAllStats(),
            _comparisonGranularity
        ) { stats, granularity ->
            buildCurrentSummary(
                stats = stats,
                currentDate = currentDate,
                granularity = granularity
            )
        }
            .debounce(SUMMARY_UPDATE_DEBOUNCE_MS)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ListeningSummary())

    /** 可切换的年份列表，默认包含当前年，按新到旧排序。 */
    val availableYears: StateFlow<List<Int>> =
        statisticsRepository.observeAllStats()
            .map { stats -> availableYearsForDates(stats.map { it.date }, currentYear) }
            .flowOn(Dispatchers.Default)
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
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDate = MutableStateFlow<String?>(currentDate)
    val selectedDate: StateFlow<String?> = _selectedDate

    val comparisonGranularity: StateFlow<ListeningComparisonGranularity> = _comparisonGranularity

    val summaryArtwork: StateFlow<ListeningSummaryArtwork?> =
        _comparisonGranularity
            .flatMapLatest { granularity ->
                val range = currentSummaryRange(currentDate, granularity)
                    ?: return@flatMapLatest flowOf(null)
                listeningRecordRepository.observeTopAlbum(range.startDate, range.endDate)
                    .map { row -> row?.toSummaryArtwork() }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isDlsiteLoggedIn = MutableStateFlow(authStore.isLoggedIn())
    val isDlsiteLoggedIn: StateFlow<Boolean> = _isDlsiteLoggedIn

    val summaryComparison: StateFlow<ListeningSummaryComparison> =
        combine(
            statisticsRepository.observeAllStats(),
            _selectedDate,
            _comparisonGranularity
        ) { stats, selectedDate, granularity ->
            buildSummaryComparison(
                stats = stats,
                currentDate = currentDate,
                selectedDate = selectedDate,
                granularity = granularity
            )
        }
            .debounce(SUMMARY_UPDATE_DEBOUNCE_MS)
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                ListeningSummaryComparison()
            )

    /** 选中日期的会话列表（垂直时间线数据），相邻同作品会合并显示。 */
    val selectedSessions: StateFlow<List<ListeningSessionEntity>> =
        _selectedDate
            .flatMapLatest { date ->
                if (date == null) {
                    flowOf(emptyList())
                } else {
                    listeningRecordRepository.observeSessionsForDate(date)
                }
            }
            .map { sessions -> mergeAdjacentListeningSessions(sessions) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(date: String?) {
        _selectedDate.value = date
    }

    fun selectComparisonGranularity(granularity: ListeningComparisonGranularity) {
        _comparisonGranularity.value = granularity
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

        internal fun buildSummaryComparison(
            stats: List<DailyStatEntity>,
            currentDate: String,
            selectedDate: String?,
            granularity: ListeningComparisonGranularity
        ): ListeningSummaryComparison {
            val window = comparisonWindow(currentDate, selectedDate, granularity)
                ?: return ListeningSummaryComparison()
            val byDate = stats.associateBy { it.date }
            val current = summaryValuesForRange(byDate, window.currentRange)
            val reference = summaryValuesForRange(byDate, window.referenceRange)
            return ListeningSummaryComparison(
                referenceLabel = window.referenceLabel,
                durationMs = ListeningMetricDelta(current.durationMs, reference.durationMs),
                trackCount = ListeningMetricDelta(current.trackCount, reference.trackCount),
                activeDayCount = ListeningMetricDelta(current.activeDayCount, reference.activeDayCount),
                trafficBytes = ListeningMetricDelta(current.trafficBytes, reference.trafficBytes)
            )
        }

        internal fun buildCurrentSummary(
            stats: List<DailyStatEntity>,
            currentDate: String,
            granularity: ListeningComparisonGranularity
        ): ListeningSummary {
            val range = currentSummaryRange(currentDate, granularity) ?: return ListeningSummary()
            val values = summaryValuesForRange(
                byDate = stats.associateBy { it.date },
                range = range
            )
            return ListeningSummary(
                totalDurationMs = values.durationMs.roundToLong(),
                totalTrackCount = values.trackCount.roundToInt(),
                totalTrafficBytes = values.trafficBytes.roundToLong(),
                activeDayCount = values.activeDayCount.roundToInt()
            )
        }

        internal fun mergeAdjacentListeningSessions(
            sessions: List<ListeningSessionEntity>
        ): List<ListeningSessionEntity> {
            if (sessions.size <= 1) return sessions

            val merged = ArrayList<ListeningSessionEntity>(sessions.size)
            var current = sessions.first()
            sessions.drop(1).forEach { next ->
                if (isSameListeningWork(current, next)) {
                    current = current.copy(
                        durationMs = current.durationMs + next.durationMs,
                        trafficBytes = current.trafficBytes + next.trafficBytes,
                        trackCount = current.trackCount + next.trackCount,
                        lastActiveAtMs = maxOf(current.lastActiveAtMs, next.lastActiveAtMs)
                    )
                } else {
                    merged += current
                    current = next
                }
            }
            merged += current
            return merged
        }

        private fun isSameListeningWork(
            first: ListeningSessionEntity,
            second: ListeningSessionEntity
        ): Boolean {
            val firstRj = first.rjCode.normalizedRjCode()
            val secondRj = second.rjCode.normalizedRjCode()
            if (firstRj != null && secondRj != null) return firstRj == secondRj
            if (first.albumId > 0L && second.albumId > 0L) return first.albumId == second.albumId
            if (firstRj != null || secondRj != null || first.albumId > 0L || second.albumId > 0L) return false

            val firstMeta = first.normalizedFallbackWorkMeta() ?: return false
            val secondMeta = second.normalizedFallbackWorkMeta() ?: return false
            return firstMeta == secondMeta
        }

        private fun String.normalizedRjCode(): String? =
            trim().uppercase(Locale.US).takeIf { it.isNotBlank() }

        private fun ListeningSessionEntity.normalizedFallbackWorkMeta(): String? {
            val normalizedTitle = title.trim().lowercase(Locale.ROOT)
            if (normalizedTitle.isBlank()) return null
            val normalizedCircle = circle.trim().lowercase(Locale.ROOT)
            val normalizedCv = cv.trim().lowercase(Locale.ROOT)
            return "$normalizedTitle|$normalizedCircle|$normalizedCv"
        }

        private fun currentSummaryRange(
            currentDate: String,
            granularity: ListeningComparisonGranularity
        ): DateRange? {
            return when (granularity) {
                ListeningComparisonGranularity.Day -> DateRange(currentDate, currentDate)
                ListeningComparisonGranularity.Week -> DateRange(
                    startDate = startOfWeek(currentDate) ?: return null,
                    endDate = currentDate
                )
                ListeningComparisonGranularity.Month -> DateRange(
                    startDate = startOfMonth(currentDate) ?: return null,
                    endDate = currentDate
                )
                ListeningComparisonGranularity.Year -> DateRange(
                    startDate = startOfYear(currentDate) ?: return null,
                    endDate = currentDate
                )
            }
        }

        private fun comparisonWindow(
            currentDate: String,
            selectedDate: String?,
            granularity: ListeningComparisonGranularity
        ): SummaryComparisonWindow? {
            return when (granularity) {
                ListeningComparisonGranularity.Day -> dayComparisonWindow(currentDate, selectedDate)
                ListeningComparisonGranularity.Week -> weekComparisonWindow(currentDate, selectedDate)
                ListeningComparisonGranularity.Month -> monthComparisonWindow(currentDate, selectedDate)
                ListeningComparisonGranularity.Year -> yearComparisonWindow(currentDate, selectedDate)
            }
        }

        private fun dayComparisonWindow(
            currentDate: String,
            selectedDate: String?
        ): SummaryComparisonWindow? {
            val yesterday = addDays(currentDate, -1) ?: return null
            val referenceDate = selectedDate
                ?.takeIf { it != currentDate }
                ?: yesterday
            return SummaryComparisonWindow(
                currentRange = DateRange(currentDate, currentDate),
                referenceRange = DateRange(referenceDate, referenceDate),
                referenceLabel = if (referenceDate == yesterday) "昨日" else shortDateLabel(referenceDate)
            )
        }

        private fun weekComparisonWindow(
            currentDate: String,
            selectedDate: String?
        ): SummaryComparisonWindow? {
            val currentStart = startOfWeek(currentDate) ?: return null
            val currentElapsedDays = (dayDistance(currentStart, currentDate) ?: return null) + 1
            val weeksBack = weeksBackForSelection(currentDate, selectedDate)
            val referenceStart = addDays(currentStart, -weeksBack * 7) ?: return null
            val referenceEnd = addDays(referenceStart, currentElapsedDays - 1) ?: return null
            return SummaryComparisonWindow(
                currentRange = DateRange(currentStart, currentDate),
                referenceRange = DateRange(referenceStart, referenceEnd),
                referenceLabel = if (weeksBack == 1) "上周" else "${weeksBack}周前"
            )
        }

        private fun monthComparisonWindow(
            currentDate: String,
            selectedDate: String?
        ): SummaryComparisonWindow? {
            val currentStart = startOfMonth(currentDate) ?: return null
            val monthsBack = monthsBackForSelection(currentDate, selectedDate)
            val referenceStart = addMonths(currentStart, -monthsBack) ?: return null
            val currentDay = dayOfMonth(currentDate) ?: return null
            val referenceDays = daysInMonth(referenceStart) ?: return null
            val alignedDays = minOf(currentDay, referenceDays).coerceAtLeast(1)
            val currentEnd = addDays(currentStart, alignedDays - 1) ?: return null
            val referenceEnd = addDays(referenceStart, alignedDays - 1) ?: return null
            return SummaryComparisonWindow(
                currentRange = DateRange(currentStart, currentEnd),
                referenceRange = DateRange(referenceStart, referenceEnd),
                referenceLabel = if (monthsBack == 1) "上月" else "${monthsBack}个月前"
            )
        }

        private fun yearComparisonWindow(
            currentDate: String,
            selectedDate: String?
        ): SummaryComparisonWindow? {
            val currentStart = startOfYear(currentDate) ?: return null
            val yearsBack = yearsBackForSelection(currentDate, selectedDate)
            val referenceStart = addYears(currentStart, -yearsBack) ?: return null
            val referenceEnd = addYears(currentDate, -yearsBack) ?: return null
            return SummaryComparisonWindow(
                currentRange = DateRange(currentStart, currentDate),
                referenceRange = DateRange(referenceStart, referenceEnd),
                referenceLabel = if (yearsBack == 1) "去年" else "${yearsBack}年前"
            )
        }

        private fun weeksBackForSelection(currentDate: String, selectedDate: String?): Int {
            val selected = selectedDate ?: return 1
            val currentWeekStart = startOfWeek(currentDate) ?: return 1
            val selectedWeekStart = startOfWeek(selected) ?: return 1
            val days = dayDistance(selectedWeekStart, currentWeekStart) ?: return 1
            return (days / 7).coerceAtLeast(1)
        }

        private fun monthsBackForSelection(currentDate: String, selectedDate: String?): Int {
            val selected = selectedDate ?: return 1
            val currentStart = calendarFor(startOfMonth(currentDate) ?: return 1) ?: return 1
            val selectedStart = calendarFor(startOfMonth(selected) ?: return 1) ?: return 1
            val currentIndex = currentStart.get(Calendar.YEAR) * 12 + currentStart.get(Calendar.MONTH)
            val selectedIndex = selectedStart.get(Calendar.YEAR) * 12 + selectedStart.get(Calendar.MONTH)
            return (currentIndex - selectedIndex).coerceAtLeast(1)
        }

        private fun yearsBackForSelection(currentDate: String, selectedDate: String?): Int {
            val selected = selectedDate ?: return 1
            val currentYear = yearOf(currentDate) ?: return 1
            val selectedYear = yearOf(selected) ?: return 1
            return (currentYear - selectedYear).coerceAtLeast(1)
        }

        private fun summaryValuesForRange(
            byDate: Map<String, DailyStatEntity>,
            range: DateRange
        ): SummaryValues {
            val dates = ListeningDay.datesBetween(range.startDate, range.endDate)
            if (dates.isEmpty()) return SummaryValues()
            var durationMs = 0L
            var trackCount = 0
            var trafficBytes = 0L
            var activeDayCount = 0
            dates.forEach { date ->
                val stat = byDate[date]
                if (stat != null) {
                    durationMs += stat.listeningDurationMs
                    trackCount += stat.trackCount
                    trafficBytes += stat.networkTrafficBytes
                    if (stat.listeningDurationMs > 0L || stat.trackCount > 0 || stat.networkTrafficBytes > 0L) {
                        activeDayCount += 1
                    }
                }
            }
            return SummaryValues(
                durationMs = durationMs.toDouble(),
                trackCount = trackCount.toDouble(),
                activeDayCount = activeDayCount.toDouble(),
                trafficBytes = trafficBytes.toDouble()
            )
        }

        private fun startOfWeek(date: String): String? {
            val cal = calendarFor(date) ?: return null
            val daysSinceMonday = when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                else -> 6
            }
            cal.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
            return formatCalendar(cal)
        }

        private fun startOfMonth(date: String): String? {
            val cal = calendarFor(date) ?: return null
            cal.set(Calendar.DAY_OF_MONTH, 1)
            return formatCalendar(cal)
        }

        private fun startOfYear(date: String): String? {
            val cal = calendarFor(date) ?: return null
            cal.set(Calendar.MONTH, Calendar.JANUARY)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            return formatCalendar(cal)
        }

        private fun dayOfMonth(date: String): Int? {
            return calendarFor(date)?.get(Calendar.DAY_OF_MONTH)
        }

        private fun daysInMonth(date: String): Int? {
            return calendarFor(date)?.getActualMaximum(Calendar.DAY_OF_MONTH)
        }

        private fun addDays(date: String, days: Int): String? {
            val cal = calendarFor(date) ?: return null
            cal.add(Calendar.DAY_OF_MONTH, days)
            return formatCalendar(cal)
        }

        private fun addMonths(date: String, months: Int): String? {
            val cal = calendarFor(date) ?: return null
            cal.add(Calendar.MONTH, months)
            return formatCalendar(cal)
        }

        private fun addYears(date: String, years: Int): String? {
            val cal = calendarFor(date) ?: return null
            cal.add(Calendar.YEAR, years)
            return formatCalendar(cal)
        }

        private fun dayDistance(startDate: String, endDate: String): Int? {
            if (startDate == endDate) return 0
            val ascending = startDate < endDate
            val dates = if (ascending) {
                ListeningDay.datesBetween(startDate, endDate)
            } else {
                ListeningDay.datesBetween(endDate, startDate)
            }
            if (dates.isEmpty()) return null
            val distance = dates.size - 1
            return if (ascending) distance else -distance
        }

        private fun calendarFor(date: String): Calendar? {
            val parsed = dateFormat.parse(date) ?: return null
            return Calendar.getInstance(TimeZone.getDefault()).apply {
                time = parsed
            }
        }

        private fun formatCalendar(calendar: Calendar): String {
            return dateFormat.format(calendar.time)
        }

        private fun shortDateLabel(date: String): String {
            val cal = calendarFor(date) ?: return date
            return "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"
        }

        private fun formatYearDate(year: Int, month: Int, day: Int): String {
            return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
        }

        private val dateFormat: SimpleDateFormat
            get() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }

        private data class DateRange(
            val startDate: String,
            val endDate: String
        )

        private data class SummaryComparisonWindow(
            val currentRange: DateRange,
            val referenceRange: DateRange,
            val referenceLabel: String
        )

        private data class SummaryValues(
            val durationMs: Double = 0.0,
            val trackCount: Double = 0.0,
            val activeDayCount: Double = 0.0,
            val trafficBytes: Double = 0.0
        )

        private const val MILLIS_PER_MINUTE = 60_000L
        private const val SUMMARY_UPDATE_DEBOUNCE_MS = 600L
    }
}

private fun AlbumListeningRow.toSummaryArtwork(): ListeningSummaryArtwork =
    ListeningSummaryArtwork(
        title = title,
        coverUrl = coverUrl,
        coverPath = coverPath,
        coverThumbPath = coverThumbPath
    )
