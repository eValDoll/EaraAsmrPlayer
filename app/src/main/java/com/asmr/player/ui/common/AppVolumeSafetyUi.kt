package com.asmr.player.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.asmr.player.playback.AppVolume
import com.asmr.player.playback.AppVolumeChangeSource
import com.asmr.player.playback.AppVolumeSafety

data class PendingAppVolumeChange(
    val currentPercent: Int,
    val targetPercent: Int
)

@Stable
class AppVolumeWarningSessionState internal constructor() {
    var hasAcknowledgedWarningThisLaunch by mutableStateOf(false)
        private set

    fun markWarningAcknowledged() {
        hasAcknowledgedWarningThisLaunch = true
    }
}

@Stable
class ProtectedAppVolumeChangeState internal constructor(
    private val warningSessionState: AppVolumeWarningSessionState,
    private val onApplyVolumeChange: (Int) -> Unit
) {
    var pendingChange by mutableStateOf<PendingAppVolumeChange?>(null)
        private set

    fun requestVolumeChange(
        currentPercent: Int,
        targetPercent: Int,
        source: AppVolumeChangeSource
    ) {
        val current = AppVolume.clampPercent(currentPercent)
        val target = AppVolume.clampPercent(targetPercent)
        if (
            AppVolumeSafety.shouldWarnBeforeLoudVolume(
                targetPercent = target,
                source = source,
                hasAcknowledgedWarningThisLaunch = warningSessionState.hasAcknowledgedWarningThisLaunch
            )
        ) {
            pendingChange = PendingAppVolumeChange(currentPercent = current, targetPercent = target)
            return
        }
        pendingChange = null
        onApplyVolumeChange(target)
    }

    fun confirmPendingChange() {
        val change = pendingChange ?: return
        pendingChange = null
        warningSessionState.markWarningAcknowledged()
        onApplyVolumeChange(change.targetPercent)
    }

    fun dismissPendingChange() {
        pendingChange = null
    }
}

@Composable
fun rememberAppVolumeWarningSessionState(): AppVolumeWarningSessionState {
    return remember { AppVolumeWarningSessionState() }
}

@Composable
fun rememberProtectedAppVolumeChangeState(
    warningSessionState: AppVolumeWarningSessionState,
    onApplyVolumeChange: (Int) -> Unit
): ProtectedAppVolumeChangeState {
    val currentOnApply = rememberUpdatedState(onApplyVolumeChange)
    return remember(warningSessionState) {
        ProtectedAppVolumeChangeState(warningSessionState) { percent ->
            currentOnApply.value(percent)
        }
    }
}

@Composable
fun AppVolumeHearingWarningDialog(
    state: ProtectedAppVolumeChangeState
) {
    val pendingChange = state.pendingChange ?: return
    AlertDialog(
        onDismissRequest = state::dismissPendingChange,
        title = { Text("听力损伤提醒") },
        text = {
            Text(
                "当前要调整到 ${pendingChange.targetPercent}% 音量，已经超过 40%，" +
                    "可能造成听力损伤。确认后，本次启动内不再重复提示。"
            )
        },
        confirmButton = {
            TextButton(onClick = state::confirmPendingChange) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = state::dismissPendingChange) {
                Text("取消")
            }
        }
    )
}
