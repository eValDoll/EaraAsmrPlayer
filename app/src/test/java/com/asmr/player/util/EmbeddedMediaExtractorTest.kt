package com.asmr.player.util

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class EmbeddedMediaExtractorTest {
    @Test
    fun extractEmbeddedLyricsEntries_returnsEmptyWhenProviderRejectsMissingDocument() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowContentResolver.registerProviderInternal(
            "missing-media",
            MissingMediaProvider(),
        )

        val result = EmbeddedMediaExtractor.extractEmbeddedLyricsEntries(
            context,
            "content://missing-media/audio.wav",
        )

        assertTrue(result.isEmpty())
    }

    private class MissingMediaProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
            throw IllegalArgumentException("missing document")
        }
    }
}
