package com.asmr.player.ui.common

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asmr.player.ui.createEaraComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActiveStateCollectionTest {
    @get:Rule
    val composeRule = createEaraComposeRule()

    @Test
    fun booleanActivationRetainsStateAndStopsWithLifecycle() {
        assertLifecycleAndActivation(stableActivation = false)
    }

    @Test
    fun stateActivationRetainsStateAndStopsWithLifecycle() {
        assertLifecycleAndActivation(stableActivation = true)
    }

    private fun assertLifecycleAndActivation(stableActivation: Boolean) {
        val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.STARTED
            }
        }
        val source = MutableStateFlow(10)
        val active = mutableStateOf(true)
        lateinit var retained: State<Int>
        var compositionCount = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                retained = if (stableActivation) {
                    source.collectAsStateWhileActive(active)
                } else {
                    source.collectAsStateWhileActive(active.value)
                }
                SideEffect { compositionCount++ }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { source.subscriptionCount.value == 1 }
        val initialState = retained
        val initialCompositionCount = compositionCount
        composeRule.runOnIdle {
            assertEquals(10, retained.value)
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.CREATED
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { source.subscriptionCount.value == 0 }
        composeRule.runOnIdle {
            assertEquals(initialCompositionCount, compositionCount)
            source.value = 20
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertSame(initialState, retained)
            assertEquals(10, retained.value)
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.STARTED
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            source.subscriptionCount.value == 1 && retained.value == 20
        }
        composeRule.runOnIdle {
            assertSame(initialState, retained)
            assertEquals(initialCompositionCount, compositionCount)
            active.value = false
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) { source.subscriptionCount.value == 0 }
        composeRule.runOnIdle {
            source.value = 30
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.CREATED
            lifecycleOwner.lifecycle.currentState = Lifecycle.State.STARTED
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(0, source.subscriptionCount.value)
            assertEquals(20, retained.value)
            assertSame(initialState, retained)
            active.value = true
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            source.subscriptionCount.value == 1 && retained.value == 30
        }
        composeRule.runOnIdle { assertSame(initialState, retained) }
    }
}
