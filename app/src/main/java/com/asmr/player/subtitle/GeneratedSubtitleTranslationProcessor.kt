package com.asmr.player.subtitle

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

internal fun validateGeneratedSubtitleCaptions(
    captions: List<GeneratedSubtitleCaption>,
    expectedSources: List<GeneratedSubtitleSource>
): List<GeneratedSubtitleCaption> {
    require(expectedSources.isNotEmpty())
    val expectedIndices = expectedSources.map(GeneratedSubtitleSource::index)
    require(expectedIndices.distinct().size == expectedIndices.size)
    require(expectedIndices.zipWithNext().all { (left, right) -> right == left + 1 }) {
        "目标字幕片段索引必须连续递增"
    }
    val normalized = validateSubtitleCaptionBatch(
        captions = captions,
        expectedRemainingSources = expectedSources,
        allowMerging = true
    )
    require(normalized.flatMap(GeneratedSubtitleCaption::sourceIndices) == expectedIndices) {
        "字幕重组索引未覆盖全部目标片段"
    }
    return normalized
}

internal fun validateSubtitleCaptionBatch(
    captions: List<GeneratedSubtitleCaption>,
    expectedRemainingSources: List<GeneratedSubtitleSource>,
    allowMerging: Boolean
): List<GeneratedSubtitleCaption> {
    require(expectedRemainingSources.isNotEmpty())
    val expectedIndices = expectedRemainingSources.map(GeneratedSubtitleSource::index)
    require(expectedIndices.distinct().size == expectedIndices.size) { "目标字幕索引不能重复" }
    require(captions.isNotEmpty()) { "缺少 captions 字幕数组" }
    val sourceByIndex = expectedRemainingSources.associateBy(GeneratedSubtitleSource::index)
    val sourcePositionByIndex = expectedIndices.withIndex().associate { (position, index) -> index to position }

    val normalizedCaptions = captions.map { caption ->
        require(caption.sourceIndices.isNotEmpty()) { "字幕组的 source_indices 不能为空" }
        require(allowMerging || caption.sourceIndices.size == 1) {
            "导入字幕必须逐条提交，不能合并 source_indices"
        }
        val sourcePositions = caption.sourceIndices.map { index ->
            sourcePositionByIndex[index] ?: throw IllegalArgumentException("字幕组包含非目标 index：$index")
        }
        require(sourcePositions.zipWithNext().all { (left, right) -> right == left + 1 }) {
            "字幕组的 source_indices 必须按源字幕顺序连续递增"
        }
        require(caption.correctedJapanese.isNotBlank()) { "修正后的日文不能为空" }
        require(caption.chineseText.isNotBlank()) { "翻译文本不能为空" }
        val groupedSources = caption.sourceIndices.map { index ->
            sourceByIndex[index] ?: throw IllegalArgumentException("字幕组包含非目标 index：$index")
        }
        caption.copy(
            startMs = groupedSources.first().startMs,
            endMs = groupedSources.last().endMs
        )
    }

    val actualIndices = normalizedCaptions.flatMap(GeneratedSubtitleCaption::sourceIndices)
    require(actualIndices == expectedIndices.take(actualIndices.size)) {
        "本次工具调用必须从首个未确认索引开始，按顺序提交连续前缀"
    }
    return normalizedCaptions
}
