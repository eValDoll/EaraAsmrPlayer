package com.asmr.player.ui.library

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.ui.settings.SubtitleTranslationSettingsHeader
import com.asmr.player.ui.theme.AsmrPlayerTheme
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
@Config(application = Application::class, sdk = [28])
class SubtitleGenerationFeedbackUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }).around(compose)

    @Test
    fun batchWithoutLocalTargetsStillDeliversClick() {
        var clicks = 0
        showDirectory(hasTargets = false, modelAvailable = true, onBatch = { clicks++ })
        compose.onNodeWithText("批量翻译").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun batchWithoutModelStillDeliversClick() {
        var clicks = 0
        showDirectory(hasTargets = true, modelAvailable = false, onBatch = { clicks++ })
        compose.onNodeWithText("批量翻译").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun readyBatchStillStartsNormally() {
        var clicks = 0
        showDirectory(hasTargets = true, modelAvailable = true, onBatch = { clicks++ })
        compose.onNodeWithText("批量翻译").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun selectedOnlineAudioStillDeliversSelectedFiles() {
        var selectedFiles: List<DirectoryFileItem> = emptyList()
        showDirectory(hasTargets = false, modelAvailable = false, onSelected = { selectedFiles = it })
        compose.onNodeWithText("在线音频").performClick()
        compose.onNodeWithText("翻译选中").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("track.mp3"), selectedFiles.map { it.path }) }
    }

    @Test
    fun settingsRequirementsAreOnlyShownAfterInfoClick() {
        compose.setContent {
            AsmrPlayerTheme {
                val activeTip = remember { mutableStateOf<String?>(null) }
                SubtitleTranslationSettingsHeader(activeTip.value) { key ->
                    activeTip.value = if (activeTip.value == key) null else key
                }
            }
        }
        val explanation = "保存 API Key 不会自动创建任务"
        compose.onNodeWithText(explanation, substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("字幕翻译说明").performClick()
        compose.onNodeWithText(explanation, substring = true).assertExists()
        compose.onNodeWithText("本地音频", substring = true).assertExists()
    }

    private fun showDirectory(
        hasTargets: Boolean,
        modelAvailable: Boolean,
        onBatch: () -> Unit = {},
        onSelected: (List<DirectoryFileItem>) -> Unit = {}
    ) {
        compose.setContent {
            AsmrPlayerTheme {
                DirectoryBrowserPanelV4(
                    panelKey = "test",
                    currentPath = "",
                    breadcrumbs = emptyList(),
                    batchTargets = emptyList(),
                    folders = emptyList(),
                    files = listOf(DirectoryFileItem("track.mp3", "在线音频", TreeFileType.Audio, true, isOnline = true)),
                    onNavigate = {},
                    onAddToFavorites = {},
                    onOpenBatchPlaylistPicker = {},
                    onAddMediaItemsToQueue = {},
                    onGenerateSubtitlesForCurrentDirectory = onBatch,
                    subtitleGenerationForCurrentDirectoryEnabled = hasTargets,
                    onGenerateSubtitlesForSelectedFiles = onSelected,
                    canGenerateSubtitleForSelectedFile = { false },
                    subtitleModelAvailable = modelAvailable,
                    animateIntro = false,
                    folderKeyPrefix = "folder",
                    fileKeyPrefix = "file"
                ) { file, _, _, _, enterSelection, _ ->
                    Text(file.title, modifier = Modifier.clickable(onClick = enterSelection))
                }
            }
        }
    }
}
