package com.asmr.player.domain.model

enum class CollectedSearchSource(val apiPath: String) {
    AsmrOne("asmr-one"),
    JapaneseAsmr("jp-asmr");

    companion object {
        fun fromName(name: String?): CollectedSearchSource =
            entries.firstOrNull { it.name == name } ?: AsmrOne
    }
}
