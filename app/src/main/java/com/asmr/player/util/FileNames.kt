package com.asmr.player.util

private val invalidFolderNameCharacters = Regex("""[\\/:*?"<>|]""")

fun sanitizeFolderName(name: String): String =
    name.trim().ifEmpty { "item" }.replace(invalidFolderNameCharacters, "_")
