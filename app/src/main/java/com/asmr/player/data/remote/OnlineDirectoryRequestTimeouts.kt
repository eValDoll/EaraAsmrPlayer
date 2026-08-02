package com.asmr.player.data.remote

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal const val ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS = 2_500L

internal fun OkHttpClient.withOnlineDirectoryRequestTimeouts(): OkHttpClient =
    newBuilder()
        .callTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .connectTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(ONLINE_DIRECTORY_REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
