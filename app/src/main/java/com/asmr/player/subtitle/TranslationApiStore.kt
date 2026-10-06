package com.asmr.player.subtitle

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal class TranslationApiStore internal constructor(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
    private val keyAlias: String = KEY_ALIAS
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE
    )
    private val lock = Any()

    fun readDeepSeekKey(): String = synchronized(lock) { readEncrypted() }

    fun readSettings(): TranslationApiSettings = synchronized(lock) {
        val custom = readCustom()
        TranslationApiSettings(
            provider = selectedProvider(),
            customBaseUrl = custom?.baseUrl.orEmpty(),
            customModel = custom?.model.orEmpty(),
            customKeyConfigured = custom?.apiKey?.isNotBlank() == true
        )
    }

    fun readConfiguration(): TranslationApiConfig = synchronized(lock) {
        when (selectedProvider()) {
            TranslationProvider.DEEPSEEK -> TranslationApiConfig(apiKey = readEncrypted())
            TranslationProvider.CUSTOM -> readCustom() ?: TranslationApiConfig(
                provider = TranslationProvider.CUSTOM, apiKey = "", baseUrl = "", model = ""
            )
        }
    }

    fun selectProvider(provider: TranslationProvider) = synchronized(lock) {
        if (provider == TranslationProvider.CUSTOM) {
            checkNotNull(readCustom()) { "请先配置自定义翻译服务" }.requireConfigured()
        }
        check(preferences.edit().putString(KEY_PROVIDER, provider.name).commit()) { "翻译服务保存失败" }
    }

    fun saveCustom(baseUrl: String, model: String, apiKey: String) = synchronized(lock) {
        val config = TranslationApiConfig(
            provider = TranslationProvider.CUSTOM,
            apiKey = apiKey.trim().ifBlank { readCustom()?.apiKey.orEmpty() },
            baseUrl = baseUrl.trim(),
            model = model.trim()
        ).requireConfigured()
        val raw = JSONObject()
            .put("baseUrl", config.baseUrl)
            .put("model", config.model)
            .put("apiKey", config.apiKey)
            .toString()
        saveEncrypted(raw, CUSTOM_PREFIX, TranslationProvider.CUSTOM)
    }

    private fun selectedProvider(): TranslationProvider =
        if (preferences.getString(KEY_PROVIDER, null) == TranslationProvider.CUSTOM.name) {
            TranslationProvider.CUSTOM
        } else TranslationProvider.DEEPSEEK

    private fun readCustom(): TranslationApiConfig? {
        val raw = readEncrypted(CUSTOM_PREFIX)
        if (raw.isBlank()) return null
        return runCatching {
            val json = JSONObject(raw)
            TranslationApiConfig(
                provider = TranslationProvider.CUSTOM,
                apiKey = json.getString("apiKey"),
                baseUrl = json.getString("baseUrl"),
                model = json.getString("model")
            )
        }.getOrNull()
    }

    private fun readEncrypted(prefix: String = ""): String {
        val encrypted = preferences.getString(prefix + KEY_ENCRYPTED_VALUE, null) ?: return ""
        val iv = preferences.getString(prefix + KEY_INITIALIZATION_VECTOR, null) ?: return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP))
            )
            cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrElse {
            clearStoredValue(prefix)
            ""
        }
    }

    fun saveDeepSeekKey(apiKey: String) = synchronized(lock) {
        val normalized = apiKey.trim()
        if (normalized.isEmpty()) {
            clearStoredValue()
            return@synchronized
        }
        saveEncrypted(normalized)
    }

    private fun saveEncrypted(value: String, prefix: String = "", provider: TranslationProvider? = null) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val editor = preferences.edit()
            .putString(prefix + KEY_ENCRYPTED_VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(prefix + KEY_INITIALIZATION_VECTOR, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        if (provider != null) editor.putString(KEY_PROVIDER, provider.name)
        check(editor.commit()) { "翻译配置保存失败" }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun clearStoredValue(prefix: String = "") {
        preferences.edit()
            .remove(prefix + KEY_ENCRYPTED_VALUE)
            .remove(prefix + KEY_INITIALIZATION_VECTOR)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "deepseek_api_key_preferences"
        private const val KEY_PROVIDER = "translation_provider"
        private const val CUSTOM_PREFIX = "custom_"
        private const val KEY_ENCRYPTED_VALUE = "encrypted_value"
        private const val KEY_INITIALIZATION_VECTOR = "initialization_vector"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "eara_deepseek_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128

        @Volatile
        private var instance: TranslationApiStore? = null

        fun get(context: Context): TranslationApiStore {
            return instance ?: synchronized(this) {
                instance ?: TranslationApiStore(context).also { instance = it }
            }
        }
    }
}
