package com.asmr.player.playback

enum class AppVolumeChangeSource {
    TapJump,
    Drag
}

object AppVolumeSafety {
    const val HearingWarningVolumePercent = 40

    fun shouldWarnBeforeLoudVolume(
        targetPercent: Int,
        source: AppVolumeChangeSource,
        hasAcknowledgedWarningThisLaunch: Boolean
    ): Boolean {
        if (source != AppVolumeChangeSource.TapJump) return false
        if (hasAcknowledgedWarningThisLaunch) return false
        val target = AppVolume.clampPercent(targetPercent)
        return target > HearingWarningVolumePercent
    }
}
