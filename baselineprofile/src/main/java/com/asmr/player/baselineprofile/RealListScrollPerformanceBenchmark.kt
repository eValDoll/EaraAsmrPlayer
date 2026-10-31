package com.asmr.player.baselineprofile

import android.app.UiAutomation
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import java.io.File
import java.util.regex.Pattern
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

/** Measures installed real lists without clearing data, seeding fixtures or resetting compilation. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class, ExperimentalMacrobenchmarkApi::class)
class RealListScrollPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun libraryScroll() = measureList("library")

    @Test
    fun searchScroll() = measureList("search")

    @Test
    fun hotListeningScroll() = measureList("hot_listening")

    private fun measureList(route: String) {
        val arguments = InstrumentationRegistry.getArguments()
        val reuseLoadedHotList = route == "hot_listening" &&
            arguments.getString("eara.hotWarmScroll") == "true"
        var scrollBounds: Rect? = null
        var initialHotPrefix: List<String>? = null
        val configurator = Configurator.getInstance()
        val previousIdleTimeout = configurator.waitForIdleTimeout
        // Readiness is polled explicitly below. Continuous player/scroll animations must
        // not add a ten-second accessibility idle wait to every metadata query.
        configurator.waitForIdleTimeout = 0L
        try {
            benchmarkRule.measureRepeated(
                packageName = PackageName,
                metrics = buildList {
                    add(FrameTimingGfxInfoMetric())
                    if (arguments.getString("eara.traceFrameTiming") == "true") {
                        add(FrameTimingMetric())
                    }
                },
                compilationMode = CompilationMode.Ignore(),
                startupMode = null,
                iterations = arguments.getString("eara.scrollIterations")?.toIntOrNull()
                    ?.coerceIn(1, 20) ?: 5,
                setupBlock = {
                    check(!realListFocusLost) { "Previous gesture lost the target window; ending this capture" }
                    val preparation = RealListPreparation()
                    val initialPrefix = initialHotPrefix
                    val loaded = if (reuseLoadedHotList && initialPrefix != null) {
                        // Keep the loaded ViewModel and the user's player layout. Clicking
                        // a collapsed navigation tab would persist a player layout change.
                        device.returnHotListToInitialPrefix(initialPrefix, preparation)
                    } else {
                        killProcess()
                        startMainActivity(
                            startRoute = if (route == "hot_listening") "settings" else route,
                            clearData = false,
                        )
                        if (route == "hot_listening") {
                            device.prepareHotListThroughPrimaryNavigation(preparation)
                        }
                        device.waitForRealList(route, preparation)
                    }
                    // Finish initial cover delivery and the window's opening transition outside measurement.
                    SystemClock.sleep(1_500)
                    val fixture = if (arguments.getString("eara.requireFullscreenList") == "true") {
                        device.waitForRealList(route, preparation).also {
                            val bounds = it.bounds
                            check(bounds.left <= 1 && bounds.top <= 1 && bounds.right >= device.displayWidth - 1 &&
                                bounds.bottom >= device.displayHeight - 1) { "Expected a full-screen list; got $bounds" }
                        }
                    } else loaded
                    if (reuseLoadedHotList && initialPrefix == null) {
                        initialHotPrefix = fixture.identities.take(3)
                    }
                    if (route == "library") {
                        assumeTrue(
                            "Library scrolling requires at least two distinct visible album cards",
                            fixture.identities.size > 1,
                        )
                    }
                    scrollBounds = fixture.bounds
                    device.recordListFixture(route, fixture)
                    if (reuseLoadedHotList) {
                        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                            putString("eara.hotWarmScroll", "true:${initialHotPrefix.orEmpty().joinToString("|")}")
                        })
                    }
                },
            ) {
                performRealListScrollProfile(checkNotNull(scrollBounds))
                recordRefreshRate(route)
            }
        } finally {
            configurator.waitForIdleTimeout = previousIdleTimeout
        }
    }
}

/** Drives an already loaded list for external sampling without managing its process. */
@RunWith(AndroidJUnit4::class)
class ExistingListScrollDriver {
    @Test
    fun scrollLoadedList() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val route = InstrumentationRegistry.getArguments()
            .getString("eara.existingScrollDriver").orEmpty()
        assumeTrue("Requires an explicitly selected, already loaded list", route in setOf("search", "hot_listening"))
        val device = UiDevice.getInstance(instrumentation)
        val configurator = Configurator.getInstance()
        val previousIdleTimeout = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = 0L
        try {
            val fixture = device.waitForRealList(route, RealListPreparation(allowHotRetry = false))
            val bounds = fixture.bounds
            device.recordListFixture(route, fixture)
            instrumentation.sendStatus(2, Bundle().apply {
                putString("eara.existingScrollDriverPrefix", fixture.identities.take(3).joinToString("|"))
                putString("eara.existingScrollDriverReady", route)
            })
            SystemClock.sleep(3_000L)
            instrumentation.sendStatus(2, Bundle().apply {
                putString("eara.existingScrollDriverPhase", "$route:scrolling")
            })
            performRealListScrollProfile(bounds)
            recordRefreshRate(route)
            instrumentation.sendStatus(2, Bundle().apply {
                putString("eara.existingScrollDriverPhase", "$route:complete")
            })
        } finally {
            configurator.waitForIdleTimeout = previousIdleTimeout
        }
    }
}

