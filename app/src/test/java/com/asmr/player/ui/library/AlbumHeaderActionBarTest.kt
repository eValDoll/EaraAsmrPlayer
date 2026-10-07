package com.asmr.player.ui.library

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.asmr.player.data.remote.dlsite.DlsiteLanguageEdition
import com.asmr.player.ui.theme.AsmrPlayerTheme
import com.asmr.player.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w1280dp-h800dp-land-mdpi")
class AlbumHeaderActionBarTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    // Release 不包含 ui-test-manifest，使用仅供测试的 Activity 注册。
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(context.packageManager).addActivityIfNotPresent(
                ComponentName(context, ComponentActivity::class.java)
            )
        }
    }).around(compose)

    @Test
    fun landscapeOnlineActions_remainVisibleAndClickableBesideLanguageAndWebsite() {
        var downloads = 0
        var saves = 0
        showActionBar(
            width = 736.dp,
            onDownload = { downloads++ },
            onSave = { saves++ }
        )

        assertActionsFit(width = 736.dp, secondaryLabel = "保存")
        compose.onNodeWithText("打开网页").assertWidthIsEqualTo(104.dp)
        compose.onNodeWithText("日语").assertIsDisplayed()
        compose.onNodeWithText("下载").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals(1, downloads)
            assertEquals(1, saves)
        }
    }

    @Test
    fun landscapeLocalActions_fitBesideGroupAndWebsiteInLightTheme() {
        showActionBar(width = 480.dp, showGroup = true, mode = ThemeMode.Light)

        assertActionsFit(width = 480.dp, secondaryLabel = "保存")
        compose.onNodeWithText("分组").assertIsDisplayed()
        compose.onNodeWithText("打开网页").assertWidthIsEqualTo(104.dp)
    }

    @Test
    fun landscapePaidActions_keepLosslessDownloadVisibleAndClickable() {
        var losslessDownloads = 0
        showActionBar(
            width = 640.dp,
            groupState = AlbumHeaderButtonGroupState.Lossless,
            onLosslessDownload = { losslessDownloads++ }
        )

        assertActionsFit(width = 640.dp, secondaryLabel = "无损下载")
        compose.onNodeWithText("无损下载").performClick()
        compose.runOnIdle { assertEquals(1, losslessDownloads) }
    }

    @Test
    fun landscapeLoadingActions_remainVisibleWhileDisabled() {
        showActionBar(width = 480.dp, enabled = false, mode = ThemeMode.Light)

        assertActionsFit(width = 480.dp, secondaryLabel = "保存")
        compose.onNodeWithText("下载").assertIsNotEnabled()
        compose.onNodeWithText("保存").assertIsNotEnabled()
    }

    @Test
    fun portraitActions_keepExistingDownloadAndSaveBehavior() {
        showActionBar(width = 400.dp, floating = false)

        assertActionsFit(width = 400.dp, secondaryLabel = "保存")
    }

    private fun showActionBar(
        width: Dp,
        floating: Boolean = true,
        showGroup: Boolean = false,
        groupState: AlbumHeaderButtonGroupState = AlbumHeaderButtonGroupState.Save,
        enabled: Boolean = true,
        mode: ThemeMode = ThemeMode.Dark,
        onDownload: () -> Unit = {},
        onSave: () -> Unit = {},
        onLosslessDownload: () -> Unit = {}
    ) {
        compose.setContent {
            AsmrPlayerTheme(mode = mode) {
                Box(modifier = Modifier.width(width)) {
                    AlbumHeaderActionBar(
                        groupState = groupState,
                        onDownloadClick = onDownload,
                        onSaveClick = onSave,
                        onLosslessDownloadClick = onLosslessDownload,
                        downloadEnabled = enabled,
                        saveEnabled = enabled,
                        losslessDownloadEnabled = enabled,
                        showGroupAction = showGroup,
                        groupEnabled = true,
                        onGroupClick = {},
                        dlsiteEditions = if (showGroup) emptyList() else listOf(
                            DlsiteLanguageEdition("RJ123456", "JPN", "日本語", 1),
                            DlsiteLanguageEdition("RJ123457", "CHI_HANS", "简体中文", 2)
                        ),
                        dlsiteSelectedLang = "JPN",
                        onDlsiteLangSelected = {},
                        dlsiteUrl = "https://www.dlsite.com/",
                        asmrOneUrl = "https://asmr.one/",
                        japaneseAsmrUrl = "https://japaneseasmr.com/",
                        availableWidth = width,
                        floating = floating
                    )
                }
            }
        }
    }

    private fun assertActionsFit(width: Dp, secondaryLabel: String) {
        val download = compose.onNodeWithText("下载").assertIsDisplayed().getUnclippedBoundsInRoot()
        val secondary = compose.onNodeWithText(secondaryLabel).assertIsDisplayed().getUnclippedBoundsInRoot()
        val website = compose.onNodeWithText("打开网页").assertIsDisplayed().getUnclippedBoundsInRoot()

        assertTrue("下载按钮必须具有可点击宽度：$download", download.right - download.left >= 60.dp)
        assertTrue("次要操作必须具有可点击宽度：$secondary", secondary.right - secondary.left >= 60.dp)
        assertTrue("按钮组不能覆盖右侧菜单", secondary.right <= website.left)
        assertTrue("网页入口必须位于操作栏内", website.right <= width)
    }
}
