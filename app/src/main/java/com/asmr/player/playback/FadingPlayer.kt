package com.asmr.player.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@UnstableApi
class FadingPlayer(
    private val delegate: Player,
    private val volumeFader: VolumeFader,
    private val playFadeMs: Long,
    private val pauseFadeMs: Long,
    private val switchFadeOutMs: Long,
    private val switchFadeInMs: Long
) : ForwardingPlayer(delegate) {

    private var pendingSwitchFadeIn: Boolean = false

    private val transitionListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!pendingSwitchFadeIn) return
            pendingSwitchFadeIn = false
            volumeFader.fadeTo(delegate, 1f, switchFadeInMs)
        }
    }

    init {
        delegate.addListener(transitionListener)
    }

    override fun play() {
        pendingSwitchFadeIn = false
        volumeFader.cancel()
        delegate.volume = 0f
        delegate.play()
        volumeFader.fadeTo(delegate, 1f, playFadeMs)
    }

    override fun pause() {
        pendingSwitchFadeIn = false
        if (!delegate.isPlaying) {
            delegate.pause()
            return
        }
        volumeFader.fadeTo(delegate, 0f, pauseFadeMs) {
            delegate.pause()
        }
    }

    override fun seekToNextMediaItem() {
        seekWithFade { delegate.seekToNextMediaItem() }
    }

    override fun seekToPreviousMediaItem() {
        seekWithFade { delegate.seekToPreviousMediaItem() }
    }

    private fun seekWithFade(seekAction: () -> Unit) {
        val wasPlaying = delegate.playWhenReady
        pendingSwitchFadeIn = wasPlaying
        if (!wasPlaying) {
            seekAction()
            return
        }
        val beforeIndex = delegate.currentMediaItemIndex
        volumeFader.fadeTo(delegate, 0f, switchFadeOutMs) {
            seekAction()
            val afterIndex = delegate.currentMediaItemIndex
            if (afterIndex == beforeIndex) {
                pendingSwitchFadeIn = false
                volumeFader.fadeTo(delegate, 1f, switchFadeInMs)
            }
        }
    }
}
