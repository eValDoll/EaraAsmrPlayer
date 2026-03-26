package com.asmr.player.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collectStartupProfile {
        startMainActivity()
    }

    @Test
    fun libraryAndPlaylistLongLists() = baselineProfileRule.collectBaselineProfile {
        startHarnessScenario(BenchmarkScenarioValue.LibraryAlbums)
        device.performLongListScrollProfile()

        startHarnessScenario(BenchmarkScenarioValue.LibraryTracks)
        device.performLongListScrollProfile()

        startHarnessScenario(BenchmarkScenarioValue.FavoritesDetail)
        device.performLongListScrollProfile()

        startHarnessScenario(BenchmarkScenarioValue.PlaylistsList)
        device.performLongListScrollProfile()

        startHarnessScenario(BenchmarkScenarioValue.PlaylistDetail)
        device.performLongListScrollProfile()
    }

    @Test
    fun groupsQueueAndPickers() = baselineProfileRule.collectBaselineProfile {
        startHarnessScenario(BenchmarkScenarioValue.GroupsList)
        device.performLongListScrollProfile()

        startHarnessScenario(BenchmarkScenarioValue.GroupDetail)
        device.performLongListScrollProfile()

        startHarnessScenario(BenchmarkScenarioValue.Queue)
        device.performLongListScrollProfile()

        startHarnessScenario(BenchmarkScenarioValue.PlaylistPicker)
        device.performLongListScrollProfile()

        startHarnessScenario(BenchmarkScenarioValue.GroupPicker)
        device.performLongListScrollProfile()
    }

    @Test
    fun searchNetworkRefreshAndScroll() = baselineProfileRule.collectBaselineProfile {
        startMainActivity(startRoute = "search")
        waitForSearchNetworkLoad()
        device.pullToRefreshSearch()
        waitForSearchRefresh()
        device.performLongListScrollProfile()
    }
}
