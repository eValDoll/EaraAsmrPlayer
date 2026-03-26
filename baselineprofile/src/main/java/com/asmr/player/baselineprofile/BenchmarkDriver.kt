package com.asmr.player.baselineprofile

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val PackageName = "com.asmr.player"
private const val MainActivityName = "com.asmr.player.MainActivity"
private const val BenchmarkHarnessActivityName = "com.asmr.player.benchmark.BenchmarkHarnessActivity"
private const val BenchmarkReadyPrefix = "benchmark-ready:"
private const val BenchmarkWaitTimeoutMs = 45_000L
private const val SearchNetworkLoadWaitMs = 3_500L
private const val SearchRefreshWaitMs = 2_500L
private const val SceneSettleWaitMs = 600L
private const val BaselineProfileMaxIterations = 3
private const val BaselineProfileStableIterations = 1

internal object BenchmarkScenarioValue {
    const val LibraryAlbums = "library_albums"
    const val LibraryTracks = "library_tracks"
    const val SearchNetwork = "search_network"
    const val FavoritesDetail = "favorites_detail"
    const val PlaylistsList = "playlists_list"
    const val PlaylistDetail = "playlist_detail"
    const val PlaylistPicker = "playlist_picker"
    const val GroupsList = "groups_list"
    const val GroupDetail = "group_detail"
    const val GroupPicker = "group_picker"
    const val Queue = "queue"
}

internal fun BaselineProfileRule.collectStartupProfile(
    block: MacrobenchmarkScope.() -> Unit
) {
    collect(
        packageName = PackageName,
        includeInStartupProfile = true,
        maxIterations = BaselineProfileMaxIterations,
        stableIterations = BaselineProfileStableIterations
    ) {
        block()
    }
}

internal fun BaselineProfileRule.collectBaselineProfile(
    block: MacrobenchmarkScope.() -> Unit
) {
    collect(
        packageName = PackageName,
        includeInStartupProfile = false,
        maxIterations = BaselineProfileMaxIterations,
        stableIterations = BaselineProfileStableIterations
    ) {
        block()
    }
}

internal fun MacrobenchmarkScope.startMainActivity(
    startRoute: String? = null
) {
    clearTargetAppData()
    device.pressHome()
    val intent = Intent(Intent.ACTION_MAIN).apply {
        setClassName(PackageName, MainActivityName)
        addCategory(Intent.CATEGORY_LAUNCHER)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (!startRoute.isNullOrBlank()) {
            putExtra("start_route", startRoute)
        }
    }
    startActivityAndWait(intent)
    waitForSceneToSettle()
}

internal fun MacrobenchmarkScope.startHarnessScenario(
    scenario: String
) {
    clearTargetAppData()
    device.pressHome()
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setClassName(PackageName, BenchmarkHarnessActivityName)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        putExtra("benchmark_scenario", scenario)
    }
    startActivityAndWait(intent)
    waitForBenchmarkReady(device, scenario)
    waitForSceneToSettle()
}

internal fun MacrobenchmarkScope.clearTargetAppData() {
    device.executeShellCommand("pm clear $PackageName")
    SystemClock.sleep(250)
}

internal fun waitForBenchmarkReady(
    device: UiDevice,
    scenario: String
) {
    val label = BenchmarkReadyPrefix + scenario
    check(
        device.wait(
            Until.hasObject(By.text(label)),
            BenchmarkWaitTimeoutMs
        )
    ) {
        "Benchmark harness did not become ready for scenario=$scenario"
    }
    device.waitForIdle()
    SystemClock.sleep(400)
}

internal fun waitForSceneToSettle() {
    SystemClock.sleep(SceneSettleWaitMs)
}

internal fun waitForSearchNetworkLoad() {
    SystemClock.sleep(SearchNetworkLoadWaitMs)
}

internal fun waitForSearchRefresh() {
    SystemClock.sleep(SearchRefreshWaitMs)
}

internal fun UiDevice.performLongListScrollProfile() {
    val centerX = displayWidth / 2
    val startY = (displayHeight * 0.84f).toInt()
    val endY = (displayHeight * 0.22f).toInt()
    val midY = (displayHeight * 0.42f).toInt()

    swipe(centerX, startY, centerX, endY, 22)
    waitForIdle()
    swipe(centerX, startY, centerX, endY, 8)
    waitForIdle()
    swipe(centerX, midY, centerX, startY, 16)
    waitForIdle()
}

internal fun UiDevice.performSlowDragAndFling() {
    val centerX = displayWidth / 2
    val startY = (displayHeight * 0.84f).toInt()
    val endY = (displayHeight * 0.22f).toInt()

    swipe(centerX, startY, centerX, endY, 30)
    waitForIdle()
    swipe(centerX, startY, centerX, endY, 10)
    waitForIdle()
    swipe(centerX, endY, centerX, startY, 14)
    waitForIdle()
}

internal fun UiDevice.pullToRefreshSearch() {
    val centerX = displayWidth / 2
    val topY = (displayHeight * 0.24f).toInt()
    val bottomY = (displayHeight * 0.74f).toInt()
    swipe(centerX, topY, centerX, bottomY, 36)
    waitForIdle()
}

internal fun defaultFrameTimingStartupMode(): StartupMode = StartupMode.WARM
