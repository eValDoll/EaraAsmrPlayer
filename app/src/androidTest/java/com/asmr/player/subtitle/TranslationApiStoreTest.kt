package com.asmr.player.subtitle

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.util.UUID
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

        store.saveCustom(" https://custom.example/v1/ ", " vendor/model ", " custom-api-key ")
        val reopened = open()
        val custom = reopened.readConfiguration().requireConfigured()
        assertEquals(TranslationProvider.CUSTOM, custom.provider)
        assertEquals("https://custom.example/v1/chat/completions", custom.completionsUrl)
        assertEquals("vendor/model", custom.model)
        assertEquals("custom-api-key", custom.apiKey)
        assertFalse(preferences.all.values.any { it.toString().contains("custom-api-key") || it.toString().contains("legacy-deepseek-key") })

        reopened.selectProvider(TranslationProvider.DEEPSEEK)
        assertEquals("legacy-deepseek-key", open().readConfiguration().apiKey)
        reopened.selectProvider(TranslationProvider.CUSTOM)
        assertEquals("custom-api-key", open().readConfiguration().apiKey)
        reopened.saveCustom("https://custom.example/v2", "new-model", "")
        assertEquals("custom-api-key", open().readConfiguration().apiKey)
        assertEquals("new-model", open().readConfiguration().model)
    }

    @Test
    fun invalidCustomSave_doesNotActivateOrOverwriteConfiguredProfile() {
        val store = open()
        assertThrows(IllegalArgumentException::class.java) { store.saveCustom("invalid", "model", "key") }
        assertEquals(TranslationProvider.DEEPSEEK, store.readSettings().provider)
        assertThrows(IllegalStateException::class.java) { store.selectProvider(TranslationProvider.CUSTOM) }
        store.saveCustom("https://example.com/v1", "model", "key")
        assertThrows(IllegalArgumentException::class.java) { store.saveCustom("https://other.example/v1", "", "replacement") }
        assertEquals("https://example.com/v1", store.readConfiguration().baseUrl)
        assertEquals("key", store.readConfiguration().apiKey)
    }
}
