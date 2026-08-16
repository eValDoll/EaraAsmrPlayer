package com.asmr.player.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface ProxyPasswordStorage {
    fun read(): String
    fun save(password: String)
    fun clear()
    fun isConfigured(): Boolean = read().isNotEmpty()
}

internal class ProxyPasswordStore private constructor(context: Context) : ProxyPasswordStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
    private val lock = Any()

    override fun read(): String = synchronized(lock) {
        val encrypted = preferences.getString(KEY_ENCRYPTED_VALUE, null) ?: return@synchronized ""
        val iv = preferences.getString(KEY_INITIALIZATION_VECTOR, null) ?: return@synchronized ""
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP))
            )
            cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrElse {
            clearStoredValue()
            ""
        }
    }

    override fun save(password: String) = synchronized(lock) {
        if (password.isEmpty()) {
            clearStoredValue()
            return@synchronized
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_ENCRYPTED_VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_INITIALIZATION_VECTOR, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    override fun clear() = synchronized(lock) {
        clearStoredValue()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
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

    private fun clearStoredValue() {
        preferences.edit()
            .remove(KEY_ENCRYPTED_VALUE)
            .remove(KEY_INITIALIZATION_VECTOR)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "proxy_password_preferences"
        private const val KEY_ENCRYPTED_VALUE = "encrypted_value"
        private const val KEY_INITIALIZATION_VECTOR = "initialization_vector"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "eara_proxy_password"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128

        @Volatile
        private var instance: ProxyPasswordStore? = null

        fun get(context: Context): ProxyPasswordStore {
            return instance ?: synchronized(this) {
                instance ?: ProxyPasswordStore(context).also { instance = it }
            }
        }
    }
}

internal class InMemoryProxyPasswordStorage : ProxyPasswordStorage {
    private var password = ""

    override fun read(): String = password

    override fun save(password: String) {
        this.password = password
    }

    override fun clear() {
        password = ""
    }
}
