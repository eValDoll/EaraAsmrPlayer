package com.asmr.player.nas

import com.asmr.player.data.local.db.dao.LocalTreeCacheDao
import com.asmr.player.data.local.db.entities.LocalTreeCacheEntity
import org.json.JSONArray

internal data class WebDavFile(val relativePath: String, val uri: String, val size: Long)

internal suspend fun LocalTreeCacheDao.storeWebDavFiles(albumId: Long, source: String, files: List<WebDavFile>) {
    val payload = JSONArray()
    for (file in files) payload.put(JSONArray().put(file.relativePath).put(file.uri).put(file.size))
    upsert(LocalTreeCacheEntity(albumId = albumId, cacheKey = "webdav:$source", stamp = 0,
        payloadJson = payload.toString(), updatedAt = System.currentTimeMillis()))
}

internal suspend fun LocalTreeCacheDao.webDavFiles(albumId: Long, source: String): List<WebDavFile>? {
    val cached = getByAlbumAndKey(albumId, "webdav:$source") ?: return null
    return try {
        val array = JSONArray(cached.payloadJson)
        List(array.length()) { i ->
            val row = array.getJSONArray(i)
            WebDavFile(row.getString(0), row.getString(1), row.getLong(2))
        }
    } catch (_: org.json.JSONException) {
        null
    }
}
