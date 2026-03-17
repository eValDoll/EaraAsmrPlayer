package com.asmr.player.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
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
    private val staticHueArgbLightKey = intPreferencesKey("static_hue_argb_light")
    private val staticHueArgbDarkKey = intPreferencesKey("static_hue_argb_dark")
    private val coverBackgroundEnabledKey = booleanPreferencesKey("cover_background_enabled")
    private val coverBackgroundClarityKey = floatPreferencesKey("cover_background_clarity")
    private val coverPreviewModeKey = stringPreferencesKey("cover_preview_mode")
    private val lyricsPageFontSizeKey = floatPreferencesKey("lyrics_page_font_size")
    private val lyricsPageStrokeWidthKey = floatPreferencesKey("lyrics_page_stroke_width")
    private val lyricsPageLineHeightMultiplierKey = floatPreferencesKey("lyrics_page_line_height_multiplier")
    private val lyricsPageAlignKey = intPreferencesKey("lyrics_page_align")
    private val lyricsPageDisplayAreaModeKey = intPreferencesKey("lyrics_page_display_area_mode")
    private val recentAlbumsPanelExpandedKey = booleanPreferencesKey("recent_albums_panel_expanded")

    val theme: Flow<String> = context.settingsDataStore.data.map { it[themeKey] ?: "system" }
    val sfwMode: Flow<Boolean> = context.settingsDataStore.data.map { it[sfwModeKey] ?: false }
    val libraryRoots: Flow<Set<String>> = context.settingsDataStore.data.map { it[libraryRootsKey] ?: emptySet() }
    val dynamicPlayerHueEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[dynamicPlayerHueEnabledKey] ?: false }
    val staticHueArgbLight: Flow<Int?> = context.settingsDataStore.data.map { prefs ->
        if (prefs.contains(staticHueArgbLightKey)) prefs[staticHueArgbLightKey] else null
    }
    val staticHueArgbDark: Flow<Int?> = context.settingsDataStore.data.map { prefs ->
        if (prefs.contains(staticHueArgbDarkKey)) prefs[staticHueArgbDarkKey] else null
    }
    val staticHueArgb: Flow<Int?> = context.settingsDataStore.data.map { prefs ->
        val themeMode = prefs[themeKey] ?: "system"
        val isDark = themeMode == "dark" || themeMode == "soft_dark"
        val key = if (isDark) staticHueArgbDarkKey else staticHueArgbLightKey
        if (prefs.contains(key)) prefs[key] else null
    }
    val coverBackgroundEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[coverBackgroundEnabledKey] ?: true }
    val coverBackgroundClarity: Flow<Float> = context.settingsDataStore.data.map { it[coverBackgroundClarityKey] ?: 0.35f }
    val coverPreviewMode: Flow<CoverPreviewMode> = context.settingsDataStore.data.map {
        CoverPreviewMode.fromStorageValue(it[coverPreviewModeKey])
    }
    val lyricsPageSettings: Flow<LyricsPageSettings> = context.settingsDataStore.data.map { prefs ->
        LyricsPageSettings(
            fontSizeSp = prefs[lyricsPageFontSizeKey] ?: 21f,
            strokeWidthSp = prefs[lyricsPageStrokeWidthKey] ?: 0.1f,
            lineHeightMultiplier = prefs[lyricsPageLineHeightMultiplierKey] ?: 1.5f,
            align = prefs[lyricsPageAlignKey] ?: 0,
            displayAreaMode = prefs[lyricsPageDisplayAreaModeKey] ?: 0
        )
    }
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
            val themeMode = prefs[themeKey] ?: "system"
            val isDark = themeMode == "dark" || themeMode == "soft_dark"
            val key = if (isDark) staticHueArgbDarkKey else staticHueArgbLightKey
            if (argb == null) {
                prefs.remove(key)
            } else {
                prefs[key] = argb
            }
        }
    }

    suspend fun setCoverBackgroundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[coverBackgroundEnabledKey] = enabled }
    }

    suspend fun setCoverBackgroundClarity(clarity: Float) {
        context.settingsDataStore.edit { it[coverBackgroundClarityKey] = clarity }
    }

    suspend fun setCoverPreviewMode(mode: CoverPreviewMode) {
        context.settingsDataStore.edit { it[coverPreviewModeKey] = mode.storageValue }
    }

    suspend fun setLyricsPageSettings(settings: LyricsPageSettings) {
        context.settingsDataStore.edit {
            it[lyricsPageFontSizeKey] = settings.fontSizeSp
            it[lyricsPageStrokeWidthKey] = settings.strokeWidthSp
            it[lyricsPageLineHeightMultiplierKey] = settings.lineHeightMultiplier
            it[lyricsPageAlignKey] = settings.align
            it[lyricsPageDisplayAreaModeKey] = settings.displayAreaMode
        }
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
