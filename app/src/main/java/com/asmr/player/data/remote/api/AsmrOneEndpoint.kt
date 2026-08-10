package com.asmr.player.data.remote.api

object AsmrOneEndpoint {
    const val BACKUP = -1
    const val MAIN = 0
    const val MIRROR_100 = 100
    const val MIRROR_200 = 200
    const val MIRROR_300 = 300

    val options = listOf(MAIN, MIRROR_100, MIRROR_200, MIRROR_300, BACKUP)

    fun normalize(value: Int): Int = if (value in options) value else MIRROR_200

    fun displayName(value: Int): String = when (normalize(value)) {
        BACKUP -> "备用"
        MAIN -> "asmr.one"
        MIRROR_100 -> "asmr-100"
        MIRROR_300 -> "asmr-300"
        else -> "asmr-200"
    }

    fun directBaseUrl(value: Int): String? = when (normalize(value)) {
        BACKUP -> null
        MAIN -> AsmrOneApi.BASE_URL
        MIRROR_100 -> Asmr100Api.BASE_URL
        MIRROR_300 -> Asmr300Api.BASE_URL
        else -> Asmr200Api.BASE_URL
    }
}
