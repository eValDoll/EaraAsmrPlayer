package com.asmr.player.data.remote.dlsite

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlsiteProductInfoClientTest {
    @Test
    fun parseLanguageEditions_extractsExpectedLanguages() {
        val json = """
            {
              "RJ01348345": {
                "dl_count_items": [
                  {
                    "workno": "RJ01348345",
                    "edition_id": 35299,
                    "edition_type": "language",
                    "display_order": 1,
                    "label": "日本語",
                    "lang": "JPN",
                    "dl_count": "17775",
                    "display_label": "日本語"
                  },
                  {
                    "workno": "RJ01375469",
                    "edition_id": 35299,
                    "edition_type": "language",
                    "display_order": 5,
                    "label": "簡体中文",
                    "lang": "CHI_HANS",
                    "dl_count": 1950,
                    "display_label": "簡体中文"
                  },
                  {
                    "workno": "RJ01377020",
                    "edition_id": 35299,
                    "edition_type": "language",
                    "display_order": 7,
                    "label": "繁体中文",
                    "lang": "CHI_HANT",
                    "dl_count": 308,
                    "display_label": "繁体中文"
                  }
                ]
              }
            }
        """.trimIndent()

        val client = DlsiteProductInfoClient(OkHttpClient())
        val editions = client.parseLanguageEditions("RJ01348345", json)

        assertEquals(3, editions.size)
        assertTrue(editions.any { it.lang == "JPN" && it.workno == "RJ01348345" })
        assertTrue(editions.any { it.lang == "CHI_HANS" && it.workno == "RJ01375469" })
        assertTrue(editions.any { it.lang == "CHI_HANT" && it.workno == "RJ01377020" })
    }

    @Test
    fun parseLanguageEditions_returnsEmptyWhenKeyMismatch() {
        val json = """
            {
              "RJ99999999": {
                "dl_count_items": [
                  {
                    "workno": "RJ99999999",
                    "edition_type": "language",
                    "display_order": 1,
                    "label": "日本語",
                    "lang": "JPN"
                  }
                ]
              }
            }
        """.trimIndent()

        val client = DlsiteProductInfoClient(OkHttpClient())
        val editions = client.parseLanguageEditions("RJ01348345", json)

        assertEquals(emptyList<DlsiteLanguageEdition>(), editions)
    }

    @Test
    fun parseLanguageEditions_usesCurrentTranslatedChildInsteadOfParentEdition() {
        val json = """
            {
              "RJ01189945": {
                "translation_info": {
                  "is_original": false,
                  "original_workno": "RJ365382",
                  "parent_workno": "RJ01189944",
                  "lang": "CHI_HANS"
                },
                "dl_count_items": [
                  {
                    "workno": "RJ365382",
                    "edition_type": "language",
                    "display_order": 1,
                    "label": "日本語",
                    "lang": "JPN",
                    "display_label": "日本語"
                  },
                  {
                    "workno": "RJ01189944",
                    "edition_type": "language",
                    "display_order": 5,
                    "label": "簡体中文",
                    "lang": "CHI_HANS",
                    "display_label": "簡体中文"
                  }
                ]
              }
            }
        """.trimIndent()

        val client = DlsiteProductInfoClient(OkHttpClient())
        val editions = client.parseLanguageEditions("RJ01189945", json)

        assertEquals("RJ365382", editions.single { it.lang == "JPN" }.workno)
        assertEquals("RJ01189945", editions.single { it.lang == "CHI_HANS" }.workno)
        assertTrue(editions.none { it.workno == "RJ01189944" })
    }

    @Test
    fun parseLanguageEditions_keepsCurrentTranslationWithoutEditionItems() {
        val json = """
            {
              "RJ01189945": {
                "translation_info": {
                  "lang": "CHI_HANS"
                }
              }
            }
        """.trimIndent()

        val client = DlsiteProductInfoClient(OkHttpClient())
        val editions = client.parseLanguageEditions("RJ01189945", json)

        assertEquals(
            listOf(
                DlsiteLanguageEdition(
                    workno = "RJ01189945",
                    lang = "CHI_HANS",
                    label = "简体中文",
                    displayOrder = 5
                )
            ),
            editions
        )
    }

    @Test
    fun parseTranslationParentWorkNo_returnsDirectParentInsteadOfOriginalWork() {
        val client = DlsiteProductInfoClient(OkHttpClient())
        val json = translationChildJson(
            translationInfo = """
                {
                  "is_child": true,
                  "original_workno": "RJ01279210",
                  "parent_workno": "RJ01287594",
                  "lang": "KOR"
                }
            """.trimIndent(),
        )

        assertEquals("RJ01287594", client.parseTranslationParentWorkNo("RJ01287595", json))
    }

    @Test
    fun parseTranslationParentWorkNo_acceptsCaseAndOuterWhitespace() {
        val client = DlsiteProductInfoClient(OkHttpClient())
        val json = translationChildJson(
            responseKey = " rj01287595 ",
            responseProductId = " rj01287595 ",
            translationInfo = """{"is_child": true, "parent_workno": " rj01287594 "}""",
        )

        assertEquals("RJ01287594", client.parseTranslationParentWorkNo(" rj01287595 ", json))
    }

    @Test
    fun parseTranslationParentWorkNo_rejectsMismatchedResponseIdentity() {
        val client = DlsiteProductInfoClient(OkHttpClient())
        val responses = listOf(
            translationChildJson(responseKey = "RJ01287594"),
            translationChildJson(responseProductId = "RJ01287594"),
            """{"RJ01287595": {"translation_info": {"is_child": true, "parent_workno": "RJ01287594"}}}""",
        )

        responses.forEach { json ->
            assertNull(client.parseTranslationParentWorkNo("RJ01287595", json))
        }
    }

    @Test
    fun parseTranslationParentWorkNo_requiresExplicitChildFlag() {
        val client = DlsiteProductInfoClient(OkHttpClient())
        val translationInfos = listOf(
            """{"is_child": false, "parent_workno": "RJ01287594"}""",
            """{"is_child": null, "parent_workno": "RJ01287594"}""",
            """{"is_child": "true", "parent_workno": "RJ01287594"}""",
            """{"parent_workno": "RJ01287594"}""",
        )

        translationInfos.forEach { translationInfo ->
            assertNull(client.parseTranslationParentWorkNo("RJ01287595", translationChildJson(translationInfo = translationInfo)))
        }
    }

    @Test
    fun parseTranslationParentWorkNo_rejectsMissingInvalidAndSelfParent() {
        val client = DlsiteProductInfoClient(OkHttpClient())
        val translationInfos = listOf(
            """{"is_child": true, "original_workno": "RJ01279210"}""",
            """{"is_child": true, "parent_workno": null}""",
            """{"is_child": true, "parent_workno": ""}""",
            """{"is_child": true, "parent_workno": "作品 RJ01287594"}""",
            """{"is_child": true, "parent_workno": "RJinvalid"}""",
            """{"is_child": true, "parent_workno": "RJ01287595"}""",
        )

        translationInfos.forEach { translationInfo ->
            assertNull(client.parseTranslationParentWorkNo("RJ01287595", translationChildJson(translationInfo = translationInfo)))
        }
    }

    @Test
    fun parseTranslationParentWorkNo_rejectsInvalidInputAndJson() {
        val client = DlsiteProductInfoClient(OkHttpClient())

        assertNull(client.parseTranslationParentWorkNo("", translationChildJson()))
        assertNull(client.parseTranslationParentWorkNo("invalid", translationChildJson(responseKey = "invalid", responseProductId = "invalid")))
        assertNull(client.parseTranslationParentWorkNo("RJ01287595", "not json"))
        assertNull(client.parseTranslationParentWorkNo("RJ01287595", "null"))
    }

    @Test
    fun fetchTranslationParentWorkNo_requestsExactProductAndRejectsUnsuccessfulResponse() = runBlocking {
        val requests = mutableListOf<Request>()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests += chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (requests.size == 1) 200 else 503)
                    .message("test response")
                    .body(translationChildJson().toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val client = DlsiteProductInfoClient(httpClient)

        assertEquals("RJ01287594", client.fetchTranslationParentWorkNo(" rj01287595 "))
        assertNull(client.fetchTranslationParentWorkNo("RJ01287595"))
        assertEquals(2, requests.size)
        requests.forEach { request ->
            assertEquals("www.dlsite.com", request.url.host)
            assertEquals("/maniax/product/info/ajax", request.url.encodedPath)
            assertEquals("RJ01287595", request.url.queryParameter("product_id"))
            assertEquals("1", request.url.queryParameter("cdn_cache_min"))
        }
    }

    private fun translationChildJson(
        responseKey: String = "RJ01287595",
        responseProductId: String = "RJ01287595",
        translationInfo: String = """{"is_child": true, "parent_workno": "RJ01287594"}""",
    ): String = """
        {
          "$responseKey": {
            "product_id": "$responseProductId",
            "translation_info": $translationInfo
          }
        }
    """.trimIndent()
}

