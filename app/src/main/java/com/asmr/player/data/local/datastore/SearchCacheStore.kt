package com.asmr.player.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asmr.player.domain.model.Album
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchCacheDataStore by preferencesDataStore(name = "search_cache")

data class LastSearchStateV1(
    val savedAtMs: Long,
    val keyword: String,
    val orderName: String,
    val purchasedOnly: Boolean,
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
    private val gson = Gson()

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

    suspend fun clear() {
        context.searchCacheDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }
}