/** Captures an already loaded real Hot list without restarting or reconfiguring the target app. */
@RunWith(AndroidJUnit4::class)
class ExternalWarmHotScrollCapture {
    @Test
    fun captureLoadedHotList() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "Requires an explicitly prepared, already loaded Hot page",
            InstrumentationRegistry.getArguments().getString("eara.externalWarmHotCapture") == "true",
        )
        assumeTrue("Warm capture requires platform shell stderr capture", Build.VERSION.SDK_INT >= 34)
        val device = UiDevice.getInstance(instrumentation)
        val configurator = Configurator.getInstance()
        val previousIdleTimeout = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = 0L
        val output = File(
            checkNotNull(instrumentation.context.externalMediaDirs.firstOrNull()),
            "warm-hot-${SystemClock.uptimeMillis()}",
        )
        try {
            check(output.mkdirs()) { "Cannot create the warm Hot output directory" }
            instrumentation.sendStatus(2, Bundle().apply {
                putString("eara.externalWarmHotOutputRoot", output.absolutePath)
            })
            val prefix = device.waitForRealList("hot_listening", RealListPreparation(allowHotRetry = false))
                .identities.take(3)
            check(prefix.size == 3) { "Warm Hot capture requires three visible album identities" }
            val config = File(output, "capture.textproto").apply { writeText(WarmHotTraceConfig, Charsets.UTF_8) }
            instrumentation.sendStatus(2, Bundle().apply {
                putString("eara.externalWarmHotConfig", config.absolutePath)
            })
            repeat(3) { index ->
                val iteration = File(output, "iter${index.toString().padStart(3, '0')}").apply { check(mkdirs()) }
                val before = device.returnHotListToInitialPrefix(prefix, RealListPreparation(allowHotRetry = false))
                val scrollBounds = before.bounds
                device.writeWarmHotFixture(iteration, "before", before)
                SystemClock.sleep(1_500L)
                val context = JSONObject().put("before", device.warmHotContext())
                device.executeShellCommand("dumpsys gfxinfo $PackageName reset")
                val remoteTrace = "/data/misc/perfetto-traces/eara-${output.name}-${iteration.name}.perfetto-trace"
                val trace = File(iteration, "trace.perfetto-trace")
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("eara.externalWarmHotIteration", "$index:${iteration.absolutePath}")
                    putString("eara.externalWarmHotRemoteTrace", remoteTrace)
                })
                val startOutput = File(iteration, "perfetto-start.txt")
                val startError = File(iteration, "perfetto-start-stderr.txt")
                instrumentation.uiAutomation.captureWarmShellOutput(
                    "perfetto --txt -c ${config.absolutePath} -o $remoteTrace --background-wait",
                    startOutput,
                    startError,
                )
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("eara.externalWarmHotPerfettoStartBytes", "stdout=${startOutput.length()}:stderr=${startError.length()}")
                    putString("eara.externalWarmHotPerfettoStderr", startError.absolutePath)
                })
                val startedAt = SystemClock.uptimeMillis()
                try {
                    performRealListScrollProfile(scrollBounds)
                    recordRefreshRate("hot_listening")
                } finally {
                    val remaining = startedAt + 22_500L - SystemClock.uptimeMillis()
                    if (remaining > 0L) SystemClock.sleep(remaining)
                    val copyError = File(iteration, "perfetto-copy-stderr.txt")
                    instrumentation.uiAutomation.captureWarmShellOutput("cat $remoteTrace", trace, copyError)
                    File(iteration, "gfxinfo.txt").writeText(
                        device.executeShellCommand("dumpsys gfxinfo $PackageName framestats"), Charsets.UTF_8,
                    )
                    context.put("after", device.warmHotContext())
                    File(iteration, "context.json").writeText(context.toString(2), Charsets.UTF_8)
                    instrumentation.sendStatus(2, Bundle().apply {
                        putString("eara.externalWarmHotTraceBytes", trace.length().toString())
                        putString("eara.externalWarmHotCopyStderr", copyError.absolutePath)
                    })
                    check(trace.length() > 1_024L) {
                        "No completed Perfetto trace: ${trace.length()} bytes; " +
                            "start stderr=${startError.absolutePath}; copy stderr=${copyError.absolutePath}"
                    }
                }
                val after = device.waitForRealList("hot_listening", RealListPreparation(allowHotRetry = false))
                device.writeWarmHotFixture(iteration, "after", after)
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("additionalTestOutputFile_warmHot_${iteration.name}.perfetto-trace", trace.absolutePath)
                    putString("eara.externalWarmHotArtifacts", iteration.absolutePath)
                    putString("eara.externalWarmHotGfx", File(iteration, "gfxinfo.txt").absolutePath)
                    putString("eara.externalWarmHotContext", File(iteration, "context.json").absolutePath)
                })
            }
        } finally {
            configurator.waitForIdleTimeout = previousIdleTimeout
        }
    }
}

