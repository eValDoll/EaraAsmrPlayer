package com.asmr.player.util

import com.asmr.player.i18n.appString
import com.asmr.player.R

val ASMR_ONE_SITE_FAILURE_MESSAGE: String get() = appString(R.string.ui_asmr_one_request_failed_please_go_to_settings)
val ASMR_ONE_SITE_TEST_FAILURE_MESSAGE: String get() = appString(R.string.ui_the_current_site_connection_failed_please_try_switching)

object AppErrorMessageFormatter {
    private val whitespaceRegex = Regex("""\s+""")
    private val workNoSampleRegex = Regex("""[（(]\s*如\s*(?:RJ|BJ|VJ)\d{6,}\s*[)）]""", RegexOption.IGNORE_CASE)
    private val userFacingSubtitleErrorPrefixes = listOf(
        "本地转录失败：",
        "本地转录未识别到",
        "字幕在转录期间已被修改",
        "字幕翻译失败：",
        "字幕翻译模型返回格式错误：",
        "DeepSeek ",
        "自定义翻译服务",
        "无法连接 自定义翻译服务",
        "无法与 自定义翻译服务",
        "请先在设置中配置自定义翻译模型",
        "请先在设置中配置 自定义翻译服务 API Key",
        "无法连接 DeepSeek",
        "无法与 DeepSeek",
        "请先在设置中配置 DeepSeek API Key"
    )
    private val technicalDetailRegexes = listOf(
        Regex("""\bHTTP\s*\d{3}\b""", RegexOption.IGNORE_CASE),
        Regex("""\b[A-Za-z]+Exception\b"""),
        Regex("""\b[A-Z]{2,}(?:_[A-Z0-9]+)+\b"""),
        Regex("""\b(error\s*code|errorCode|status\s*code|code)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(sockettimeout|timeout|timed out|ioexception|illegalstate|unknownhost|ssl|eof|cancelled|canceled)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:RJ|BJ|VJ)\d{6,}\b""", RegexOption.IGNORE_CASE)
    )

    fun sanitize(rawMessage: String, fallback: String = appString(R.string.ui_operation_failed_please_try_again_later)): String {
        val normalized = rawMessage
            .replace(whitespaceRegex, " ")
            .replace(workNoSampleRegex, "")
            .trim()
            .trimEnd('：', ':')

        if (normalized.isBlank()) return fallback

        explicitMessage(normalized)?.let { return it }
        knownTechnicalMessage(normalized)?.let { return it }

        if (hasTechnicalDetails(normalized)) {
            messagePrefix(normalized)?.let { prefix ->
                friendlyPrefixMessage(prefix)?.let { return it }
            }
            inferCategory(normalized)?.let { return it }
            return fallback
        }

        return normalized
    }

    private fun explicitMessage(message: String): String? {
        return when {
            message == ASMR_ONE_SITE_FAILURE_MESSAGE ||
                message == ASMR_ONE_SITE_TEST_FAILURE_MESSAGE -> message
            userFacingSubtitleErrorPrefixes.any(message::startsWith) -> message
            message.startsWith("请输入有效 RJ 号") || message.startsWith("请输入有效的作品编号") ->
                appString(R.string.ui_please_enter_a_valid_work_number)
            message.startsWith("仅支持本地库专辑手动绑定 RJ") ||
                message.startsWith("仅支持本地库专辑手动绑定作品编号") ->
                appString(R.string.ui_only_supports_manual_binding_of_work_numbers_to)
            else -> null
        }
    }

