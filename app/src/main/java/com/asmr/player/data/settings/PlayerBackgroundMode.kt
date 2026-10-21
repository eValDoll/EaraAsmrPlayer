package com.asmr.player.data.settings

enum class PlayerBackgroundMode(val storageValue: String) {
    Theme("solid"),
    Solid("neutral"),
    Flowing("flowing");

    companion object {
        fun fromStorageValue(value: String?): PlayerBackgroundMode {
            return entries.firstOrNull { it.storageValue == value } ?: Theme
        }
    }
}
