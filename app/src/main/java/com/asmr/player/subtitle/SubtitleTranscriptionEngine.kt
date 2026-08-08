package com.asmr.player.subtitle

import android.content.Context
import java.io.Closeable
import java.io.File

internal data class SubtitleModelArtifact(
    val fileName: String,
    val bytes: Long,
    val sha256: String
)

internal data class SubtitleTranscriptionModel(
    val id: String,
    val displayName: String,
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
    fun defaultFactory(context: Context): SubtitleTranscriptionEngineFactory {
        val repository = SubtitleModelRepository.get(context)
        return ParakeetEngineFactory(
            context = context.applicationContext,
            modelDirectory = repository.requireInstalledModelDirectory(),
            model = SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8
        )
    }
}

internal object SubtitleTranscriptionModels {
    val PARAKEET_TDT_CTC_06B_JA_INT8 = SubtitleTranscriptionModel(
        id = "parakeet-tdt-ctc-0.6b-ja-35000-int8",
        displayName = "Parakeet TDT-CTC 0.6B Japanese INT8",
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
}
