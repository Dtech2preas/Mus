package com.example.musicdownloader

import android.content.Context
import android.content.SharedPreferences

object CookieManager {
    private const val PREF_NAME = "cookie_prefs"
    private const val KEY_COOKIE = "YOUTUBE_COOKIE"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveCookie(context: Context, cookie: String) {
        getPrefs(context).edit().putString(KEY_COOKIE, cookie).apply()
    }

    fun getCookie(context: Context): String {
        return getPrefs(context).getString(KEY_COOKIE, "") ?: ""
    }
}
