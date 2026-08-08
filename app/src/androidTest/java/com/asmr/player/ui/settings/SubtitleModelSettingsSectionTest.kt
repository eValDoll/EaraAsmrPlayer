package com.asmr.player.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.asmr.player.subtitle.SubtitleModelDownloadSource
import com.asmr.player.subtitle.SubtitleModelInstallStage
import com.asmr.player.subtitle.SubtitleModelInstallationState
import com.asmr.player.subtitle.SubtitleModelOperation
import com.asmr.player.subtitle.SubtitleModelState
import com.asmr.player.subtitle.SubtitleTranscriptionModels
import com.asmr.player.ui.theme.AsmrPlayerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3Api::class)
class SubtitleModelSettingsSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun installedModels_defaultToLightweightAndShowCurrentState() {
        val parakeet = SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8
        val senseVoice = SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8
        setContent(
            SubtitleModelState(
                activeModelId = parakeet.id,
                installations = mapOf(
                    parakeet.id to SubtitleModelInstallationState.Available(
                        SubtitleModelDownloadSource.HuggingFace
                    ),
                    senseVoice.id to SubtitleModelInstallationState.Available(
                        SubtitleModelDownloadSource.HuggingFace
                    )
                )
            )
        )

        composeRule.onNodeWithText("高精度 · 当前").assertExists()
        composeRule.onNodeWithTag("subtitle_model_choice_${senseVoice.id}").assertIsSelected()
        composeRule.onNodeWithText("已安装").assertExists()
        composeRule.onNodeWithText("转录模型可同时安装", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("日语专用", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("安装来源", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("体积更小", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("下载来源", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("subtitle_model_select_${senseVoice.id}").assertIsEnabled()
    }

    @Test
    fun downloadingOneModel_disablesOtherModelDownload() {
        val parakeet = SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8
        val senseVoice = SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8
        setContent(
            SubtitleModelState(
                activeModelId = parakeet.id,
                installations = emptyMap(),
                operation = SubtitleModelOperation.Downloading(
                    modelId = parakeet.id,
                    source = SubtitleModelDownloadSource.HuggingFace,
                    stage = SubtitleModelInstallStage.Model,
                    downloadedBytes = 100L,
                    totalBytes = 1_000L
                )
            )
        )

        composeRule.onNodeWithText(SubtitleModelInstallStage.Model.displayName).assertExists()
        composeRule.onNodeWithText("取消下载").assertExists()
        composeRule.onNodeWithTag("subtitle_model_choice_${senseVoice.id}").performClick()
        composeRule.onNodeWithText("其他模型正在下载").assertExists()
        composeRule.onNodeWithTag("subtitle_model_download_${senseVoice.id}").assertIsNotEnabled()
    }

    @Test
    fun failedSenseVoice_keepsParakeetCurrentAndOffersRetry() {
        val parakeet = SubtitleTranscriptionModels.PARAKEET_TDT_CTC_06B_JA_INT8
        val senseVoice = SubtitleTranscriptionModels.SENSE_VOICE_SMALL_INT8
        setContent(
            SubtitleModelState(
                activeModelId = parakeet.id,
                installations = mapOf(
                    parakeet.id to SubtitleModelInstallationState.Available(null)
                ),
                operation = SubtitleModelOperation.Failed(
                    modelId = senseVoice.id,
                    source = SubtitleModelDownloadSource.HuggingFace,
                    message = "模型校验失败"
                )
            )
        )

        composeRule.onNodeWithText("高精度 · 当前").assertExists()
        composeRule.onNodeWithText("模型校验失败").assertExists()
        composeRule.onNodeWithText(SubtitleModelDownloadSource.GitHub.displayName).assertExists()
        composeRule.onNodeWithText("重新下载").assertIsEnabled()
    }

    private fun setContent(state: SubtitleModelState) {
        composeRule.setContent {
            AsmrPlayerTheme {
                Column {
                    SubtitleModelSettingsSection(
                        state = state,
                        selectedSourceIds = SubtitleTranscriptionModels.all.associate {
                            it.id to SubtitleModelDownloadSource.HuggingFace.id
                        },
                        deviceSupported = true,
                        segmentedButtonColors = SegmentedButtonDefaults.colors(),
                        onSourceSelected = { _, _ -> },
                        onDownload = { _, _ -> },
                        onCancelDownload = {},
                        onSelect = {},
                        onDelete = {},
                        onClearFailure = {}
                    )
                }
            }
        }
    }
}
