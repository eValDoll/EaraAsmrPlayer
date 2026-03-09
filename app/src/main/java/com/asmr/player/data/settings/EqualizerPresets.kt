package com.asmr.player.data.settings

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AsmrPreset(
    val name: String,
    val bandLevels: List<Int>,
    val virtualizerStrength: Int = 0,
    val isCustom: Boolean = false
)

object EqualizerPresets {
    private val gson = Gson()
    
    val DefaultPresets = listOf(
        AsmrPreset(
            name = "默认",
            bandLevels = listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            virtualizerStrength = 0
        ),
        AsmrPreset(
            name = "耳语 (Whisper)",
            bandLevels = listOf(-400, -300, -200, -100, 0, 200, 400, 700, 900, 800),
            virtualizerStrength = 200
        ),
        AsmrPreset(
            name = "舔耳 (Ear Licking)",
            bandLevels = listOf(300, 250, 200, 150, 100, 0, -100, -250, -400, -500),
            virtualizerStrength = 500
        ),
        AsmrPreset(
            name = "拍打-清脆 (Tapping-Crisp)",
            bandLevels = listOf(-300, -200, -100, 0, 150, 300, 650, 900, 1000, 800),
            virtualizerStrength = 100
        ),
        AsmrPreset(
            name = "拍打-沉闷 (Tapping-Dull)",
            bandLevels = listOf(400, 500, 600, 400, 150, -200, -500, -800, -1100, -1200),
            virtualizerStrength = 150
        ),
        AsmrPreset(
            name = "人声增强 (Vocal)",
            bandLevels = listOf(-300, -200, -100, 100, 300, 700, 900, 650, 200, -150),
            virtualizerStrength = 0
        )
    )

    fun decodeCustomPresets(json: String?): List<AsmrPreset> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = object : TypeToken<List<AsmrPreset>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun encodeCustomPresets(presets: List<AsmrPreset>): String {
        return gson.toJson(presets)
    }
}
