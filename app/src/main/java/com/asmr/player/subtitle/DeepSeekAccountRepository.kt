package com.asmr.player.subtitle

import android.content.Context
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.di.DEEPSEEK_HTTP_CLIENT
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal data class DeepSeekBalance(
    val currency: String,
    val totalBalance: String
)

internal data class DeepSeekAccountState(
    val totalTokens: Long = 0L,
    val balances: List<DeepSeekBalance> = emptyList(),
    val balanceAvailable: Boolean? = null
)

private data class DeepSeekBalanceResponse(
    @SerializedName("is_available")
    val isAvailable: Boolean = false,
    @SerializedName("balance_infos")
    val balanceInfos: List<DeepSeekBalanceInfo> = emptyList()
)

private data class DeepSeekBalanceInfo(
    val currency: String = "",
    @SerializedName("total_balance")
    val totalBalance: String = ""
)

@Singleton
class DeepSeekAccountRepository @Inject internal constructor(
    @ApplicationContext context: Context,
    @Named(DEEPSEEK_HTTP_CLIENT) private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private var apiKeyFingerprint = preferences.getString(KEY_API_KEY_FINGERPRINT, "").orEmpty()
    private val _state = MutableStateFlow(loadState())
    internal val state = _state.asStateFlow()

    internal fun bindApiKey(apiKey: String) {
        val fingerprint = fingerprint(apiKey)
        synchronized(lock) {
            if (apiKeyFingerprint == fingerprint) return
            apiKeyFingerprint = fingerprint
            val resetState = DeepSeekAccountState()
            _state.value = resetState
            preferences.edit()
                .putString(KEY_API_KEY_FINGERPRINT, fingerprint)
                .putLong(KEY_TOTAL_TOKENS, 0L)
                .remove(KEY_BALANCES)
                .remove(KEY_BALANCE_AVAILABLE)
                .apply()
        }
    }

    internal fun recordTokenUsage(apiKey: String, totalTokens: Long) {
        if (totalTokens <= 0L) return
        val requestFingerprint = fingerprint(apiKey)
        synchronized(lock) {
            if (apiKeyFingerprint != requestFingerprint) return
            val current = _state.value
            val updatedTotal = if (Long.MAX_VALUE - current.totalTokens < totalTokens) {
                Long.MAX_VALUE
            } else {
                current.totalTokens + totalTokens
            }
            _state.value = current.copy(totalTokens = updatedTotal)
            preferences.edit().putLong(KEY_TOTAL_TOKENS, updatedTotal).apply()
        }
    }

    internal suspend fun refreshBalance(apiKey: String) {
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) return
        bindApiKey(normalized)
        val requestFingerprint = fingerprint(normalized)
        try {
            val responseBody = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(DEEPSEEK_BALANCE_URL)
                    .header("Authorization", "Bearer $normalized")
                    .header(NetworkHeaders.HEADER_SILENT_IO_ERROR, NetworkHeaders.SILENT_IO_ERROR_ON)
                    .get()
                    .build()
                executeCancellable(okHttpClient.newCall(request)).use { response ->
                    if (!response.isSuccessful) throw IOException("DeepSeek balance HTTP ${response.code}")
                    response.body?.string().orEmpty()
                }
            }
            val parsed = gson.fromJson(responseBody, DeepSeekBalanceResponse::class.java)
            val balances = parsed.balanceInfos.mapNotNull { info ->
                val currency = info.currency.trim().uppercase(Locale.US)
                val amount = info.totalBalance.trim()
                if (currency.isEmpty() || amount.toBigDecimalOrNull() == null) null
                else DeepSeekBalance(currency = currency, totalBalance = amount)
            }
            synchronized(lock) {
                if (apiKeyFingerprint != requestFingerprint) return@synchronized
                val updated = _state.value.copy(
                    balances = balances,
                    balanceAvailable = parsed.isAvailable
                )
                _state.value = updated
                preferences.edit()
                    .putString(KEY_BALANCES, gson.toJson(balances))
                    .putBoolean(KEY_BALANCE_AVAILABLE, parsed.isAvailable)
                    .apply()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // 余额查询失败不影响翻译，继续展示上次成功查询的结果。
        }
    }

    private fun loadState(): DeepSeekAccountState {
        val balances = preferences.getString(KEY_BALANCES, null)?.let { raw ->
            runCatching {
                gson.fromJson(raw, Array<DeepSeekBalance>::class.java).toList()
            }.getOrDefault(emptyList())
        }.orEmpty()
        return DeepSeekAccountState(
            totalTokens = preferences.getLong(KEY_TOTAL_TOKENS, 0L).coerceAtLeast(0L),
            balances = balances,
            balanceAvailable = if (preferences.contains(KEY_BALANCE_AVAILABLE)) {
                preferences.getBoolean(KEY_BALANCE_AVAILABLE, false)
            } else {
                null
            }
        )
    }

    private suspend fun executeCancellable(call: Call): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isActive) return
                    if (call.isCanceled()) {
                        continuation.resumeWithException(CancellationException("余额查询已取消", e))
                    } else {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(response) else response.close()
                }
            })
        }

    private fun fingerprint(apiKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(apiKey.trim().toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val PREFERENCES_NAME = "deepseek_account_usage"
        const val KEY_API_KEY_FINGERPRINT = "api_key_fingerprint"
        const val KEY_TOTAL_TOKENS = "total_tokens"
        const val KEY_BALANCES = "balances"
        const val KEY_BALANCE_AVAILABLE = "balance_available"
    }
}

internal fun formatDeepSeekTokenTotal(tokens: Long): String {
    val safeTokens = tokens.coerceAtLeast(0L)
    val (divisor, suffix) = when {
        safeTokens >= 1_000_000_000L -> 1_000_000_000.0 to "B"
        safeTokens >= 1_000_000L -> 1_000_000.0 to "M"
        safeTokens >= 1_000L -> 1_000.0 to "K"
        else -> return safeTokens.toString()
    }
    return String.format(Locale.US, "%.1f", safeTokens / divisor)
        .removeSuffix(".0") + suffix
}

internal fun formatDeepSeekBalances(balances: List<DeepSeekBalance>): String {
    if (balances.isEmpty()) return "--"
    return balances.joinToString(separator = " / ") { balance ->
        val amount = balance.totalBalance.toBigDecimalOrNull()
            ?.setScale(2, RoundingMode.HALF_UP)
            ?.stripTrailingZeros()
            ?.toPlainString()
            ?: balance.totalBalance
        when (balance.currency.uppercase(Locale.US)) {
            "CNY" -> "¥$amount"
            "USD" -> "$$amount"
            else -> "${balance.currency} $amount"
        }
    }
}

internal const val DEEPSEEK_BALANCE_URL = "https://api.deepseek.com/user/balance"
