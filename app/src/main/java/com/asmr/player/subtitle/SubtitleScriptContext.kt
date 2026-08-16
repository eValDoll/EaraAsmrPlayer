package com.asmr.player.subtitle

/**
 * 作品目录下可供翻译 agent 检索查看的台本/文本文件索引项。
 * 只暴露文件名与索引，不暴露本机绝对路径。
 */
internal data class SubtitleScriptFile(
    val index: Int,
    val name: String
)

/**
 * 按字符偏移分页读取台本文件文本的结果。
 */
internal data class SubtitleScriptReadResult(
    val fileIndex: Int,
    val name: String,
    val offset: Int,
    val totalChars: Int,
    val content: String,
    val truncated: Boolean
)

/**
 * 由服务层实现的文件读取器，按字符偏移分页读取台本文件文本。
 * 返回 null 表示该文件当前不可读（例如文件已不存在）。
 */
internal fun interface SubtitleScriptReader {
    suspend fun read(fileIndex: Int, offset: Int, limit: Int): SubtitleScriptReadResult?
}

/**
 * 翻译 agent 可用的台本上下文：作品目录下发现的文本文件及其读取器。
 */
internal data class SubtitleScriptContext(
    val files: List<SubtitleScriptFile>,
    val reader: SubtitleScriptReader
)
