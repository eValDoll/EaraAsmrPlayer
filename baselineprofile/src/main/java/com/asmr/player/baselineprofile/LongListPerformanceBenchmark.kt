package com.asmr.player.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongListPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupColdMainActivity() {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.COLD,
            iterations = 3
        ) {
            device.pressHome()
            startMainActivity()
        }
    }

    @Test
    fun libraryAlbumsFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.LibraryAlbums)

    @Test
    fun libraryTracksFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.LibraryTracks)

    @Test
    fun favoritesDetailFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.FavoritesDetail)

    @Test
    fun playlistsListFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.PlaylistsList)

    @Test
    fun playlistDetailFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.PlaylistDetail)

    @Test
    fun groupsListFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.GroupsList)

    @Test
    fun groupDetailFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.GroupDetail)

    @Test
    fun queueFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.Queue)

    @Test
    fun playlistPickerFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.PlaylistPicker)

    @Test
    fun groupPickerFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.GroupPicker)

    @Test
    fun searchNetworkRefreshAndScrollFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = defaultFrameTimingStartupMode(),
            iterations = 1
        ) {
            startMainActivity(startRoute = "search")
            waitForSearchNetworkLoad()
            device.pullToRefreshSearch()
            waitForSearchRefresh()
            device.performSlowDragAndFling()
        }
    }

    @Test
    fun albumDetailDlTabFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = defaultFrameTimingStartupMode(),
            iterations = 1
        ) {
            device.pressHome()
            startAlbumDetailDlTabExample()
            device.performSlowDragAndFling()
        }
    }

    private fun measureScenarioFrameTiming(
        scenario: String
    ) {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = defaultFrameTimingStartupMode(),
            iterations = 1
        ) {
            device.pressHome()
            startHarnessScenario(scenario)
            device.performSlowDragAndFling()
        }
    }
}
