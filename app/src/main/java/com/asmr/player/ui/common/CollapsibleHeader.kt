package com.asmr.player.ui.common

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

internal const val COLLAPSIBLE_HEADER_STATE_EXPANDED = "expanded"
internal const val COLLAPSIBLE_HEADER_STATE_PARTIAL = "partial"
internal const val COLLAPSIBLE_HEADER_STATE_COLLAPSED = "collapsed"

@Stable
class CollapsibleHeaderState {
    var heightPx by mutableFloatStateOf(0f)
        private set

    var offsetPx by mutableFloatStateOf(0f)
        private set

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            onScrollDelta(consumed.y)
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (heightPx <= 0f) return Velocity.Zero
            if (offsetPx > -heightPx * 0.5f) {
                expand()
            } else {
                collapse()
            }
            return Velocity.Zero
        }
    }

    val collapseFraction: Float
        get() = if (heightPx <= 0f) 0f else (-offsetPx / heightPx).coerceIn(0f, 1f)

    fun updateHeight(heightPx: Float) {
        if (heightPx <= 0f) return
        this.heightPx = heightPx
        offsetPx = offsetPx.coerceIn(-heightPx, 0f)
    }

    fun onScrollDelta(deltaY: Float) {
        if (heightPx <= 0f || deltaY == 0f) return
        offsetPx = (offsetPx + deltaY).coerceIn(-heightPx, 0f)
    }

    fun expand() {
        offsetPx = 0f
    }

    fun collapse() {
        if (heightPx <= 0f) return
        offsetPx = -heightPx
    }
}

@androidx.compose.runtime.Composable
fun rememberCollapsibleHeaderState(): CollapsibleHeaderState = remember { CollapsibleHeaderState() }

internal fun collapsibleHeaderUiState(collapseFraction: Float): String = when {
    collapseFraction <= 0.01f -> COLLAPSIBLE_HEADER_STATE_EXPANDED
    collapseFraction >= 0.99f -> COLLAPSIBLE_HEADER_STATE_COLLAPSED
    else -> COLLAPSIBLE_HEADER_STATE_PARTIAL
}
