package com.asmr.player.subtitle

import android.app.Application
import android.content.Context
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SubtitleModelRepositoryTest {
    @Test
    fun downloadSource_roundTripsStableIds() {
        SubtitleModelDownloadSource.entries.forEach { source ->
            assertEquals(source, SubtitleModelDownloadSource.fromId(source.id))
        }
        assertNull(SubtitleModelDownloadSource.fromId("unknown"))
        assertEquals(
            listOf(
                SubtitleModelDownloadSource.GitHub,
                SubtitleModelDownloadSource.HuggingFace
            ),
            configuredSubtitleModelDownloadSources(SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8)
        )
    }

    @Test
    fun senseVoiceGitHubSource_mapsLocalArtifactNamesToReleaseAssets() {
        val baseUrl = subtitleModelDownloadBaseUrl(
            SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8,
            SubtitleModelDownloadSource.GitHub
        )

        assertEquals(
            "https://github.com/eValDoll/EaraAsmrPlayer/releases/download/" +
                "subtitle-model-parakeet-ja-int8/sensevoice-model.int8.onnx",
            buildSubtitleModelArtifactUrl(baseUrl, "model.int8.onnx")
        )
        assertEquals(
            "https://github.com/eValDoll/EaraAsmrPlayer/releases/download/" +
                "subtitle-model-parakeet-ja-int8/sensevoice-tokens.txt",
            buildSubtitleModelArtifactUrl(baseUrl, "tokens.txt")
        )
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

    @Test
    fun senseVoiceModel_usesVerifiedOfficialArtifacts() {
        val model = SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8

        assertEquals("sense-voice-small-zh-en-ja-ko-yue-int8-2024-07-17", model.id)
        assertEquals(239_549_735L, model.artifactBytes)
        assertEquals(
            "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51",
            model.artifacts.single { it.fileName == "model.int8.onnx" }.sha256
        )
        assertEquals(
            "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc",
            model.artifacts.single { it.fileName == "tokens.txt" }.sha256
        )
        assertEquals(model, SubtitleTranscriptionModels.fromId(model.id))
    }

    @Test
    fun legacyInstalledSource_migratesToParakeetWithoutOverwritingNewValue() {
        val preferences = RuntimeEnvironment.getApplication()
            .getSharedPreferences("subtitle-model-migration-test", Context.MODE_PRIVATE)
        preferences.edit()
            .clear()
            .putString("installed_source", SubtitleModelDownloadSource.GitHub.id)
            .commit()

        SubtitleModelRepository.migrateLegacyPreferences(preferences)

        val parakeetKey = "installed_source:${SubtitleTranscriptionModels.default.id}"
        assertEquals(SubtitleModelDownloadSource.GitHub.id, preferences.getString(parakeetKey, null))
        assertFalse(preferences.contains("installed_source"))

        preferences.edit()
            .putString("installed_source", SubtitleModelDownloadSource.GitHub.id)
            .putString(parakeetKey, SubtitleModelDownloadSource.HuggingFace.id)
            .commit()
        SubtitleModelRepository.migrateLegacyPreferences(preferences)
        assertEquals(
            SubtitleModelDownloadSource.HuggingFace.id,
            preferences.getString(parakeetKey, null)
        )
        preferences.edit().clear().commit()
    }

    @Test
    fun deletingCurrentModel_fallsBackToInstalledAlternativeOrDefault() {
        val parakeet = SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8.id
        val senseVoice = SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8.id

        assertEquals(senseVoice, fallbackSubtitleModelId(parakeet, setOf(senseVoice)))
        assertEquals(parakeet, fallbackSubtitleModelId(senseVoice, emptySet()))
    }

    @Test
    fun state_tracksCoexistingModelsAndStableFailureTarget() {
        val parakeet = SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8.id
        val senseVoice = SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8.id
        val failure = SubtitleModelOperation.Failed(
            modelId = senseVoice,
            source = SubtitleModelDownloadSource.HuggingFace,
            message = "校验失败"
        )
        val state = SubtitleModelState(
            activeModelId = parakeet,
            installations = mapOf(
                parakeet to SubtitleModelInstallationState.Available(null),
                senseVoice to SubtitleModelInstallationState.Missing
            ),
            operation = failure
        )

        assertTrue(state.installation(parakeet) is SubtitleModelInstallationState.Available)
        assertEquals(SubtitleModelInstallationState.Missing, state.installation(senseVoice))
        assertEquals(senseVoice, state.operation?.modelId)
        assertEquals(parakeet, state.activeModelId)
    }
}
