package com.asmr.player.ui.player

sealed interface SliceUiEvent {
    data object CutStartMarked : SliceUiEvent
    data object CutInvalidRange : SliceUiEvent
    data object CutSliceCreated : SliceUiEvent
}
