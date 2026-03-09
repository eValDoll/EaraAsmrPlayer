package com.asmr.player.baselineprofile

import android.content.Intent
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
    fun startup() = baselineProfileRule.collect(
        packageName = "com.asmr.player",
        includeInStartupProfile = true,
    ) {
        pressHome()
        val intent = Intent().apply {
            setClassName("com.asmr.player", "com.asmr.player.MainActivity")
            putExtra("start_route", "search")
        }
        startActivityAndWait(intent)
        device.waitForIdle()
    }

    @Test
    fun searchScrollFlingAndImages() = baselineProfileRule.collect(
        packageName = "com.asmr.player",
        includeInStartupProfile = false,
    ) {
        pressHome()
        val intent = Intent().apply {
            setClassName("com.asmr.player", "com.asmr.player.MainActivity")
            putExtra("start_route", "search")
        }
        startActivityAndWait(intent)
        device.waitForIdle()
        device.waitForIdle()

        val x = device.displayWidth / 2
        val y1 = (device.displayHeight * 0.85f).toInt()
        val y2 = (device.displayHeight * 0.25f).toInt()

        repeat(3) {
            device.swipe(x, y1, x, y2, 20)
            device.waitForIdle()
        }
        repeat(2) {
            device.swipe(x, y2, x, y1, 20)
            device.waitForIdle()
        }

        repeat(3) {
            device.swipe(x, y1, x, y2, 5)
            device.waitForIdle()
        }
        repeat(2) {
            device.swipe(x, y2, x, y1, 5)
            device.waitForIdle()
        }
    }
}
