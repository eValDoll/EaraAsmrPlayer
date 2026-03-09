package com.asmr.player.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.settingsDataStore by preferencesDataStore(name = "settings")

object SettingsKeys {
    val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
    fun eqBandLevel(index: Int) = intPreferencesKey("eq_band_$index")

    val LIBRARY_VIEW_MODE = intPreferencesKey("library_view_mode")
    val SEARCH_VIEW_MODE = intPreferencesKey("search_view_mode")

    val PLAY_MODE = intPreferencesKey("play_mode")

    val ASMR_ONE_SITE = intPreferencesKey("asmr_one_site")

    val FLOATING_LYRICS_ENABLED = booleanPreferencesKey("floating_lyrics_enabled")
    val FLOATING_LYRICS_COLOR = intPreferencesKey("floating_lyrics_color")
    val FLOATING_LYRICS_SIZE = floatPreferencesKey("floating_lyrics_size")
    val FLOATING_LYRICS_OPACITY = floatPreferencesKey("floating_lyrics_opacity")
    val FLOATING_LYRICS_Y = intPreferencesKey("floating_lyrics_y")
    val FLOATING_LYRICS_ALIGN = intPreferencesKey("floating_lyrics_align")
    val FLOATING_LYRICS_TOUCHABLE = booleanPreferencesKey("floating_lyrics_touchable")

    // Enhanced EQ & Audio Effects
    val EQ_VIRTUALIZER_STRENGTH = intPreferencesKey("eq_virtualizer_strength") // 0-1000
    val EQ_BALANCE = floatPreferencesKey("eq_balance") // -1.0 to 1.0
    val EQ_PRESET_NAME = stringPreferencesKey("eq_preset_name")
    val CUSTOM_EQ_PRESETS = stringPreferencesKey("custom_eq_presets_json")

    val FX_ORIGINAL_GAIN = floatPreferencesKey("fx_original_gain") // 0.0-2.0

    val FX_REVERB_ENABLED = booleanPreferencesKey("fx_reverb_enabled")
    val FX_REVERB_PRESET = stringPreferencesKey("fx_reverb_preset")
    val FX_REVERB_WET = intPreferencesKey("fx_reverb_wet") // 0-100

    val FX_ORBIT_ENABLED = booleanPreferencesKey("fx_orbit_enabled")
    val FX_ORBIT_SPEED = floatPreferencesKey("fx_orbit_speed") // 0-50
    val FX_ORBIT_DISTANCE = floatPreferencesKey("fx_orbit_distance") // 0-10
    val FX_ORBIT_AZIMUTH_DEG = floatPreferencesKey("fx_orbit_azimuth_deg") // 0-360

    val FX_CHANNEL_ENABLED = booleanPreferencesKey("fx_channel_enabled")
    val FX_CHANNEL_MODE = intPreferencesKey("fx_channel_mode")
    val FX_VOLUME_THRESHOLD_ENABLED = booleanPreferencesKey("fx_volume_threshold_enabled")
    val FX_VOLUME_THRESHOLD_MODE = intPreferencesKey("fx_volume_threshold_mode")
    val FX_VOLUME_THRESHOLD_MIN_DB = floatPreferencesKey("fx_volume_threshold_min_db")
    val FX_VOLUME_THRESHOLD_MAX_DB = floatPreferencesKey("fx_volume_threshold_max_db")
    val FX_VOLUME_LOUDNESS_TARGET_DB = floatPreferencesKey("fx_volume_loudness_target_db")

    val FX_STEREO_ENABLED = booleanPreferencesKey("fx_stereo_enabled")

    val UI_FX_CHANNEL_EXPANDED = booleanPreferencesKey("ui_fx_channel_expanded")
    val UI_FX_VOLUME_THRESHOLD_EXPANDED = booleanPreferencesKey("ui_fx_volume_threshold_expanded")
    val UI_FX_SPEED_PITCH_ENABLED = booleanPreferencesKey("ui_fx_speed_pitch_enabled")
    val UI_FX_SPEED_PITCH_EXPANDED = booleanPreferencesKey("ui_fx_speed_pitch_expanded")
    val UI_FX_STEREO_EXPANDED = booleanPreferencesKey("ui_fx_stereo_expanded")
    val UI_FX_EQUALIZER_EXPANDED = booleanPreferencesKey("ui_fx_equalizer_expanded")

    val SLEEP_TIMER_END_AT_MS = longPreferencesKey("sleep_timer_end_at_ms")
    val SLEEP_TIMER_LAST_DURATION_MIN = intPreferencesKey("sleep_timer_last_duration_min")
}
