package com.asmr.player.data.settings

import com.asmr.player.i18n.appString
import com.asmr.player.R

data class SceneDelayTap(
    val delayMs: Int,
    val gain: Float,
    val crossfeed: Float = 0f
)

data class SceneEffectProfile(
    val dryMix: Float,
    val directMix: Float,
    val reflectionMix: Float,
    val feedback: Float = 0f,
    val monoMix: Float = 0f,
    val stereoWidth: Float = 1f,
    val highPassHz: Float = 0f,
    val highPassStages: Int = 0,
    val lowPassHz: Float = 0f,
    val lowPassStages: Int = 0,
    val softClipDrive: Float = 0f,
    val outputGain: Float = 1f,
    val taps: List<SceneDelayTap> = emptyList()
)

data class SceneEffectPreset(
    val id: String,
    @androidx.annotation.StringRes private val labelResource: Int,
    @androidx.annotation.StringRes private val descriptionResource: Int,
    val profile: SceneEffectProfile
) {
    val label: String get() = appString(labelResource)
    val description: String get() = appString(descriptionResource)
}

object SceneEffectPresets {
    const val DefaultPresetId = "bathroom_echo"
    const val DefaultAmount = 50

    val All = listOf(
        SceneEffectPreset(
            id = DefaultPresetId,
            labelResource = R.string.ui_bathroom_echo,
            descriptionResource = R.string.ui_tile_short_echo,
            profile = SceneEffectProfile(
                dryMix = 0.58f,
                directMix = 0.30f,
                reflectionMix = 0.72f,
                feedback = 0.52f,
                monoMix = 0.20f,
                stereoWidth = 0.88f,
                highPassHz = 180f,
                highPassStages = 1,
                lowPassHz = 6400f,
                lowPassStages = 1,
                outputGain = 0.90f,
                taps = listOf(
                    SceneDelayTap(delayMs = 16, gain = 0.32f),
                    SceneDelayTap(delayMs = 27, gain = 0.28f),
                    SceneDelayTap(delayMs = 41, gain = 0.23f),
                    SceneDelayTap(delayMs = 63, gain = 0.18f),
                    SceneDelayTap(delayMs = 96, gain = 0.12f)
                )
            )
        ),
        SceneEffectPreset(
            id = "through_wall",
            labelResource = R.string.ui_muffled_sound_in_bed,
            descriptionResource = R.string.ui_muffled_sound_wrapping_feeling,
            profile = SceneEffectProfile(
                dryMix = 0.03f,
                directMix = 1.02f,
                reflectionMix = 0.16f,
                feedback = 0.20f,
                monoMix = 0.68f,
                stereoWidth = 0.18f,
                highPassHz = 120f,
                highPassStages = 1,
                lowPassHz = 1200f,
                lowPassStages = 3,
                outputGain = 1.00f,
                taps = listOf(
                    SceneDelayTap(delayMs = 14, gain = 0.10f),
                    SceneDelayTap(delayMs = 24, gain = 0.07f),
                    SceneDelayTap(delayMs = 39, gain = 0.05f)
                )
            )
        ),
        SceneEffectPreset(
            id = "telephone",
            labelResource = R.string.ui_phone_tone,
            descriptionResource = R.string.ui_narrowband_call_feeling,
            profile = SceneEffectProfile(
                dryMix = 0f,
                directMix = 1.08f,
                reflectionMix = 0f,
                monoMix = 1.00f,
                stereoWidth = 0f,
                highPassHz = 430f,
                highPassStages = 3,
                lowPassHz = 2850f,
                lowPassStages = 3,
                softClipDrive = 0.34f,
                outputGain = 1.06f
            )
        )
    )

    fun resolve(id: String?): SceneEffectPreset {
        return All.firstOrNull { it.id == id } ?: All.first()
    }
}
