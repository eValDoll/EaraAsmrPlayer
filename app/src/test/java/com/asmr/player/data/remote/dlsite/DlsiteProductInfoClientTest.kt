package com.asmr.player.data.remote.dlsite

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
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
}

