package com.asmr.player.data.settings

import android.content.Context
import android.media.audiofx.Equalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class EqualizerSettings(
    val enabled: Boolean = false,
    val bandLevels: List<Int> = List(10) { 0 },
    val virtualizerStrength: Int = 0,
    val channelEnabled: Boolean = false,
    val channelExpanded: Boolean = true,
    val balance: Float = 0f,
    val presetName: String = "默认",
    val originalGain: Float = 1f,
    val reverbEnabled: Boolean = false,
    val reverbPreset: String = "无",
    val reverbWet: Int = 0,
    val volumeThresholdExpanded: Boolean = true,
    val stereoEnabled: Boolean = false,
    val stereoExpanded: Boolean = true,
    val orbitEnabled: Boolean = false,
    val orbitSpeed: Float = 25f,
    val orbitDistance: Float = 5f,
    val orbitAzimuthDeg: Float = 0f,
    val channelMode: Int = 0,
    val volumeThresholdEnabled: Boolean = false,
    val volumeThresholdMode: Int = 1,
    val volumeThresholdMinDb: Float = -24f,
    val volumeThresholdMaxDb: Float = -6f,
    val volumeLoudnessTargetDb: Float = -18f,
    val speedPitchEnabled: Boolean = true,
    val speedPitchExpanded: Boolean = true,
    val equalizerExpanded: Boolean = true
)

@Singleton
class AudioEffectController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val equalizerSettings: Flow<EqualizerSettings> = context.settingsDataStore.data.map { prefs ->
        val enabled = prefs[SettingsKeys.EQ_ENABLED] ?: false
        val levels = (0 until 10).map { idx -> prefs[SettingsKeys.eqBandLevel(idx)] ?: 0 }
        val virt = prefs[SettingsKeys.EQ_VIRTUALIZER_STRENGTH] ?: 0
        val bal = prefs[SettingsKeys.EQ_BALANCE] ?: 0f
        val preset = prefs[SettingsKeys.EQ_PRESET_NAME] ?: "自定义"
        val gain = prefs[SettingsKeys.FX_ORIGINAL_GAIN] ?: 1f
        val reverbEnabled = prefs[SettingsKeys.FX_REVERB_ENABLED] ?: false
        val reverbPreset = prefs[SettingsKeys.FX_REVERB_PRESET] ?: "无"
        val reverbWet = prefs[SettingsKeys.FX_REVERB_WET] ?: 0
        val orbitEnabled = prefs[SettingsKeys.FX_ORBIT_ENABLED] ?: false
        val orbitSpeed = prefs[SettingsKeys.FX_ORBIT_SPEED] ?: 25f
        val orbitDistance = prefs[SettingsKeys.FX_ORBIT_DISTANCE] ?: 5f
        val channelMode = prefs[SettingsKeys.FX_CHANNEL_MODE] ?: 0
        val channelEnabled = prefs[SettingsKeys.FX_CHANNEL_ENABLED] ?: (bal != 0f || channelMode != 0)
        val channelExpanded = prefs[SettingsKeys.UI_FX_CHANNEL_EXPANDED] ?: true
        val vtEnabled = prefs[SettingsKeys.FX_VOLUME_THRESHOLD_ENABLED] ?: false
        val vtMode = prefs[SettingsKeys.FX_VOLUME_THRESHOLD_MODE] ?: 1
        val vtMinDb = prefs[SettingsKeys.FX_VOLUME_THRESHOLD_MIN_DB] ?: -24f
        val vtMaxDb = prefs[SettingsKeys.FX_VOLUME_THRESHOLD_MAX_DB] ?: -6f
        val loudnessTargetDb = prefs[SettingsKeys.FX_VOLUME_LOUDNESS_TARGET_DB] ?: -18f
        val volumeThresholdExpanded = prefs[SettingsKeys.UI_FX_VOLUME_THRESHOLD_EXPANDED] ?: true
        val stereoEnabled = prefs[SettingsKeys.FX_STEREO_ENABLED] ?: false
        val stereoExpanded = prefs[SettingsKeys.UI_FX_STEREO_EXPANDED] ?: true
        val orbitAzimuthDeg = prefs[SettingsKeys.FX_ORBIT_AZIMUTH_DEG] ?: 0f
        val speedPitchEnabled = prefs[SettingsKeys.UI_FX_SPEED_PITCH_ENABLED] ?: true
        val speedPitchExpanded = prefs[SettingsKeys.UI_FX_SPEED_PITCH_EXPANDED] ?: true
        val equalizerExpanded = prefs[SettingsKeys.UI_FX_EQUALIZER_EXPANDED] ?: true
        EqualizerSettings(
            enabled = enabled,
            bandLevels = levels,
            virtualizerStrength = virt,
            channelEnabled = channelEnabled,
            channelExpanded = channelExpanded,
            balance = bal,
            presetName = preset,
            originalGain = gain,
            reverbEnabled = reverbEnabled,
            reverbPreset = reverbPreset,
            reverbWet = reverbWet,
            volumeThresholdExpanded = volumeThresholdExpanded,
            stereoEnabled = stereoEnabled,
            stereoExpanded = stereoExpanded,
            orbitEnabled = orbitEnabled,
            orbitSpeed = orbitSpeed,
            orbitDistance = orbitDistance,
            orbitAzimuthDeg = orbitAzimuthDeg,
            channelMode = channelMode,
            volumeThresholdEnabled = vtEnabled,
            volumeThresholdMode = vtMode,
            volumeThresholdMinDb = vtMinDb,
            volumeThresholdMaxDb = vtMaxDb,
            volumeLoudnessTargetDb = loudnessTargetDb,
            speedPitchEnabled = speedPitchEnabled,
            speedPitchExpanded = speedPitchExpanded,
            equalizerExpanded = equalizerExpanded
        )
    }

    fun applyTo(equalizer: Equalizer, settings: EqualizerSettings) {
        if (settings.enabled != equalizer.enabled) {
            equalizer.enabled = settings.enabled
        }
        if (!settings.enabled) return
        
        val bands = equalizer.numberOfBands.toInt()
        val minLevel = equalizer.bandLevelRange[0].toInt()
        val maxLevel = equalizer.bandLevelRange[1].toInt()
        val levels = settings.bandLevels
        if (levels.isEmpty() || bands <= 0) return
        val targetHz = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val centerHz = IntArray(bands) { idx ->
            (equalizer.getCenterFreq(idx.toShort()).toLong() / 1000L).toInt().coerceAtLeast(1)
        }
        val sum = IntArray(bands)
        val count = IntArray(bands)
        val takeN = minOf(levels.size, targetHz.size)
        for (i in 0 until takeN) {
            val hz = targetHz[i]
            var best = 0
            var bestDist = Int.MAX_VALUE
            for (b in 0 until bands) {
                val d = kotlin.math.abs(centerHz[b] - hz)
                if (d < bestDist) {
                    bestDist = d
                    best = b
                }
            }
            sum[best] += levels[i]
            count[best] += 1
        }
        for (b in 0 until bands) {
            val raw = if (count[b] == 0) 0 else (sum[b] / count[b])
            val clamped = raw.coerceIn(minLevel, maxLevel).toShort()
            equalizer.setBandLevel(b.toShort(), clamped)
        }
    }
}
