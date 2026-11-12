package com.asmr.player.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asmr.player.domain.model.Album
import com.asmr.player.domain.model.CollectedSearchSource
import com.asmr.player.domain.model.Track
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchCacheDataStore by preferencesDataStore(name = "search_cache")
private const val SearchHistoryLimit = 50

data class LastSearchState(
    val savedAtMs: Long,
    val keyword: String,
    val orderName: String,
    val purchasedOnly: Boolean,
    val presaleOnly: Boolean = false,
    val chineseTranslatedOnly: Boolean = false,
    val collectedOnly: Boolean = false,
    val collectedSourceName: String? = null,
    val hasSubtitle: Boolean = false,
    val allAges: Boolean = false,
    val collectedSortName: String = "",
    val locale: String?,
    val page: Int,
    val canGoNext: Boolean,
    val results: List<Album>
)

internal fun LastSearchState.restoreCollectedSource(): LastSearchState {
    val source = CollectedSearchSource.fromName(collectedSourceName)
    return copy(
        collectedSourceName = source.name,
        results = if (collectedOnly && !purchasedOnly && !presaleOnly && !chineseTranslatedOnly) {
            results.map { it.copy(collectedSourceName = source.name) }
        } else results
    )
}

@Singleton
class SearchCacheStore internal constructor(
    private val dataStore: DataStore<Preferences>
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.searchCacheDataStore)

    private val key: Preferences.Key<String> = stringPreferencesKey("last_search_state_v1")
    private val historyKey: Preferences.Key<String> = stringPreferencesKey("search_history_keywords_v1")
    private val gson = GsonBuilder()
        .registerTypeAdapterFactory(SearchCacheDefaultsAdapter(Album(title = "", path = ""), setOf("tagDetails")))
        .registerTypeAdapterFactory(SearchCacheDefaultsAdapter(Track(albumId = 0L, title = "", path = "")))
        .registerTypeAdapterFactory(SearchCacheDefaultsAdapter(LastSearchState(
            savedAtMs = 0L, keyword = "", orderName = "", purchasedOnly = false,
            locale = null, page = 1, canGoNext = false, results = emptyList()
        )))
        .create()
    private val historyListType = object : TypeToken<List<String>>() {}.type

    suspend fun readLast(): LastSearchState? {
        val raw = dataStore.data.first()[key].orEmpty()
        if (raw.isBlank()) return null
        return withContext(Dispatchers.Default) {
            runCatching {
                gson.fromJson(raw, LastSearchState::class.java).restoreCollectedSource()
            }.getOrNull()
        }
    }

    suspend fun writeLast(state: LastSearchState) {
        dataStore.edit { prefs ->
            prefs[key] = gson.toJson(state)
        }
    }

    suspend fun readHistory(): List<String> {
        val raw = dataStore.data.first()[historyKey].orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val parsed: List<String> = gson.fromJson(raw, historyListType)
            mergeSearchHistory("", parsed)
        }.getOrDefault(emptyList())
    }

    suspend fun addHistory(keyword: String) {
        val current = readHistory()
        val next = mergeSearchHistory(keyword, current)
        dataStore.edit { prefs ->
            prefs[historyKey] = gson.toJson(next)
        }
    }

    suspend fun clearHistory() {
        dataStore.edit { prefs ->
            prefs.remove(historyKey)
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(key)
        }
    }
}

// Gson 绕过这些 Kotlin 模型的构造函数；旧缓存缺失的字段不会自动获得默认值。
// 仅在缓存读取边界补齐默认值，保留现有 JSON 格式及可空字段的显式 null。
private class SearchCacheDefaultsAdapter(
    private val defaultValue: Any,
    private val nullableDefaultFields: Set<String> = emptySet()
) : TypeAdapterFactory {
    private val defaults = Gson().toJsonTree(defaultValue).asJsonObject

    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != defaultValue.javaClass) return null
        val delegate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T>() {
            override fun read(reader: JsonReader): T {
                val stored = JsonParser.parseReader(reader)
                if (!stored.isJsonObject) throw JsonParseException("Expected ${type.rawType.simpleName} object")
                val restored = defaults.deepCopy()
                stored.asJsonObject.entrySet().forEach { (name, value) ->
                    if (!value.isJsonNull || name in nullableDefaultFields || !defaults.has(name)) {
                        restored.add(name, value)
                    }
                }
                return delegate.fromJsonTree(restored)
            }

            override fun write(writer: JsonWriter, value: T) = delegate.write(writer, value)
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
