package com.asmr.player.ui

import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf

/** Registers the Compose host in Robolectric without packaging a test activity in Release APKs. */
fun createEaraComposeRule(): ComposeContentTestRule {
    val composeRule = createComposeRule()
    val registerActivity = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(context, ComponentActivity::class.java))
        }
    }
    return object : ComposeContentTestRule by composeRule {
        override fun apply(base: Statement, description: Description): Statement =
            RuleChain.outerRule(registerActivity).around(composeRule).apply(base, description)
    }
}
