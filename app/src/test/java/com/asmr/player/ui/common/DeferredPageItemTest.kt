package com.asmr.player.ui.common

import android.app.Application
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.onNodeWithText
import com.asmr.player.ui.createEaraComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DeferredPageItemTest {
    @get:Rule
    val compose = createEaraComposeRule()

    @Test
    fun visiblePageBypassesWarmupAndKeepsItsCompositionWhenHiddenAgain() {
        val visible = mutableStateOf(false)
        val state = DeferredPageContentState(visible)
        var contentInstances = 0
        compose.setContent {
            CompositionLocalProvider(LocalDeferredPageContent provides state) {
                DeferredPageItem(placeholder = { BasicText("placeholder") }) {
                    val instance = remember { ++contentInstances }
                    BasicText("content-$instance")
                }
            }
        }
        compose.onNodeWithText("placeholder").assertExists()
        compose.onNodeWithText("content-1").assertDoesNotExist()

        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText("content-1").assertExists()
        compose.onNodeWithText("placeholder").assertDoesNotExist()

        compose.runOnIdle { visible.value = false }
        compose.onNodeWithText("content-1").assertExists()
        compose.onNodeWithText("placeholder").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, contentInstances) }
    }
}
