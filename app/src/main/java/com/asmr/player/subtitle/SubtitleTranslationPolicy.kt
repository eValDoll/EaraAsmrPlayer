package com.asmr.player.subtitle

internal const val DEEPSEEK_TRANSLATION_CONCURRENCY = 10

internal fun fullTranslationRequestCount(totalSources: Int): Int = if (totalSources > 0) 1 else 0
