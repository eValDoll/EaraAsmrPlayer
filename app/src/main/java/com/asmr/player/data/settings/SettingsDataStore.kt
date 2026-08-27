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
    val APP_VOLUME_PERCENT = intPreferencesKey("app_volume_percent")
    val APP_CACHE_MAX_SIZE_MB = intPreferencesKey("app_cache_max_size_mb")

    val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
    fun eqBandLevel(index: Int) = intPreferencesKey("eq_band_$index")

    val LIBRARY_VIEW_MODE = intPreferencesKey("library_view_mode")
    val SEARCH_VIEW_MODE = intPreferencesKey("search_view_mode")
    val HOT_LISTENING_VIEW_MODE = intPreferencesKey("hot_listening_view_mode")
    val HOT_LISTENING_SORT_MODE = stringPreferencesKey("hot_listening_sort_mode")
    val SEARCH_BLOCKED_KEYWORDS = stringPreferencesKey("search_blocked_keywords")
    val DOWNLOAD_DIRECTORY_TREE_URI = stringPreferencesKey("download_directory_tree_uri")
    val DOWNLOAD_DIRECTORY_LABEL = stringPreferencesKey("download_directory_label")
    val GENERATED_SUBTITLE_FILE_BACKFILL_VERSION = intPreferencesKey("generated_subtitle_file_backfill_version")

    val PLAY_MODE = intPreferencesKey("play_mode")

    val ASMR_ONE_SITE = intPreferencesKey("asmr_one_site")
    val NETWORK_PROXY_MODE = stringPreferencesKey("network_proxy_mode")
    val NETWORK_PROXY_HOST = stringPreferencesKey("network_proxy_host")
    val NETWORK_PROXY_PORT = intPreferencesKey("network_proxy_port")
    val NETWORK_PROXY_AUTHENTICATION_ENABLED = booleanPreferencesKey("network_proxy_authentication_enabled")
    val NETWORK_PROXY_USERNAME = stringPreferencesKey("network_proxy_username")
    val NETWORK_PROXY_PASSWORD_CONFIGURED = booleanPreferencesKey("network_proxy_password_configured")
    val NETWORK_PROXY_CREDENTIAL_VERSION = longPreferencesKey("network_proxy_credential_version")
    val CUSTOM_DNS_SERVER = stringPreferencesKey("custom_dns_server")

    val FLOATING_LYRICS_ENABLED = booleanPreferencesKey("floating_lyrics_enabled")
    val FLOATING_LYRICS_COLOR = intPreferencesKey("floating_lyrics_color")
    val FLOATING_LYRICS_SIZE = floatPreferencesKey("floating_lyrics_size")
    val FLOATING_LYRICS_OPACITY = floatPreferencesKey("floating_lyrics_opacity")
    val FLOATING_LYRICS_X = intPreferencesKey("floating_lyrics_x")
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
    val FX_SCENE_ENABLED = booleanPreferencesKey("fx_scene_enabled")
    val FX_SCENE_PRESET_ID = stringPreferencesKey("fx_scene_preset_id")
    val FX_SCENE_AMOUNT = intPreferencesKey("fx_scene_amount")

    val FX_STEREO_ENABLED = booleanPreferencesKey("fx_stereo_enabled")

    val UI_FX_CHANNEL_EXPANDED = booleanPreferencesKey("ui_fx_channel_expanded")
    val UI_FX_VOLUME_THRESHOLD_EXPANDED = booleanPreferencesKey("ui_fx_volume_threshold_expanded")
    val UI_FX_SCENE_EXPANDED = booleanPreferencesKey("ui_fx_scene_expanded")
    val UI_FX_SPEED_PITCH_ENABLED = booleanPreferencesKey("ui_fx_speed_pitch_enabled")
    val UI_FX_SPEED_PITCH_EXPANDED = booleanPreferencesKey("ui_fx_speed_pitch_expanded")
    val UI_FX_STEREO_EXPANDED = booleanPreferencesKey("ui_fx_stereo_expanded")
    val UI_FX_EQUALIZER_EXPANDED = booleanPreferencesKey("ui_fx_equalizer_expanded")

    val SLEEP_TIMER_END_AT_MS = longPreferencesKey("sleep_timer_end_at_ms")
    val SLEEP_TIMER_LAST_DURATION_MIN = intPreferencesKey("sleep_timer_last_duration_min")

    val PAUSE_ON_OUTPUT_DISCONNECT = booleanPreferencesKey("pause_on_output_disconnect")
    val RESUME_ON_OUTPUT_CONNECT = booleanPreferencesKey("resume_on_output_connect")
    val PAUSE_ON_OTHER_AUDIO = booleanPreferencesKey("pause_on_other_audio")
    val PLAY_FADE_IN_MS = intPreferencesKey("play_fade_in_ms")
    val PAUSE_FADE_OUT_MS = intPreferencesKey("pause_fade_out_ms")
    val SFW_HIDE_SYSTEM_CONTROLS = booleanPreferencesKey("sfw_hide_system_controls")
    val SHOW_MINI_PLAYER_BAR = booleanPreferencesKey("show_mini_player_bar")

    val DEEPSEEK_THINKING_ENABLED = booleanPreferencesKey("deepseek_thinking_enabled")
    val DEEPSEEK_REASONING_EFFORT = stringPreferencesKey("deepseek_reasoning_effort")
    val DEEPSEEK_FINAL_POLISH_ENABLED = booleanPreferencesKey("deepseek_final_polish_enabled")
}
