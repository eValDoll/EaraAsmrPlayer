package com.asmr.player.util

object AppErrorMessageFormatter {
    private val whitespaceRegex = Regex("""\s+""")
    private val rjSampleRegex = Regex("""[（(]\s*如\s*RJ\d{6,}\s*[)）]""", RegexOption.IGNORE_CASE)
    private val technicalDetailRegexes = listOf(
        Regex("""\bHTTP\s*\d{3}\b""", RegexOption.IGNORE_CASE),
        Regex("""\b[A-Za-z]+Exception\b"""),
        Regex("""\b[A-Z]{2,}(?:_[A-Z0-9]+)+\b"""),
        Regex("""\b(error\s*code|errorCode|status\s*code|code)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(sockettimeout|timeout|timed out|ioexception|illegalstate|unknownhost|ssl|eof|cancelled|canceled|failed)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bRJ\d{6,}\b""", RegexOption.IGNORE_CASE)
    )

    fun sanitize(rawMessage: String, fallback: String = "操作失败，请稍后重试"): String {
        val normalized = rawMessage
            .replace(whitespaceRegex, " ")
            .replace(rjSampleRegex, "")
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
            message.startsWith("请输入有效 RJ 号") -> "请输入有效的作品编号"
            message.startsWith("仅支持本地库专辑手动绑定 RJ") -> "仅支持本地库专辑手动绑定作品编号"
            else -> null
        }
    }

    private fun knownTechnicalMessage(message: String): String? {
        val lower = message.lowercase()
        return when {
            ".m3u8" in lower -> "当前暂不支持在线播放该音频，请先下载后再播放"
            "401" in lower || "认证" in message || "登录" in message && ("失败" in message || "过期" in message) ->
                "登录状态已失效，请重新登录后重试"
            "403" in lower || "访问被拒绝" in message -> "当前访问受限，请稍后再试"
            "404" in lower || "not found" in lower -> "未找到相关内容，请检查后重试"
            "429" in lower -> "请求过于频繁，请稍后再试"
            "500" in lower || "502" in lower || "503" in lower || "504" in lower -> "服务器开小差了，请稍后重试"
            "timeout" in lower || "timed out" in lower -> "请求超时，请稍后重试"
            "unknownhost" in lower || "network" in lower || "ioexception" in lower || "网络" in message ->
                "网络连接失败，请检查网络后重试"
            else -> null
        }
    }

    private fun inferCategory(message: String): String? {
        return when {
            "播放" in message -> "播放失败，请稍后重试"
            "试听" in message -> "试听刷新失败，请稍后重试"
            "在线资源" in message -> "在线资源加载失败，请稍后重试"
            "DLsite Play" in message -> "DLsite Play 加载失败，请稍后重试"
            "封面" in message -> "设置封面失败，请检查后重试"
            "扫描" in message -> "扫描失败，请检查目录权限后重试"
            "刷新" in message -> "刷新失败，请稍后重试"
            "云同步" in message || "同步" in message -> "同步失败，请稍后重试"
            "删除" in message -> "删除失败，请稍后重试"
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
            "播放" in normalizedPrefix -> "播放失败，请稍后重试"
            "试听刷新" in normalizedPrefix -> "试听刷新失败，请稍后重试"
            "在线资源加载" in normalizedPrefix -> "在线资源加载失败，请稍后重试"
            "DLsite Play 加载" in normalizedPrefix -> "DLsite Play 加载失败，请稍后重试"
            "设置封面" in normalizedPrefix -> "设置封面失败，请检查后重试"
            "扫描" in normalizedPrefix -> "扫描失败，请检查目录权限后重试"
            "目录刷新" in normalizedPrefix || "刷新" in normalizedPrefix -> "刷新失败，请稍后重试"
            "重扫" in normalizedPrefix -> "重扫失败，请检查目录后重试"
            "云同步" in normalizedPrefix || "同步" in normalizedPrefix -> "同步失败，请稍后重试"
            "删除" in normalizedPrefix -> "删除失败，请稍后重试"
            normalizedPrefix.endsWith("失败") -> "$normalizedPrefix，请稍后重试"
            else -> null
        }
    }

    private fun hasTechnicalDetails(message: String): Boolean {
        return technicalDetailRegexes.any { it.containsMatchIn(message) } || !containsOnlyReadableChinese(message)
    }

    private fun containsOnlyReadableChinese(message: String): Boolean {
        val stripped = message
            .replace(Regex("""[，。！？；：“”‘’（）()、/\\\-+*#@]"""), "")
            .replace(Regex("""\d+"""), "")
            .trim()
        if (stripped.isBlank()) return false
        return stripped.all { it.code in 0x4E00..0x9FFF || it.isWhitespace() }
    }
}
