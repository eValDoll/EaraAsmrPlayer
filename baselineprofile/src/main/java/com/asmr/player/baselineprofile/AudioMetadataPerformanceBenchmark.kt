package com.asmr.player.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 使用设备现有收藏，不启动会清库的基准数据准备流程。 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class, ExperimentalMacrobenchmarkApi::class)
class AudioMetadataPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun onlinePlaybackKeepsFavoriteRowHeight() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("am force-stop $PackageName")
        device.executeShellCommand("am start -W -n $PackageName/.MainActivity --es start_route playlist_system/favorites")
        check(device.wait(Until.hasObject(By.text("我的收藏")), 5_000))
        check(device.wait(Until.hasObject(By.desc("在线")), 10_000)) {
            "设备中需要至少一条在线音频收藏"
        }
        device.waitForIdle()
        fun UiObject2.audioRow(): UiObject2 {
            var node = this
            while (!node.isClickable) node = checkNotNull(node.parent)
            return node
        }
        val qualitySelector = By.text(Pattern.compile("HQ|SQ"))
        val row = device.findObjects(By.desc("在线"))
            .map { it.audioRow() }
            .first { it.visibleBounds.top > 0 && !it.hasObject(qualitySelector) }
        val title = checkNotNull(row.findObject(By.text(Pattern.compile(".+")))).text
        val beforeHeight = row.visibleBounds.height()
        try {
            row.click()
            assertNotNull("播放后应收到 HQ 或 SQ 信息", row.wait(Until.findObject(qualitySelector), 15_000))
            device.waitForIdle()
            val updatedRow = checkNotNull(device.findObject(By.text(title))).audioRow()
            assertEquals("音质信息到达后列表项高度发生变化", beforeHeight, updatedRow.visibleBounds.height())
        } finally {
            device.pressKeyCode(KeyEvent.KEYCODE_MEDIA_PAUSE)
        }
    }

    @Test
    fun existingFavoritesFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Ignore(),
            startupMode = null,
            iterations = 3,
            setupBlock = {
                startMainActivity(startRoute = "playlist_system/favorites", clearData = false)
                check(device.wait(Until.hasObject(By.text("我的收藏")), 5_000)) {
                    "未进入我的收藏页面"
                }
                check(device.wait(Until.hasObject(By.desc(Pattern.compile("本地|在线"))), 5_000)) {
                    "设备中需要至少一条音频收藏"
                }
                device.waitForIdle()
            },
        ) {
            device.performSlowDragAndFling()
            check(device.hasObject(By.text("我的收藏"))) { "测量期间页面发生了切换" }
        }
    }
}
