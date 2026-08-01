package com.asmr.player.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

private const val CollapsibleHeaderSettleDurationMillis = 220

internal const val COLLAPSIBLE_HEADER_STATE_EXPANDED = "expanded"
internal const val COLLAPSIBLE_HEADER_STATE_PARTIAL = "partial"
internal const val COLLAPSIBLE_HEADER_STATE_COLLAPSED = "collapsed"

@Stable
class CollapsibleHeaderState internal constructor(
    initialHeightPx: Float = 0f,
    initialOffsetPx: Float = 0f
) {
    private var descendantScrollBlocked: Boolean = false

    var heightPx by mutableFloatStateOf(initialHeightPx)
        private set

    var offsetPx by mutableFloatStateOf(
        if (initialHeightPx > 0f) {
            initialOffsetPx.coerceIn(-initialHeightPx, 0f)
        } else {
            0f
        }
    )
        private set

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (descendantScrollBlocked) return Offset.Zero
            onScrollDelta(consumed.y)
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (descendantScrollBlocked) return Velocity.Zero
            if (heightPx <= 0f) return Velocity.Zero
            settleAfterFling()
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

    fun setDescendantScrollBlocked(blocked: Boolean) {
        descendantScrollBlocked = blocked
    }

    fun expand() {
        offsetPx = 0f
    }

    fun collapse() {
        if (heightPx <= 0f) return
        offsetPx = -heightPx
    }

    private suspend fun settleAfterFling() {
        val targetOffsetPx = collapsibleHeaderSettleTargetOffset(
            heightPx = heightPx,
            offsetPx = offsetPx,
        )
        if (targetOffsetPx == offsetPx) return
        Animatable(offsetPx).animateTo(
            targetValue = targetOffsetPx,
            animationSpec = tween(
                durationMillis = CollapsibleHeaderSettleDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        ) {
            offsetPx = value.coerceIn(-heightPx, 0f)
        }
    }

    companion object {
        val Saver = listSaver<CollapsibleHeaderState, Float>(
            save = { listOf(it.heightPx, it.offsetPx) },
            restore = { restored ->
                CollapsibleHeaderState(
                    initialHeightPx = restored.getOrElse(0) { 0f },
                    initialOffsetPx = restored.getOrElse(1) { 0f }
                )
            }
        )
    }
}

@Composable
fun rememberCollapsibleHeaderState(): CollapsibleHeaderState =
    rememberSaveable(saver = CollapsibleHeaderState.Saver) { CollapsibleHeaderState() }

internal fun collapsibleHeaderSettleTargetOffset(heightPx: Float, offsetPx: Float): Float {
    if (heightPx <= 0f) return 0f
    val clampedOffsetPx = offsetPx.coerceIn(-heightPx, 0f)
    return if (clampedOffsetPx > -heightPx * 0.5f) 0f else -heightPx
}

internal fun collapsibleHeaderUiState(collapseFraction: Float): String = when {
    collapseFraction <= 0.01f -> COLLAPSIBLE_HEADER_STATE_EXPANDED
    collapseFraction >= 0.99f -> COLLAPSIBLE_HEADER_STATE_COLLAPSED
    else -> COLLAPSIBLE_HEADER_STATE_PARTIAL
}
