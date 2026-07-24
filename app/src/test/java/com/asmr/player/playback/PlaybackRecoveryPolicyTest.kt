package com.asmr.player.playback

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryPolicyTest {

    @Test
    fun retriesUseBoundedExponentialBackoff() {
        val policy = PlaybackRecoveryPolicy(
            maxAttempts = 4,
            initialDelayMs = 1_000L,
            maxDelayMs = 5_000L
        )

        assertEquals(PlaybackRecoveryAttempt(1, 1_000L), policy.nextAttempt("track"))
        assertEquals(PlaybackRecoveryAttempt(2, 2_000L), policy.nextAttempt("track"))
        assertEquals(PlaybackRecoveryAttempt(3, 4_000L), policy.nextAttempt("track"))
        assertEquals(PlaybackRecoveryAttempt(4, 5_000L), policy.nextAttempt("track"))
        assertNull(policy.nextAttempt("track"))
    }

    @Test
    fun changingMediaResetsRetryBudget() {
        val policy = PlaybackRecoveryPolicy(maxAttempts = 1)

        assertEquals(1, policy.nextAttempt("first")?.number)
        assertNull(policy.nextAttempt("first"))
        assertEquals(1, policy.nextAttempt("second")?.number)
    }

    @Test
    fun resetRestoresRetryBudget() {
        val policy = PlaybackRecoveryPolicy(maxAttempts = 1)

        assertEquals(1, policy.nextAttempt("track")?.number)
        policy.reset()

        assertEquals(1, policy.nextAttempt("track")?.number)
    }

    @Test
    fun remoteNetworkFailuresAreRecoverable() {
        assertTrue(
            isRecoverableRemotePlaybackFailure(
                uriText = "https://example.com/audio.flac",
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
            )
        )
        assertTrue(
            isRecoverableRemotePlaybackFailure(
                uriText = "http://example.com/audio.wav",
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            )
        )
    }

    @Test
    fun onlyTransientHttpStatusesAreRecoverable() {
        assertTrue(
            isRecoverableRemotePlaybackFailure(
                uriText = "https://example.com/audio.mp3",
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpStatusCode = 503
            )
        )
        assertTrue(
            isRecoverableRemotePlaybackFailure(
                uriText = "https://example.com/audio.mp3",
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpStatusCode = 429
            )
        )
        assertFalse(
            isRecoverableRemotePlaybackFailure(
                uriText = "https://example.com/missing.mp3",
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpStatusCode = 404
            )
        )
    }

    @Test
    fun localAndUnsupportedFailuresAreNotRetried() {
        assertFalse(
            isRecoverableRemotePlaybackFailure(
                uriText = "file:///storage/emulated/0/audio.mp3",
                errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
            )
        )
        assertFalse(
            isRecoverableRemotePlaybackFailure(
                uriText = "https://example.com/audio.mp3",
                errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED
            )
        )
    }
}
