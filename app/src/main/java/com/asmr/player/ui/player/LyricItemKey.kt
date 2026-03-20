package com.asmr.player.ui.player

import com.asmr.player.util.SubtitleEntry

internal fun lyricItemKey(index: Int, entry: SubtitleEntry): String {
    return "${entry.startMs}:${entry.endMs}:$index"
}
