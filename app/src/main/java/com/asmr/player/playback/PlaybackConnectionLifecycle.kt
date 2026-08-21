package com.asmr.player.playback

import java.util.concurrent.atomic.AtomicBoolean

/** 控制显式退出后是否允许媒体控制器重新拉起播放服务。 */
internal object PlaybackConnectionLifecycle {
    private val appExitRequested = AtomicBoolean(false)

    fun markAppExit() {
        appExitRequested.set(true)
    }

    /**
     * 标记应用重新进入前台。
     *
     * @return 此前是否发生过显式退出；若为 true，调用方需要重新执行播放状态恢复。
     */
    fun markAppOpened(): Boolean = appExitRequested.getAndSet(false)

    fun canConnect(): Boolean = !appExitRequested.get()
}
