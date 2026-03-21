package com.asmr.player.service

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOutputDevicePolicyTest {

    @Test
    fun builtInSpeakerOnlyPausesButDoesNotResume() {
        assertTrue(isDisconnectSensitiveOutputDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertFalse(isResumeEligibleOutputDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
    }

    @Test
    fun externalOutputsRemainResumeEligible() {
        assertTrue(isResumeEligibleOutputDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(isResumeEligibleOutputDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertTrue(isResumeEligibleOutputDeviceType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertTrue(isResumeEligibleOutputDeviceType(AudioDeviceInfo.TYPE_HDMI))
        assertTrue(isResumeEligibleOutputDeviceType(AudioDeviceInfo.TYPE_LINE_ANALOG))
    }

    @Test
    fun unknownDevicesDoNotTriggerPauseOrResume() {
        assertFalse(isDisconnectSensitiveOutputDeviceType(AudioDeviceInfo.TYPE_UNKNOWN))
        assertFalse(isResumeEligibleOutputDeviceType(AudioDeviceInfo.TYPE_UNKNOWN))
    }

    @Test
    fun headphoneRoutesMapToHeadphonesIconState() {
        assertEquals(AudioOutputRouteKind.Headphones, audioOutputRouteKindForDeviceType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(AudioOutputRouteKind.Headphones, audioOutputRouteKindForDeviceType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(AudioOutputRouteKind.Headphones, audioOutputRouteKindForDeviceType(AudioDeviceInfo.TYPE_USB_HEADSET))
    }

    @Test
    fun speakerRoutesMapToSpeakerIconState() {
        assertEquals(AudioOutputRouteKind.Speaker, audioOutputRouteKindForDeviceType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(AudioOutputRouteKind.Speaker, audioOutputRouteKindForDeviceType(AudioDeviceInfo.TYPE_BLE_SPEAKER))
        assertEquals(AudioOutputRouteKind.Speaker, audioOutputRouteKindForDeviceType(AudioDeviceInfo.TYPE_HDMI))
        assertEquals(AudioOutputRouteKind.Speaker, audioOutputRouteKindForDeviceType(AudioDeviceInfo.TYPE_LINE_ANALOG))
    }

    @Test
    fun routeResolutionPrefersHeadphonesWhenSpeakerIsAlsoPresent() {
        assertEquals(
            AudioOutputRouteKind.Headphones,
            resolveAudioOutputRouteKind(
                listOf(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                )
            )
        )
    }
}
