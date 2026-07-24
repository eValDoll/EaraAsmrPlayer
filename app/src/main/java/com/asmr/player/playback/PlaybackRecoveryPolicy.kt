package com.asmr.player.playback

import androidx.media3.common.PlaybackException

internal data class PlaybackRecoveryAttempt(
    val number: Int,
    val delayMs: Long
)

internal class PlaybackRecoveryPolicy(
    private val maxAttempts: Int = 4,
    private val initialDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 8_000L
) {
    private var currentMediaKey: String? = null
    private var attemptCount: Int = 0

    init {
        require(maxAttempts > 0)
        require(initialDelayMs >= 0L)
        require(maxDelayMs >= initialDelayMs)
    }

    fun nextAttempt(mediaKey: String): PlaybackRecoveryAttempt? {
        if (currentMediaKey != mediaKey) {
            currentMediaKey = mediaKey
            attemptCount = 0
        }
        if (attemptCount >= maxAttempts) return null

        val attempt = attemptCount + 1
        val multiplier = 1L shl attemptCount.coerceAtMost(30)
        val delayMs = (initialDelayMs * multiplier).coerceAtMost(maxDelayMs)
        attemptCount = attempt
        return PlaybackRecoveryAttempt(number = attempt, delayMs = delayMs)
    }

    fun reset() {
        currentMediaKey = null
        attemptCount = 0
    }
}

internal fun isRecoverableRemotePlaybackFailure(
    uriText: String,
    errorCode: Int,
    httpStatusCode: Int? = null
): Boolean {
    val normalizedUri = uriText.trim()
    if (
        !normalizedUri.startsWith("http://", ignoreCase = true) &&
        !normalizedUri.startsWith("https://", ignoreCase = true)
    ) {
        return false
    }

    return when (errorCode) {
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> true

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            httpStatusCode == null ||
                httpStatusCode == 408 ||
                httpStatusCode == 429 ||
                httpStatusCode in 500..599
        }

        else -> false
    }
}
