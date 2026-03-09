package com.asmr.player.util

import android.content.Context

class ScanRootsStore(context: Context) {
    private val prefs = context.getSharedPreferences("scan_roots", Context.MODE_PRIVATE)

    fun getRoots(): Set<String> {
        return prefs.getStringSet("roots", emptySet()) ?: emptySet()
    }

    fun addRoot(uri: String): Boolean {
        val current = getRoots().toMutableSet()
        val added = current.add(uri)
        if (added) {
            prefs.edit().putStringSet("roots", current).apply()
        }
        return added
    }

    fun removeRoot(uri: String) {
        val current = getRoots().toMutableSet()
        current.remove(uri)
        prefs.edit().putStringSet("roots", current).apply()
    }
}
