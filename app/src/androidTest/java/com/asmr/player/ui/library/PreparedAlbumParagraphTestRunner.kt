package com.asmr.player.ui.library

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Explicit isolated fixture only; never initializes AsmrApp or its stores and services. */
class PreparedAlbumParagraphTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)

    override fun onCreate(arguments: Bundle) {
        require(arguments.getString("class") == PreparedAlbumParagraphAndroidTest::class.java.name) {
            "This runner requires the explicit PreparedAlbumParagraphAndroidTest class"
        }
        super.onCreate(arguments)
    }
}
