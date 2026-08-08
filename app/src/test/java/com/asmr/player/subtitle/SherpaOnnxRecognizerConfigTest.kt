package com.asmr.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SherpaOnnxRecognizerConfigTest {
    private val artifacts = mapOf(
        "model.int8.onnx" to "/models/model.int8.onnx",
        "tokens.txt" to "/models/tokens.txt"
    )

    @Test
    fun parakeet_usesNemoCtcOnCpu() {
        val config = buildOfflineRecognizerConfig(
            model = SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8,
            artifactPaths = artifacts,
            numThreads = 3
        )

        assertEquals(artifacts.getValue("model.int8.onnx"), config.modelConfig.nemo.model)
        assertTrue(config.modelConfig.senseVoice.model.isEmpty())
        assertEquals("cpu", config.modelConfig.provider)
        assertEquals(3, config.modelConfig.numThreads)
        assertEquals(artifacts.getValue("tokens.txt"), config.modelConfig.tokens)
    }

    @Test
    fun senseVoice_usesJapaneseItnOnSameCpuRuntime() {
        val config = buildOfflineRecognizerConfig(
            model = SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8,
            artifactPaths = artifacts,
            numThreads = 2
        )

        assertEquals(artifacts.getValue("model.int8.onnx"), config.modelConfig.senseVoice.model)
        assertEquals("ja", config.modelConfig.senseVoice.language)
        assertTrue(config.modelConfig.senseVoice.useInverseTextNormalization)
        assertTrue(config.modelConfig.nemo.model.isEmpty())
        assertEquals("cpu", config.modelConfig.provider)
        assertFalse(config.modelConfig.tokens.isEmpty())
    }
}