@android.annotation.TargetApi(34)
private fun UiAutomation.captureWarmShellOutput(command: String, stdout: File, stderr: File) {
    // UiAutomationConnection uses Runtime.exec(String), which splits whitespace
    // without shell quote processing. All command paths here are generated Unix paths.
    val descriptors = executeShellCommandRwe(command)
    descriptors[1].close()
    var stderrFailure: Throwable? = null
    val errorReader = Thread({
        runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(descriptors[2]).use { input ->
                stderr.outputStream().use { stream -> input.copyTo(stream) }
            }
        }.onFailure { stderrFailure = it }
    }, "warm-hot-shell-stderr")
    errorReader.start()
    try {
        ParcelFileDescriptor.AutoCloseInputStream(descriptors[0]).use { input ->
            stdout.outputStream().use { stream -> input.copyTo(stream) }
        }
    } finally {
        try {
            errorReader.join()
        } finally {
            descriptors.forEach { runCatching { it.close() } }
        }
    }
    stderrFailure?.let { throw IllegalStateException("Cannot save shell stderr to ${stderr.absolutePath}", it) }
}

private fun UiDevice.warmHotContext(): JSONObject = JSONObject().apply {
    listOf("battery", "display", "thermalservice").forEach { name ->
        put(name, executeShellCommand("dumpsys $name"))
    }
}

private fun UiDevice.writeWarmHotFixture(directory: File, phase: String, fixture: RealListFixture) {
    InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
        putString("eara.externalWarmHotPhase", "${directory.name}:$phase")
        putString("eara.externalWarmHotIdentities", fixture.identities.joinToString("|"))
    })
    recordListFixture("hot_listening", fixture)
    File(directory, "$phase-identities.txt").writeText(fixture.identities.joinToString("\n"), Charsets.UTF_8)
    dumpWindowHierarchy(File(directory, "$phase.xml"))
    check(takeScreenshot(File(directory, "$phase.png"))) { "Cannot capture the $phase Hot screenshot" }
}

private val WarmHotTraceConfig = """
    buffers { size_kb: 65536 fill_policy: RING_BUFFER }
    write_into_file: true
    file_write_period_ms: 1000
    duration_ms: 22000
    data_sources { config { name: "linux.ftrace" ftrace_config {
      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_waking"
      ftrace_events: "power/cpu_frequency"
      atrace_categories: "gfx"
      atrace_categories: "view"
      atrace_categories: "input"
      atrace_categories: "dalvik"
      atrace_categories: "wm"
      atrace_apps: "com.asmr.player"
    } } }
    data_sources { config { name: "linux.process_stats" process_stats_config { scan_all_processes_on_start: true } } }
    data_sources { config { name: "android.surfaceflinger.frametimeline" } }
""".trimIndent()

private data class RealListFixture(
    val bounds: Rect,
    val identities: List<String>,
    val texts: List<String>,
)

// 在 readiness 的 StaleObjectException 重试范围内读取；后续记录和手势只使用快照。
private fun snapshotRealListFixture(list: UiObject2, identities: List<String>): RealListFixture {
    val bounds = Rect(list.visibleBounds)
    val texts = list.findObjects(By.text(NonEmptyListText))
        .mapNotNull { it.text?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
    return RealListFixture(bounds, identities, texts)
}

private class RealListPreparation(val allowHotRetry: Boolean = true) {
    private val readyTimeout = InstrumentationRegistry.getArguments()
        .getString("eara.listReadyTimeoutMs")?.toLongOrNull()?.coerceIn(5_000L, 180_000L)
        ?: 90_000L
    val deadlineMillis = SystemClock.uptimeMillis() + readyTimeout
    var hotRetryAttempted = false
}

private fun UiDevice.prepareHotListThroughPrimaryNavigation(preparation: RealListPreparation) {
    val pages = listOf("设置", "ASMR 看板", "我的分组", "我的列表", "我的收藏", "热门收听")
    waitForBootstrapHeader(
        expectedHeader = pages.first(),
        preparation = preparation,
        requiredContent = "管理扫描目录、刷新本地内容与同步元数据",
    )
    // The app loads network routing and other stored settings asynchronously. Leave
    // startup outside the measured block, then enter Hot through the real pager.
    waitDuringHotBootstrap(5_000L, preparation)
    if (navigateHotThroughExpandedNavigation(preparation)) return
    InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
        putString("eara.listBootstrap", "hot_listening:settings:5000ms:pager:${pages.joinToString("->")}")
    })
    pages.drop(1).forEach { expectedHeader ->
        // Stay in page content, away from the navigation pill and player controls.
        val y = (displayHeight * 0.17f).toInt()
        requireEaraWindow()
        check(swipe((displayWidth * 0.18f).toInt(), y, (displayWidth * 0.82f).toInt(), y, 35)) {
            "Could not inject the Hot bootstrap pager gesture"
        }
        waitForBootstrapHeader(expectedHeader, preparation)
        waitDuringHotBootstrap(1_000L, preparation)
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("eara.listBootstrapPage", expectedHeader)
        })
    }
}

