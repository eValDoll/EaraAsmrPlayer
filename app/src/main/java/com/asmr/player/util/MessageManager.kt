package com.asmr.player.util

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

enum class MessageType {
    Success,
    Error,
    Info,
    Warning
}

data class AppMessage(
    val id: Long,
    val message: String,
    val type: MessageType = MessageType.Info,
    val durationMs: Long = 3000,
    val createdAtMs: Long
) {
    fun formatForSnackbar(): String = "[${type.name.uppercase()}]$message"
}

@Singleton
class MessageManager @Inject constructor() {
    private val seq = AtomicLong(0L)
    private val _messages = MutableSharedFlow<AppMessage>(
        replay = 1,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages = _messages.asSharedFlow()

    fun showMessage(message: String, type: MessageType = MessageType.Info, durationMs: Long = 3000) {
        val now = System.currentTimeMillis()
        val id = seq.incrementAndGet()
        _messages.tryEmit(AppMessage(id = id, message = message, type = type, durationMs = durationMs, createdAtMs = now))
    }

    fun showSuccess(message: String) = showMessage(message, MessageType.Success)
    fun showError(message: String) = showMessage(message, MessageType.Error)
    fun showWarning(message: String) = showMessage(message, MessageType.Warning)
    fun showInfo(message: String) = showMessage(message, MessageType.Info)
}
