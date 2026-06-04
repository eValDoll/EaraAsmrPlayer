package com.asmr.player.data.settings

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val repository = SettingsRepository(context)

    @Before
    fun setUp() = runBlocking {
        context.settingsDataStore.edit { it.clear() }
    }

    @After
    fun tearDown() = runBlocking {
        context.settingsDataStore.edit { it.clear() }
    }

    @Test
    fun loadPlaybackRuntimeSettings_returnsDefaultsWhenUnset() = runBlocking {
        assertEquals(PlaybackRuntimeSettings(), repository.loadPlaybackRuntimeSettings())
    }

    @Test
    fun loadPlaybackRuntimeSettings_returnsStoredValues() = runBlocking {
        context.settingsDataStore.edit { prefs ->
            prefs[SettingsKeys.PAUSE_ON_OUTPUT_DISCONNECT] = false
            prefs[SettingsKeys.RESUME_ON_OUTPUT_CONNECT] = true
            prefs[SettingsKeys.PAUSE_ON_OTHER_AUDIO] = false
            prefs[SettingsKeys.PLAY_FADE_IN_MS] = 1200
            prefs[SettingsKeys.PAUSE_FADE_OUT_MS] = 900
            prefs[SettingsKeys.SFW_HIDE_SYSTEM_CONTROLS] = true
            prefs[SettingsKeys.FLOATING_LYRICS_ENABLED] = true
        }

        assertEquals(
            PlaybackRuntimeSettings(
                pauseOnOutputDisconnect = false,
                resumeOnOutputConnect = true,
                pauseOnOtherAudio = false,
                playFadeInMs = 1200,
                pauseFadeOutMs = 900,
                sfwHideSystemControls = true,
                floatingLyricsEnabled = true
            ),
            repository.loadPlaybackRuntimeSettings()
        )
    }

    @Test
    fun setAppVolumePercent_storesClampedPercent() = runBlocking {
        repository.setAppVolumePercent(47)

        assertEquals(48, repository.appVolumePercentValue())
    }

    @Test
    fun setAppVolumePercent_overwritesStoredValue() = runBlocking {
        repository.setAppVolumePercent(72)
        repository.setAppVolumePercent(32)

        assertEquals(32, repository.appVolumePercentValue())
    }

    @Test
    fun syncAppVolumePercentFromSystem_marksNextMatchingValueAsSystemSync() = runBlocking {
        repository.setAppVolumePercent(72)

        repository.syncAppVolumePercentFromSystem(32)

        assertEquals(32, repository.appVolumePercentValue())
        assertEquals(true, repository.consumePendingSystemVolumeSync(32))
        assertEquals(false, repository.consumePendingSystemVolumeSync(32))
    }

    @Test
    fun setAppVolumePercent_clearsPendingSystemSync() = runBlocking {
        repository.syncAppVolumePercentFromSystem(32)

        repository.setAppVolumePercent(48)

        assertEquals(false, repository.consumePendingSystemVolumeSync(32))
    }

    @Test
    fun equalizerSettings_includeSceneEffectDefaultsAndStoredValues() = runBlocking {
        val defaults = repository.equalizerSettings.first()
        assertFalse(defaults.sceneEffectEnabled)
        assertEquals(SceneEffectPresets.DefaultPresetId, defaults.sceneEffectPresetId)
        assertEquals(SceneEffectPresets.DefaultAmount, defaults.sceneEffectAmount)
        assertEquals(true, defaults.sceneEffectExpanded)

        repository.updateEqualizerSettings(
            defaults.copy(
                sceneEffectEnabled = true,
                sceneEffectPresetId = "tunnel",
                sceneEffectAmount = 73,
                sceneEffectExpanded = false
            )
        )

        val stored = repository.equalizerSettings.first()
        assertEquals(true, stored.sceneEffectEnabled)
        assertEquals("tunnel", stored.sceneEffectPresetId)
        assertEquals(73, stored.sceneEffectAmount)
        assertEquals(false, stored.sceneEffectExpanded)
    }

    private suspend fun SettingsRepository.appVolumePercentValue(): Int {
        return appVolumePercent.first()
    }
}