private val BootstrapNavigationLabels = listOf(
    "本地库", "在线搜索", "热门收听", "我的收藏", "我的列表", "我的分组", "ASMR 看板", "设置", "切换",
)

private fun UiDevice.findExpandedBootstrapNavigation(): Map<String, UiObject2>? {
    refreshRealListHierarchy()
    val minimumIconSize = minOf(displayWidth, displayHeight) / 30
    val icons = BootstrapNavigationLabels.mapNotNull { label ->
        findObjects(By.pkg(PackageName).desc(label)).firstOrNull { icon ->
            val bounds = icon.visibleBounds
            bounds.top > displayHeight * 3 / 4 &&
                bounds.width() >= minimumIconSize && bounds.height() >= minimumIconSize
        }?.let { label to it }
    }.toMap()
    // A collapsed pill exposes only its current icon. Never click it: that action
    // would persist a mini-player mode change. Require all four slots plus toggle.
    return icons.takeIf { it.size == 5 && it.containsKey("切换") }
}

private fun UiDevice.navigateHotThroughExpandedNavigation(preparation: RealListPreparation): Boolean {
    var icons = findExpandedBootstrapNavigation() ?: return false
    if (!icons.containsKey("热门收听")) {
        clickBootstrapNavigationIcon(checkNotNull(icons["切换"]))
        waitDuringHotBootstrap(500L, preparation)
        val groupDeadline = minOf(preparation.deadlineMillis, SystemClock.uptimeMillis() + 3_000L)
        do {
            val refreshed = findExpandedBootstrapNavigation()
            if (refreshed?.containsKey("热门收听") == true) {
                icons = refreshed
                break
            }
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < groupDeadline)
    }
    val target = icons["热门收听"] ?: run {
        val diagnostics = recordListFailure("hot_listening", "设置")
        error("The expanded navigation did not expose the Hot tab. $diagnostics")
    }
    InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
        putString("eara.listBootstrap", "hot_listening:settings:5000ms:expanded_navigation:热门收听")
    })
    clickBootstrapNavigationIcon(target)
    waitForBootstrapHeader("热门收听", preparation)
    waitDuringHotBootstrap(1_000L, preparation)
    return true
}

private fun UiDevice.clickBootstrapNavigationIcon(icon: UiObject2) {
    var action = icon
    while (!action.isClickable) {
        action = checkNotNull(action.parent) { "Navigation icon has no clickable ancestor" }
    }
    val bounds = action.visibleBounds
    check(action.isEnabled && !bounds.isEmpty) { "Navigation action is unavailable" }
    requireEaraWindow()
    executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}")
}

private fun UiDevice.waitForBootstrapHeader(
    expectedHeader: String,
    preparation: RealListPreparation,
    requiredContent: String? = null,
) {
    val phaseDeadline = minOf(preparation.deadlineMillis, SystemClock.uptimeMillis() + 10_000L)
    var stableSince = 0L
    var stableBounds: Rect? = null
    do {
        try {
            refreshRealListHierarchy()
            val headerBounds = findObjects(By.pkg(PackageName).text(expectedHeader)).map { it.visibleBounds }.firstOrNull {
                !it.isEmpty && it.top < displayHeight / 5 &&
                    it.width() >= minOf(displayWidth, displayHeight) / 16 &&
                    it.centerX() in (displayWidth * 0.4f).toInt()..(displayWidth * 0.6f).toInt()
            }
            val contentVisible = requiredContent == null || hasObject(By.pkg(PackageName).text(requiredContent))
            if (headerBounds != null && contentVisible) {
                if (headerBounds == stableBounds) {
                    if (SystemClock.uptimeMillis() - stableSince >= 300L) return
                } else {
                    stableSince = SystemClock.uptimeMillis()
                    stableBounds = headerBounds
                }
            } else {
                stableBounds = null
            }
        } catch (_: StaleObjectException) {
            // The settled pager page replaced its semantics while the header changed.
            stableBounds = null
        }
        SystemClock.sleep(100)
    } while (SystemClock.uptimeMillis() < phaseDeadline)
    val diagnostics = recordListFailure("hot_listening", expectedHeader)
    error("Hot bootstrap did not reach the primary page $expectedHeader. $diagnostics")
}

