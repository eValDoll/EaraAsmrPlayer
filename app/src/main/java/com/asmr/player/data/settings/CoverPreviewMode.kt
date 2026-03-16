package com.asmr.player.data.settings

enum class CoverPreviewMode(val storageValue: String) {
    Disabled("disabled"),
    Drag("drag"),
    Motion("motion");

    companion object {
        fun fromStorageValue(value: String?): CoverPreviewMode {
            return entries.firstOrNull { it.storageValue == value } ?: Disabled
        }
    }
}
