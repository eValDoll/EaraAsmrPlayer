package com.asmr.player.ui.library

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.libraryDataStore by preferencesDataStore(name = "library")

data class LibraryFilterPreset(
    val id: String,
    val name: String,
    val spec: LibraryQuerySpec
)

class LibraryPresetStore(
    private val context: Context,
    private val gson: Gson = Gson()
) {
    private val key: Preferences.Key<String> = stringPreferencesKey("filter_presets_v1")

    val presets: Flow<List<LibraryFilterPreset>> = context.libraryDataStore.data.map { prefs ->
        val json = prefs[key].orEmpty()
        if (json.isBlank()) return@map emptyList()
        runCatching {
            val t = object : TypeToken<List<LibraryFilterPreset>>() {}.type
            gson.fromJson<List<LibraryFilterPreset>>(json, t).orEmpty()
        }.getOrDefault(emptyList())
    }

    suspend fun savePreset(name: String, spec: LibraryQuerySpec): LibraryFilterPreset {
        val preset = LibraryFilterPreset(id = UUID.randomUUID().toString(), name = name.trim(), spec = spec)
        context.libraryDataStore.edit { prefs ->
            val current = readPresetsFromPrefs(prefs)
            val updated = (current + preset)
                .distinctBy { it.id }
                .takeLast(50)
            prefs[key] = gson.toJson(updated)
        }
        return preset
    }

    suspend fun deletePreset(id: String) {
        context.libraryDataStore.edit { prefs ->
            val current = readPresetsFromPrefs(prefs)
            val updated = current.filterNot { it.id == id }
            prefs[key] = gson.toJson(updated)
        }
    }

    private fun readPresetsFromPrefs(prefs: Preferences): List<LibraryFilterPreset> {
        val json = prefs[key].orEmpty()
        if (json.isBlank()) return emptyList()
        return runCatching {
            val t = object : TypeToken<List<LibraryFilterPreset>>() {}.type
            gson.fromJson<List<LibraryFilterPreset>>(json, t).orEmpty()
        }.getOrDefault(emptyList())
    }
}

