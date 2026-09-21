package com.asmr.player.ui.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemAudioEffectsSettingsAndroidTest {
    @Test
    fun opensSystemAudioSettingsFromAppUid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            assertTrue(openSystemAudioEffectsSettings(instrumentation.targetContext))
        }
    }
}
