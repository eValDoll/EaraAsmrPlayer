package com.asmr.player.data.settings

import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.local.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class NowPlayingLyricsPersistenceTest {
    @Test
    fun savesModeAndSize() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val settings = NowPlayingLyricsSettings(highlightFontSizeSp = 32f, multilineEnabled = true)
        val original = SettingsDataStore(context).nowPlayingLyricsSettings.first()
        try {
            SettingsDataStore(context).setNowPlayingLyricsSettings(settings)
            assertEquals(settings, SettingsDataStore(context).nowPlayingLyricsSettings.first())
            SettingsDataStore(context).setNowPlayingLyricsSettings(settings.copy(multilineEnabled = false))
            assertEquals(settings.copy(multilineEnabled = false), SettingsDataStore(context).nowPlayingLyricsSettings.first())
        } finally {
            SettingsDataStore(context).setNowPlayingLyricsSettings(original)
        }
    }
}
