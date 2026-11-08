package com.asmr.player.subtitle

import com.asmr.player.data.settings.DeepSeekTranslationSettings
import com.google.gson.Gson

internal fun buildDeepSeekSubtitleTranslationRequest(
    gson: Gson,
    sources: List<GeneratedSubtitleSource>,
    allowMerging: Boolean,
    confirmedCaptions: List<GeneratedSubtitleCaption> = emptyList(),
    workContext: SubtitleWorkContext? = null,
    scriptContext: SubtitleScriptContext? = null,
    settings: DeepSeekTranslationSettings = DeepSeekTranslationSettings()
): String {
    val messages = buildSubtitleAgentInitialMessages(
        gson = gson,
        sources = sources,
        allowMerging = allowMerging,
        confirmedCaptions = confirmedCaptions,
        workContext = workContext,
        scriptContext = scriptContext
    )
    return buildDeepSeekSubtitleAgentRequest(gson, messages, settings, scriptContext)
}
