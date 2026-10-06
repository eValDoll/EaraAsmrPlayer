package com.asmr.player.subtitle

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val DEFAULT_TRANSLATION_MAX_OUTPUT_TOKENS = 32_768
internal const val DEEPSEEK_TRANSLATION_BASE_URL = "https://api.deepseek.com"

internal enum class CustomThinkingMode(val label: String) {
    FOLLOW_SERVER("跟随服务端"), DISABLED("关闭"), ENABLED("开启")
}

internal enum class CustomReasoningEffort(val wireValue: String) {
    MINIMAL("minimal"), LOW("low"), MEDIUM("medium"), HIGH("high"), XHIGH("xhigh"), MAX("max")
}

internal enum class TranslationProvider(val label: String) {
    DEEPSEEK("DeepSeek"),
    CUSTOM("自定义（OpenAI 兼容）")
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
        require(maxOutputTokens > 0) { "最大输出 Token 必须为正整数" }
    }

    val isDeepSeek: Boolean get() = provider == TranslationProvider.DEEPSEEK
    val serviceName: String get() = if (isDeepSeek) "DeepSeek" else "自定义翻译服务"
    val completionsUrl: String get() = translationChatCompletionsUrl(baseUrl)
    val customReasoningEffortValue: String? get() = when (thinkingMode) {
        CustomThinkingMode.FOLLOW_SERVER -> null
        CustomThinkingMode.DISABLED -> "none"
        CustomThinkingMode.ENABLED -> reasoningEffort.wireValue
    }

    fun requireConfigured(): TranslationApiConfig {
        require(apiKey.isNotBlank()) { "请先在设置中配置 $serviceName API Key" }
        require(apiKey.all { it.code in 33..126 }) { "API Key 包含无效字符" }
        require(model.isNotBlank()) { "请先在设置中配置自定义翻译模型" }
        translationChatCompletionsUrl(baseUrl)
        return this
    }
}

internal fun translationChatCompletionsUrl(baseUrl: String): String {
    val input = baseUrl.trim()
    require(input.startsWith("https://", ignoreCase = true) || input.startsWith("http://", ignoreCase = true)) {
        "请输入有效的 HTTP 或 HTTPS API 地址"
    }
    val url = requireNotNull(input.toHttpUrlOrNull()) { "请输入有效的 API 地址" }
    require(url.username.isEmpty() && url.password.isEmpty() && url.fragment == null && url.query == null) {
        "API 地址不能包含账号、密码、查询参数或片段"
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
