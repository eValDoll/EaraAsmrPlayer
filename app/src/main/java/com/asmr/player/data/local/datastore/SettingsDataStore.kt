package com.asmr.player.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import com.asmr.player.data.settings.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme")
    private val sfwModeKey = booleanPreferencesKey("sfw_mode")
    private val libraryRootsKey = stringSetPreferencesKey("library_roots")
    private val dynamicPlayerHueEnabledKey = booleanPreferencesKey("dynamic_player_hue_enabled")
    private val staticHueArgbKey = intPreferencesKey("static_hue_argb")
    private val coverBackgroundEnabledKey = booleanPreferencesKey("cover_background_enabled")
    private val coverBackgroundClarityKey = floatPreferencesKey("cover_background_clarity")
    private val recentAlbumsPanelExpandedKey = booleanPreferencesKey("recent_albums_panel_expanded")

    val theme: Flow<String> = context.settingsDataStore.data.map { it[themeKey] ?: "system" }
    val sfwMode: Flow<Boolean> = context.settingsDataStore.data.map { it[sfwModeKey] ?: false }
    val libraryRoots: Flow<Set<String>> = context.settingsDataStore.data.map { it[libraryRootsKey] ?: emptySet() }
    val dynamicPlayerHueEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[dynamicPlayerHueEnabledKey] ?: false }
    val staticHueArgb: Flow<Int?> = context.settingsDataStore.data.map { prefs ->
        if (prefs.contains(staticHueArgbKey)) prefs[staticHueArgbKey] else null
    }
    val coverBackgroundEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[coverBackgroundEnabledKey] ?: true }
    val coverBackgroundClarity: Flow<Float> = context.settingsDataStore.data.map { it[coverBackgroundClarityKey] ?: 0.35f }
    val recentAlbumsPanelExpanded: Flow<Boolean> = context.settingsDataStore.data.map { it[recentAlbumsPanelExpandedKey] ?: true }

    suspend fun setTheme(theme: String) {
        context.settingsDataStore.edit { it[themeKey] = theme }
    }

    suspend fun setSfwMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[sfwModeKey] = enabled }
    }

    suspend fun setDynamicPlayerHueEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[dynamicPlayerHueEnabledKey] = enabled }
    }

    suspend fun setStaticHueArgb(argb: Int?) {
        context.settingsDataStore.edit { prefs ->
            if (argb == null) {
                prefs.remove(staticHueArgbKey)
            } else {
                prefs[staticHueArgbKey] = argb
            }
        }
    }

    suspend fun setCoverBackgroundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[coverBackgroundEnabledKey] = enabled }
    }

    suspend fun setCoverBackgroundClarity(clarity: Float) {
        context.settingsDataStore.edit { it[coverBackgroundClarityKey] = clarity }
    }

    suspend fun setRecentAlbumsPanelExpanded(expanded: Boolean) {
        context.settingsDataStore.edit { it[recentAlbumsPanelExpandedKey] = expanded }
    }

    suspend fun addLibraryRoot(path: String) {
        context.settingsDataStore.edit {
            val current = it[libraryRootsKey] ?: emptySet()
            it[libraryRootsKey] = current + path
        }
    }

    suspend fun removeLibraryRoot(path: String) {
        context.settingsDataStore.edit {
            val current = it[libraryRootsKey] ?: emptySet()
            it[libraryRootsKey] = current - path
        }
    }
}
