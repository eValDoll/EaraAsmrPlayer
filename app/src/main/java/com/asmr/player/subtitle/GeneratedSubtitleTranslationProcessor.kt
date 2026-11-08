package com.asmr.player.subtitle

import com.asmr.player.i18n.appString
import com.asmr.player.R

import com.google.gson.annotations.SerializedName

internal data class GeneratedSubtitleSource(
    val index: Int,
    @SerializedName("start") val startMs: Long,
    @SerializedName("end") val endMs: Long,
    val text: String
)

internal data class GeneratedSubtitleCaption(
    @SerializedName("source_indices") val sourceIndices: List<Int>,
    @SerializedName("start") val startMs: Long,
    @SerializedName("end") val endMs: Long,
    @SerializedName("corrected_ja") val correctedJapanese: String,
    @SerializedName("text") val chineseText: String
)

internal fun validateSubtitleCaptionBatch(
    captions: List<GeneratedSubtitleCaption>,
    expectedRemainingSources: List<GeneratedSubtitleSource>,
    allowMerging: Boolean
): List<GeneratedSubtitleCaption> {
    require(expectedRemainingSources.isNotEmpty())
    val expectedIndices = expectedRemainingSources.map(GeneratedSubtitleSource::index)
    require(expectedIndices.distinct().size == expectedIndices.size) { appString(R.string.ui_the_target_subtitle_index_cannot_be_repeated) }
    require(captions.isNotEmpty()) { appString(R.string.ui_missing_captions_subtitles_array) }
    val sourceByIndex = expectedRemainingSources.associateBy(GeneratedSubtitleSource::index)
    val sourcePositionByIndex = expectedIndices.withIndex().associate { (position, index) -> index to position }

    val normalizedCaptions = captions.map { caption ->
        require(caption.sourceIndices.isNotEmpty()) { appString(R.string.ui_the_source_indices_of_the_subtitle_group_cannot) }
        require(allowMerging || caption.sourceIndices.size == 1) {
            appString(R.string.ui_imported_subtitles_must_be_submitted_one_by_one)
        }
        val sourcePositions = caption.sourceIndices.map { index ->
            sourcePositionByIndex[index] ?: throw IllegalArgumentException(appString(R.string.ui_subtitle_group_contains_non_target_index, index))
        }
        require(sourcePositions.zipWithNext().all { (left, right) -> right == left + 1 }) {
            appString(R.string.ui_the_source_indices_of_the_subtitle_group_must)
        }
        require(caption.correctedJapanese.isNotBlank()) { appString(R.string.ui_the_corrected_japanese_cannot_be_empty) }
        require(caption.chineseText.isNotBlank()) { appString(R.string.ui_translation_text_cannot_be_empty) }
        val groupedSources = caption.sourceIndices.map { index ->
            sourceByIndex[index] ?: throw IllegalArgumentException(appString(R.string.ui_subtitle_group_contains_non_target_index, index))
        }
        caption.copy(
            startMs = groupedSources.first().startMs,
            endMs = groupedSources.last().endMs
        )
    }

    val actualIndices = normalizedCaptions.flatMap(GeneratedSubtitleCaption::sourceIndices)
    require(actualIndices == expectedIndices.take(actualIndices.size)) {
        appString(R.string.ui_this_tool_call_must_start_from_the_first)
    }
    return normalizedCaptions
}
