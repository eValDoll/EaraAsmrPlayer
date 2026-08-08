package com.asmr.player.subtitle

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SherpaOnnxRuntimeLoadingTest {
    @Test
    fun installsPinnedLocalAssetAndLoadsJni() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testAssets = instrumentation.context.assets
        val repository = SherpaOnnxRuntimeRepository.get(context)
        val descriptor = repository.descriptor
        assumeTrue(Build.SUPPORTED_ABIS.any { it == descriptor.abi })
        assumeTrue(testAssets.list("").orEmpty().contains(descriptor.archiveFileName))

        val partialArchive = repository.partialArchiveFile()
        testAssets.open(descriptor.archiveFileName).use { input ->
            partialArchive.outputStream().use { output -> input.copyTo(output) }
        }
        repository.installDownloadedArchive()
        assertTrue(repository.isInstalled())

        SherpaOnnxNativeLoader.load(context)
        Vad(
            assetManager = context.assets,
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = "subtitle/silero_vad.onnx"
                )
            )
        ).release()
    }
}
