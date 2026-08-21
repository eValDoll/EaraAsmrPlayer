package com.asmr.player.playback

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackConnectionLifecycleTest {
    @Before
    fun setUp() {
        PlaybackConnectionLifecycle.markAppOpened()
    }

    @After
    fun tearDown() {
        PlaybackConnectionLifecycle.markAppOpened()
    }

    @Test
    fun appExitBlocksReconnectUntilAppIsOpenedAgain() {
        PlaybackConnectionLifecycle.markAppExit()

        assertFalse(PlaybackConnectionLifecycle.canConnect())
        assertTrue(PlaybackConnectionLifecycle.markAppOpened())
        assertTrue(PlaybackConnectionLifecycle.canConnect())
    }

    @Test
    fun openingWithoutPriorExitDoesNotRequestRestore() {
        assertFalse(PlaybackConnectionLifecycle.markAppOpened())
        assertTrue(PlaybackConnectionLifecycle.canConnect())
    }
}
