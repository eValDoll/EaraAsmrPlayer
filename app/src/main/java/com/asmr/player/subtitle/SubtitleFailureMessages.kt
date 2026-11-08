package com.asmr.player.subtitle

import com.asmr.player.i18n.appString
import com.asmr.player.R

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
            normalized.contains("账户余额不足") || normalized.contains("insufficient account balance", ignoreCase = true) ||
            normalized == SubtitleTaskRepository.ALBUM_ALREADY_POLISHING_MESSAGE ||
            normalized == SubtitleTaskRepository.ACTIVE_TASKS_BLOCK_POLISH_MESSAGE ||
            normalized == SubtitleTaskRepository.POLISHING_BLOCKS_RETRY_MESSAGE ||
            normalized.startsWith(SubtitleModelRepository.MODEL_REQUIRED_MESSAGE) ||
            normalized.startsWith("当前设备不支持本地字幕生成") || normalized.contains("does not support local subtitle", ignoreCase = true)
    }

    fun transcription(error: Throwable): String {
        val causes = error.causeChain()
        val details = causes.joinToString(" ") { cause -> cause.message.orEmpty() }
            .lowercase(Locale.ROOT)
        return when {
            causes.any { it is OutOfMemoryError } || details.containsAny(MEMORY_ERROR_MARKERS) ->
                appString(R.string.ui_local_transcription_failed_out_of_memory_oom_while) +
                    appString(R.string.ui_please_close_the_applications_that_occupy_memory_and)

            causes.any { it is UnsatisfiedLinkError || it is LinkageError } ||
                details.containsAny(RUNTIME_ERROR_MARKERS) ->
                appString(R.string.ui_local_transcription_failed_the_subtitle_running_environment_is) +
                    appString(R.string.ui_this_function_only_supports_arm64_v8a_devices_please)

            details.containsAny(COMPONENT_ERROR_MARKERS) ->
                appString(R.string.ui_local_transcription_failed_the_subtitle_model_or_running) +
                    appString(R.string.ui_please_go_to_settings_to_delete_and_re)

            details.containsAny(AUDIO_ERROR_MARKERS) ->
                appString(R.string.ui_local_transcription_failed_audio_reading_or_decoding_failed) +
                    appString(R.string.ui_please_confirm_that_the_file_is_complete_and)

            (details.contains("未识别到可生成字幕的日语语音") || details.contains("no japanese speech")) ->
                appString(R.string.ui_the_local_transcription_did_not_recognize_the_japanese)

            (details.contains("字幕在转录期间已被修改") || details.contains("modified during transcription")) ->
                appString(R.string.ui_the_subtitles_have_been_modified_during_transcription_the)

            else ->
                appString(R.string.ui_local_transcription_failed_the_model_is_running_abnormally) +
                    appString(R.string.ui_please_try_again_if_it_continues_to_fail)
        }
    }

    fun translation(error: Throwable): String {
        val causes = error.causeChain()
        val details = causes.joinToString(" ") { cause -> cause.message.orEmpty() }
            .lowercase(Locale.ROOT)
        return when {
            causes.any { it is OutOfMemoryError } || details.containsAny(MEMORY_ERROR_MARKERS) ->
                appString(R.string.ui_subtitle_translation_failed_out_of_memory_while_processing)

            details.contains("deepseek api key") || details.contains("api key") ->
                appString(R.string.ui_subtitle_translation_failed_api_key_missing_configure_it)

            else ->
                appString(R.string.ui_subtitle_translation_failed_an_unexpected_runtime_error_occurred)
        }
    }

    fun network(error: IOException, serviceName: String = "DeepSeek"): String {
        val causes = error.causeChain()
        val connectionService = if (serviceName.endsWith("翻译服务") || serviceName.endsWith("translation service")) serviceName else appString(R.string.ui_translation_service_name, serviceName)
        return when {
            causes.any { it is SocketTimeoutException } ->
                appString(R.string.ui_request_timed_out_check_your_network_or_proxy, serviceName)

            causes.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException } ->
                appString(R.string.ui_unable_to_connect_to_check_your_network_or, connectionService)

            causes.any { it is SSLException } ->
                appString(R.string.ui_unable_to_connect_securely_to_check_your_system, serviceName)

            else ->
                appString(R.string.ui_request_failed_check_your_network_or_proxy_settings, serviceName)
        }
    }

    fun deepSeekHttp(
        statusCode: Int,
        serviceMessage: String?,
        serviceName: String = "DeepSeek",
        custom: Boolean = false
    ): DeepSeekHttpFailure {
        val retryable = statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500
        val message = when (statusCode) {
            400, 422 -> buildHttpMessage(
                prefix = appString(R.string.ui_rejected_the_translation_request_http, serviceName, statusCode),
                serviceMessage = serviceMessage,
                action = if (custom) appString(R.string.ui_check_the_api_url_model_and_api_compatibility) else appString(R.string.ui_please_update_the_app_or_try_again_later)
            )
            401 -> appString(R.string.ui_the_api_key_is_invalid_or_expired_update, serviceName)
            402 -> appString(R.string.ui_insufficient_account_balance_add_funds_and_try_again, serviceName)
            403 -> appString(R.string.ui_denied_access_check_your_api_key_and_its, serviceName)
            404 -> if (custom) appString(R.string.ui_the_url_or_model_is_unavailable_check_your, serviceName)
                else appString(R.string.ui_the_translation_model_is_unavailable_update_the_app, serviceName)
            408 -> appString(R.string.ui_request_timed_out_check_your_network_and_try, serviceName)
            425 -> appString(R.string.ui_cannot_process_the_translation_request_right_now_try, serviceName)
            429 -> appString(R.string.ui_too_many_requests_try_again_later, serviceName)
            in 500..599 -> appString(R.string.ui_is_temporarily_unavailable_http_try_again_later, serviceName, statusCode)
            else -> buildHttpMessage(
                prefix = appString(R.string.ui_translation_request_failed_http, serviceName, statusCode),
                serviceMessage = serviceMessage,
                action = appString(R.string.ui_please_try_again_later)
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
        return if (detail == null) appString(R.string.ui_http_message_action, prefix, action) else appString(R.string.ui_http_message_detail_action, prefix, detail, action)
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
