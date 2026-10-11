package com.asmr.player.subtitle

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class CustomTranslationUsageRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun clearTestPreferences() {
        context.getSharedPreferences("custom_translation_usage", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun usage_isPersistedAndSeparatedByEndpointAndKeyAcrossModelChanges() {
        val original = identity("https://example.com/v1", "secret", "first")
        assertEquals(original, identity("https://example.com/v1/chat/completions/", " secret ", "second"))
        val otherKey = identity("https://example.com/v1", "another-secret")
        val otherService = identity("https://other.example.com/v1", "secret")
        val repository = CustomTranslationUsageRepository(context)
        repository.recordTokenUsage(original, 100)
        repository.recordTokenUsage(otherKey, 20)
        repository.recordTokenUsage(otherService, 30)
        // 已切换配置后，旧请求完成仍记到原服务。
        repository.recordTokenUsage(original, 50)
        assertEquals(mapOf(original to 150L, otherKey to 20L, otherService to 30L), repository.totals.value)
        assertEquals(repository.totals.value, CustomTranslationUsageRepository(context).apply { load() }.totals.value)
        assertFalse(context.getSharedPreferences("custom_translation_usage", Context.MODE_PRIVATE).all.toString().contains("secret"))
    }

    @Test
    fun usage_ignoresNonPositiveValuesAndSaturatesWithoutOverflow() {
        val id = identity("https://example.com/v1", "key")
        val repository = CustomTranslationUsageRepository(context)
        repository.recordTokenUsage(id, 0)
        repository.recordTokenUsage(id, -1)
        assertTrue(repository.totals.value.isEmpty())
        repository.recordTokenUsage(id, Long.MAX_VALUE - 1)
        repository.recordTokenUsage(id, 10)
        assertEquals(Long.MAX_VALUE, repository.totals.value[id])
    }

    private fun identity(url: String, key: String, model: String = "model") = customTranslationUsageIdentity(
        TranslationApiConfig(TranslationProvider.CUSTOM, key, url, model)
    )
}
