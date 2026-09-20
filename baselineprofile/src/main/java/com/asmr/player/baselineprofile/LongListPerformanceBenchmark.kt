package com.asmr.player.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
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
    fun primaryNavigationPagerFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = null,
            iterations = FrameTimingIterations,
            setupBlock = {
                startMainActivity()
            }
        ) {
            device.performPrimaryNavigationProfile()
        }
    }

    @Test
    fun primaryNavigationClickTransitionsFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = null,
            iterations = FrameTimingIterations,
            setupBlock = {
                startMainActivity()
            }
        ) {
            device.performPrimaryNavigationClickProfile()
        }
    }

    @Test
    fun secondaryNavigationTransitionsFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = null,
            iterations = FrameTimingIterations,
            setupBlock = {
                startHarnessScenario(BenchmarkScenarioValue.LibraryAlbums)
                startMainActivity(clearData = false)
            }
        ) {
            device.performSecondaryNavigationTransitionsProfile()
        }
    }

    @Test
    fun libraryAlbumsFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.LibraryAlbums)

    /** Exercise the real on-demand translation path while new rows enter the viewport. */
    @Test
    @OptIn(ExperimentalMacrobenchmarkApi::class)
    fun libraryPageTranslationFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Ignore(),
            startupMode = null,
            iterations = FrameTimingIterations,
            setupBlock = {
                startMainActivity(startRoute = "library", clearData = false)
                val action = device.wait(Until.findObject(By.desc(Pattern.compile("翻译页面|显示原文"))), 5_000)
                checkNotNull(action) { "Page translation action is missing" }
                if (action.contentDescription == "翻译页面") action.click()
                device.waitForIdle()
            },
        ) {
            device.performSlowDragAndFling()
        }
    }

    @Test
    fun libraryTracksFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.LibraryTracks)

    @Test
    fun favoritesDetailFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.FavoritesDetail)

    @Test
    fun playlistsListFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.PlaylistsList)

    @Test
    fun playlistDetailFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.PlaylistDetail)

    @Test
    fun downloadsListFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = null,
            iterations = FrameTimingIterations,
            setupBlock = {
                startHarnessScenario(BenchmarkScenarioValue.DownloadsList)
            }
        ) {
            device.expandFirstVisibleDownloadTask()
            device.performSlowDragAndFling()
        }
    }

    @Test
    fun translationTasksFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = null,
            iterations = FrameTimingIterations,
            setupBlock = {
                startHarnessScenario(BenchmarkScenarioValue.TranslationTasks)
                device.openAndExpandTranslationTasks()
            }
        ) {
            device.performSlowDragAndFling()
        }
    }

    @Test
    fun settingsFrameTiming() = measureScenarioFrameTiming(BenchmarkScenarioValue.Settings)

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
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = null,
            iterations = FrameTimingIterations,
            setupBlock = {
                startMainActivity(startRoute = "search")
                waitForSearchNetworkLoad()
            }
        ) {
            device.pullToRefreshSearch()
            waitForSearchRefresh()
            device.performSlowDragAndFling()
        }
    }

    @Test
    fun albumDetailDlTabFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = null,
            iterations = FrameTimingIterations,
            setupBlock = {
                startAlbumDetailDlTabExample()
            }
        ) {
            device.performSlowDragAndFling()
        }
    }

    private fun measureScenarioFrameTiming(
        scenario: String
    ) {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = null,
            iterations = FrameTimingIterations,
            setupBlock = {
                startHarnessScenario(scenario)
            }
        ) {
            device.performSlowDragAndFling()
        }
    }

    private companion object {
        const val FrameTimingIterations = 10
    }
}
