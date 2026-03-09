package com.asmr.player.service

import android.os.CountDownTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepTimerManager @Inject constructor(
    private val playbackManager: PlaybackManager
) {
    private var timer: CountDownTimer? = null
    
    private val _remainingTime = MutableStateFlow(0L)
    val remainingTime = _remainingTime.asStateFlow()

    fun startTimer(minutes: Int) {
        timer?.cancel()
        val totalMs = minutes * 60 * 1000L
        
        timer = object : CountDownTimer(totalMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _remainingTime.value = millisUntilFinished
            }

            override fun onFinish() {
                _remainingTime.value = 0
                playbackManager.togglePlayPause() // Pause playback
            }
        }.start()
    }

    fun stopTimer() {
        timer?.cancel()
        _remainingTime.value = 0
    }
}
