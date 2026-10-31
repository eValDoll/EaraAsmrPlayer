package com.asmr.player.ui.common

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Explicit fixture only; never starts AsmrApp, its database or its services. */
class AlbumRowRasterCacheTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, AlbumCoverShadowPixelTestApplication::class.java.name, context)

    override fun onCreate(arguments: Bundle) {
        require(arguments.getString("class") == AlbumRowRasterCacheAndroidTest::class.java.name)
        super.onCreate(arguments)
    }
}
