package com.asmr.player.subtitle

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleModelRepositoryTest {
    @Test
    fun downloadSource_roundTripsStableIds() {
        SubtitleModelDownloadSource.entries.forEach { source ->
            assertEquals(source, SubtitleModelDownloadSource.fromId(source.id))
            assertEquals(
                source.downloadBaseUrl().trim().startsWith("https://"),
                source.isConfigured()
            )
        }
        assertNull(SubtitleModelDownloadSource.fromId("unknown"))
    }

    @Test
    fun installedArtifact_requiresRegularFileWithExactSize() {
        val directory = Files.createTempDirectory("subtitle-model-test").toFile()
        try {
            val modelFile = File(directory, "model.bin")
            modelFile.writeBytes(byteArrayOf(1, 2, 3, 4))

            assertTrue(isInstalledSubtitleModelArtifact(modelFile, 4L))
            assertFalse(isInstalledSubtitleModelArtifact(modelFile, 3L))
            assertFalse(isInstalledSubtitleModelArtifact(directory, directory.length()))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun artifactUrl_appendsFileNameOrReplacesPlaceholder() {
        assertEquals(
            "https://example.com/models/encoder.onnx",
            buildSubtitleModelArtifactUrl("https://example.com/models/", "encoder.onnx")
        )
        assertEquals(
            "https://example.com/encoder.onnx?download=1",
            buildSubtitleModelArtifactUrl(
                "https://example.com/{fileName}?download=1",
                "encoder.onnx"
            )
        )
    }

    @Test
    fun parakeetModel_usesVerifiedMobileArtifacts() {
        val model = SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8

        assertEquals("parakeet-tdt-ctc-0.6b-ja-35000-int8", model.id)
        assertEquals(655_571_161L, model.artifactBytes)
        assertEquals(2, model.artifacts.size)
        assertTrue(model.artifacts.all { it.sha256.length == 64 })
    }
}
