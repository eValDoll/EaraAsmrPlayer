package com.asmr.player.subtitle

import com.asmr.player.i18n.appString
import com.asmr.player.R

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val DEFAULT_TRANSLATION_MAX_OUTPUT_TOKENS = 32_768
internal const val DEEPSEEK_TRANSLATION_BASE_URL = "https://api.deepseek.com"

internal enum class CustomThinkingMode(private val labelProvider: () -> String) {
    FOLLOW_SERVER( { appString(R.string.ui_server_default) }), DISABLED( { appString(R.string.ui_close) }), ENABLED( { appString(R.string.ui_on) });

    val label: String get() = labelProvider()
}

internal enum class CustomReasoningEffort(val wireValue: String) {
    MINIMAL("minimal"), LOW("low"), MEDIUM("medium"), HIGH("high"), XHIGH("xhigh"), MAX("max")
}

internal enum class TranslationProvider(private val labelProvider: () -> String) {
    DEEPSEEK( { "DeepSeek" }),
    CUSTOM( { appString(R.string.ui_custom_openai_compatible) });

    val label: String get() = labelProvider()
}

internal data class TranslationApiSettings(
    val provider: TranslationProvider = TranslationProvider.DEEPSEEK,
    val customBaseUrl: String = "",
    val customModel: String = "",
    val customKeyConfigured: Boolean = false,
    val customMaxOutputTokens: Int = DEFAULT_TRANSLATION_MAX_OUTPUT_TOKENS,
    val customThinkingMode: CustomThinkingMode = CustomThinkingMode.FOLLOW_SERVER,
    val customReasoningEffort: CustomReasoningEffort = CustomReasoningEffort.HIGH,
    val deepSeekModel: String = DEEPSEEK_SUBTITLE_MODEL,
)

// 密钥不参与 data class 的 toString，避免在日志中意外输出。
internal class TranslationApiConfig(
    val provider: TranslationProvider = TranslationProvider.DEEPSEEK,
    val apiKey: String,
    val baseUrl: String = DEEPSEEK_TRANSLATION_BASE_URL,
    val model: String = DEEPSEEK_SUBTITLE_MODEL,
    val maxOutputTokens: Int = DEFAULT_TRANSLATION_MAX_OUTPUT_TOKENS,
    val thinkingMode: CustomThinkingMode = CustomThinkingMode.FOLLOW_SERVER,
    val reasoningEffort: CustomReasoningEffort = CustomReasoningEffort.HIGH
) {
    init {
        require(maxOutputTokens > 0) { appString(R.string.ui_maximum_output_tokens_must_be_a_positive_integer) }
    }

    val isDeepSeek: Boolean get() = provider == TranslationProvider.DEEPSEEK
    val serviceName: String get() = if (isDeepSeek) "DeepSeek" else appString(R.string.ui_custom_translation_service)
    val completionsUrl: String get() = translationChatCompletionsUrl(baseUrl)
    val customReasoningEffortValue: String? get() = when (thinkingMode) {
        CustomThinkingMode.FOLLOW_SERVER -> null
        CustomThinkingMode.DISABLED -> "none"
        CustomThinkingMode.ENABLED -> reasoningEffort.wireValue
    }

    fun requireConfigured(): TranslationApiConfig {
        require(apiKey.isNotBlank()) { appString(R.string.ui_configure_your_api_key_in_settings_first, serviceName) }
        require(apiKey.all { it.code in 33..126 }) { appString(R.string.ui_the_api_key_contains_invalid_characters) }
        require(model.isNotBlank()) { appString(R.string.ui_configure_a_custom_translation_model_in_settings_first) }
        translationChatCompletionsUrl(baseUrl)
        return this
    }
}

internal fun translationChatCompletionsUrl(baseUrl: String): String {
    val input = baseUrl.trim()
    require(input.startsWith("https://", ignoreCase = true) || input.startsWith("http://", ignoreCase = true)) {
        appString(R.string.ui_enter_a_valid_http_or_https_api_url)
    }
    val url = requireNotNull(input.toHttpUrlOrNull()) { appString(R.string.ui_enter_a_valid_api_url) }
    require(url.username.isEmpty() && url.password.isEmpty() && url.fragment == null && url.query == null) {
        appString(R.string.ui_the_api_url_cannot_include_credentials_query_parameters)
    }
    val path = url.encodedPath.trimEnd('/')
    val endpoint = if (path.endsWith("/chat/completions")) path else "$path/chat/completions"
    return url.newBuilder().encodedPath(endpoint).build().toString()
}

internal fun translationModelsUrl(baseUrl: String): String {
    val url = translationChatCompletionsUrl(baseUrl).toHttpUrlOrNull()!!
    return url.newBuilder()
        .encodedPath(url.encodedPath.removeSuffix("/chat/completions") + "/models")
        .build().toString()
}
