package com.asmr.player.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.asmr.player.cache.AppCacheLimits
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        dataStore = InMemoryPreferencesDataStore()
        repository = SettingsRepository(dataStore)
    }

    @Test
    fun loadPlaybackRuntimeSettings_returnsDefaultsWhenUnset() = runBlocking {
        assertEquals(PlaybackRuntimeSettings(), repository.loadPlaybackRuntimeSettings())
    }

    @Test
    fun loadPlaybackRuntimeSettings_returnsStoredValues() = runBlocking {
        dataStore.edit { prefs ->
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
    fun appCacheMaxSizeMb_defaultsTo150() = runBlocking {
        assertEquals(AppCacheLimits.DefaultSizeMb, repository.appCacheMaxSizeMb.first())
    }

    @Test
    fun setAppCacheMaxSizeMb_clampsToSupportedRange() = runBlocking {
        repository.setAppCacheMaxSizeMb(1)
        assertEquals(AppCacheLimits.MinSizeMb, repository.appCacheMaxSizeMb.first())

        repository.setAppCacheMaxSizeMb(2_000)
        assertEquals(AppCacheLimits.MaxSizeMb, repository.appCacheMaxSizeMb.first())
    }

    @Test
    fun clearSleepTimer_skipsDataStoreWriteWhenAlreadyCleared() = runBlocking {
        val inMemoryDataStore = dataStore as InMemoryPreferencesDataStore

        repository.clearSleepTimer()

        assertEquals(0, inMemoryDataStore.updateCount)

        repository.setSleepTimerEndAtMs(1_000L)
        repository.clearSleepTimer()

        assertEquals(0L, repository.sleepTimerEndAtMs.first())
        assertEquals(2, inMemoryDataStore.updateCount)
    }

    @Test
    fun deepSeekTranslationSettings_defaultToThinkingDisabled() = runBlocking {
        val defaults = repository.loadDeepSeekTranslationSettings()
        assertFalse(defaults.thinkingEnabled)
        assertEquals(DeepSeekTranslationSettings(), defaults)
        assertEquals(
            listOf("low", "high", "max"),
            DeepSeekReasoningEffort.entries.map { it.wireValue }
        )
    }

    @Test
    fun deepSeekTranslationSettings_persistToggleAndMaxEffort() = runBlocking {
        repository.setDeepSeekThinkingEnabled(false)
        repository.setDeepSeekReasoningEffort(DeepSeekReasoningEffort.MAX)

        assertEquals(
            DeepSeekTranslationSettings(
                thinkingEnabled = false,
                reasoningEffort = DeepSeekReasoningEffort.MAX
            ),
            repository.deepSeekTranslationSettings.first()
        )
    }

    @Test
    fun deepSeekTranslationSettings_persistLowEffort() = runBlocking {
        repository.setDeepSeekReasoningEffort(DeepSeekReasoningEffort.LOW)

        assertEquals(
            DeepSeekReasoningEffort.LOW,
            repository.loadDeepSeekTranslationSettings().reasoningEffort
        )
    }

    @Test
    fun deepSeekTranslationSettings_fallBackToHighForUnknownEffort() = runBlocking {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.DEEPSEEK_REASONING_EFFORT] = "unknown"
        }

        assertEquals(
            DeepSeekReasoningEffort.HIGH,
            repository.loadDeepSeekTranslationSettings().reasoningEffort
        )
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

    @Test
    fun searchBlockedKeywords_defaultToEmptyList() = runBlocking {
        assertEquals(emptyList<String>(), repository.searchBlockedKeywords.first())
    }

    @Test
    fun addSearchBlockedKeyword_trimsIgnoresBlankAndDeduplicatesIgnoringCase() = runBlocking {
        repository.addSearchBlockedKeyword("  言语侵犯  ")
        repository.addSearchBlockedKeyword("")
        repository.addSearchBlockedKeyword("言语侵犯")
        repository.addSearchBlockedKeyword("VOICE")
        repository.addSearchBlockedKeyword("voice")

        assertEquals(listOf("言语侵犯", "VOICE"), repository.searchBlockedKeywords.first())
    }

    @Test
    fun removeSearchBlockedKeyword_removesIgnoringCase() = runBlocking {
        repository.addSearchBlockedKeyword("言语侵犯")
        repository.addSearchBlockedKeyword("VOICE")

        repository.removeSearchBlockedKeyword("voice")

        assertEquals(listOf("言语侵犯"), repository.searchBlockedKeywords.first())
    }

    private suspend fun SettingsRepository.appVolumePercentValue(): Int {
        return appVolumePercent.first()
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        private val updateMutex = Mutex()
        var updateCount: Int = 0
            private set

        override val data: StateFlow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = updateMutex.withLock {
            updateCount += 1
            transform(state.value).also { state.value = it }
        }
    }
}
