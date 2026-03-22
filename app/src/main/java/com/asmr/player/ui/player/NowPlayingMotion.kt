package com.asmr.player.ui.player

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateInt
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

internal enum class NowPlayingMotionLayout {
    PORTRAIT,
    PHONE_LANDSCAPE,
    SPLIT_LANDSCAPE
}

internal enum class NowPlayingMotionSlot {
    HEADER,
    COVER,
    LYRICS,
    PROGRESS,
    ACTION_ROW,
    CONTROLS,
    VOLUME,
    INFO_PANEL
}

internal object NowPlayingMotionSpec {
    const val HeaderOffsetDp = 20
    const val CoverOffsetDp = 64
    const val ContentOffsetDp = 40

    const val EnterDurationMs = 360
    const val EnterStepDelayMs = 90

    const val ExitDurationMs = 140
    const val ExitStepDelayMs = 45

    const val RouteFadeDurationMs = 120

    const val PlayerForegroundEnterDurationMs = 180
    const val PlayerForegroundExitDurationMs = 130
    const val PlayerForegroundFloatOffsetFraction = 0.028f
    const val PlayerForegroundSinkOffsetFraction = 0.022f
    const val PlayerForegroundInitialScale = 0.988f
    const val PlayerForegroundTargetScale = 0.994f

    fun orderedSlots(layout: NowPlayingMotionLayout): List<NowPlayingMotionSlot> =
        when (layout) {
            NowPlayingMotionLayout.PORTRAIT -> listOf(
                NowPlayingMotionSlot.HEADER,
                NowPlayingMotionSlot.COVER,
                NowPlayingMotionSlot.LYRICS,
                NowPlayingMotionSlot.PROGRESS,
                NowPlayingMotionSlot.ACTION_ROW,
                NowPlayingMotionSlot.CONTROLS,
                NowPlayingMotionSlot.VOLUME
            )
            NowPlayingMotionLayout.PHONE_LANDSCAPE -> listOf(
                NowPlayingMotionSlot.HEADER,
                NowPlayingMotionSlot.COVER,
                NowPlayingMotionSlot.PROGRESS,
                NowPlayingMotionSlot.LYRICS,
                NowPlayingMotionSlot.CONTROLS
            )
            NowPlayingMotionLayout.SPLIT_LANDSCAPE -> listOf(
                NowPlayingMotionSlot.HEADER,
                NowPlayingMotionSlot.COVER,
                NowPlayingMotionSlot.PROGRESS,
                NowPlayingMotionSlot.INFO_PANEL,
                NowPlayingMotionSlot.CONTROLS
            )
        }

    fun enterDelayMs(
        layout: NowPlayingMotionLayout,
        slot: NowPlayingMotionSlot
    ): Int {
        val index = slotIndex(layout, slot)
        return when (slot) {
            NowPlayingMotionSlot.HEADER,
            NowPlayingMotionSlot.COVER -> 0
            else -> (index - 1).coerceAtLeast(0) * EnterStepDelayMs
        }
    }

    fun exitDelayMs(
        layout: NowPlayingMotionLayout,
        slot: NowPlayingMotionSlot
    ): Int {
        val contentSlots = orderedSlots(layout).filterNot { it == NowPlayingMotionSlot.HEADER }
        return when (slot) {
            NowPlayingMotionSlot.HEADER -> contentSlots.size * ExitStepDelayMs
            else -> contentSlots
                .asReversed()
                .indexOf(slot)
                .let { index ->
                    require(index >= 0) {
                        "Slot $slot is not part of $layout now playing motion exit order"
                    }
                    index * ExitStepDelayMs
                }
        }
    }

    fun offsetDp(slot: NowPlayingMotionSlot): Int =
        when (slot) {
            NowPlayingMotionSlot.HEADER -> HeaderOffsetDp
            NowPlayingMotionSlot.COVER -> CoverOffsetDp
            else -> ContentOffsetDp
        }

    fun totalExitDurationMs(layout: NowPlayingMotionLayout): Int =
        exitDelayMs(layout, NowPlayingMotionSlot.HEADER) + ExitDurationMs

    private fun slotIndex(
        layout: NowPlayingMotionLayout,
        slot: NowPlayingMotionSlot
    ): Int {
        val index = orderedSlots(layout).indexOf(slot)
        require(index >= 0) {
            "Slot $slot is not part of $layout now playing motion order"
        }
        return index
    }
}

@Composable
internal fun Transition<Boolean>.nowPlayingMotionModifier(
    layout: NowPlayingMotionLayout,
    slot: NowPlayingMotionSlot
): Modifier {
    val density = LocalDensity.current
    val offsetPx = with(density) { NowPlayingMotionSpec.offsetDp(slot).dp.roundToPx() }
    val enterDelayMs = NowPlayingMotionSpec.enterDelayMs(layout, slot)
    val exitDelayMs = NowPlayingMotionSpec.exitDelayMs(layout, slot)
    val alpha by animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) {
                    NowPlayingMotionSpec.EnterDurationMs
                } else {
                    NowPlayingMotionSpec.ExitDurationMs
                },
                delayMillis = if (targetState) {
                    enterDelayMs
                } else {
                    exitDelayMs
                },
                easing = if (targetState) {
                    LinearOutSlowInEasing
                } else {
                    FastOutLinearInEasing
                }
            )
        },
        label = "nowPlayingMotionAlpha-$layout-$slot"
    ) { visible: Boolean ->
        if (visible) 1f else 0f
    }
    val translationY by animateInt(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) {
                    NowPlayingMotionSpec.EnterDurationMs
                } else {
                    NowPlayingMotionSpec.ExitDurationMs
                },
                delayMillis = if (targetState) {
                    enterDelayMs
                } else {
                    exitDelayMs
                },
                easing = if (targetState) {
                    LinearOutSlowInEasing
                } else {
                    FastOutLinearInEasing
                }
            )
        },
        label = "nowPlayingMotionTranslation-$layout-$slot"
    ) { visible: Boolean ->
        if (visible) 0 else offsetPx
    }

    return Modifier.graphicsLayer {
        this.alpha = alpha
        this.translationY = translationY.toFloat()
    }
}
