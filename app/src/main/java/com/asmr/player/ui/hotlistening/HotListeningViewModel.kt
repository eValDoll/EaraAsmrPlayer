package com.asmr.player.ui.hotlistening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import com.asmr.player.data.settings.SettingsRepository
import com.asmr.player.domain.model.Album
import com.asmr.player.hotlistening.HotListeningApi
import com.asmr.player.hotlistening.HotListeningItem
import com.asmr.player.hotlistening.HotListeningSortMode
import com.asmr.player.util.MessageManager
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
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HotListeningViewModel @Inject constructor(
    private val hotListeningApi: HotListeningApi,
    private val settingsRepository: SettingsRepository,
    val messageManager: MessageManager,
) : ViewModel() {

    private val _rawUiState = MutableStateFlow<HotListeningRawUiState>(HotListeningRawUiState.Loading)
    val uiState: StateFlow<HotListeningUiState> = combine(
        _rawUiState,
        settingsRepository.searchBlockedKeywords
    ) { rawState, blockedKeywords ->
        rawState.toUiState(blockedKeywords)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HotListeningUiState.Loading)

    private val _selectedPeriod = MutableStateFlow("day")
    val selectedPeriod: StateFlow<String> = _selectedPeriod.asStateFlow()

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

    fun refresh() {
        val period = _selectedPeriod.value
        val mode = sortMode.value
        viewModelScope.launch {
            loadTopListings(period, mode)
        }
    }

    private suspend fun loadTopListings(period: String, sortMode: HotListeningSortMode) {
        if (!hotListeningApi.isBackendConfigured) {
            _rawUiState.update { HotListeningRawUiState.Error("后端未配置") }
            return
        }
        _rawUiState.update { HotListeningRawUiState.Loading }
        runCatching {
            val items = hotListeningApi.getTopListings(period, sortMode)
            if (items == null) {
                _rawUiState.update { HotListeningRawUiState.Error("请求失败") }
                return
            }
            val entries = items.map { it.toEntry(sortMode) }
            _rawUiState.update {
                HotListeningRawUiState.Success(
                    entries = entries,
                    period = period,
                    sortMode = sortMode
                )
            }
        }.onFailure { error ->
            _rawUiState.update {
                HotListeningRawUiState.Error(error.message ?: "加载失败")
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
                rjCode = rj,
                releaseDate = releaseDate.trim(),
                ratingValue = ratingValue ?: rateAverage2dp,
                ratingCount = (ratingCount ?: rateCount ?: reviewCount ?: 0).coerceAtLeast(0),
                dlCount = dlCount.coerceAtLeast(0),
                priceJpy = (priceJpy ?: price ?: 0).coerceAtLeast(0),
                description = description.trim()
            ),
            playCount = playCount,
            listenDurationMs = listenDurationMs,
            sortMode = sortMode
        )
    }
}

private sealed class HotListeningRawUiState {
    data object Loading : HotListeningRawUiState()
    data class Success(
        val entries: List<HotListeningEntry>,
        val period: String,
        val sortMode: HotListeningSortMode
    ) : HotListeningRawUiState()
    data class Error(val message: String) : HotListeningRawUiState()
}

@Immutable
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

@Immutable
sealed class HotListeningUiState {
    data object Loading : HotListeningUiState()
    data class Success(
        val entries: List<HotListeningEntry>,
        val blockedEntries: List<HotListeningEntry>,
        val period: String,
        val sortMode: HotListeningSortMode
    ) : HotListeningUiState()
    data class Error(val message: String) : HotListeningUiState()
}

@Immutable
internal data class HotListeningBlockedEntries(
    val visibleEntries: List<HotListeningEntry>,
    val blockedEntries: List<HotListeningEntry>
)

private fun HotListeningRawUiState.toUiState(blockedKeywords: List<String>): HotListeningUiState {
    return when (this) {
        HotListeningRawUiState.Loading -> HotListeningUiState.Loading
        is HotListeningRawUiState.Error -> HotListeningUiState.Error(message)
        is HotListeningRawUiState.Success -> {
            val filtered = filterHotListeningEntries(entries, blockedKeywords)
            HotListeningUiState.Success(
                entries = filtered.visibleEntries,
                blockedEntries = filtered.blockedEntries,
                period = period,
                sortMode = sortMode
            )
        }
    }
}

internal fun filterHotListeningEntries(
    entries: List<HotListeningEntry>,
    blockedKeywords: List<String>
): HotListeningBlockedEntries {
    val normalizedKeywords = normalizeHotListeningBlockedKeywords(blockedKeywords)
    if (normalizedKeywords.isEmpty()) {
        return HotListeningBlockedEntries(
            visibleEntries = entries,
            blockedEntries = emptyList()
        )
    }
    val (blockedEntries, visibleEntries) = entries.partition { entry ->
        val searchableText = entry.album.toBlockedKeywordSearchText()
        normalizedKeywords.any { keyword -> searchableText.contains(keyword) }
    }
    return HotListeningBlockedEntries(
        visibleEntries = visibleEntries,
        blockedEntries = blockedEntries
    )
}

private fun normalizeHotListeningBlockedKeywords(keywords: List<String>): List<String> {
    return keywords
        .map { it.trim() }
        .map { it.removePrefix("-").trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .map { it.lowercase(Locale.ROOT) }
}

private fun Album.toBlockedKeywordSearchText(): String {
    return buildString {
        appendLine(title)
        appendLine(circle)
        appendLine(cv)
        appendLine(rjCode)
        appendLine(workId)
        tags.forEach { appendLine(it) }
    }.lowercase(Locale.ROOT)
}
