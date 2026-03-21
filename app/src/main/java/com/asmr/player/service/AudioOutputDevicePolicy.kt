package com.asmr.player.service

import android.media.AudioDeviceInfo
import android.media.AudioManager

enum class AudioOutputRouteKind {
    Speaker,
    Headphones
}

internal fun AudioDeviceInfo.isDisconnectSensitiveOutputDevice(): Boolean {
    return isDisconnectSensitiveOutputDeviceType(type)
}

internal fun AudioDeviceInfo.isResumeEligibleOutputDevice(): Boolean {
    return isResumeEligibleOutputDeviceType(type)
}

internal fun AudioDeviceInfo.outputRouteKind(): AudioOutputRouteKind? {
    return audioOutputRouteKindForDeviceType(type)
}

internal fun isDisconnectSensitiveOutputDeviceType(type: Int): Boolean {
    return when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> true

        else -> false
    }
}

internal fun isResumeEligibleOutputDeviceType(type: Int): Boolean {
    return when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> true

        else -> false
    }
}

internal fun audioOutputRouteKindForDeviceType(type: Int): AudioOutputRouteKind? {
    return when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioOutputRouteKind.Headphones

        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> AudioOutputRouteKind.Speaker

        else -> null
    }
}

internal fun resolveAudioOutputRouteKind(deviceTypes: Iterable<Int>): AudioOutputRouteKind {
    val routeKinds = deviceTypes.mapNotNull(::audioOutputRouteKindForDeviceType)
    return if (routeKinds.any { it == AudioOutputRouteKind.Headphones }) {
        AudioOutputRouteKind.Headphones
    } else {
        routeKinds.firstOrNull() ?: AudioOutputRouteKind.Speaker
    }
}

internal fun resolveCurrentAudioOutputRouteKind(audioManager: AudioManager): AudioOutputRouteKind {
    return resolveAudioOutputRouteKind(
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
    )
}
