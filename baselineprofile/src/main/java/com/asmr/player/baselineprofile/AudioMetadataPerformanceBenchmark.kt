package com.asmr.player.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
