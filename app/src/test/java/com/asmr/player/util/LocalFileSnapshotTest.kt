package com.asmr.player.util

import android.app.Application
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LocalFileSnapshotTest {
    @Test
    fun deletedOrChangedSourceCannotBeCommitted() {
        val context = RuntimeEnvironment.getApplication()
        val source = File.createTempFile("snapshot", ".wav", context.cacheDir)
        try {
            source.writeText("原始音频", Charsets.UTF_8)
            val snapshot = LocalFileSnapshot.read(context, source.path)
            snapshot.requireUnchanged(context, source.path)
            source.appendText("已改变", Charsets.UTF_8)
            assertThrows(IllegalStateException::class.java) { snapshot.requireUnchanged(context, source.path) }
            source.delete()
            assertThrows(IllegalStateException::class.java) { snapshot.requireUnchanged(context, source.path) }
        } finally {
            source.delete()
        }
    }
}
