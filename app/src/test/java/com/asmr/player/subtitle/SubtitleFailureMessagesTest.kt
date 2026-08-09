package com.asmr.player.subtitle

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleFailureMessagesTest {
    @Test
    fun userActionWarnings_includeMissingModelKeyAndBalance() {
        assertTrue(
            SubtitleFailureMessages.isUserActionWarning(SubtitleModelRepository.MODEL_REQUIRED_MESSAGE)
        )
        assertTrue(
            SubtitleFailureMessages.isUserActionWarning("请先在设置中配置 DeepSeek API Key")
        )
        assertTrue(
            SubtitleFailureMessages.isUserActionWarning("DeepSeek 账户余额不足，请充值后重试。")
        )
        assertTrue(
            SubtitleFailureMessages.isUserActionWarning(
                SubtitleTaskRepository.ACTIVE_TASKS_BLOCK_POLISH_MESSAGE
            )
        )
        assertTrue(
            SubtitleFailureMessages.isUserActionWarning(
                SubtitleTaskRepository.POLISHING_BLOCKS_RETRY_MESSAGE
            )
        )
        assertFalse(
            SubtitleFailureMessages.isUserActionWarning("DeepSeek 网络请求超时，请检查网络后重试。")
        )
    }

    @Test
    fun transcription_reportsNestedOomAndPreservedProgress() {
        val message = SubtitleFailureMessages.transcription(
            IllegalStateException("模型初始化失败", OutOfMemoryError("Failed to allocate tensor"))
        )

        assertTrue(message.contains("内存不足（OOM）"))
        assertTrue(message.contains("已保留已完成的转录进度"))
    }

    @Test
    fun transcription_reportsNativeRuntimeIncompatibility() {
        val message = SubtitleFailureMessages.transcription(
            UnsatisfiedLinkError("dlopen failed: cannot locate symbol")
        )

        assertTrue(message.contains("运行环境与当前设备不兼容"))
        assertTrue(message.contains("arm64-v8a"))
    }

    @Test
    fun transcription_reportsCorruptedComponentWithRecoveryAction() {
        val message = SubtitleFailureMessages.transcription(
            IllegalStateException("模型校验失败：model.int8.onnx")
        )

        assertTrue(message.contains("模型或运行组件缺失、损坏"))
        assertTrue(message.contains("重新下载字幕模型"))
    }

    @Test
    fun network_errorsProvideSpecificRecoveryActions() {
        assertTrue(
            SubtitleFailureMessages.network(SocketTimeoutException()).contains("请求超时")
        )
        assertTrue(
            SubtitleFailureMessages.network(UnknownHostException()).contains("网络或代理设置")
        )
        assertTrue(
            SubtitleFailureMessages.network(SSLHandshakeException("certificate"))
                .contains("系统时间、证书或代理设置")
        )
    }

    @Test
    fun deepSeekHttp_mapsCredentialAndBalanceFailuresWithoutRetry() {
        val invalidKey = SubtitleFailureMessages.deepSeekHttp(401, "Authentication Fails")
        val insufficientBalance = SubtitleFailureMessages.deepSeekHttp(402, "Insufficient Balance")

        assertEquals("DeepSeek API Key 无效或已失效，请前往设置重新配置后重试。", invalidKey.message)
        assertFalse(invalidKey.retryable)
        assertEquals("DeepSeek 账户余额不足，请充值后重试。", insufficientBalance.message)
        assertFalse(insufficientBalance.retryable)
    }

    @Test
    fun deepSeekHttp_retriesRateLimitAndServerFailures() {
        val rateLimited = SubtitleFailureMessages.deepSeekHttp(429, null)
        val unavailable = SubtitleFailureMessages.deepSeekHttp(503, null)

        assertTrue(rateLimited.retryable)
        assertTrue(rateLimited.message.contains("请求过于频繁"))
        assertTrue(unavailable.retryable)
        assertTrue(unavailable.message.contains("服务暂时不可用"))
    }
}
