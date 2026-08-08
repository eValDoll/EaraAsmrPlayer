package com.asmr.player.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageManagerTest {

    @Test
    fun errorFormatter_preservesActionableSubtitleFailureDetails() {
        val oomMessage = "本地转录失败：运行模型时内存不足（OOM），请关闭占用内存的应用后重试。"
        val keyMessage = "DeepSeek API Key 无效或已失效，请前往设置重新配置后重试。"

        assertEquals(oomMessage, AppErrorMessageFormatter.sanitize(oomMessage))
        assertEquals(keyMessage, AppErrorMessageFormatter.sanitize(keyMessage))
    }

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
