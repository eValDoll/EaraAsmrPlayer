package com.asmr.player.subtitle

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import javax.net.ssl.SSLException

internal data class DeepSeekHttpFailure(
    val message: String,
    val retryable: Boolean
)

internal object SubtitleFailureMessages {
    fun isUserActionWarning(message: String): Boolean {
        val normalized = message.trim()
        return normalized.contains("API Key") ||
            normalized.contains("账户余额不足") ||
            normalized.startsWith(SubtitleModelRepository.MODEL_REQUIRED_MESSAGE) ||
            normalized.startsWith("当前设备不支持本地字幕生成")
    }

    fun transcription(error: Throwable): String {
        val causes = error.causeChain()
        val details = causes.joinToString(" ") { cause -> cause.message.orEmpty() }
            .lowercase(Locale.ROOT)
        return when {
            causes.any { it is OutOfMemoryError } || details.containsAny(MEMORY_ERROR_MARKERS) ->
                "本地转录失败：运行模型时内存不足（OOM）。已保留已完成的转录进度；" +
                    "请关闭占用内存的应用后重试，若仍失败则当前设备可能无法稳定运行此模型。"

            causes.any { it is UnsatisfiedLinkError || it is LinkageError } ||
                details.containsAny(RUNTIME_ERROR_MARKERS) ->
                "本地转录失败：字幕运行环境与当前设备不兼容。已保留已完成的转录进度；" +
                    "本功能仅支持 arm64-v8a 设备，请确认系统兼容性，或在设置中重新下载字幕组件后重试。"

            details.containsAny(COMPONENT_ERROR_MARKERS) ->
                "本地转录失败：字幕模型或运行组件缺失、损坏。已保留已完成的转录进度；" +
                    "请前往设置删除并重新下载字幕模型后重试。"

            details.containsAny(AUDIO_ERROR_MARKERS) ->
                "本地转录失败：音频读取或解码失败。已保留已完成的转录进度；" +
                    "请确认文件完整且音频格式受当前系统支持后重试。"

            details.contains("未识别到可生成字幕的日语语音") ->
                "本地转录未识别到可生成字幕的日语语音，请确认音频包含清晰的日语语音后重试。"

            details.contains("字幕在转录期间已被修改") ->
                "字幕在转录期间已被修改，任务已停止写入，未覆盖用户版本。"

            else ->
                "本地转录失败：模型运行异常。已保留已完成的转录进度；" +
                    "请重试，若持续失败请在设置中重新下载字幕模型。"
        }
    }

    fun translation(error: Throwable): String {
        val causes = error.causeChain()
        val details = causes.joinToString(" ") { cause -> cause.message.orEmpty() }
            .lowercase(Locale.ROOT)
        return when {
            causes.any { it is OutOfMemoryError } || details.containsAny(MEMORY_ERROR_MARKERS) ->
                "字幕翻译失败：处理模型响应时内存不足。请关闭占用内存的应用后重试。"

            details.contains("deepseek api key") || details.contains("api key") ->
                "字幕翻译失败：DeepSeek API Key 未配置或已被移除，请前往设置重新配置后重试。"

            else ->
                "字幕翻译失败：发生未预期的运行错误。请重试；若持续失败，请更新应用后再试。"
        }
    }

    fun network(error: IOException): String {
        val causes = error.causeChain()
        return when {
            causes.any { it is SocketTimeoutException } ->
                "DeepSeek 网络请求超时，请检查网络或代理设置后重试。"

            causes.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException } ->
                "无法连接 DeepSeek 翻译服务，请检查网络或代理设置后重试。"

            causes.any { it is SSLException } ->
                "无法与 DeepSeek 建立安全连接，请检查系统时间、证书或代理设置后重试。"

            else ->
                "DeepSeek 网络请求失败，请检查网络或代理设置后重试。"
        }
    }

    fun deepSeekHttp(statusCode: Int, serviceMessage: String?): DeepSeekHttpFailure {
        val retryable = statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500
        val message = when (statusCode) {
            400, 422 -> buildHttpMessage(
                prefix = "DeepSeek 拒绝了翻译请求（HTTP $statusCode）",
                serviceMessage = serviceMessage,
                action = "请更新应用或稍后重试。"
            )
            401 -> "DeepSeek API Key 无效或已失效，请前往设置重新配置后重试。"
            402 -> "DeepSeek 账户余额不足，请充值后重试。"
            403 -> "DeepSeek 拒绝访问，请检查 API Key 权限或有效状态后重试。"
            404 -> "DeepSeek 翻译模型不可用，请更新应用后重试。"
            408 -> "DeepSeek 请求超时，请检查网络后重试。"
            425 -> "DeepSeek 暂时无法处理翻译请求，请稍后重试。"
            429 -> "DeepSeek 请求过于频繁，请稍后重试。"
            in 500..599 -> "DeepSeek 服务暂时不可用（HTTP $statusCode），请稍后重试。"
            else -> buildHttpMessage(
                prefix = "DeepSeek 翻译请求失败（HTTP $statusCode）",
                serviceMessage = serviceMessage,
                action = "请稍后重试。"
            )
        }
        return DeepSeekHttpFailure(message = message, retryable = retryable)
    }

    private fun buildHttpMessage(prefix: String, serviceMessage: String?, action: String): String {
        val detail = serviceMessage
            ?.trim()
            ?.replace(Regex("[\\r\\n\\t]+"), " ")
            ?.take(MAX_SERVICE_MESSAGE_LENGTH)
            ?.takeIf(String::isNotBlank)
        return if (detail == null) "$prefix。$action" else "$prefix：$detail。$action"
    }

    private val MEMORY_ERROR_MARKERS = listOf(
        "out of memory",
        "outofmemory",
        "failed to allocate",
        "cannot allocate memory",
        "memory allocation",
        "std::bad_alloc"
    )
    private val RUNTIME_ERROR_MARKERS = listOf(
        "dlopen failed",
        "unsupported abi",
        "当前设备不支持",
        "no implementation found",
        "cannot locate symbol",
        "unsatisfiedlink"
    )
    private val COMPONENT_ERROR_MARKERS = listOf(
        "字幕运行时",
        "模型文件缺失",
        "模型校验失败",
        "文件校验失败",
        "no such file",
        "file too short",
        "unable to open model"
    )
    private val AUDIO_ERROR_MARKERS = listOf(
        "音频解码",
        "无法读取音频",
        "无法打开音频",
        "mediacodec",
        "mediaextractor"
    )
    private const val MAX_SERVICE_MESSAGE_LENGTH = 160
}

private fun String.containsAny(markers: List<String>): Boolean = markers.any(::contains)

private fun Throwable.causeChain(): List<Throwable> {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val causes = mutableListOf<Throwable>()
    var current: Throwable? = this
    while (current != null && causes.size < 12 && seen.add(current)) {
        causes += current
        current = current.cause
    }
    return causes
}
