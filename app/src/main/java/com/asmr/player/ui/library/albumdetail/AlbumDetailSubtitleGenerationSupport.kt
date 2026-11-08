package com.asmr.player.ui.library

import com.asmr.player.i18n.appString
import com.asmr.player.R

import com.asmr.player.util.isOnlineTrackPath

internal fun subtitleGenerationSelectionRequirementMessage(files: List<DirectoryFileItem>): String {
    if (files.isEmpty()) return appString(R.string.ui_select_local_audio_to_translate_first)
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
        !hasAudio -> appString(R.string.ui_no_audio_to_translate_select_local_audio_files)
        !hasLocalAudio -> appString(R.string.ui_batch_translation_requires_local_audio_download_it_first)
        !hasSupportedLocalAudio -> appString(R.string.ui_batch_translation_does_not_support_the_selected_audio)
        else -> appString(R.string.ui_no_local_tracks_to_translate_rescan_your_library)
    }
}
