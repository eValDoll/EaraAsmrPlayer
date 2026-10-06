package com.asmr.player.subtitle

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class LocalAudioDecoderFormatTest {
    @Test fun mp3() = verifyFormat("mp3")
    @Test fun wav() = verifyFormat("wav")
    @Test fun flac24Bit() = verifyFormat("flac")
    @Test fun m4aAac() = verifyFormat("m4a")
    @Test fun adtsAac() = verifyFormat("aac")
    @Test fun oggVorbis() = verifyFormat("ogg")
    @Test fun oggOpus() = verifyFormat("opus")

    // Each fixture contains two seconds of synthesized stereo tones at 440/880 Hz, 48 kHz.
    private fun verifyFormat(extension: String) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File.createTempFile("decoder-format-", ".$extension", context.cacheDir)
        try {
            instrumentation.context.assets.open("decoder-formats/stereo.$extension").use { input ->
                file.outputStream().use(input::copyTo)
            }
            for (startAtMs in listOf(0L, 1_000L)) {
                var frames = 0
                var firstStartMs: Long? = null
                var previousEndMs = startAtMs
                val energy = DoubleArray(2)
                var channelDifference = 0.0
                withTimeout(20_000L) {
                    LocalAudioDecoder(context).decode(file.absolutePath, startAtMs) { chunk ->
                        assertEquals(extension, 2, chunk.channelSamples.size)
                        assertTrue(extension, chunk.startMs >= previousEndMs)
                        if (firstStartMs == null) firstStartMs = chunk.startMs
                        previousEndMs = chunk.startMs + chunk.durationMs
                        val left = chunk.channelSamples[0]
                        val right = chunk.channelSamples[1]
                        assertEquals(extension, left.size, right.size)
                        frames += left.size
                        for (index in left.indices) {
                            assertTrue(extension, left[index].isFinite() && abs(left[index]) <= 1f)
                            assertTrue(extension, right[index].isFinite() && abs(right[index]) <= 1f)
                            energy[0] += left[index].toDouble() * left[index]
                            energy[1] += right[index].toDouble() * right[index]
                            channelDifference += abs(left[index] - right[index])
                        }
                    }
                }
                val expectedFrames = (2_000L - startAtMs) * 16
                assertTrue("$extension start=$startAtMs frames=$frames", abs(frames - expectedFrames) < 2_400)
                assertTrue("$extension firstStart=$firstStartMs", requireNotNull(firstStartMs) in startAtMs..(startAtMs + 100L))
                assertTrue(extension, energy.all { it / frames > 0.01 })
                assertTrue(extension, channelDifference / frames > 0.1)
            }
        } finally {
            file.delete()
        }
    }
}
