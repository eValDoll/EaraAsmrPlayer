package com.asmr.player.ui.player

internal fun updateSilenceActive(
    current: Boolean,
    maxEnergy: Float,
    enterMax: Float,
    exitMax: Float
): Boolean {
    return if (current) {
        maxEnergy <= exitMax
    } else {
        maxEnergy < enterMax
    }
}

