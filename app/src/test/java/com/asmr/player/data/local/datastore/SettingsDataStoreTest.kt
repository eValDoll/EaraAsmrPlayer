package com.asmr.player.data.local.datastore

import androidx.datastore.preferences.core.edit
import com.asmr.player.data.settings.BackgroundEffectType
import com.asmr.player.data.settings.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SettingsDataStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private val dataStore = SettingsDataStore(context)

    @Before
    fun setUp() = runBlocking {
        context.settingsDataStore.edit { it.clear() }
    }

    @After
    fun tearDown() = runBlocking {
        context.settingsDataStore.edit { it.clear() }
    }

    @Test
    fun backgroundEffectSettings_returnDefaultsWhenUnset() = runBlocking {
        assertFalse(dataStore.backgroundEffectEnabled.first())
        assertEquals(BackgroundEffectType.Flow, dataStore.backgroundEffectType.first())
    }

    @Test
    fun backgroundEffectSettings_roundTripStoredValues() = runBlocking {
        dataStore.setBackgroundEffectEnabled(true)
        dataStore.setBackgroundEffectType(BackgroundEffectType.Flow)

        assertTrue(dataStore.backgroundEffectEnabled.first())
        assertEquals(BackgroundEffectType.Flow, dataStore.backgroundEffectType.first())
    }
}
