package com.asmr.player.ui.common

import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSpectrumTransitionTest {

    @Test
    fun activityLevelFadesInAndOutWithoutJumpingToEndpoints() {
        val firstFadeInFrame = nextAudioSpectrumActivityLevel(
            currentLevel = 0f,
            playbackActive = true,
            deltaSeconds = 1f / 60f
        )
        val secondFadeInFrame = nextAudioSpectrumActivityLevel(
            currentLevel = firstFadeInFrame,
            playbackActive = true,
            deltaSeconds = 1f / 60f
        )
        val firstFadeOutFrame = nextAudioSpectrumActivityLevel(
            currentLevel = 1f,
            playbackActive = false,
            deltaSeconds = 1f / 60f
        )

        assertTrue(firstFadeInFrame > 0f && firstFadeInFrame < 1f)
        assertTrue(secondFadeInFrame > firstFadeInFrame && secondFadeInFrame < 1f)
        assertTrue(firstFadeOutFrame in 0f..1f && firstFadeOutFrame > 0f)
    }
}
