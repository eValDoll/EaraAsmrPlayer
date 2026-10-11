package com.asmr.player.subtitle

import com.asmr.player.data.remote.NetworkHeaders
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

internal class TranslationModelsClient(client: OkHttpClient) {
    private val calls = client.newBuilder()
        .apply {
            interceptors().removeAll { it is HttpLoggingInterceptor }
            networkInterceptors().removeAll { it is HttpLoggingInterceptor }
        }
        .callTimeout(20, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun loadModels(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        require(key.isNotBlank()) { "请先输入 API Key" }
        require(key.all { it.code in 33..126 }) { "API Key 包含无效字符" }
        val request = Request.Builder()
            .url(translationModelsUrl(baseUrl))
            .header("Authorization", "Bearer $key")
            .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
            .get().build()
        execute(calls.newCall(request)).use { response ->
            if (!response.isSuccessful) throw IOException(when (response.code) {
                401, 403 -> "模型列表认证失败，请检查 API Key"
                404, 405, 501 -> "此服务不支持模型列表，可手动输入模型"
                else -> "模型列表拉取失败（${response.code}）"
            })
            parseTranslationModels(response.body?.string().orEmpty())
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun execute(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) { response.close() }
                else response.close()
            }
        })
    }
}

internal fun parseTranslationModels(raw: String): List<String> {
    val models = try {
        JsonParser.parseString(raw).asJsonObject.getAsJsonArray("data").mapNotNull { item ->
            val id = item.takeIf { it.isJsonObject }?.asJsonObject?.get("id")
            id?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.trim()?.takeIf { it.isNotEmpty() }
        }.distinct().sorted()
    } catch (_: Exception) {
        throw IOException("模型列表格式无效")
    }
    if (models.isEmpty()) throw IOException("此服务未返回可用模型")
    return models
}
