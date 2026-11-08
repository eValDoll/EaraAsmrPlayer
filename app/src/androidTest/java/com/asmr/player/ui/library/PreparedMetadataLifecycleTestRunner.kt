package com.asmr.player.ui.library

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Explicit plain-Application fixture; no activity, service, database, or business startup. */
class PreparedMetadataLifecycleTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)

    override fun onCreate(arguments: Bundle) {
        require(arguments.getString("class") == PreparedMetadataLifecycleAndroidTest::class.java.name)
        super.onCreate(arguments)
    }
}
