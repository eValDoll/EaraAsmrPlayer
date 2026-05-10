package com.asmr.player.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asmr.player.util.MessageType

data class VisibleAppMessage(
    val id: Long,
    val renderId: Long = id,
    val key: String,
    val message: String,
    val type: MessageType,
    val count: Int = 1,
    val durationMs: Long = 2000L,
    val isVisible: Boolean = true
)

@Composable
fun AppMessageOverlay(
    messages: List<VisibleAppMessage>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        messages.asReversed().forEach { msg ->
            key(msg.id) {
                val visibleState = remember { MutableTransitionState(false) }
                visibleState.targetState = msg.isVisible

                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = fadeIn(animationSpec = tween(durationMillis = 180)) +
                        slideInVertically(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            initialOffsetY = { it / 3 }
                        ) +
                        scaleIn(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            initialScale = 0.96f
                        ) +
                        expandVertically(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Bottom
                        ),
                    exit = fadeOut(animationSpec = tween(durationMillis = 160)) +
                        slideOutVertically(
                            animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing),
                            targetOffsetY = { -it / 4 }
                        ) +
                        scaleOut(
                            animationSpec = tween(durationMillis = 180, easing = FastOutLinearInEasing),
                            targetScale = 0.96f
                        )
                ) {
                    AppSnackbar(
                        messageId = msg.renderId,
                        message = msg.message,
                        type = msg.type,
                        count = msg.count,
                        durationMs = msg.durationMs
                    )
                }
            }
        }
    }
}
