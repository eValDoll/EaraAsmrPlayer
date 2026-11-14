package com.asmr.player.playback

import androidx.annotation.StringRes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.asmr.player.R

enum class PlayMode(
    val storageValue: Int,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    @StringRes val labelRes: Int
) {
    ListLoop(0, Player.REPEAT_MODE_ALL, false, R.string.ui_list_loop),
    SingleLoop(1, Player.REPEAT_MODE_ONE, false, R.string.ui_single_loop),
    Shuffle(2, Player.REPEAT_MODE_ALL, true, R.string.ui_shuffle),
    PauseAfterTrack(3, Player.REPEAT_MODE_OFF, false, R.string.ui_pause_after_track);

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]

    fun applyTo(player: Player) {
        player.repeatMode = repeatMode
        player.shuffleModeEnabled = shuffleEnabled
    }

    companion object {
        fun fromStorageValue(value: Int?): PlayMode =
            entries.firstOrNull { it.storageValue == value } ?: ListLoop

        fun fromPlayer(repeatMode: Int, shuffleEnabled: Boolean): PlayMode = when {
            shuffleEnabled -> Shuffle
            repeatMode == Player.REPEAT_MODE_ONE -> SingleLoop
            repeatMode == Player.REPEAT_MODE_OFF -> PauseAfterTrack
            else -> ListLoop
        }
    }
}

@UnstableApi
internal fun ExoPlayer.configureTrackEndPause() {
    // 在音轨边界由播放引擎暂停，避免后台轮询延迟导致下一曲先播放出声。
    pauseAtEndOfMediaItems = repeatMode == Player.REPEAT_MODE_OFF
    addListener(object : Player.Listener {
        override fun onRepeatModeChanged(repeatMode: Int) {
            pauseAtEndOfMediaItems = repeatMode == Player.REPEAT_MODE_OFF
        }
    })
}
