package com.asmr.player.playback

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackNetworkRecoveryTest {
    @Test
    fun bufferingKeepsItsMediaPeriodAndLetsTheLoaderRetry() {
        assertFalse(shouldPrepareRemotePlayback("https://example.com/audio.wav", true, Player.STATE_BUFFERING, null))
        assertFalse(shouldPrepareRemotePlayback("https://example.com/audio.wav", true, Player.STATE_BUFFERING, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
    }

    @Test
    fun aNetworkChangeDoesNotRestartHealthyPausedEndedOrLocalPlayback() {
        assertFalse(shouldPrepareRemotePlayback("https://example.com/audio.wav", true, Player.STATE_READY, null))
        assertFalse(shouldPrepareRemotePlayback("https://example.com/audio.wav", false, Player.STATE_IDLE, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertFalse(shouldPrepareRemotePlayback("https://example.com/audio.wav", true, Player.STATE_ENDED, null))
        assertFalse(shouldPrepareRemotePlayback("content://audio/1", true, Player.STATE_IDLE, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertFalse(shouldPrepareRemotePlayback("file:///audio.wav", true, Player.STATE_IDLE, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
    }

    @Test
    fun aNetworkChangeRecoversTransientErrorsButNotAStopOrDeniedResource() {
        val uri = "https://example.com/audio.wav"
        assertTrue(shouldPrepareRemotePlayback(uri, true, Player.STATE_IDLE, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertFalse(shouldPrepareRemotePlayback(uri, true, Player.STATE_IDLE, null))
        assertFalse(shouldPrepareRemotePlayback(uri, true, Player.STATE_IDLE, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403))
    }

    @Test
    fun initialAndRepeatedNetworkNotificationsDoNotReportAChange() {
        val state = PlaybackNetworkState(1L, true)

        state.available(1L)
        assertFalse(state.validated(1L, true))
        assertFalse(state.validated(1L, true))
    }

    @Test
    fun reconnectingTheSameNetworkAndSwitchingNetworksCanRecoverPlayback() {
        val state = PlaybackNetworkState(1L, true)

        state.lost(1L)
        state.available(1L)
        assertTrue(state.validated(1L, true))
        state.available(2L)
        assertTrue(state.validated(2L, true))
        state.lost(1L)
        state.available(2L)
        assertFalse(state.validated(2L, true))
    }

    @Test
    fun restoredInternetValidationIsDetectedWithoutChangingNetwork() {
        val state = PlaybackNetworkState(1L, true)

        assertFalse(state.validated(1L, false))
        assertTrue(state.validated(1L, true))
        assertFalse(state.validated(1L, true))
        assertFalse(state.validated(2L, true))
    }

    @Test
    fun wifiReconnectIsDetectedEvenWhenTheVpnNetworkNeverChanges() {
        val defaultNetwork = PlaybackNetworkState(100L, true)
        val underlying = PlaybackNetworkState(1L, true)

        underlying.available(1L)
        assertFalse(underlying.validated(1L, true))
        underlying.lost(1L)
        defaultNetwork.available(100L)
        assertFalse(defaultNetwork.validated(100L, true))
        underlying.available(2L)
        assertTrue(underlying.validated(2L, true))
        assertFalse(underlying.validated(2L, true))
    }

    @Test
    fun losingWifiCanSwitchToAnAlreadyConnectedMobileNetwork() {
        val underlying = PlaybackUnderlyingNetworks(mapOf(1L to true, 2L to true))

        assertTrue(underlying.lost(1L))
        assertFalse(underlying.lost(1L))
        assertFalse(underlying.validated(1L, true))
        assertFalse(underlying.lost(2L))
    }

    @Test
    fun delayedValidationFromThePreviousMobileRouteDoesNotInterruptWifi() {
        val best = PlaybackNetworkState(null, false)

        best.available(1L)
        best.available(2L)
        assertTrue(best.validated(2L, true))
        assertFalse(best.validated(1L, true))
        assertFalse(best.validated(2L, true))
    }

    @Test
    fun registeringBehindAVpnDoesNotInterruptTheInitialHealthyRoute() {
        val best = PlaybackNetworkState(null, false, ignoreInitialCapabilities = true)

        best.available(1L)
        assertFalse(best.validated(1L, true))
        best.lost(1L)
        best.available(2L)
        assertTrue(best.validated(2L, true))
    }

}