private fun refreshRealListHierarchy() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // Android 16 can retain the previous Compose page's virtual nodes after a
        // transition even while screenshots already show the destination page.
        InstrumentationRegistry.getInstrumentation().uiAutomation.clearCache()
    }
}

private fun UiDevice.waitDuringHotBootstrap(durationMillis: Long, preparation: RealListPreparation) {
    val remaining = preparation.deadlineMillis - SystemClock.uptimeMillis()
    if (remaining > 0L) SystemClock.sleep(minOf(durationMillis, remaining))
    if (SystemClock.uptimeMillis() >= preparation.deadlineMillis) {
        val diagnostics = recordListFailure("hot_listening", "热门收听")
        error("Hot bootstrap exceeded the list readiness deadline. $diagnostics")
    }
}

private fun UiDevice.waitForRealList(route: String, preparation: RealListPreparation): RealListFixture {
    val expectedHeader = when (route) {
        "library" -> "本地库"
        "search" -> "在线搜索"
        "hot_listening" -> "热门收听"
        else -> error("Unknown real list route: $route")
    }
    do {
        try {
            refreshRealListHierarchy()
            if (!hasObject(By.text(expectedHeader))) {
                SystemClock.sleep(100)
                continue
            }
            // Start at album identities and walk outwards: the outer HorizontalPager also
            // advertises scrolling and contains header text while its page is loading.
            val albumWorkNumbers = findObjects(By.text(AlbumWorkNoText))
                .filterNot { it.visibleBounds.isEmpty }
            if (route == "hot_listening" && preparation.allowHotRetry && albumWorkNumbers.isEmpty() &&
                !preparation.hotRetryAttempted && hasObject(By.text("数据加载失败"))
            ) {
                findObject(By.text("重试").enabled(true))?.let { retry ->
                    preparation.hotRetryAttempted = true
                    requireEaraWindow()
                    retry.click()
                    InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                        putString("eara.listSetupRetry", "hot_listening:1")
                    })
                    SystemClock.sleep(250)
                }
            }
            albumWorkNumbers.forEach { identity ->
                nearestListAncestor(identity)?.let { list ->
                    val codes = list.findObjects(By.text(AlbumWorkNoText))
                        .flatMap { AlbumWorkNo.findAll(it.text.orEmpty()).map { match -> match.value }.toList() }
                        .distinct()
                    if (codes.isNotEmpty()) {
                        val identities = if (route == "library") localAlbumIdentities(list) else codes
                        return snapshotRealListFixture(list, identities)
                    }
                }
            }
            if (route == "library") {
                // Local albums need not have an RJ number. The album actions are attached
                // to the full card; header controls and metadata chips are much shorter.
                val shortEdge = minOf(displayWidth, displayHeight)
                findObjects(By.clickable(true).longClickable(true)).forEach { card ->
                    val bounds = card.visibleBounds
                    if (bounds.width() >= shortEdge / 6 &&
                        bounds.height() >= shortEdge / 6 &&
                        bounds.height() < displayHeight * 3 / 4 &&
                        !card.isScrollable
                    ) {
                        nearestListAncestor(card)?.let { list ->
                            val identities = localAlbumIdentities(list)
                            if (identities.isNotEmpty()) return snapshotRealListFixture(list, identities)
                        }
                    }
                }
            }
        } catch (_: StaleObjectException) {
            // A network response or Paging refresh replaced the semantics tree; poll again.
        }
        SystemClock.sleep(100)
    } while (SystemClock.uptimeMillis() < preparation.deadlineMillis)
    val diagnostics = recordListFailure(route, expectedHeader)
    error("No populated real list is visible for $route; refusing to measure an empty/loading page. $diagnostics")
}

private fun UiDevice.returnHotListToInitialPrefix(
    initialPrefix: List<String>,
    preparation: RealListPreparation,
): RealListFixture {
    check(initialPrefix.isNotEmpty()) { "Loaded hot list has no initial album identity" }
    var fixture = waitForRealList("hot_listening", preparation)
    for (attempt in 0..20) {
        if (fixture.identities.take(initialPrefix.size) == initialPrefix) return fixture
        if (attempt == 20 || SystemClock.uptimeMillis() >= preparation.deadlineMillis) break
        val bounds = fixture.bounds
        val x = bounds.centerX()
        val upperY = bounds.top + (bounds.height() * 0.32f).toInt()
        val lowerY = bounds.top + (bounds.height() * 0.78f).toInt()
        // Reverse the measured profile's interior swipe. UiObject2.scroll's default
        // lower gesture margin can overlap the mini-player on this full-height list.
        requireEaraWindow()
        swipe(x, upperY, x, lowerY, 60)
        SystemClock.sleep(350)
        fixture = waitForRealList("hot_listening", preparation)
    }
    val diagnostics = recordListFailure("hot_listening", "热门收听")
    error("Could not restore the initial hot-list prefix $initialPrefix within 20 swipes. $diagnostics")
}

