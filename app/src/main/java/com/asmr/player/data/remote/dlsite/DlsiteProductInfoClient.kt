package com.asmr.player.data.remote.dlsite

import com.asmr.player.data.remote.NetworkHeaders
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class DlsiteLanguageEdition(
    val workno: String,
    val lang: String,
    val label: String,
    val displayOrder: Int
)

@Singleton
class DlsiteProductInfoClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val gson = Gson()

    internal fun parseLanguageEditions(productId: String, json: String): List<DlsiteLanguageEdition> {
        val clean = productId.trim().uppercase()
        if (clean.isBlank() || json.isBlank()) return emptyList()

        val root = runCatching { gson.fromJson(json, Map::class.java) as? Map<*, *> }.getOrNull() ?: return emptyList()
        val workObj = (root[clean] as? Map<*, *>)
            ?: (root.entries.firstOrNull { (k, _) ->
                (k as? String)?.trim()?.equals(clean, ignoreCase = true) == true
            }?.value as? Map<*, *>)
            ?: return emptyList()
        val rawItems = workObj?.get("dl_count_items") as? List<*> ?: return emptyList()

        return rawItems.mapNotNull { it as? Map<*, *> }
            .mapNotNull { item ->
                val editionType = (item["edition_type"] as? String).orEmpty().trim()
                if (editionType != "language") return@mapNotNull null
                val lang = (item["lang"] as? String).orEmpty().trim()
                val workno = (item["workno"] as? String).orEmpty().trim().uppercase()
                val label = (item["display_label"] as? String).orEmpty().trim().ifBlank {
                    (item["label"] as? String).orEmpty().trim()
                }
                val order = (item["display_order"] as? Number)?.toInt() ?: 0
                if (lang.isBlank() || workno.isBlank()) return@mapNotNull null
                if (lang !in setOf("JPN", "CHI_HANS", "CHI_HANT")) return@mapNotNull null
                DlsiteLanguageEdition(workno = workno, lang = lang, label = label, displayOrder = order)
            }
            .sortedWith(compareBy({ it.displayOrder }, { it.lang }))
    }

    suspend fun fetchLanguageEditions(productId: String): List<DlsiteLanguageEdition> = withContext(Dispatchers.IO) {
        val clean = productId.trim().uppercase()
        if (clean.isBlank()) return@withContext emptyList()

        val url = "https://www.dlsite.com/maniax/product/info/ajax".toHttpUrl()
            .newBuilder()
            .addQueryParameter("product_id", clean)
            .addQueryParameter("cdn_cache_min", "1")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkHeaders.USER_AGENT)
            .header("Referer", NetworkHeaders.REFERER_DLSITE)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", NetworkHeaders.getAcceptLanguage("ja"))
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) return@withContext emptyList()
            parseLanguageEditions(clean, body)
        }
    }
}
