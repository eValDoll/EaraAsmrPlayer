package com.asmr.player.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
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
    val durationMs: Long = 3000L
)

@Composable
fun AppMessageOverlay(
    messages: List<VisibleAppMessage>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.widthIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        messages.asReversed().forEach { msg ->
            Box {
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
