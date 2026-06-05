package com.asmr.player.ui.hotlistening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.hotlistening.HotListeningApi
import com.asmr.player.hotlistening.HotListeningItem
import com.asmr.player.hotlistening.HotListeningSortMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HotListeningViewModel @Inject constructor(
    private val hotListeningApi: HotListeningApi,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HotListeningUiState>(HotListeningUiState.Loading)
    val uiState: StateFlow<HotListeningUiState> = _uiState.asStateFlow()

    private val _selectedPeriod = MutableStateFlow("day")
    val selectedPeriod: StateFlow<String> = _selectedPeriod.asStateFlow()

    var scrollPosition: HotListeningScrollPosition = HotListeningScrollPosition()
        private set

    val viewMode: StateFlow<Int> = settingsRepository.hotListeningViewMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val sortMode: StateFlow<HotListeningSortMode> = settingsRepository.hotListeningSortMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HotListeningSortMode.PlayCount)

    init {
        viewModelScope.launch {
            combine(_selectedPeriod, sortMode) { period, mode -> period to mode }
                .collectLatest { (period, mode) ->
                    loadTopListings(period, mode)
                }
        }
    }

    fun selectPeriod(period: String) {
        _selectedPeriod.value = period
    }

    fun selectSortMode(mode: HotListeningSortMode) {
        viewModelScope.launch {
            settingsRepository.setHotListeningSortMode(mode)
        }
    }

    fun setViewMode(mode: Int) {
        viewModelScope.launch {
            settingsRepository.setHotListeningViewMode(mode.coerceIn(0, 1))
        }
    }

    fun updateListScrollPosition(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        scrollPosition = scrollPosition.copy(
            listFirstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
            listFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0)
        )
    }

    fun updateGridScrollPosition(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        scrollPosition = scrollPosition.copy(
            gridFirstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
            gridFirstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0)
        )
    }

    fun resetScrollPosition() {
        scrollPosition = HotListeningScrollPosition()
    }

    fun refresh() {
        val period = _selectedPeriod.value
        val mode = sortMode.value
        viewModelScope.launch {
            loadTopListings(period, mode)
        }
    }

    private suspend fun loadTopListings(period: String, sortMode: HotListeningSortMode) {
        if (!hotListeningApi.isBackendConfigured) {
            _uiState.update { HotListeningUiState.Error("后端未配置") }
            return
        }
        _uiState.update { HotListeningUiState.Loading }
        runCatching {
            val items = hotListeningApi.getTopListings(period, sortMode)
            if (items == null) {
                _uiState.update { HotListeningUiState.Error("请求失败") }
                return
            }
            val entries = items.map { it.toEntry(sortMode) }
            _uiState.update {
                HotListeningUiState.Success(
                    entries = entries,
                    period = period,
                    sortMode = sortMode
                )
            }
        }.onFailure { error ->
            _uiState.update {
                HotListeningUiState.Error(error.message ?: "加载失败")
            }
        }
    }

    private fun HotListeningItem.toEntry(sortMode: HotListeningSortMode): HotListeningEntry {
        return HotListeningEntry(
            album = Album(
                title = title,
                path = "",
                circle = circle,
                cv = cv,
                tags = tagList,
                coverUrl = coverUrl,
                rjCode = rj
            ),
            playCount = playCount,
            listenDurationMs = listenDurationMs,
            sortMode = sortMode
        )
    }
}

data class HotListeningScrollPosition(
    val listFirstVisibleItemIndex: Int = 0,
    val listFirstVisibleItemScrollOffset: Int = 0,
    val gridFirstVisibleItemIndex: Int = 0,
    val gridFirstVisibleItemScrollOffset: Int = 0
)

data class HotListeningEntry(
    val album: Album,
    val playCount: Int,
    val listenDurationMs: Long,
    val sortMode: HotListeningSortMode
) {
    val metricLabel: String
        get() = when (sortMode) {
            HotListeningSortMode.PlayCount -> formatCompactCount(playCount.toLong())
            HotListeningSortMode.ListenDuration -> formatCompactDuration(listenDurationMs)
        }

    private fun formatCompactCount(value: Long): String {
        return when {
            value >= 100_000_000L -> formatDecimalUnit(value, 100_000_000L, "亿")
            value >= 10_000L -> formatDecimalUnit(value, 10_000L, "万")
            else -> value.toString()
        }
    }

    private fun formatDecimalUnit(value: Long, unitValue: Long, unit: String): String {
        val whole = value / unitValue
        val decimal = (value % unitValue) / (unitValue / 10L)
        return if (decimal > 0L && whole < 100L) {
            "$whole.$decimal$unit"
        } else {
            "$whole$unit"
        }
    }

    private fun formatCompactDuration(ms: Long): String {
        if (ms < 60_000L) return "<1分钟"

        val totalMinutes = ms / 60_000L
        if (totalMinutes < 60L) return "${totalMinutes}分钟"

        val totalHours = totalMinutes / 60L
        val compactHours = when {
            totalHours >= 100_000_000L -> "${totalHours / 100_000_000L}亿"
            totalHours >= 10_000L -> "${totalHours / 10_000L}万"
            else -> totalHours.toString()
        }
        return "${compactHours}小时"
    }
}

sealed class HotListeningUiState {
    data object Loading : HotListeningUiState()
    data class Success(
        val entries: List<HotListeningEntry>,
        val period: String,
        val sortMode: HotListeningSortMode
    ) : HotListeningUiState()
    data class Error(val message: String) : HotListeningUiState()
}
