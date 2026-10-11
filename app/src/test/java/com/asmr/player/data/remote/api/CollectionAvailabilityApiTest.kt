package com.asmr.player.data.remote.api

import android.app.Application
import com.asmr.player.domain.model.CollectedSearchSource
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CollectionAvailabilityApiTest {
    @Test
    fun optsIntoBothSourcesAndKeepsTheirIdentitiesSeparate() = withServer { server, api ->
        server.enqueue(MockResponse().setBody("""{"items":[
            {"rj":"RJ123456","collected":true,"workId":123456,"sources":[
                {"source":"asmr.one","rj":"RJ123456","workId":123456},
                {"source":"jp-asmr","rj":"RJ123456","workId":150866,"pageUrl":"https://japaneseasmr.com/150866/"}]},
            {"rj":"BJ02370869","collected":true,"sources":[{"source":"asmr.one","rj":"BJ02370869","workId":100000062}]},
            {"rj":"UN124393","collected":true,"sources":[{"source":"jp-asmr","rj":"UN124393","workId":138562,"pageUrl":"https://japaneseasmr.com/138562/"}]},
            {"rj":"UND124393","collected":false,"sources":[]},
            {"rj":"VJ123456","collected":false,"sources":[]}
        ]}"""))
        val result = api.check(listOf(" rj123456 ", "BJ02370869", "un124393", "UND124393", "VJ123456", "RJ123456", "invalid"))
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/api/asmr-one/availability", request.path)
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertTrue(body["includeJpAsmr"].asBoolean)
        assertEquals(listOf("RJ123456", "BJ02370869", "UN124393", "UND124393", "VJ123456"),
            body["rjs"].asJsonArray.map { it.asString })
        assertEquals("收录", result.getValue("RJ123456").label)
        assertEquals(123456, result.getValue("RJ123456").asmrOneWorkId)
        assertEquals("asmr.one", result.getValue("BJ02370869").label)
        val jp = result.getValue("UN124393")
        assertEquals("jp-asmr", jp.label)
        assertEquals(CollectedSearchSource.JapaneseAsmr, jp.preferredSource)
        assertFalse(jp.hasAsmrOne)
        assertNull(jp.asmrOneWorkId)
        assertEquals("https://japaneseasmr.com/138562/", jp.japaneseAsmrPageUrl)
        assertNull(result.getValue("UND124393").label)
        assertNull(result.getValue("VJ123456").label)
    }

    @Test
    fun aliasesAndOriginalWorkNumbersDoNotOverwriteAnotherRequestedLanguage() = withServer { server, api ->
        server.enqueue(MockResponse().setBody("""{"items":[
            {"rj":"RJ01557280","collected":false,"sources":[]},
            {"rj":"RJ01569011","collected":true,"matchedRjs":["RJ01569011","RJ01570449"],"originalWorkno":"RJ01557280",
                "sources":[{"source":"asmr.one","rj":"RJ01569011","workId":1569012}]}
        ]}"""))
        val result = api.check(listOf("RJ01557280", "RJ01569011", "RJ01570449"))
        assertNull(result.getValue("RJ01557280").label)
        assertEquals("asmr.one", result.getValue("RJ01569011").label)
        assertFalse(result.containsKey("RJ01570449"))
    }

    @Test
    fun oldServerLeavesJapaneseAsmrUnknown() = withServer { server, api ->
        server.enqueue(MockResponse().setBody("""{"items":[
            {"rj":"RJ123456","collected":true,"workId":123456},
            {"rj":"RJ654321","collected":false}
        ]}"""))
        val result = api.check(listOf("RJ123456", "RJ654321"))
        assertEquals("收录", result.getValue("RJ123456").label)
        assertNull(result.getValue("RJ123456").hasJapaneseAsmr)
        assertNull(result.getValue("RJ654321").hasJapaneseAsmr)
        assertNull(result.getValue("RJ654321").label)
    }

    @Test
    fun failedOrUnrecognizedResponseDoesNotReportNotCollected() = withServer { server, api ->
        for (response in listOf(
            MockResponse().setResponseCode(500).setBody("""{"error":"internal_error"}"""),
            MockResponse().setBody("invalid JSON"),
            MockResponse().setBody("""{"items":[{"rj":"RJ123456","collected":true,"sources":[{"source":"jp-asmr","rj":"UN123456","workId":1}]}]}""")
        )) {
            server.enqueue(response)
            assertTrue(api.check(listOf("RJ123456")).isEmpty())
        }
    }

    @Test
    fun batchLimitAndEmptyInputAvoidUnnecessaryRequests() = withServer { server, api ->
        assertTrue(api.check(listOf("invalid")).isEmpty())
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        api.check((0..100).map { "RJ%06d".format(it) })
        val body = JsonParser.parseString(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()).asJsonObject
        assertEquals(100, body["rjs"].asJsonArray.size())
    }

    @Test
    fun asmrOneResolutionRetainsLegacyContract() = withServer { server, api ->
        server.enqueue(MockResponse().setBody("""{"items":[{"rj":"RJ123456","collected":true,"workId":123456}]}"""))
        assertEquals(123456, api.resolve("RJ123456")!!.workId)
        val body = JsonParser.parseString(server.takeRequest(5, TimeUnit.SECONDS)!!.body.readUtf8()).asJsonObject
        assertFalse(body.has("includeJpAsmr"))
    }

    private fun withServer(block: suspend (MockWebServer, AsmrOneAvailabilityApi) -> Unit) = runBlocking {
        val server = MockWebServer().apply { start() }
        try {
            block(server, AsmrOneAvailabilityApi(OkHttpClient(), Gson()) { server.url("/").toString() })
        } finally {
            server.shutdown()
        }
    }
}
