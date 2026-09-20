package com.asmr.player.translation

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageTranslationPreferencesAndroidTest {
    @Test fun targetPersistsAndLegacySourceIsIgnoredAfterStoreReopens() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "page-translation-test-${System.nanoTime()}.preferences_pb")
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            val preferences = PageTranslationPreferences(store)
            assertEquals(PageTranslationSettings(), preferences.settings.first())
            store.edit { it[stringPreferencesKey("source")] = "ja" }
            assertEquals(PageTranslationSettings(), preferences.settings.first())
            for (language in PageTranslationLanguages.keys) {
                preferences.setTarget(language)
                assertEquals(language, preferences.settings.first().target)
            }
            preferences.setAutomatic(true)
            assertTrue(runCatching { preferences.setTarget("auto") }.exceptionOrNull() is IllegalArgumentException)
            preferences.setTarget("zh-TW")
            job.cancelAndJoin()
            val reopenedJob = SupervisorJob()
            try {
                val reopenedStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(reopenedJob + Dispatchers.IO), produceFile = { file })
                assertEquals(PageTranslationSettings(true, "zh-TW"), PageTranslationPreferences(reopenedStore).settings.first())
            } finally { reopenedJob.cancelAndJoin() }
        } finally {
            job.cancelAndJoin()
            file.delete()
        }
    }
}
