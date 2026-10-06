package com.asmr.player.subtitle

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DeepSeekModelStoreTest {
    @Test
    fun selection_isPersistedUsedByConfigurationAndDoesNotChangeProvider() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        fun open() = TranslationApiStore(context, "deepseek_model_selection_test", "test_unused_key")
        val store = open()
        assertEquals(DEEPSEEK_SUBTITLE_MODEL, store.readSettings().deepSeekModel)
        store.saveDeepSeekModel(" deepseek-v4-pro ")
        assertEquals("deepseek-v4-pro", open().readSettings().deepSeekModel)
        assertEquals("deepseek-v4-pro", open().readConfiguration().model)
        assertEquals(TranslationProvider.DEEPSEEK, store.readSettings().provider)
        assertEquals("", store.readDeepSeekKey())
        assertThrows(IllegalArgumentException::class.java) { store.saveDeepSeekModel(" ") }
        assertEquals("deepseek-v4-pro", store.readConfiguration().model)
    }
}
