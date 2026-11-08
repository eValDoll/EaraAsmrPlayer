package com.asmr.player

import android.app.Application
import com.asmr.player.i18n.AppLanguage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class LocalizedTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RuntimeEnvironment.setQualifiers("+zh-rCN")
        AppLanguage.initialize(this)
    }
}

/** Resource-dependent tests keep the existing Chinese assertions explicit. */
@RunWith(RobolectricTestRunner::class)
@Config(application = LocalizedTestApplication::class, sdk = [34])
abstract class LocalizedRobolectricTest
