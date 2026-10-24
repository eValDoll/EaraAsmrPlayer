package com.asmr.player.baselineprofile

import android.os.Build
import android.os.SystemClock
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the installed app's real content and preserves its library, accounts and settings. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class, ExperimentalMacrobenchmarkApi::class)
class PrimaryNavigationPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private var originalIdleTimeoutMs = 0L

    @Before
    fun configureNavigationLookups() {
        val configurator = Configurator.getInstance()
        originalIdleTimeoutMs = configurator.getWaitForIdleTimeout()
        // Explicit transition and content waits cover animations without waiting for global idle.
        configurator.setWaitForIdleTimeout(0L)
    }

    @After
    fun restoreNavigationLookups() {
        Configurator.getInstance().setWaitForIdleTimeout(originalIdleTimeoutMs)
    }

    @Test
    fun firstNonAdjacentClickAfterStartup() = measureNonAdjacentNavigation(warmPages = false)

    @Test
    fun warmedNonAdjacentClicks() = measureNonAdjacentNavigation(warmPages = true)

    @Test
    fun warmedFavoritesRoundTrips() = measureNonAdjacentNavigation(
        warmPages = true,
        targetLabels = listOf("我的收藏"),
    )

    @Test
    fun firstFarSettingsClickAfterStartup() = measureNonAdjacentNavigation(
        warmPages = false,
        targetLabels = listOf("设置"),
    )

    @Test
    fun warmedFarSettingsClicks() = measureNonAdjacentNavigation(
        warmPages = true,
        targetLabels = listOf("设置"),
    )

    @Test
    fun firstFarSearchClickAfterStartup() = measureNonAdjacentNavigation(
        warmPages = false,
        targetLabels = listOf("在线搜索"),
        sourceRoute = "settings",
    )

    @Test
    fun warmedFarSearchClicks() = measureNonAdjacentNavigation(
        warmPages = true,
        targetLabels = listOf("在线搜索"),
        sourceRoute = "settings",
    )

    @Test
    fun firstLocalCollectionClicksAfterStartup() = measureNonAdjacentNavigation(
        warmPages = false,
        targetLabels = LocalCollectionLabels,
    )

    @Test
    fun warmedLocalCollectionClicks() = measureNonAdjacentNavigation(
        warmPages = true,
        targetLabels = LocalCollectionLabels,
    )

    @Test
    fun coldStartupAndIdlePreload() = measureColdStartupAndIdlePreload(StartupIdleWindowMs)

    @Test
    fun coldStartupAndExtendedIdlePreload() = measureColdStartupAndIdlePreload(15_000L)

    private fun measureColdStartupAndIdlePreload(idleWindowMs: Long) {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = frameMetrics() + StartupTimingMetric(),
            compilationMode = CompilationMode.Ignore(),
            startupMode = StartupMode.COLD,
            iterations = Iterations,
            setupBlock = { device.pressHome() },
        ) {
            startMainActivity(startRoute = "library", clearData = false)
            device.waitForPrimaryPageContent("本地库")
            // Measure the idle preload window too, so startup work cannot hide a regression.
            SystemClock.sleep(idleWindowMs)
        }
    }

    private fun measureNonAdjacentNavigation(
        warmPages: Boolean,
        targetLabels: List<String> = listOf("热门收听"),
        sourceRoute: String = "library",
    ) {
        val sourceLabel = when (sourceRoute) {
            "library" -> "本地库"
            "settings" -> "设置"
            else -> error("Unsupported primary navigation source: $sourceRoute")
        }
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = frameMetrics(),
            compilationMode = CompilationMode.Ignore(),
            startupMode = null,
            iterations = Iterations,
            setupBlock = {
                killProcess()
                startMainActivity(startRoute = sourceRoute, clearData = false)
                device.waitForPrimaryPageContent(sourceLabel)
                device.preparePrimaryNavigationSource(sourceLabel)
                // Do not visit the target before the first-click measurement.
                SystemClock.sleep(StartupIdleWindowMs)
                if (warmPages) {
                    targetLabels.forEach { label ->
                        device.clickNonAdjacentPage(label, sourceLabel)
                        device.clickNonAdjacentPage(sourceLabel, label)
                    }
                }
                // Changing the icon group does not navigate or initialize the target page.
                device.showPrimaryNavigationItem(targetLabels.first(), sourceLabel)
            },
        ) {
            targetLabels.forEachIndexed { index, label ->
                // Each configured source and target pair is separated by at least one page.
                device.clickNonAdjacentPage(label, sourceLabel)
                if (warmPages || index < targetLabels.lastIndex) {
                    device.clickNonAdjacentPage(sourceLabel, label)
                }
            }
        }
    }

    private fun frameMetrics() = buildList {
        add(FrameTimingGfxInfoMetric())
        // Match the other real-content benchmarks on devices without named RenderThread traces.
        if (InstrumentationRegistry.getArguments().getString("eara.traceFrameTiming") == "true") {
            add(FrameTimingMetric())
        }
    }

    private companion object {
        const val Iterations = 5
        const val StartupIdleWindowMs = 5_000L
        val LocalCollectionLabels = listOf("我的收藏", "我的列表", "我的分组", "ASMR 看板")
    }
}

