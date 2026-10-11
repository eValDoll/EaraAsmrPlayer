package com.asmr.player.ui.library

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.remote.api.AsmrOneTrackNodeResponse
import com.asmr.player.ui.common.LocalVisibleAppMessages
import com.asmr.player.ui.common.VisibleAppMessage
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import com.asmr.player.util.MessageType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w480dp-h960dp-port-mdpi")
class AsmrOneDownloadDialogTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }).around(compose)

    private val tree = listOf(AsmrOneTrackNodeResponse(
        title = "japaneseasmr.com", type = "folder", children = listOf(
            AsmrOneTrackNodeResponse(title = "01_T0.m4a", type = "audio", mediaDownloadUrl = "https://files.example/01.m4a"),
            AsmrOneTrackNodeResponse(title = "02_T1.m4a", type = "audio", mediaDownloadUrl = "https://files.example/02.m4a"),
        ),
    ))

    @Test fun checkingKeepsTheSheetAndSelectionWhileBlockingRepeatedStart() {
        val preparing = mutableStateOf(false)
        var submitted: Set<String>? = null
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Light) {
                AsmrOneDownloadDialog("RJ01637938", tree, isPreparing = preparing.value,
                    onDismiss = {}, onConfirm = { submitted = it; preparing.value = true })
            }
        }
        compose.onNodeWithText("全不选").performClick()
        compose.onNodeWithText("japaneseasmr.com").performClick()
        compose.onAllNodes(isToggleable()).assertCountEquals(3)
        compose.onAllNodes(isToggleable())[1].performClick()
        compose.onNodeWithText("开始下载").performClick()
        compose.onNodeWithText("选择要下载的文件").assertIsDisplayed()
        compose.onNodeWithText("已选 1 / 可选 2").assertIsDisplayed()
        compose.onNodeWithText("开始下载").assertIsNotEnabled()
        compose.onNodeWithText("全不选").assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(setOf("japaneseasmr.com/01_T0.m4a"), submitted)
            preparing.value = false
        }
        compose.onNodeWithText("开始下载").assertIsEnabled()
        compose.onAllNodes(isToggleable())[1].assertIsOn().assertIsEnabled()
    }

    @Test fun checkFailureFeedbackIsVisibleAboveTheRetainedSheet() {
        val feedback = listOf(VisibleAppMessage(
            id = 1L, key = "source-check-timeout", message = "源文件检查超时，请重试", type = MessageType.Error,
        ))
        compose.setContent {
            AsmrPlayerTheme(mode = ThemeMode.Dark) {
                CompositionLocalProvider(LocalVisibleAppMessages provides feedback) {
                    AsmrOneDownloadDialog("RJ01637938", tree, onDismiss = {}, onConfirm = {})
                }
            }
        }
        compose.onNodeWithText("源文件检查超时，请重试").assertIsDisplayed()
        compose.onNodeWithText("选择要下载的文件").assertIsDisplayed()
        compose.onNodeWithText("开始下载").assertIsEnabled()
    }
}
