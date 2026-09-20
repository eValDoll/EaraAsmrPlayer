package com.asmr.player.translation

import com.google.gson.Gson
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

internal fun RecordedRequest.pageTranslationTexts(): List<String> =
    ("https://test.invalid/?" + body.clone().readUtf8()).toHttpUrl().queryParameterValues("q").filterNotNull()

internal fun MockWebServer.echoPageTranslations(delayMillis: Long = 0) {
    dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            MockResponse().setBody(Gson().toJson(request.pageTranslationTexts().map { "译文：$it" }))
                .setBodyDelay(delayMillis, TimeUnit.MILLISECONDS)
    }
}
