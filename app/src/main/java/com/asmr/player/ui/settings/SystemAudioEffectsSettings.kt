package com.asmr.player.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

internal fun openSystemAudioEffectsSettings(context: Context): Boolean {
    val intents = buildList {
        if (Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")) {
            add(Intent("miui.intent.action.HEADSET_SETTINGS").setPackage("com.miui.misound"))
        }
        add(Intent(Settings.ACTION_SOUND_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: ActivityNotFoundException) {
            // Continue to the platform settings when the vendor page is unavailable.
        } catch (_: SecurityException) {
            // Some firmware exposes its action but restricts access to system apps.
        }
    }
    return false
}
