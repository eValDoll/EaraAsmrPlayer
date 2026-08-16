package com.asmr.player.cache

import android.content.Context
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheKeyFactoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun localFileKeyChangesWhenFileIsReplacedAtSamePath() {
        val file = File(context.cacheDir, "cache-key-image.jpg")
        try {
            file.writeBytes(byteArrayOf(1))
            val initialModifiedAt = file.lastModified()
            val firstKey = CacheKeyFactory.createKey(
                context = context,
                model = file,
                size = IntSize(320, 240),
                version = "test"
            )

            file.writeBytes(byteArrayOf(1, 2))
            file.setLastModified(initialModifiedAt + 1_000L)
            val replacedKey = CacheKeyFactory.createKey(
                context = context,
                model = file,
                size = IntSize(320, 240),
                version = "test"
            )

            assertNotEquals(firstKey, replacedKey)
            assertEquals(
                replacedKey,
                CacheKeyFactory.createKey(context, file, IntSize(320, 240), "test")
            )
        } finally {
            file.delete()
        }
    }
}
