package com.asmr.player.util

/** 作品编号与实际目录共同决定冲突范围；目录边界必须包含分隔符。 */
data class LocalFileScope(
    val identities: Set<String> = emptySet(),
    val roots: Set<String> = emptySet(),
    val global: Boolean = false,
) {
    fun overlaps(other: LocalFileScope): Boolean = global || other.global ||
        identities.any(other.identities::contains) || roots.any { left ->
            other.roots.any { right -> left == right || left.startsWith("$right/") || right.startsWith("$left/") }
        }

    operator fun plus(other: LocalFileScope) = LocalFileScope(
        identities + other.identities, roots + other.roots, global || other.global
    )

    companion object {
        val Global = LocalFileScope(global = true)
    }
}
