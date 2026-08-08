package com.asmr.player.subtitle

import com.asmr.player.data.local.db.entities.SubtitleEntity

internal val SUBTITLE_ORDER = compareBy<SubtitleEntity>(
    SubtitleEntity::startMs,
    SubtitleEntity::endMs,
    SubtitleEntity::id
)
