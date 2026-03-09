package com.asmr.player.data.remote.auth

import android.content.Context

class DlsiteAuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("dlsite_auth", Context.MODE_PRIVATE)

    fun saveDlsiteCookie(cookie: String, expiresAtMs: Long? = null) {
        val editor = prefs.edit().putString(KEY_COOKIE_DLSITE, cookie.trim())
        if (expiresAtMs != null) {
            editor.putLong(KEY_COOKIE_DLSITE_EXPIRES_AT_MS, expiresAtMs)
        } else {
            editor.remove(KEY_COOKIE_DLSITE_EXPIRES_AT_MS)
        }
        editor.apply()
    }

    fun savePlayCookie(cookie: String, expiresAtMs: Long? = null) {
        val editor = prefs.edit().putString(KEY_COOKIE_PLAY, cookie.trim())
        if (expiresAtMs != null) {
            editor.putLong(KEY_COOKIE_PLAY_EXPIRES_AT_MS, expiresAtMs)
        } else {
            editor.remove(KEY_COOKIE_PLAY_EXPIRES_AT_MS)
        }
        editor.apply()
    }

    fun getDlsiteCookie(): String = prefs.getString(KEY_COOKIE_DLSITE, "").orEmpty()
    fun getPlayCookie(): String = prefs.getString(KEY_COOKIE_PLAY, "").orEmpty()

    fun getDlsiteCookieExpiresAtMs(): Long? = prefs.getLong(KEY_COOKIE_DLSITE_EXPIRES_AT_MS, -1L).takeIf { it > 0L }
    fun getPlayCookieExpiresAtMs(): Long? = prefs.getLong(KEY_COOKIE_PLAY_EXPIRES_AT_MS, -1L).takeIf { it > 0L }

    fun isDlsiteLoggedIn(): Boolean = getDlsiteCookie().isNotBlank()
    fun isPlayLoggedIn(): Boolean = getPlayCookie().isNotBlank()
    fun isLoggedIn(): Boolean = isDlsiteLoggedIn() || isPlayLoggedIn()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_COOKIE_DLSITE = "cookie_dlsite"
        private const val KEY_COOKIE_PLAY = "cookie_play"
        private const val KEY_COOKIE_DLSITE_EXPIRES_AT_MS = "cookie_dlsite_expires_at_ms"
        private const val KEY_COOKIE_PLAY_EXPIRES_AT_MS = "cookie_play_expires_at_ms"
    }
}
