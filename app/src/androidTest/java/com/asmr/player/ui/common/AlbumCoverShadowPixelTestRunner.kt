package com.asmr.player.ui.common

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.asmr.player.cache.ImageCacheEntryPoint
import com.asmr.player.cache.ImageCacheManager
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager

/** Explicit-only runner: never constructs AsmrApp or runs its business initialization. */
class AlbumCoverShadowPixelTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, AlbumCoverShadowPixelTestApplication::class.java.name, context)

    override fun onCreate(arguments: Bundle) {
        require(arguments.getString("class") == AlbumCoverShadowPixelAndroidTest::class.java.name) {
            "This isolated runner requires the explicit AlbumCoverShadowPixelAndroidTest class"
        }
        super.onCreate(arguments)
    }
}

class AlbumCoverShadowPixelTestApplication : Application(), GeneratedComponentManager<Any> {
    internal lateinit var fixtureImages: ImageCacheManager

    private val component = object : GeneratedComponent, ImageCacheEntryPoint {
        override fun imageCacheManager(): ImageCacheManager = fixtureImages
    }

    override fun generatedComponent(): Any = component
}
