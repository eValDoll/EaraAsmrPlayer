package com.asmr.player.data.remote.dlsite

import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.awaitResponse
import com.asmr.player.data.remote.scraper.DLSITE_DOMAIN
import com.asmr.player.data.remote.scraper.storeSegment
import com.asmr.player.util.DlsiteWorkNo
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
        val workObj = parseProductInfo(clean, json) ?: return emptyList()
        return parseLanguageEditions(clean, workObj)
    }

    private fun parseLanguageEditions(clean: String, workObj: Map<*, *>): List<DlsiteLanguageEdition> {
        val rawItems = workObj["dl_count_items"] as? List<*> ?: emptyList<Any?>()

        val listedEditions = rawItems.mapNotNull { it as? Map<*, *> }
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
                if (lang !in SUPPORTED_LANGUAGES) return@mapNotNull null
                DlsiteLanguageEdition(workno = workno, lang = lang, label = label, displayOrder = order)
            }

        val translationInfo = workObj["translation_info"] as? Map<*, *>
        val currentLang = (translationInfo?.get("lang") as? String).orEmpty().trim()
        val currentEdition = currentLang
            .takeIf { it in SUPPORTED_LANGUAGES }
            ?.let { lang ->
                listedEditions.firstOrNull { it.lang == lang }
                    ?.copy(workno = clean)
                    ?: DlsiteLanguageEdition(
                        workno = clean,
                        lang = lang,
                        label = defaultLanguageLabel(lang),
                        displayOrder = defaultLanguageOrder(lang)
                    )
            }

        return buildList {
            if (currentEdition != null) add(currentEdition)
            addAll(listedEditions.filterNot { it.lang == currentLang })
        }
            .sortedWith(compareBy({ it.displayOrder }, { it.lang }))
    }

    internal fun parseTranslationParentWorkNo(productId: String, json: String): String? {
        val clean = productId.trim().uppercase()
        val workObj = parseProductInfo(clean, json) ?: return null
        return parseTranslationParentWorkNo(clean, workObj)
    }

    private fun parseTranslationParentWorkNo(clean: String, workObj: Map<*, *>): String? {
        if (clean.isBlank() || DlsiteWorkNo.normalizeWorkNo(clean) != clean) return null
        if ((workObj["product_id"] as? String)?.trim()?.uppercase() != clean) return null
        val translationInfo = workObj["translation_info"] as? Map<*, *> ?: return null
        if (translationInfo["is_child"] != true) return null
        val parent = DlsiteWorkNo.normalizeWorkNo((translationInfo["parent_workno"] as? String).orEmpty())
        return parent.takeIf { it.isNotBlank() && it != clean }
    }

    private fun parseProductInfo(productId: String, json: String): Map<*, *>? {
        val clean = productId.trim().uppercase()
        if (clean.isBlank() || json.isBlank()) return null
        val root = runCatching { gson.fromJson(json, Map::class.java) }.getOrNull() ?: return null
        return (root[clean] as? Map<*, *>)
            ?: (root.entries.firstOrNull { (key, _) ->
                (key as? String)?.trim()?.equals(clean, ignoreCase = true) == true
            }?.value as? Map<*, *>)
    }

    private fun defaultLanguageLabel(lang: String): String {
        return when (lang) {
            "CHI_HANS" -> "简体中文"
            "CHI_HANT" -> "繁体中文"
            else -> "日本語"
        }
    }

    private fun defaultLanguageOrder(lang: String): Int {
        return when (lang) {
            "CHI_HANS" -> 5
            "CHI_HANT" -> 7
            else -> 1
        }
    }

    suspend fun fetchLanguageEditions(productId: String): List<DlsiteLanguageEdition> = withContext(Dispatchers.IO) {
        val clean = productId.trim().uppercase()
        if (clean.isBlank()) return@withContext emptyList()
        val workObj = fetchProductInfo(clean) ?: return@withContext emptyList()
        parseLanguageEditions(clean, workObj)
    }

    suspend fun fetchTranslationParentWorkNo(productId: String): String? = withContext(Dispatchers.IO) {
        val clean = productId.trim().uppercase()
        if (clean.isBlank() || DlsiteWorkNo.normalizeWorkNo(clean) != clean) return@withContext null
        val workObj = fetchProductInfo(clean) ?: return@withContext null
        parseTranslationParentWorkNo(clean, workObj)
    }

    private suspend fun fetchProductInfo(productId: String): Map<*, *>? {
        val url = "$DLSITE_DOMAIN${storeSegment()}/product/info/ajax".toHttpUrl()
            .newBuilder()
            .addQueryParameter("product_id", productId)
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

        return okHttpClient.newCall(request).awaitResponse().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) return@use null
            parseProductInfo(productId, body)
        }
    }

    private companion object {
        val SUPPORTED_LANGUAGES = setOf("JPN", "CHI_HANS", "CHI_HANT")
    }
}