    private fun knownTechnicalMessage(message: String): String? {
        val lower = message.lowercase()
        return when {
            "401" in lower || "认证" in message || "登录" in message && ("失败" in message || "过期" in message) ->
                appString(R.string.ui_login_status_has_expired_please_log_in_again)
            "403" in lower || "访问被拒绝" in message -> appString(R.string.ui_access_is_currently_restricted_please_try_again_later)
            "404" in lower || "not found" in lower -> appString(R.string.ui_no_relevant_content_found_please_check_and_try)
            "429" in lower -> appString(R.string.ui_the_request_is_too_frequent_please_try_again)
            "500" in lower || "502" in lower || "503" in lower || "504" in lower -> appString(R.string.ui_the_server_is_out_of_service_please_try)
            "timeout" in lower || "timed out" in lower -> appString(R.string.ui_request_timed_out_please_try_again_later)
            "unknownhost" in lower || "network" in lower || "ioexception" in lower || "网络" in message ->
                appString(R.string.ui_network_connection_failed_please_check_the_network_and)
            else -> null
        }
    }

    private fun inferCategory(message: String): String? {
        return when {
            "播放" in message -> appString(R.string.ui_playback_failed_please_try_again_later)
            "试听" in message -> appString(R.string.ui_audition_refresh_failed_please_try_again_later)
            "在线资源" in message -> appString(R.string.ui_online_resource_loading_failed_please_try_again_later)
            "DLsite Play" in message -> appString(R.string.ui_dlsite_play_failed_to_load_please_try_again)
            "封面" in message -> appString(R.string.ui_failed_to_set_cover_please_check_and_try)
            "扫描" in message -> appString(R.string.ui_scan_failed_please_check_directory_permissions_and_try)
            "刷新" in message -> appString(R.string.ui_refresh_failed_please_try_again_later)
            "云同步" in message || "同步" in message -> appString(R.string.ui_synchronization_failed_please_try_again_later)
            "删除" in message -> appString(R.string.ui_deletion_failed_please_try_again_later)
            else -> null
        }
    }

    private fun messagePrefix(message: String): String? {
        val separatorIndex = message.indexOfFirst { it == '：' || it == ':' }
        if (separatorIndex <= 0) return null
        return message.substring(0, separatorIndex).trim().takeIf { it.isNotBlank() }
    }

    private fun friendlyPrefixMessage(prefix: String): String? {
        val normalizedPrefix = prefix.replace("异常", "失败")
        return when {
            "播放" in normalizedPrefix -> appString(R.string.ui_playback_failed_please_try_again_later)
            "试听刷新" in normalizedPrefix -> appString(R.string.ui_audition_refresh_failed_please_try_again_later)
            "在线资源加载" in normalizedPrefix -> appString(R.string.ui_online_resource_loading_failed_please_try_again_later)
            "DLsite Play 加载" in normalizedPrefix -> appString(R.string.ui_dlsite_play_failed_to_load_please_try_again)
            "设置封面" in normalizedPrefix -> appString(R.string.ui_failed_to_set_cover_please_check_and_try)
            "扫描" in normalizedPrefix -> appString(R.string.ui_scan_failed_please_check_directory_permissions_and_try)
            "目录刷新" in normalizedPrefix || "刷新" in normalizedPrefix -> appString(R.string.ui_refresh_failed_please_try_again_later)
            "重扫" in normalizedPrefix -> appString(R.string.ui_rescan_failed_please_check_the_directory_and_try)
            "云同步" in normalizedPrefix || "同步" in normalizedPrefix -> appString(R.string.ui_synchronization_failed_please_try_again_later)
            "删除" in normalizedPrefix -> appString(R.string.ui_deletion_failed_please_try_again_later)
            normalizedPrefix.endsWith("失败") -> "$normalizedPrefix，请稍后重试"
            else -> null
        }
    }

    private fun hasTechnicalDetails(message: String): Boolean {
        return technicalDetailRegexes.any { it.containsMatchIn(message) } || !containsReadableMessage(message)
    }

    private fun containsReadableMessage(message: String): Boolean {
        if (message.split(whitespaceRegex).size >= 3 && message.all {
                it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?;:'’\"()-/"
            }) return true
        val stripped = message
            .replace(Regex("""[，。！？；：“”‘’（）()、/\\\-+*#@]"""), "")
            .replace(Regex("""\d+"""), "")
            .trim()
        if (stripped.isBlank()) return false
        return stripped.all { it.code in 0x4E00..0x9FFF || it.isWhitespace() }
    }
}
