package com.asmr.player.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import com.asmr.player.data.settings.CoverPreviewMode
import com.asmr.player.data.settings.LyricsPageSettings
import com.asmr.player.data.settings.NowPlayingHomeLayoutMode
import com.asmr.player.data.settings.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ThemeBootstrapPreferences(
    val theme: String = "system",
    val dynamicPlayerHueEnabled: Boolean = false,
    val staticHueArgbLight: Int? = null,
    val staticHueArgbDark: Int? = null,
    val lastDynamicHueSourceKey: String? = null,
    val lastDynamicHueSeedArgb: Int? = null
)

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
    private val lastDynamicHueSourceKeyKey = stringPreferencesKey("last_dynamic_hue_source_key")
    private val lastDynamicHueSeedArgbKey = intPreferencesKey("last_dynamic_hue_seed_argb")
    private val coverBackgroundEnabledKey = booleanPreferencesKey("cover_background_enabled")
    private val coverBackgroundClarityKey = floatPreferencesKey("cover_background_clarity")
    private val coverPreviewModeKey = stringPreferencesKey("cover_preview_mode")
    private val nowPlayingHomeLayoutModeKey = stringPreferencesKey("now_playing_home_layout_mode")
    private val nowPlayingHomeLayoutHintDismissedKey = booleanPreferencesKey("now_playing_home_layout_hint_dismissed")
    private val lyricsPageFontSizeKey = floatPreferencesKey("lyrics_page_font_size")
    private val lyricsPageStrokeWidthKey = floatPreferencesKey("lyrics_page_stroke_width")
    private val lyricsPageLineHeightMultiplierKey = floatPreferencesKey("lyrics_page_line_height_multiplier")
    private val lyricsPageAlignKey = intPreferencesKey("lyrics_page_align")
    private val lyricsPageDisplayAreaModeKey = intPreferencesKey("lyrics_page_display_area_mode")
    private val recentAlbumsPanelExpandedKey = booleanPreferencesKey("recent_albums_panel_expanded")
    private val miniPlayerDisplayModeKey = stringPreferencesKey("mini_player_display_mode")
    private val bottomChromePinnedRouteKey = stringPreferencesKey("bottom_chrome_pinned_route")
    private val autoUpdateCheckEnabledKey = booleanPreferencesKey("auto_update_check_enabled")
    private val lastHandledClipboardEventKey = stringPreferencesKey("last_handled_clipboard_event")

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
    val lastDynamicHueSourceKey: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[lastDynamicHueSourceKeyKey]
    }
    val lastDynamicHueSeedArgb: Flow<Int?> = context.settingsDataStore.data.map { prefs ->
        if (prefs.contains(lastDynamicHueSeedArgbKey)) prefs[lastDynamicHueSeedArgbKey] else null
    }
    val themeBootstrapPreferences: Flow<ThemeBootstrapPreferences> = context.settingsDataStore.data.map { prefs ->
        ThemeBootstrapPreferences(
            theme = prefs[themeKey] ?: "system",
            dynamicPlayerHueEnabled = prefs[dynamicPlayerHueEnabledKey] ?: false,
            staticHueArgbLight = if (prefs.contains(staticHueArgbLightKey)) prefs[staticHueArgbLightKey] else null,
            staticHueArgbDark = if (prefs.contains(staticHueArgbDarkKey)) prefs[staticHueArgbDarkKey] else null,
            lastDynamicHueSourceKey = prefs[lastDynamicHueSourceKeyKey],
            lastDynamicHueSeedArgb = if (prefs.contains(lastDynamicHueSeedArgbKey)) prefs[lastDynamicHueSeedArgbKey] else null
        )
    }
    val coverBackgroundEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[coverBackgroundEnabledKey] ?: true }
    val coverBackgroundClarity: Flow<Float> = context.settingsDataStore.data.map { it[coverBackgroundClarityKey] ?: 0.35f }
    val coverPreviewMode: Flow<CoverPreviewMode> = context.settingsDataStore.data.map {
        CoverPreviewMode.fromStorageValue(it[coverPreviewModeKey])
    }
    val nowPlayingHomeLayoutMode: Flow<NowPlayingHomeLayoutMode> = context.settingsDataStore.data.map {
        NowPlayingHomeLayoutMode.fromStorageValue(it[nowPlayingHomeLayoutModeKey])
    }
    val nowPlayingHomeLayoutHintDismissed: Flow<Boolean> = context.settingsDataStore.data.map {
        it[nowPlayingHomeLayoutHintDismissedKey] ?: false
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
    val miniPlayerDisplayMode: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[miniPlayerDisplayModeKey] ?: "CoverOnly"
    }
    val bottomChromePinnedRoute: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[bottomChromePinnedRouteKey]
    }
    val autoUpdateCheckEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[autoUpdateCheckEnabledKey] ?: true
    }
    val lastHandledClipboardEvent: Flow<String?> = context.settingsDataStore.data.map { prefs ->
        prefs[lastHandledClipboardEventKey]
    }

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

    suspend fun loadThemeBootstrapPreferences(): ThemeBootstrapPreferences {
        return withContext(Dispatchers.IO) {
            themeBootstrapPreferences.first()
        }
    }

    suspend fun setLastDynamicHueSeed(sourceKey: String, argb: Int) {
        val normalizedSourceKey = sourceKey.trim()
        if (normalizedSourceKey.isBlank()) return
        context.settingsDataStore.edit { prefs ->
            prefs[lastDynamicHueSourceKeyKey] = normalizedSourceKey
            prefs[lastDynamicHueSeedArgbKey] = argb
        }
    }

    suspend fun clearLastDynamicHueSeed() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(lastDynamicHueSourceKeyKey)
            prefs.remove(lastDynamicHueSeedArgbKey)
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

    suspend fun setNowPlayingHomeLayoutMode(
        mode: NowPlayingHomeLayoutMode,
        dismissHint: Boolean = false
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[nowPlayingHomeLayoutModeKey] = mode.storageValue
            if (dismissHint) {
                prefs[nowPlayingHomeLayoutHintDismissedKey] = true
            }
        }
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

    suspend fun setMiniPlayerDisplayMode(mode: String) {
        context.settingsDataStore.edit { it[miniPlayerDisplayModeKey] = mode }
    }

    suspend fun setBottomChromePinnedRoute(route: String?) {
        context.settingsDataStore.edit { prefs ->
            if (route.isNullOrBlank()) {
                prefs.remove(bottomChromePinnedRouteKey)
            } else {
                prefs[bottomChromePinnedRouteKey] = route
            }
        }
    }

    suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[autoUpdateCheckEnabledKey] = enabled
        }
    }

    suspend fun setLastHandledClipboardEvent(eventKey: String) {
        val normalized = eventKey.trim()
        if (normalized.isBlank()) return
        context.settingsDataStore.edit { prefs ->
            prefs[lastHandledClipboardEventKey] = normalized
        }
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