private const val PrimaryContentTimeoutMs = 45_000L
private const val PrimaryTransitionSettleMs = 1_000L
private val PrimaryOnlineAlbumCode = Pattern.compile("RJ[0-9]{6,}", Pattern.CASE_INSENSITIVE)

private fun UiDevice.preparePrimaryNavigationSource(sourceLabel: String) {
    repeat(2) {
        if (findPrimaryNavigationIcon(sourceLabel) != null) {
            ensurePrimaryNavigationExpanded(sourceLabel)
            return
        }
        val toggle = findPrimaryNavigationIcon("切换")
            ?: error("Neither source nor group toggle was visible during setup: $sourceLabel")
        // Restore the source icon group without navigating to any other page.
        clickPrimaryNavigationIcon(toggle)
        SystemClock.sleep(PrimaryTransitionSettleMs)
    }
    check(findPrimaryNavigationIcon(sourceLabel) != null) {
        "Source navigation item was not visible after resetting groups: $sourceLabel"
    }
    ensurePrimaryNavigationExpanded(sourceLabel)
}

private fun UiDevice.ensurePrimaryNavigationExpanded(currentLabel: String) {
    val currentPage = waitForPrimaryNavigationIcon(currentLabel)
    // Expands a collapsed pill; an expanded pill returns the current page to its top.
    clickPrimaryNavigationIcon(currentPage)
    SystemClock.sleep(PrimaryTransitionSettleMs)
}

private fun UiDevice.clickNonAdjacentPage(label: String, currentLabel: String) {
    showPrimaryNavigationItem(label, currentLabel)
    clickPrimaryNavigationIcon(waitForPrimaryNavigationIcon(label))
    check(wait(Until.hasObject(By.text(label)), PrimaryContentTimeoutMs)) {
        "Primary navigation did not reach $label"
    }
    // Keep the full pager, shared header, navigation and cover animations in the measurement.
    SystemClock.sleep(PrimaryTransitionSettleMs)
    waitForPrimaryPageContent(label)
}

