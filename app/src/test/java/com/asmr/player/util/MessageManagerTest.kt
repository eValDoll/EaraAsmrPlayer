package com.asmr.player.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageManagerTest {

    @Test
    fun tryConsume_allowsEachMessageOnlyOnce() {
        val manager = MessageManager()

        assertTrue(manager.tryConsume(1L))
        assertFalse(manager.tryConsume(1L))
        assertFalse(manager.tryConsume(0L))
        assertTrue(manager.tryConsume(2L))
        assertFalse(manager.tryConsume(2L))
    }
}
