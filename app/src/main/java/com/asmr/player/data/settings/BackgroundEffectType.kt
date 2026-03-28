package com.asmr.player.data.settings

enum class BackgroundEffectType(val storageValue: String) {
    Flow("flow");

    companion object {
        fun fromStorageValue(value: String?): BackgroundEffectType {
            return entries.firstOrNull { it.storageValue == value } ?: Flow
        }
    }
}
