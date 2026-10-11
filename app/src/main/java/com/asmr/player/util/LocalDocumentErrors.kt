package com.asmr.player.util

import java.io.FileNotFoundException

internal fun isMissingLocalDocumentFailure(error: Throwable): Boolean {
    return generateSequence(error) { it.cause }
        .any { cause ->
            cause is FileNotFoundException ||
                cause.message.orEmpty().contains("FileNotFoundException", ignoreCase = true) ||
                cause.message.orEmpty().contains("Missing file for", ignoreCase = true)
        }
}
