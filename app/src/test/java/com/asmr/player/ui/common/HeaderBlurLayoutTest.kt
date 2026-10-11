package com.asmr.player.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeaderBlurLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun blurDoesNotReserveExtraHeaderSpace() {
        val enabled = mutableStateOf(false)
        var measuredSize = IntSize.Zero
        composeRule.setContent {
            AsmrPlayerTheme {
                EaraTopBarContainer(
                    blurEnabled = enabled.value,
                    modifier = Modifier.onSizeChanged { measuredSize = it },
                ) {
                    Box(Modifier.height(48.dp))
                }
            }
        }
        var originalSize = IntSize.Zero
        composeRule.runOnIdle {
            originalSize = measuredSize
            enabled.value = true
        }
        composeRule.runOnIdle { assertEquals(originalSize, measuredSize) }
        composeRule.runOnIdle { enabled.value = false }
        composeRule.runOnIdle { assertEquals(originalSize, measuredSize) }
    }
}
