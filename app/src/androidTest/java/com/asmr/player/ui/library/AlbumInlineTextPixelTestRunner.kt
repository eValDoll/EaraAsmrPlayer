package com.asmr.player.ui.library

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Explicit-only runner; AsmrApp and its services, stores and workers are never initialized. */
class AlbumInlineTextPixelTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)

    override fun onCreate(arguments: Bundle) {
        require(arguments.getString("class") == AlbumInlineTextPixelAndroidTest::class.java.name) {
            "This isolated runner requires the explicit AlbumInlineTextPixelAndroidTest class"
        }
        super.onCreate(arguments)
    }
}
