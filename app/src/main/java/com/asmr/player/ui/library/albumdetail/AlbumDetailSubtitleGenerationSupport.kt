package com.asmr.player.ui.library

import com.asmr.player.util.isOnlineTrackPath

internal fun subtitleGenerationSelectionRequirementMessage(files: List<DirectoryFileItem>): String {
    if (files.isEmpty()) return "请先选择要翻译的本地 MP3/WAV 音频"
    return subtitleGenerationSourceRequirementMessage(
        files.asSequence().filter { it.fileType == TreeFileType.Audio }.map { file ->
            file.path to (!file.isOnline && file.sizeSource is FileSizeSource.Local &&
                !isOnlineTrackPath(file.track?.path ?: file.absolutePath))
        }
    )
}

internal fun subtitleGenerationDirectoryRequirementMessage(index: LocalTreeIndex?, currentPath: String): String {
    val node = index?.let { findLocalTreeNode(it.root, currentPath) }
    fun audioSources(node: LocalTreeNode): Sequence<Pair<String, Boolean>> = sequence {
        if (node.children.isEmpty()) {
            if (node.fileType == TreeFileType.Audio) {
                val path = node.absolutePath.orEmpty()
                yield(node.name to (path.isNotBlank() && !isOnlineTrackPath(path) &&
                    !isOnlineTrackPath(node.track?.path.orEmpty())))
            }
        } else {
            node.children.values.forEach { yieldAll(audioSources(it)) }
        }
    }
    return subtitleGenerationSourceRequirementMessage(node?.let(::audioSources) ?: emptySequence())
}

private fun subtitleGenerationSourceRequirementMessage(audioSources: Sequence<Pair<String, Boolean>>): String {
    var hasAudio = false
    var hasLocalAudio = false
    var hasSupportedLocalAudio = false
    audioSources.forEach { (name, local) ->
        hasAudio = true
        if (local) {
            hasLocalAudio = true
            if (isSupportedSubtitleGenerationAudioName(name)) hasSupportedLocalAudio = true
        }
    }
    return when {
        !hasAudio -> "没有可翻译的音频，请选择本地 MP3/WAV 文件"
        !hasLocalAudio -> "批量翻译仅支持本地音频，请先下载音频后重试"
        !hasSupportedLocalAudio -> "批量翻译仅支持 MP3/WAV 格式，请选择对应的本地音频"
        else -> "未找到可翻译的本地音轨，请重新扫描本地库后重试"
    }
}
