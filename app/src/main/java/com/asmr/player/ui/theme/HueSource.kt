package com.asmr.player.ui.theme

sealed interface HueSource {
    data object StaticHue : HueSource
    data object DynamicHue : HueSource
    data object FallbackHue : HueSource
}

