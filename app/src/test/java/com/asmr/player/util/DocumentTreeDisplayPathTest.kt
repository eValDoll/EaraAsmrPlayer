package com.asmr.player.util

import android.app.Application
import android.os.Environment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class DocumentTreeDisplayPathTest {
    @Test
    fun primaryExternalStorageTree_isDisplayedAsFullLocalPath() {
        val context: Application = RuntimeEnvironment.getApplication()
        val displayed = documentTreeDisplayPath(
            context,
            "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FASMR",
        )

        assertEquals(
            "${Environment.getExternalStorageDirectory().absolutePath}/Music/ASMR",
            displayed,
        )
        assertFalse(displayed.contains("%3A", ignoreCase = true))
    }
}
