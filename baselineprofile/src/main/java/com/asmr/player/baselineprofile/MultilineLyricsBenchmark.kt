package com.asmr.player.baselineprofile

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class MultilineLyricsBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test fun changingCues() = measure(compact = false)
    @Test fun changingCuesInCompactViewport() = measure(compact = true)

    private fun measure(compact: Boolean) {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Full(),
            iterations = 5,
            setupBlock = {
                // 此场景不需要清除用户数据或创建媒体库样本。
                startActivityAndWait(Intent().apply {
                    setClassName(PackageName, "com.asmr.player.benchmark.BenchmarkHarnessActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("benchmark_scenario", "multiline_lyrics")
                })
                check(device.wait(Until.hasObject(By.text("开始切句")), 10_000))
                if (compact) device.findObject(By.text("紧凑高度")).click()
            }
        ) {
            device.findObject(By.text("开始切句")).click()
            SystemClock.sleep(7600)
            device.findObject(By.text("停止切句")).click()
        }
    }
}
