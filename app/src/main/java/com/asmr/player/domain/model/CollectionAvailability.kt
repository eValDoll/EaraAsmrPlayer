package com.asmr.player.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class CollectionAvailability(
    val hasAsmrOne: Boolean = false,
    /** 旧服务端未返回 sources 时，JP-ASMR 的收录状态未知。 */
    val hasJapaneseAsmr: Boolean? = null,
    val asmrOneWorkId: Int? = null,
    val japaneseAsmrPageUrl: String = ""
) {
    val label: String?
        get() = when {
            hasAsmrOne && hasJapaneseAsmr != false -> "收录"
            hasAsmrOne -> "asmr.one"
            hasJapaneseAsmr == true -> "jp-asmr"
            else -> null
        }

    val preferredSource: CollectedSearchSource?
        get() = when {
            hasAsmrOne -> CollectedSearchSource.AsmrOne
            hasJapaneseAsmr == true -> CollectedSearchSource.JapaneseAsmr
            else -> null
        }
}
