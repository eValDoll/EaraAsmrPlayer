package com.asmr.player.data.remote.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AsmrOneCredentials(val name: String, val token: String)

interface AsmrOneCredentialStorage {
    suspend fun read(): AsmrOneCredentials?
    suspend fun save(credentials: AsmrOneCredentials)
    suspend fun clear()
}

@Singleton
class AsmrOneAuthStore @Inject constructor(@ApplicationContext context: Context) : AsmrOneCredentialStorage {
    private val preferences = context.getSharedPreferences("asmr_one_auth", Context.MODE_PRIVATE)

    override suspend fun read(): AsmrOneCredentials? = withContext(Dispatchers.IO) {
        val name = preferences.getString("name", null) ?: return@withContext null
        val encrypted = preferences.getString("token", null) ?: return@withContext null
        val iv = preferences.getString("iv", null) ?: return@withContext null
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            AsmrOneCredentials(name, cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8))
        } catch (_: java.security.GeneralSecurityException) {
            clear()
            null
        } catch (_: IllegalArgumentException) {
            clear()
            null
        }
    }

    override suspend fun save(credentials: AsmrOneCredentials) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(credentials.token.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString("name", credentials.name)
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit())
    }

    override suspend fun clear() = withContext(Dispatchers.IO) { check(preferences.edit().clear().commit()) }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }

    private companion object { const val KEY_ALIAS = "eara_asmr_one_token" }
}
