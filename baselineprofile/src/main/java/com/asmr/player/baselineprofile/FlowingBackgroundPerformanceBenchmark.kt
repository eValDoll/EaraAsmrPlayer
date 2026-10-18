package com.asmr.player.baselineprofile

import android.content.Intent
import android.os.SystemClock
import android.os.Trace
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 显式启动无数据准备的内存场景；不清数据、不强停用户应用、不操作真实播放。 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class, ExperimentalMacrobenchmarkApi::class)
class FlowingBackgroundPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test fun darkSteadyFlow() = measure { SystemClock.sleep(8_000) }

    @Test fun lightSteadyFlow() = measure(light = true) { SystemClock.sleep(8_000) }

    @Test fun opacityAndCoverTransitions() = measure {
        repeat(2) {
            for (opacity in listOf(0, 50, 100)) {
                clickControl("透明度 $opacity%")
                SystemClock.sleep(600)
                clickControl("切换封面")
                SystemClock.sleep(800)
            }
        }
        clickControl("透明度 0%")
        repeat(3) {
            clickControl("切换封面")
            SystemClock.sleep(120)
        }
        SystemClock.sleep(800)
    }

    @Test fun pauseAndVisibility() = measure {
        clickControl("暂停流光")
        SystemClock.sleep(1_000)
        clickControl("继续流光")
        SystemClock.sleep(1_000)
        clickControl("隐藏背景")
        SystemClock.sleep(1_000)
        clickControl("显示背景")
        SystemClock.sleep(1_000)
        device.pressHome()
        SystemClock.sleep(1_000)
        startFlowingBackgroundActivity(reset = false)
        SystemClock.sleep(1_000)
    }

    @Test fun independentCoverAndDynamicSwitches() = measure {
        clickControl("透明度 100%")
        SystemClock.sleep(600)
        clickControl("关闭封面")
        recordFlowingState("coverOffDynamicOn")
        clickControl("关闭动态流光")
        recordFlowingState("coverOffDynamicOff")
        clickControl("开启动态流光")
        recordFlowingState("coverOffDynamicResumed")
    }

    private fun recordFlowingState(name: String) {
        SystemClock.sleep(600)
        Trace.beginSection("benchmark.flow.$name")
        try {
            SystemClock.sleep(2_000)
        } finally {
            Trace.endSection()
        }
    }

    private fun measure(light: Boolean = false, action: MacrobenchmarkScope.() -> Unit) {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = buildList {
                add(FrameTimingGfxInfoMetric())
                if (InstrumentationRegistry.getArguments().getString("eara.traceFrameTiming") == "true") {
                    add(FrameTimingMetric())
                }
            },
            compilationMode = CompilationMode.Ignore(),
            startupMode = null,
            iterations = 5,
            setupBlock = {
                startFlowingBackgroundActivity()
                if (light) clickControl("浅色主题")
                SystemClock.sleep(1_500)
            },
            measureBlock = action
        )
    }

    private fun MacrobenchmarkScope.startFlowingBackgroundActivity(reset: Boolean = true) {
        startActivityAndWait(Intent().apply {
            setClassName(PackageName, "com.asmr.player.benchmark.BenchmarkHarnessActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (reset) {
                Intent.FLAG_ACTIVITY_CLEAR_TASK
            } else {
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            })
            putExtra("benchmark_scenario", "flowing_background")
        })
        check(device.wait(Until.hasObject(By.desc("benchmark-ready:flowing_background")), 10_000)) {
            "流光背景的内存封面未就绪"
        }
    }

    private fun MacrobenchmarkScope.clickControl(label: String) {
        checkNotNull(device.wait(Until.findObject(By.text(label)), 5_000)) {
            "未找到流光基准控件：$label"
        }.click()
    }
}