private fun UiDevice.recordListFailure(route: String, expectedHeader: String): String {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    refreshRealListHierarchy()
    if (currentPackageName != PackageName) {
        val summary = "expectedHeader=$expectedHeader; target app is not in the foreground; capture skipped"
        instrumentation.sendStatus(2, Bundle().apply {
            putString("eara.listFailure", "$route:$summary")
        })
        return summary
    }
    val outputRoot = instrumentation.context.externalMediaDirs.firstOrNull()
        ?: instrumentation.context.filesDir
    val name = "real-list-timeout-$route-${SystemClock.uptimeMillis()}"
    val hierarchy = File(outputRoot, "$name.xml")
    val screenshot = File(outputRoot, "$name.png")
    val hierarchyResult = runCatching {
        check(currentPackageName == PackageName) { "Target app left the foreground" }
        dumpWindowHierarchy(hierarchy)
        hierarchy.absolutePath
    }
    val screenshotResult = runCatching {
        check(currentPackageName == PackageName) { "Target app left the foreground" }
        check(takeScreenshot(screenshot)) { "Screenshot capture failed" }
        screenshot.absolutePath
    }
    val summary = runCatching {
        val statusText = Pattern.compile("本地库|在线搜索|热门收听|数据加载失败|暂无排行数据|重试|后端未配置|请求失败")
        val statuses = findObjects(By.pkg(PackageName).text(statusText)).mapNotNull { it.text }.distinct()
        val codes = findObjects(By.pkg(PackageName).text(AlbumWorkNoText)).size
        val scrollables = findObjects(By.pkg(PackageName).scrollable(true)).map { "${it.className}:${it.visibleBounds}" }
        "expectedHeader=$expectedHeader; statuses=$statuses; RJ nodes=$codes; scrollables=$scrollables"
    }.getOrElse { "Cannot inspect timeout state: ${it.javaClass.simpleName}" }
    instrumentation.sendStatus(2, Bundle().apply {
        putString("eara.listFailure", "$route:$summary")
        hierarchyResult.getOrNull()?.let { putString("eara.listFailureHierarchy", it) }
        screenshotResult.getOrNull()?.let { putString("eara.listFailureScreenshot", it) }
    })
    return summary
}

private val NonEmptyListText = Pattern.compile(".+", Pattern.DOTALL)
private val AlbumWorkNo = Regex("RJ[0-9]{6,}", RegexOption.IGNORE_CASE)
private val AlbumWorkNoText = Pattern.compile(".*RJ[0-9]{6,}.*", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)

private fun UiDevice.localAlbumIdentities(list: UiObject2): List<String> {
    val shortEdge = minOf(displayWidth, displayHeight)
    return list.findObjects(By.clickable(true).longClickable(true))
        .filter { card ->
            val bounds = card.visibleBounds
            bounds.width() >= shortEdge / 6 && bounds.height() >= shortEdge / 6 &&
                bounds.height() < displayHeight * 3 / 4 && !card.isScrollable
        }
        .mapNotNull { card -> card.visibleTexts().takeIf { it.isNotEmpty() }?.joinToString("|") }
        .distinct()
}

private fun UiDevice.nearestListAncestor(item: UiObject2): UiObject2? {
    var ancestor = item.parent
    while (ancestor != null) {
        val bounds = ancestor.visibleBounds
        val className = ancestor.className.orEmpty()
        if (ancestor.isScrollable && bounds.height() > displayHeight / 3 &&
            bounds.width() > minOf(displayWidth, displayHeight) / 4 &&
            !className.contains("Horizontal", ignoreCase = true) &&
            !className.contains("ViewPager", ignoreCase = true)
        ) {
            return ancestor
        }
        ancestor = ancestor.parent
    }
    return null
}

private fun UiObject2.visibleTexts(): List<String> = (listOfNotNull(text) +
    findObjects(By.text(NonEmptyListText)).mapNotNull { it.text })
    .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
    .distinct()

private fun UiDevice.recordListFixture(route: String, fixture: RealListFixture) {
    InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
        putString("eara.albumIdentities", "$route:${fixture.identities.joinToString("|")}")
        putString("eara.listFixture", "$route:${fixture.texts.joinToString("|")}")
        putString("eara.listViewport", "${displayWidth}x${displayHeight}:${fixture.bounds}")
    })
}

private data class AsyncSwipeSamples(val plannedDurationMillis: Long, val actualDurationMillis: Long, val moves: Int)

@Volatile private var realListFocusLost = false

