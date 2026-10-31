package com.asmr.player.ui.common

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** A hardware window with independent owners; no Activity or business application is created. */
internal class HardwarePixelTestHost(context: Context, val width: Int, val height: Int, density: Float) {
    private val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private val display = checkNotNull(context.getSystemService(DisplayManager::class.java)
        .createVirtualDisplay("Isolated Compose pixels", width, height, (density * 160).roundToInt(), reader.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION))
    val presentation = Presentation(context, display.display)
    private val owners = Owners()
    val view = ComposeView(presentation.context)

    init {
        reader.setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(Looper.getMainLooper()))
        view.setViewTreeLifecycleOwner(owners)
        view.setViewTreeSavedStateRegistryOwner(owners)
        view.setViewTreeViewModelStoreOwner(owners)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    fun show() {
        owners.lifecycle.currentState = Lifecycle.State.RESUMED
        presentation.window?.requestFeature(Window.FEATURE_NO_TITLE)
        presentation.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        presentation.window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        presentation.setContentView(view, ViewGroup.LayoutParams(width, height))
        presentation.show()
        presentation.window?.setLayout(width, height)
    }

    fun capture(): Bitmap {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val committed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            view.invalidate()
        }
        check(committed.await(5, TimeUnit.SECONDS)) { "No hardware frame commit" }
        val copied = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        lateinit var bitmap: Bitmap
        instrumentation.runOnMainSync {
            check(view.isHardwareAccelerated) { "Pixel comparison requires hardware rendering" }
            val location = IntArray(2)
            view.getLocationInWindow(location)
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(checkNotNull(presentation.window),
                Rect(location[0], location[1], location[0] + view.width, location[1] + view.height),
                bitmap, { result = it; copied.countDown() }, Handler(Looper.getMainLooper()))
        }
        check(copied.await(5, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS) { "PixelCopy failed: $result" }
        return bitmap
    }

    fun captureSettled(): Bitmap {
        var previous: Bitmap? = null
        repeat(30) {
            val current = capture()
            val stable = previous?.sameAs(current) == true
            previous?.recycle()
            if (stable) return current
            previous = current
        }
        previous?.recycle()
        error("Hardware pixels did not settle")
    }

    fun close() {
        owners.lifecycle.currentState = Lifecycle.State.DESTROYED
        presentation.dismiss()
        owners.viewModelStore.clear()
        display.release()
        reader.close()
    }

    private class Owners : SavedStateRegistryOwner, ViewModelStoreOwner {
        override val lifecycle = LifecycleRegistry(this)
        override val viewModelStore = ViewModelStore()
        private val savedState = SavedStateRegistryController.create(this).apply {
            performAttach()
            performRestore(null)
        }
        override val savedStateRegistry get() = savedState.savedStateRegistry
    }
}
