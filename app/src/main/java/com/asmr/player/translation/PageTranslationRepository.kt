package com.asmr.player.translation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal val PageTranslationLanguages = linkedMapOf("zh-CN" to "简中", "zh-TW" to "繁中", "ja" to "日文", "en" to "英文", "ru" to "俄语", "ko" to "韩语")
internal data class PageTranslationSettings(val automatic: Boolean = false, val target: String = "zh-CN")
private val Context.pageTranslationDataStore by preferencesDataStore("page_translation")

@Singleton
internal class PageTranslationPreferences(private val store: DataStore<Preferences>) {
    @Inject constructor(@ApplicationContext context: Context) : this(context.pageTranslationDataStore)
    private val automaticKey = booleanPreferencesKey("automatic")
    private val targetKey = stringPreferencesKey("target")
    val settings = store.data.map {
        PageTranslationSettings(
            it[automaticKey] ?: false,
            it[targetKey]?.takeIf(PageTranslationLanguages::containsKey) ?: "zh-CN",
        )
    }.catch { if (it is IOException) emit(PageTranslationSettings()) else throw it }

    suspend fun setAutomatic(value: Boolean) { store.edit { it[automaticKey] = value } }
    suspend fun setTarget(value: String) {
        require(value in PageTranslationLanguages)
        store.edit { it[targetKey] = value }
    }
}

/** Only display strings live here; album metadata, paths and search keys remain untouched. */
internal data class PageTranslationBatchResult(val translations: Map<String, String>, val failure: String? = null)

@Singleton
internal class PageTranslationRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val client: PageTranslationClient,
) {
    private val cache = PageTranslationCache(context)
    private val gate = Mutex()
    private val memory = object : LinkedHashMap<Pair<String, String>, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, String>, String>?) = size > 1000
    }
    private var nextRequestAt = 0L
    private var failureUntil = 0L
    private var failureMessage = "页面翻译失败，请检查网络后重试"

    fun cached(text: String, target: String): String? = synchronized(memory) { memory[text to target] }

    private fun remember(values: Map<String, String>, target: String) {
        synchronized(memory) { values.forEach { (text, translated) -> memory[text to target] = translated } }
    }

    suspend fun translateBatch(texts: List<String>, target: String): PageTranslationBatchResult = withContext(Dispatchers.IO) {
        val unique = texts.distinct()
        // Keep automatic-detection cache entries separate from legacy fixed-source translations.
        val languagePair = "auto:$target"
        gate.withLock {
            // Recheck under the gate: overlapping page batches reuse completed translations.
            val translated = linkedMapOf<String, String>()
            unique.forEach { text ->
                if (!shouldTranslatePageText(text)) translated[text] = text
                else cached(text, target)?.let { translated[text] = it }
            }
            val diskValues = cache.read(unique.filterNot(translated::containsKey), languagePair)
            translated.putAll(diskValues)
            remember(diskValues, target)
            val missing = unique.filterNot(translated::containsKey)
            if (missing.isEmpty()) return@withLock PageTranslationBatchResult(translated)
            if (System.currentTimeMillis() < failureUntil) return@withLock PageTranslationBatchResult(translated, failureMessage)
            for (batch in pageTranslationBatches(missing)) {
                delay((nextRequestAt - System.currentTimeMillis()).coerceAtLeast(0))
                try {
                    val values = batch.zip(client.translateBatch(batch, target)).toMap()
                    cache.write(values, languagePair)
                    remember(values, target)
                    translated.putAll(values)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: IOException) {
                    failureMessage = (error as? PageTranslationException)?.message ?: "页面翻译失败，请检查网络后重试"
                    failureUntil = System.currentTimeMillis() + ((error as? PageTranslationException)?.cooldownMillis ?: 30_000)
                    // Keep successful earlier chunks; never fan out a failed batch into single requests.
                    return@withLock PageTranslationBatchResult(translated, failureMessage)
                } finally {
                    nextRequestAt = System.currentTimeMillis() + 250
                }
            }
            PageTranslationBatchResult(translated)
        }
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        gate.withLock {
            cache.clear()
            synchronized(memory) { memory.clear() }
        }
    }
}

private class PageTranslationCache(context: Context) : SQLiteOpenHelper(context, "page_translation_cache.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE translations (source TEXT NOT NULL, target TEXT NOT NULL, translated TEXT NOT NULL, created INTEGER NOT NULL, PRIMARY KEY(source, target))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun read(texts: List<String>, target: String): Map<String, String> = buildMap {
        texts.chunked(900).forEach { chunk ->
            readableDatabase.query(
                "translations", arrayOf("source", "translated"),
                "target = ? AND source IN (${chunk.joinToString(",") { "?" }})",
                (listOf(target) + chunk).toTypedArray(), null, null, null,
            ).use { cursor -> while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }
    }

    fun write(values: Map<String, String>, target: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            values.forEach { (text, translated) ->
                db.insertWithOnConflict("translations", null, ContentValues().apply {
                    put("source", text)
                    put("target", target)
                    put("translated", translated)
                    put("created", System.currentTimeMillis())
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.execSQL("DELETE FROM translations WHERE rowid IN (SELECT rowid FROM translations ORDER BY created DESC LIMIT -1 OFFSET 3000)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun clear() { writableDatabase.delete("translations", null, null) }
}