private fun requireEaraWindow() {
    val root = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
    val target = try { root?.packageName?.toString() == PackageName } finally {
        @Suppress("DEPRECATION")
        root?.recycle()
    }
    if (!target) realListFocusLost = true
    check(!realListFocusLost) { "Eara lost the active window; stopping input" }
}

private fun performRealListScrollProfile(bounds: Rect) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automation = instrumentation.uiAutomation
    val samples = ArrayList<AsyncSwipeSamples>(7)
    val x = bounds.centerX()
    val startY = bounds.top + (bounds.height() * 0.78f).toInt()
    val endY = bounds.top + (bounds.height() * 0.32f).toInt()
    repeat(2) {
        // Stay inside the list, away from refresh controls and the bottom navigation bar.
        samples += automation.injectAsyncSwipe(x, startY, x, endY, 90)
        SystemClock.sleep(800)
        samples += automation.injectAsyncSwipe(x, startY, x, endY, 12)
        SystemClock.sleep(1_200)
        samples += automation.injectAsyncSwipe(x, endY, x, startY, 22)
        SystemClock.sleep(1_200)
    }
    // Sample while the display is still reacting to input, before the final fling settles.
    samples += automation.injectAsyncSwipe(x, startY, x, endY, 12)
    instrumentation.sendStatus(2, Bundle().apply {
        putString("eara.gestureInjection", "async_ui_automation:sample=4ms:step=16ms:display=0")
        putString("eara.gestureDurationsMs", samples.joinToString("|") { it.plannedDurationMillis.toString() })
        putString("eara.gestureActualDurationsMs", samples.joinToString("|") { it.actualDurationMillis.toString() })
        putString("eara.gestureMoveSamples", samples.joinToString("|") { it.moves.toString() })
    })
}

private fun UiAutomation.injectAsyncSwipe(
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    steps: Int,
): AsyncSwipeSamples {
    requireEaraWindow()
    val durationMillis = steps * 16L
    val downTime = SystemClock.uptimeMillis()
    val deadline = downTime + durationMillis
    var x = startX.toFloat()
    var y = startY.toFloat()
    var moves = 0
    var completed = false
    try {
        injectTouch(downTime, downTime, MotionEvent.ACTION_DOWN, x, y)
        var nextSample = downTime + 4L
        while (true) {
            val waitMillis = nextSample - SystemClock.uptimeMillis()
            if (waitMillis > 0L) SystemClock.sleep(waitMillis)
            val now = SystemClock.uptimeMillis()
            if (now >= deadline) break
            val fraction = ((now - downTime).toFloat() / durationMillis).coerceIn(0f, 1f)
            x = startX + (endX - startX) * fraction
            y = startY + (endY - startY) * fraction
            injectTouch(downTime, now, MotionEvent.ACTION_MOVE, x, y)
            moves++
            // Skip elapsed slots rather than burst stale samples or accumulate sleep drift.
            nextSample = downTime + ((now - downTime) / 4L + 1L) * 4L
        }
        val upTime = SystemClock.uptimeMillis()
        injectTouch(downTime, upTime, MotionEvent.ACTION_UP, endX.toFloat(), endY.toFloat())
        completed = true
        requireEaraWindow()
        return AsyncSwipeSamples(durationMillis, upTime - downTime, moves)
    } finally {
        if (!completed) {
            runCatching { injectTouch(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, x, y) }
        }
    }
}

private fun UiAutomation.injectTouch(downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
    // The public obtain overload targets DEFAULT_DISPLAY. Every event uses the same
    // touchscreen source and downTime; the caller owns and always recycles it.
    val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
    try {
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        check(injectInputEvent(event, false)) { "Async touch injection failed for action $action" }
    } finally {
        event.recycle()
    }
}

private fun recordRefreshRate(route: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val manager = instrumentation.context.getSystemService(DisplayManager::class.java)
    val display = checkNotNull(manager.getDisplay(Display.DEFAULT_DISPLAY))
    val refreshRate = display.refreshRate
    instrumentation.sendStatus(2, Bundle().apply {
        putString("eara.displayRefreshRateHz", "$route:$refreshRate")
    })
    val minimum = InstrumentationRegistry.getArguments()
        .getString("eara.minimumRefreshRateHz")?.toFloatOrNull()
    if (minimum != null) {
        check(refreshRate >= minimum) {
            "Display is running at $refreshRate Hz; this run requires at least $minimum Hz"
        }
    }
    SystemClock.sleep(1_200)
}

