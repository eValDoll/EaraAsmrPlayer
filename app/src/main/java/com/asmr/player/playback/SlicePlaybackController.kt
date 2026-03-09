package com.asmr.player.playback

import com.asmr.player.domain.model.Slice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SlicePlaybackController @Inject constructor() {
    private val _sliceModeEnabled = MutableStateFlow(false)
    val sliceModeEnabled: StateFlow<Boolean> = _sliceModeEnabled.asStateFlow()

    private val _userScrubbing = MutableStateFlow(false)
    val userScrubbing: StateFlow<Boolean> = _userScrubbing.asStateFlow()

    private val _previewSlice = MutableStateFlow<Slice?>(null)
    val previewSlice: StateFlow<Slice?> = _previewSlice.asStateFlow()

    fun setSliceModeEnabled(enabled: Boolean) {
        _sliceModeEnabled.value = enabled
    }

    fun setUserScrubbing(scrubbing: Boolean) {
        _userScrubbing.value = scrubbing
    }

    fun startPreview(slice: Slice) {
        _previewSlice.value = slice
    }

    fun clearPreview() {
        _previewSlice.value = null
    }
}
