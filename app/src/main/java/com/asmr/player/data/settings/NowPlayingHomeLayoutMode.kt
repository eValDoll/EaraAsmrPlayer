package com.asmr.player.data.settings

enum class NowPlayingHomeLayoutMode(val storageValue: String) {
    Classic("classic"),
    Expanded("expanded");

    companion object {
        fun fromStorageValue(value: String?): NowPlayingHomeLayoutMode {
            return entries.firstOrNull { it.storageValue == value } ?: Classic
        }
    }
}
