package com.asmr.player.cache

data class CacheImageModel(
    val data: Any,
    val headers: Map<String, String> = emptyMap(),
    val keyTag: String = ""
)