private fun UiDevice.waitForPrimaryPageContent(label: String) {
    check(wait(Until.hasObject(By.text(label)), PrimaryContentTimeoutMs)) {
        "Primary page header did not become visible: $label"
    }
    val deadline = SystemClock.uptimeMillis() + PrimaryContentTimeoutMs
    fun hasPageContent(): Boolean = when (label) {
        "在线搜索", "热门收听" -> hasObject(By.text(PrimaryOnlineAlbumCode))
        "本地库" -> hasObject(By.scrollable(true)) ||
            hasObject(By.text("还没有扫描到本地专辑"))
        "我的收藏" -> hasObject(By.text("收藏的内容会在这里出现")) || hasObject(By.scrollable(true))
        "我的列表" -> hasObject(By.text("还没有创建列表")) ||
            hasObject(By.text(Pattern.compile("共 \\d+ 首")))
        "我的分组" -> hasObject(By.text("还没有创建分组")) ||
            hasObject(By.text(Pattern.compile("共 \\d+ 张专辑 · \\d+ 首")))
        "ASMR 看板" -> hasObject(By.text("总时长"))
        "设置" -> hasObject(By.text("管理扫描目录、刷新本地内容与同步元数据"))
        else -> error("No content readiness check for primary page: $label")
    }
    while (!hasPageContent()) {
        check(SystemClock.uptimeMillis() < deadline) {
            "Primary page content did not become ready: $label"
        }
        SystemClock.sleep(100)
    }
}

private fun UiDevice.showPrimaryNavigationItem(label: String, currentLabel: String) {
    if (findPrimaryNavigationIcon(currentLabel) != null) {
        // The group toggle does not expand the pill, so always click the current page first.
        ensurePrimaryNavigationExpanded(currentLabel)
    } else {
        // Setup may already have selected the destination's icon group without navigating.
        check(findPrimaryNavigationIcon(label) != null) {
            "Neither current nor target navigation item was visible: $currentLabel -> $label"
        }
    }
    repeat(3) {
        if (findPrimaryNavigationIcon(label) != null) return
        findPrimaryNavigationIcon("切换")?.let { clickPrimaryNavigationIcon(it) }
        // Check the destination before another click so a successful toggle never flips back.
        SystemClock.sleep(PrimaryTransitionSettleMs)
    }
    check(findPrimaryNavigationIcon(label) != null) {
        "Primary navigation item did not become visible after changing groups: $label"
    }
}

private fun UiDevice.waitForPrimaryNavigationIcon(label: String): UiObject2 {
    val deadline = SystemClock.uptimeMillis() + PrimaryContentTimeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
        findPrimaryNavigationIcon(label)?.let { return it }
        SystemClock.sleep(100)
    }
    error("Primary navigation item was not visible: $label")
}

private fun UiDevice.findPrimaryNavigationIcon(label: String): UiObject2? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // Discard cached virtual nodes before resolving the current icon group.
        InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
    }
    // A collapsed pill exposes its current page while the group toggle is clipped away.
    val direct = try {
        findObject(By.pkg(PackageName).desc(label))?.takeIf {
            it.contentDescription == label && !it.visibleBounds.isEmpty
        }
    } catch (_: StaleObjectException) {
        null
    }
    if (direct != null) return direct
    return try {
        // The toggle stays in the same slot across both groups. Ascend to the five-slot rail.
        var group = findObject(By.pkg(PackageName).desc("切换")) ?: return null
        while (group.children.size <= 1) {
            group = group.parent ?: return null
        }
        // UiObject2 property getters refresh each node; By.desc alone can retain old icon names.
        group.findRefreshedNavigationIcon(label)
    } catch (_: StaleObjectException) {
        null
    }
}

private fun UiObject2.findRefreshedNavigationIcon(label: String): UiObject2? {
    if (contentDescription == label && !visibleBounds.isEmpty) return this
    for (child in children) {
        child.findRefreshedNavigationIcon(label)?.let { return it }
    }
    return null
}

private fun UiDevice.clickPrimaryNavigationIcon(icon: UiObject2) {
    var action = icon
    while (!action.isClickable) {
        action = checkNotNull(action.parent) { "Navigation icon has no clickable ancestor" }
    }
    check(action.isEnabled) { "Navigation action is disabled" }
    val bounds = action.visibleBounds
    check(!bounds.isEmpty) { "Navigation action has no visible bounds" }
    // Use the same injection path as the verified manual device navigation.
    executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}")
}
