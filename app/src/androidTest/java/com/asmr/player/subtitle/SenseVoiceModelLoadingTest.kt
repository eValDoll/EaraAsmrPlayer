package com.asmr.player.subtitle

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SenseVoiceModelLoadingTest {
    @Test
    fun officialJapaneseSample_createsModelAndReturnsNonEmptyTranscription() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val assets = instrumentation.context.assets
        val runtimeRepository = SherpaOnnxRuntimeRepository.get(context)
        val runtime = runtimeRepository.descriptor
        val model = SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8
        assumeTrue(Build.SUPPORTED_ABIS.any { it == runtime.abi })
        assumeTrue(
            assets.list(ASSET_DIRECTORY).orEmpty().toList().containsAll(REQUIRED_MODEL_ASSETS)
        )
        assumeTrue(assets.list("").orEmpty().contains(runtime.archiveFileName))

        val modelDirectory = File(context.cacheDir, "sensevoice-instrumentation-model")
        modelDirectory.deleteRecursively()
        assertTrue(modelDirectory.mkdirs())
        try {
            if (!runtimeRepository.isInstalled()) {
                assets.open(runtime.archiveFileName).use { input ->
                    runtimeRepository.partialArchiveFile().outputStream().use(input::copyTo)
                }
                runtimeRepository.installDownloadedArchive()
            }
            model.artifacts.forEach { artifact ->
                assets.open("$ASSET_DIRECTORY/${artifact.fileName}").use { input ->
                    File(modelDirectory, artifact.fileName).outputStream().use(input::copyTo)
                }
            }
            val samples = assets.open("$ASSET_DIRECTORY/$JAPANESE_WAV").use { input ->
                decodeMono16BitWav(input.readBytes())
            }
            val batchedSamples = samples + FloatArray(model.inputSampleRateHz) + samples
            SherpaOnnxTranscriptionEngine(context, modelDirectory, model).use { engine ->
                val result = engine.transcribe(
                    channelSamples = listOf(batchedSamples),
                    isCancelled = { false },
                    onProgress = {}
                )
                assertTrue(result.size >= model.inferenceBatchSize)
                assertTrue(result.joinToString("") { it.text }.isNotBlank())
                assertTrue(result.flatMap { it.tokens }.isNotEmpty())
            }
        } finally {
            modelDirectory.deleteRecursively()
        }
    }

    private fun decodeMono16BitWav(bytes: ByteArray): FloatArray {
        require(bytes.size >= 44 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF")
        var offset = 12
        var channelCount = 0
        var bitsPerSample = 0
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val chunkName = bytes.copyOfRange(offset, offset + 4).decodeToString()
            val chunkSize = ByteBuffer.wrap(bytes, offset + 4, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .int
            val chunkData = offset + 8
            when (chunkName) {
                "fmt " -> {
                    val format = ByteBuffer.wrap(bytes, chunkData, chunkSize)
                        .order(ByteOrder.LITTLE_ENDIAN)
                    require(format.short.toInt() == 1) { "测试音频必须是 PCM" }
                    channelCount = format.short.toInt()
                    format.int
                    format.int
                    format.short
                    bitsPerSample = format.short.toInt()
                }
                "data" -> {
                    dataOffset = chunkData
                    dataSize = chunkSize.coerceAtMost(bytes.size - chunkData)
                }
            }
            offset = chunkData + chunkSize + (chunkSize and 1)
        }
        require(channelCount == 1 && bitsPerSample == 16 && dataOffset >= 0)
        val pcm = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(dataSize / 2) { pcm.short / 32_768f }
    }

    private companion object {
        const val ASSET_DIRECTORY = "sensevoice"
        const val JAPANESE_WAV = "ja.wav"
        val REQUIRED_MODEL_ASSETS = listOf("model.int8.onnx", "tokens.txt", JAPANESE_WAV)
    }
}