/** Measures warmed real-page round trips using an already expanded navigation bar. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class, ExperimentalMacrobenchmarkApi::class)
class RealPrimaryNavigationPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun warmedSettingsSearchRoundTrips() = measureSettingsRoundTrips("search", "在线搜索")

    @Test
    fun warmedSettingsHotRoundTrips() = measureSettingsRoundTrips("hot_listening", "热门收听")

    private fun measureSettingsRoundTrips(route: String, targetLabel: String) {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Real navigation requires explicit opt-in and an already expanded navigation bar",
            arguments.getString("eara.realNavigation") == "true",
        )
        val configurator = Configurator.getInstance()
        val previousIdleTimeout = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = 0L
        try {
            benchmarkRule.measureRepeated(
                packageName = PackageName,
                metrics = buildList {
                    add(FrameTimingGfxInfoMetric())
                    if (arguments.getString("eara.traceFrameTiming") == "true") {
                        add(FrameTimingMetric())
                    }
                },
                compilationMode = CompilationMode.Ignore(),
                startupMode = null,
                iterations = arguments.getString("eara.navigationIterations")?.toIntOrNull()
                    ?.coerceIn(1, 20) ?: 3,
                setupBlock = {
                    check(!realListFocusLost) { "Previous navigation lost the target window; ending this capture" }
                    killProcess()
                    startMainActivity(startRoute = "settings", clearData = false)
                    val preparation = RealListPreparation(allowHotRetry = false)
                    device.waitForBootstrapHeader(SettingsLabel, preparation, SettingsBody)
                    device.waitDuringHotBootstrap(5_000L, preparation)
                    requireHighRefreshDisplay()
                    device.visitPopulatedNavigationTarget(route, targetLabel, SettingsLabel, preparation)
                    device.returnNavigationToSettings(targetLabel, preparation)
                },
            ) {
                val preparation = RealListPreparation(allowHotRetry = false)
                device.visitPopulatedNavigationTarget(route, targetLabel, SettingsLabel, preparation)
                device.returnNavigationToSettings(targetLabel, preparation)
                recordRefreshRate("settings_${route}_round_trip")
                requireHighRefreshDisplay()
            }
        } finally {
            configurator.waitForIdleTimeout = previousIdleTimeout
        }
    }

    private fun UiDevice.visitPopulatedNavigationTarget(
        route: String,
        targetLabel: String,
        currentLabel: String,
        preparation: RealListPreparation,
    ) {
        clickExpandedNavigationDestination(targetLabel, currentLabel, preparation)
        waitForBootstrapHeader(targetLabel, preparation)
        // Keep the full pager, shared header, cover and navigation transitions.
        waitDuringHotBootstrap(NavigationTransitionSettleMs, preparation)
        val fixture = waitForRealList(route, preparation)
        recordListFixture(route, fixture)
        requireHighRefreshDisplay()
    }

    private fun UiDevice.returnNavigationToSettings(
        currentLabel: String,
        preparation: RealListPreparation,
    ) {
        clickExpandedNavigationDestination(SettingsLabel, currentLabel, preparation)
        waitForBootstrapHeader(SettingsLabel, preparation, SettingsBody)
        waitDuringHotBootstrap(NavigationTransitionSettleMs, preparation)
    }

    private fun UiDevice.clickExpandedNavigationDestination(
        destinationLabel: String,
        currentLabel: String,
        preparation: RealListPreparation,
    ) {
        check(destinationLabel != currentLabel) { "Never click the current navigation pill" }
        val initial = findExpandedBootstrapNavigation()
        assumeTrue("Navigation must already expose all four slots and the group toggle", initial != null)
        var icons = checkNotNull(initial)
        if (destinationLabel !in icons) {
            clickBootstrapNavigationIcon(checkNotNull(icons["切换"]))
            waitDuringHotBootstrap(NavigationTransitionSettleMs, preparation)
            val deadline = minOf(preparation.deadlineMillis, SystemClock.uptimeMillis() + 3_000L)
            do {
                findExpandedBootstrapNavigation()?.let { refreshed ->
                    icons = refreshed
                    if (destinationLabel in refreshed) {
                        clickBootstrapNavigationIcon(checkNotNull(refreshed[destinationLabel]))
                        return
                    }
                }
                SystemClock.sleep(100)
            } while (SystemClock.uptimeMillis() < deadline)
            assumeTrue("Navigation stopped exposing all four slots", findExpandedBootstrapNavigation() != null)
        }
        val destination = checkNotNull(icons[destinationLabel]) {
            "Expanded navigation did not expose $currentLabel -> $destinationLabel"
        }
        clickBootstrapNavigationIcon(destination)
    }

    private fun requireHighRefreshDisplay() {
        val manager = InstrumentationRegistry.getInstrumentation().context
            .getSystemService(DisplayManager::class.java)
        val refreshRate = checkNotNull(manager.getDisplay(Display.DEFAULT_DISPLAY)).refreshRate
        check(refreshRate >= 100f) { "Real navigation requires at least 100 Hz; actual=$refreshRate" }
    }

    private companion object {
        const val SettingsLabel = "设置"
        const val SettingsBody = "管理扫描目录、刷新本地内容与同步元数据"
        const val NavigationTransitionSettleMs = 1_000L
    }
}
