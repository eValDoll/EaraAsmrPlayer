package com.asmr.player.ui.player

import com.asmr.player.domain.model.Slice

data class SliceUiState(
    val trackMediaId: String? = null,
    val slices: List<Slice> = emptyList(),
    val tempStartMs: Long? = null,
    val sliceModeEnabled: Boolean = false,
    val selectedSliceId: Long? = null,
    val editDrag: SliceEditDrag = SliceEditDrag.None,
    val userScrubbing: Boolean = false
)

enum class SliceEditDrag {
    None,
    Start,
    End
}
