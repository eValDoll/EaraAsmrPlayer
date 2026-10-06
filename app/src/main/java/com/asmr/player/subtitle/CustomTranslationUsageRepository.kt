package com.asmr.player.subtitle

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class CustomTranslationUsageRepository @Inject constructor(@ApplicationContext context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences by lazy { applicationContext.getSharedPreferences("custom_translation_usage", Context.MODE_PRIVATE) }
    private val lock = Any()
    private var loaded = false
    private val _totals = MutableStateFlow<Map<String, Long>>(emptyMap())
    internal val totals = _totals.asStateFlow()

    internal fun load() = synchronized(lock) {
        if (!loaded) {
            _totals.value = preferences.all.mapNotNull { (key, value) ->
                (value as? Long)?.let { key to it.coerceAtLeast(0L) }
            }.toMap()
            loaded = true
        }
    }

    internal fun recordTokenUsage(identity: String, tokens: Long) {
        if (tokens <= 0) return
        synchronized(lock) {
            load()
            val previous = _totals.value[identity] ?: 0L
            val updated = if (Long.MAX_VALUE - previous < tokens) Long.MAX_VALUE else previous + tokens
            preferences.edit().putLong(identity, updated).apply()
            _totals.value = _totals.value + (identity to updated)
        }
    }
}

internal fun customTranslationUsageIdentity(config: TranslationApiConfig): String {
    require(!config.isDeepSeek)
    val identity = config.completionsUrl + "\n" + config.apiKey.trim()
    return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
