package com.asmr.player.baselineprofile

import android.os.SystemClock
import android.os.Build
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Includes the shared MainContainer header, which the standalone list harness does not render. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class, ExperimentalMacrobenchmarkApi::class)
class HeaderBlurPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test fun library() = measureHeader("library")
    @Test fun libraryTracks() = measureHeader("library", expandLibraryAlbum = true)
    @Test fun search() = measureHeader("search")
    @Test fun searchAssist() = measureHeader("search_assist")
    @Test fun navigation() = measureHeader("search", navigatePrimaryPages = true)
    @Test fun hotListening() = measureHeader("hot_listening")
    @Test fun slowHeaderEdge() = measureHeader("hot_listening", slowEdgeScroll = true)
    @Test fun favorites() = measureHeader("playlist_system/favorites")
    @Test fun playlists() = measureHeader("playlists")
    @Test fun groups() = measureHeader("groups")
    @Test fun listeningCalendar() = measureHeader("listening_calendar")
    @Test fun settings() = measureHeader("settings")

    private fun measureHeader(
        route: String,
        expandLibraryAlbum: Boolean = false,
        navigatePrimaryPages: Boolean = false,
        slowEdgeScroll: Boolean = false,
    ) {
        benchmarkRule.measureRepeated(
            packageName = PackageName,
            metrics = listOf(FrameTimingGfxInfoMetric()),
            compilationMode = CompilationMode.Ignore(),
            startupMode = null,
            iterations = 5,
            setupBlock = {
                killProcess()
                startMainActivity(startRoute = route, clearData = false)
                if (expandLibraryAlbum) {
                    checkNotNull(device.wait(Until.findObject(By.desc("切换视图")), 5_000)).click()
                    checkNotNull(device.wait(Until.findObject(By.text("音轨列表")), 5_000)).click()
                    val album = device.wait(
                        Until.findObject(By.text(Pattern.compile("RJ\\d+ · .* 音频.*"))),
                        10_000,
                    ) ?: error("Populate the library with an album before measuring expanded tracks")
                    album.click()
                    device.waitForIdle()
                }
                if (route == "search_assist") {
                    check(device.wait(Until.hasObject(By.scrollable(true)), 10_000))
                    // Tapping the empty gutter dismisses the keyboard without submitting a search.
                    device.click(device.displayWidth / 40, (device.displayHeight * 0.45f).toInt())
                    device.waitForIdle()
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    check(device.wait(Until.hasObject(By.res("main_header_progressive_blur")), 5_000)) {
                        "Progressive header did not initialize on $route"
                    }
                }
                if (route == "settings") {
                    val lyrics = device.wait(Until.findObject(By.text("歌词")), 5_000)
                        ?: error("Settings category was not visible")
                    lyrics.click()
                    check(device.wait(Until.hasObject(By.text("开启悬浮歌词")), 5_000))
                }
                check(device.wait(Until.hasObject(By.scrollable(true)), 30_000)) {
                    "No scrollable content on $route; populate this page before measuring"
                }
                if (slowEdgeScroll) {
                    val x = device.displayWidth / 2
                    device.swipe(x, (device.displayHeight * 0.8f).toInt(), x, (device.displayHeight * 0.4f).toInt(), 30)
                    device.waitForIdle()
                }
                SystemClock.sleep(2_000)
            },
        ) {
            if (navigatePrimaryPages) {
                val headerHeight = device.findObject(By.res("main_header_progressive_blur")).visibleBounds.height()
                for (label in listOf("热门收听", "在线搜索", "热门收听", "本地库", "热门收听", "在线搜索")) {
                    checkNotNull(device.findObject(By.desc(label))).click()
                    device.waitForIdle()
                    check(device.findObject(By.res("main_header_progressive_blur")).visibleBounds.height() == headerHeight) {
                        "The shared header changed height when navigating to $label"
                    }
                }
            } else if (slowEdgeScroll) {
                val x = device.displayWidth / 2
                val startY = (device.displayHeight * 0.8f).toInt()
                val endY = (device.displayHeight * 0.7f).toInt()
                device.swipe(x, startY, x, endY, 1_000)
                device.waitForIdle()
                device.swipe(x, endY, x, startY, 1_000)
                device.waitForIdle()
            } else if (route == "settings") {
                // Drag in the content gutter so sliders and switches retain the user's values.
                val x = device.displayWidth / 40
                val startY = (device.displayHeight * 0.84f).toInt()
                val endY = (device.displayHeight * 0.22f).toInt()
                device.swipe(x, startY, x, endY, 30)
                device.waitForIdle()
                device.swipe(x, startY, x, endY, 10)
                device.waitForIdle()
                device.swipe(x, endY, x, startY, 14)
                device.waitForIdle()
            } else {
                device.performSlowDragAndFling()
            }
        }
    }
}
