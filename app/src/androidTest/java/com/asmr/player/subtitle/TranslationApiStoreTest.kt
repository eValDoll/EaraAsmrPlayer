package com.asmr.player.subtitle

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationApiStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "translation_api_test_${UUID.randomUUID()}"
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun open() = TranslationApiStore(context, name, name)

    @After
    fun cleanUp() {
        context.deleteSharedPreferences(name)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(name) }
    }

    @Test
    fun encryptedProfiles_surviveReopenAndSwitchWithoutOverwritingLegacyKey() {
        val store = open()
        store.saveDeepSeekKey("legacy-deepseek-key")
        // 旧版只有这两个加密字段，没有 provider 字段。
        assertEquals(setOf("encrypted_value", "initialization_vector"), preferences.all.keys)
        assertEquals(TranslationProvider.DEEPSEEK, open().readSettings().provider)
        assertEquals("legacy-deepseek-key", open().readConfiguration().apiKey)

        store.saveCustom(" https://custom.example/v1/ ", " vendor/model ", " custom-api-key ", 65_536,
            CustomThinkingMode.ENABLED, CustomReasoningEffort.XHIGH)
        val reopened = open()
        val custom = reopened.readConfiguration().requireConfigured()
        assertEquals(TranslationProvider.CUSTOM, custom.provider)
        assertEquals("https://custom.example/v1/chat/completions", custom.completionsUrl)
        assertEquals("vendor/model", custom.model)
        assertEquals("custom-api-key", custom.apiKey)
        assertEquals(65_536, custom.maxOutputTokens)
        assertEquals(65_536, reopened.readSettings().customMaxOutputTokens)
        assertEquals(CustomThinkingMode.ENABLED, custom.thinkingMode)
        assertEquals(CustomReasoningEffort.XHIGH, reopened.readSettings().customReasoningEffort)
        assertFalse(preferences.all.values.any { it.toString().contains("custom-api-key") || it.toString().contains("legacy-deepseek-key") })

        reopened.selectProvider(TranslationProvider.DEEPSEEK)
        assertEquals("legacy-deepseek-key", open().readConfiguration().apiKey)
        reopened.selectProvider(TranslationProvider.CUSTOM)
        assertEquals("custom-api-key", open().readConfiguration().apiKey)
        assertEquals(65_536, open().readConfiguration().maxOutputTokens)
        assertEquals(CustomReasoningEffort.XHIGH, open().readConfiguration().reasoningEffort)
        reopened.saveCustom("https://custom.example/v2", "new-model", "", 100_000,
            CustomThinkingMode.DISABLED, CustomReasoningEffort.LOW)
        assertEquals("custom-api-key", open().readConfiguration().apiKey)
        assertEquals("new-model", open().readConfiguration().model)
        assertEquals(100_000, open().readConfiguration().maxOutputTokens)
        assertEquals(CustomThinkingMode.DISABLED, open().readSettings().customThinkingMode)
        assertEquals("none", open().readConfiguration().customReasoningEffortValue)
    }

    @Test
    fun invalidCustomSave_doesNotActivateOrOverwriteConfiguredProfile() {
        val store = open()
        assertThrows(IllegalArgumentException::class.java) { store.saveCustom("invalid", "model", "key", 32_768) }
        assertEquals(TranslationProvider.DEEPSEEK, store.readSettings().provider)
        assertThrows(IllegalStateException::class.java) { store.selectProvider(TranslationProvider.CUSTOM) }
        store.saveCustom("https://example.com/v1", "model", "key", 65_536)
        assertThrows(IllegalArgumentException::class.java) { store.saveCustom("https://other.example/v1", "", "replacement", 32_768) }
        assertThrows(IllegalArgumentException::class.java) { store.saveCustom("https://other.example/v1", "model", "replacement", 0) }
        assertEquals("https://example.com/v1", store.readConfiguration().baseUrl)
        assertEquals("key", store.readConfiguration().apiKey)
        assertEquals(65_536, store.readConfiguration().maxOutputTokens)
    }

    @Test
    fun legacyCustomProfile_getsExplicitLimitWithoutLosingCredentials() {
        open().saveCustom("https://example.com/v1", "model", "key", 65_536)
        val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(name, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        val legacy = """{"baseUrl":"https://example.com/v1","model":"model","apiKey":"key"}"""
        preferences.edit()
            .putString("custom_encrypted_value", Base64.encodeToString(cipher.doFinal(legacy.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            .putString("custom_initialization_vector", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        val restored = open()
        assertEquals(32_768, restored.readConfiguration().maxOutputTokens)
        assertEquals(32_768, restored.readSettings().customMaxOutputTokens)
        assertEquals("key", restored.readConfiguration().apiKey)
        assertEquals(TranslationProvider.CUSTOM, restored.readSettings().provider)
        assertEquals(CustomThinkingMode.FOLLOW_SERVER, restored.readSettings().customThinkingMode)
        assertEquals(CustomReasoningEffort.HIGH, restored.readSettings().customReasoningEffort)
    }
}
