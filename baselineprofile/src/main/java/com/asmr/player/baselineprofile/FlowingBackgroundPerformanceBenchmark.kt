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
import java.io.ByteArrayOutputStream

/** 显式启动无数据准备的内存场景；不清数据、不强停用户应用、不操作真实播放。 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class, ExperimentalMacrobenchmarkApi::class)
class FlowingBackgroundPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test fun darkSteadyFlow() = measure { SystemClock.sleep(8_000) }

    @Test fun lightSteadyFlow() = measure(light = true) { SystemClock.sleep(8_000) }

    @Test fun themeArtworkAndClarityTransitions() = measure {
        clickControl("主题色背景")
        repeat(2) {
            for (opacity in listOf(0, 50, 100)) {
                clickControl("透明度 $opacity%")
                SystemClock.sleep(600)
                clickControl("切换封面")
                SystemClock.sleep(800)
            }
        }
        clickControl("关闭封面")
        SystemClock.sleep(800)
        clickControl("开启封面")
        SystemClock.sleep(800)
    }

    @Test fun backgroundModeTransitions() = measure {
        repeat(3) {
            clickControl("主题色背景")
            clickControl("透明度 100%")
            SystemClock.sleep(800)
            clickControl("纯色背景")
            SystemClock.sleep(800)
            clickControl("流光背景")
            SystemClock.sleep(800)
        }
    }

    @Test fun solidThemeAndCoverChanges() = measure {
        clickControl("纯色背景")
        check(device.wait(Until.gone(By.text("关闭动态流光")), 5_000)) {
            "纯色背景未就绪：${windowHierarchy()}"
        }
        repeat(3) {
            clickControl("浅色主题", expectedLabel = "深色主题")
            SystemClock.sleep(800)
            clickControl("切换封面")
            SystemClock.sleep(800)
            clickControl("深色主题", expectedLabel = "浅色主题")
            SystemClock.sleep(800)
            clickControl("切换封面")
            SystemClock.sleep(800)
        }
    }

    @Test fun rapidCoverChanges() = measure {
        repeat(12) {
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

    @Test fun flowingDynamicSwitches() = measure {
        recordFlowingState("dynamicOn")
        clickControl("关闭动态流光")
        recordFlowingState("dynamicOff")
        clickControl("开启动态流光")
        recordFlowingState("dynamicResumed")
    }

    @Test fun coverThemeChanges() = measure {
        clickControl("封面主题取色")
        waitForCoverTheme(0)
        repeat(6) { index ->
            clickControl("切换封面")
            waitForCoverTheme(1 - index % 2)
            SystemClock.sleep(600)
        }
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

    private fun MacrobenchmarkScope.clickControl(label: String, expectedLabel: String? = null) {
        checkNotNull(device.wait(Until.findObject(By.text(label)), 5_000)) {
            "未找到流光基准控件：$label；${windowHierarchy()}"
        }.click()
        if (expectedLabel != null) {
            check(device.wait(Until.hasObject(By.text(expectedLabel)), 5_000)) {
                "基准操作未完成：$label → $expectedLabel；${windowHierarchy()}"
            }
        }
    }

    private fun MacrobenchmarkScope.windowHierarchy(): String {
        return ByteArrayOutputStream().use { output ->
            device.dumpWindowHierarchy(output)
            output.toString(Charsets.UTF_8.name())
        }
    }

    private fun MacrobenchmarkScope.waitForCoverTheme(coverIndex: Int) {
        check(device.wait(Until.hasObject(By.desc("benchmark-ready:cover-theme:$coverIndex")), 5_000)) {
            "封面主题色未就绪：$coverIndex"
        }
    }
}
