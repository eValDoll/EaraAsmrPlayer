package com.asmr.player.baselineprofile

import android.os.Bundle
import android.os.SystemClock
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
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real online screens, preserving the installed app's library, accounts and settings. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class, ExperimentalMacrobenchmarkApi::class)
class OnlineNavigationPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun searchEntry() = measureOnlineEntry("search")

    @Test
    fun hotListeningEntry() = measureOnlineEntry("hot_listening")

    @Test
    fun searchScroll() = measureOnlineScreen("search") {
        device.performSlowDragAndFling()
    }

    @Test
    fun hotListeningScroll() = measureOnlineScreen("hot_listening") {
        device.performSlowDragAndFling()
    }

    @Test
    fun searchAlbumTransitions() = measureOnlineScreen("search") {
        device.openOnlineAlbumAndReturn()
    }

    @Test
    fun hotListeningAlbumTransitions() = measureOnlineScreen("hot_listening") {
        device.openOnlineAlbumAndReturn()
    }

    @Test
    fun onlinePrimaryNavigation() = measureOnlineScreen("search") {
        repeat(3) {
            device.openOnlineTab("热门收听")
            device.openOnlineTab("在线搜索")
        }
    }

    private fun measureOnlineEntry(route: String) = measureOnlineScreen(route, prepareScreen = false) {
        startMainActivity(startRoute = route, clearData = false)
        device.waitForOnlineAlbum()
        // Include initial composition, image delivery and the full cover fade.
        SystemClock.sleep(2_000)
    }

    private fun measureOnlineScreen(
        route: String,
        prepareScreen: Boolean = true,
        action: MacrobenchmarkScope.() -> Unit,
    ) {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = buildList {
                add(FrameTimingGfxInfoMetric())
                // Some device traces omit RenderThread's name, which FrameTimingMetric requires.
                // Keep precise trace metrics opt-in; never silently replace a failed measurement.
                if (InstrumentationRegistry.getArguments().getString("eara.traceFrameTiming") == "true") {
                    add(FrameTimingMetric())
                }
            },
            // Measure the installed Release/benchmark build without resetting compilation or data.
            // Run both sides of a comparison with the same compilation and cache conditions.
            compilationMode = CompilationMode.Ignore(),
            startupMode = null,
            iterations = 5,
            setupBlock = {
                killProcess()
                if (prepareScreen || route == "search") {
                    startMainActivity(startRoute = route, clearData = false)
                    device.waitForOnlineAlbum()
                    if (route == "search") {
                        device.ensureFirstSearchPage()
                    }
                    device.recordOnlineFixture(route)
                    if (prepareScreen) {
                        // Include navigation between loaded screens rather than an empty loading state.
                        if (route == "search") {
                            device.openOnlineTab("热门收听")
                            device.openOnlineTab("在线搜索")
                        }
                        SystemClock.sleep(1_000)
                    } else {
                        killProcess()
                    }
                }
            },
            measureBlock = action,
        )
    }
}

private val OnlineAlbumCode = Pattern.compile("RJ[0-9]{6,}", Pattern.CASE_INSENSITIVE)
private const val OnlineContentTimeoutMs = 45_000L

private fun UiDevice.recordOnlineFixture(route: String) {
    val codes = findObjects(By.text(OnlineAlbumCode)).map { it.text }.distinct().sorted()
    check(codes.isNotEmpty()) { "Cannot identify the online measurement data" }
    InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
        putString("eara.onlineFixture", "$route:${codes.joinToString(",")}")
    })
}

private fun UiDevice.ensureFirstSearchPage() {
    val firstPage = By.text("第 1 页")
    if (!hasObject(firstPage)) {
        val reset = wait(Until.findObject(By.desc("回到第一页").enabled(true)), OnlineContentTimeoutMs)
            ?: error("Cannot reset the saved search page for a comparable measurement")
        reset.click()
        check(wait(Until.hasObject(firstPage), OnlineContentTimeoutMs)) {
            "Search did not return to its first page"
        }
        waitForOnlineAlbum()
    }
}

private fun UiDevice.waitForOnlineAlbum(): UiObject2 =
    wait(Until.findObject(By.text(OnlineAlbumCode)), OnlineContentTimeoutMs)
        ?: error("Online screen has no loaded album; refusing to benchmark an empty/loading page")

private fun UiDevice.openOnlineTab(label: String) {
    val tab = wait(Until.findObject(By.desc(label)), OnlineContentTimeoutMs)
        ?: error("Missing online navigation tab: $label")
    tab.click()
    check(wait(Until.hasObject(By.text(label)), OnlineContentTimeoutMs)) {
        "Online navigation did not reach $label"
    }
    // Preserve the full transition, including its final frame and subsequent data delivery.
    SystemClock.sleep(1_000)
    waitForOnlineAlbum()
}

private fun UiDevice.openOnlineAlbumAndReturn() {
    val code = waitForOnlineAlbum()
    var card: UiObject2? = code
    while (card != null) {
        val bounds = card.visibleBounds
        if (card.isClickable && bounds.width() > displayWidth / 4 && bounds.height() > displayWidth / 5) {
            // The cover occupies the upper-left square in both list and grid layouts.
            val inset = minOf(bounds.width(), bounds.height()) / 3
            click(bounds.left + inset, bounds.top + inset)
            check(wait(Until.gone(By.desc("任务管理")), OnlineContentTimeoutMs)) {
                "Album detail did not open"
            }
            SystemClock.sleep(1_500)
            pressBack()
            check(wait(Until.hasObject(By.desc("任务管理")), OnlineContentTimeoutMs)) {
                "Online list did not return after closing album detail"
            }
            waitForOnlineAlbum()
            SystemClock.sleep(1_000)
            return
        }
        card = card.parent
    }
    error("Loaded album has no clickable list/grid card")
}
