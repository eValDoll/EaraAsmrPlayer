package com.asmr.player.util

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkMeteredChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isActiveNetworkMetered(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        return cm?.isActiveNetworkMetered == true
    }
}
