package com.asmr.player.subtitle

import android.content.Context
import java.io.Closeable
import java.io.File

internal data class SubtitleModelArtifact(
    val fileName: String,
    val bytes: Long,
    val sha256: String
)

internal enum class SubtitleTranscriptionModelType {
    NEMO_CTC,
    SENSE_VOICE
}

internal data class SubtitleTranscriptionModel(
    val id: String,
    val displayName: String,
    val optionName: String,
    val type: SubtitleTranscriptionModelType,
    val artifacts: List<SubtitleModelArtifact>,
    val inputSampleRateHz: Int
) {
    val artifactBytes: Long = artifacts.sumOf(SubtitleModelArtifact::bytes)
}

internal data class SubtitleTranscriptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val tokens: List<SubtitleTranscriptionToken> = emptyList()
)

internal data class SubtitleTranscriptionToken(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

internal interface SubtitleTranscriptionEngine : Closeable {
    val model: SubtitleTranscriptionModel

    fun transcribe(
        channelSamples: List<FloatArray>,
        isCancelled: () -> Boolean,
        onProgress: (Int) -> Unit
    ): List<SubtitleTranscriptionSegment>
}

internal interface SubtitleTranscriptionEngineFactory {
    val model: SubtitleTranscriptionModel

    fun create(): SubtitleTranscriptionEngine
}

internal object SubtitleTranscriptionEngineRegistry {
    fun factory(context: Context, modelId: String? = null): SubtitleTranscriptionEngineFactory {
        val repository = SubtitleModelRepository.get(context)
        val installed = repository.requireInstalledModel(modelId ?: repository.activeModel().id)
        return SherpaOnnxTranscriptionEngineFactory(
            context = context.applicationContext,
            modelDirectory = installed.directory,
            model = installed.model
        )
    }
}

internal object SubtitleTranscriptionModels {
    val PARAKEET_TDT_CTC_06B_JA_INT8 = SubtitleTranscriptionModel(
        id = "parakeet-tdt-ctc-0.6b-ja-35000-int8",
        displayName = "Parakeet TDT-CTC 0.6B Japanese INT8",
        optionName = "高精度",
        type = SubtitleTranscriptionModelType.NEMO_CTC,
        artifacts = listOf(
            SubtitleModelArtifact(
                fileName = "model.int8.onnx",
                bytes = 655_542_604L,
                sha256 = "3addd00ef5bd1742078389e540b77394e4a508bdf2f4c9ad1b4a76d93e76598e"
            ),
            SubtitleModelArtifact(
                fileName = "tokens.txt",
                bytes = 28_557L,
                sha256 = "732f64c53909f2620c713f4106b487d92e6f54a6915b3cd3d1dbd32f9f4f392a"
            )
        ),
        inputSampleRateHz = 16_000
    )

    val SENSE_VOICE_SMALL_INT8 = SubtitleTranscriptionModel(
        id = "sense-voice-small-zh-en-ja-ko-yue-int8-2024-07-17",
        displayName = "SenseVoiceSmall INT8",
        optionName = "轻量快速",
        type = SubtitleTranscriptionModelType.SENSE_VOICE,
        artifacts = listOf(
            SubtitleModelArtifact(
                fileName = "model.int8.onnx",
                bytes = 239_233_841L,
                sha256 = "c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51"
            ),
            SubtitleModelArtifact(
                fileName = "tokens.txt",
                bytes = 315_894L,
                sha256 = "f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc"
            )
        ),
        inputSampleRateHz = 16_000
    )

    val all: List<SubtitleTranscriptionModel> = listOf(
        PARAKEET_TDT_CTC_06B_JA_INT8,
        SENSE_VOICE_SMALL_INT8
    )

    val default: SubtitleTranscriptionModel = PARAKEET_TDT_CTC_06B_JA_INT8

    fun fromId(id: String?): SubtitleTranscriptionModel? = all.firstOrNull { it.id == id }
}

internal fun resolveTranscriptionModelId(
    persistedModelId: String,
    activeModelId: String
): String = persistedModelId.ifBlank { activeModelId }
