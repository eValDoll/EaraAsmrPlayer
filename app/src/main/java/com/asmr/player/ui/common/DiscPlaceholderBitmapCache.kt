package com.asmr.player.ui.common

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import com.asmr.player.R
import java.util.concurrent.atomic.AtomicReference

internal object DiscPlaceholderBitmapCache {
    private val darkBitmap = AtomicReference<ImageBitmap?>()
    private val lightBitmap = AtomicReference<ImageBitmap?>()

    fun preload(resources: Resources, darkTheme: Boolean) {
        val target = if (darkTheme) darkBitmap else lightBitmap
        if (target.get() != null) return

        val resourceId = if (darkTheme) {
            R.drawable.image_empty_dark
        } else {
            R.drawable.image_empty_light
        }
        val decoded = ImageBitmap.imageResource(resources, resourceId)
        target.compareAndSet(null, decoded)
    }

    fun get(@DrawableRes resourceId: Int): ImageBitmap? {
        return when (resourceId) {
            R.drawable.image_empty_dark -> darkBitmap.get()
            R.drawable.image_empty_light -> lightBitmap.get()
            else -> null
        }
    }
}
