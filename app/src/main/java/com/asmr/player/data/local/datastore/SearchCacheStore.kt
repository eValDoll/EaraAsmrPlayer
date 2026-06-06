package com.asmr.player.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asmr.player.domain.model.Album
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchCacheDataStore by preferencesDataStore(name = "search_cache")
private const val SearchHistoryLimit = 50

data class LastSearchStateV1(
    val savedAtMs: Long,
    val keyword: String,
    val orderName: String,
    val purchasedOnly: Boolean,
    val presaleOnly: Boolean = false,
    val chineseTranslatedOnly: Boolean = false,
    val collectedOnly: Boolean = false,
    val locale: String?,
    val page: Int,
    val canGoNext: Boolean,
    val results: List<Album>
)

@Singleton
class SearchCacheStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val key: Preferences.Key<String> = stringPreferencesKey("last_search_state_v1")
    private val historyKey: Preferences.Key<String> = stringPreferencesKey("search_history_keywords_v1")
    private val gson = Gson()
    private val historyListType = object : TypeToken<List<String>>() {}.type

    suspend fun readLast(): LastSearchStateV1? {
        val raw = context.searchCacheDataStore.data.first()[key].orEmpty()
        if (raw.isBlank()) return null
        return runCatching {
            gson.fromJson(raw, LastSearchStateV1::class.java)
        }.getOrNull()
    }

    suspend fun writeLast(state: LastSearchStateV1) {
        context.searchCacheDataStore.edit { prefs ->
            prefs[key] = gson.toJson(state)
        }
    }

    suspend fun readHistory(): List<String> {
        val raw = context.searchCacheDataStore.data.first()[historyKey].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val parsed: List<String> = gson.fromJson(raw, historyListType)
            mergeSearchHistory("", parsed)
        }.getOrDefault(emptyList())
    }

    suspend fun addHistory(keyword: String) {
        val current = readHistory()
        val next = mergeSearchHistory(keyword, current)
        context.searchCacheDataStore.edit { prefs ->
            prefs[historyKey] = gson.toJson(next)
        }
    }

    suspend fun clearHistory() {
        context.searchCacheDataStore.edit { prefs ->
            prefs.remove(historyKey)
        }
    }

    suspend fun clear() {
        context.searchCacheDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }
}

internal fun mergeSearchHistory(
    keyword: String,
    existing: List<String>,
    limit: Int = SearchHistoryLimit
): List<String> {
    val normalizedKeyword = keyword.trim()
    val merged = buildList {
        if (normalizedKeyword.isNotBlank()) add(normalizedKeyword)
        existing.forEach { item ->
            val normalized = item.trim()
            if (normalized.isNotBlank()) add(normalized)
        }
    }
    return merged
        .distinctBy { it.lowercase() }
        .take(limit.coerceAtLeast(0))
}
