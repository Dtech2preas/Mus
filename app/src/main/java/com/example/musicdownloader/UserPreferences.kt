package com.example.musicdownloader

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UserPreferences {
    private const val PREF_NAME = "user_prefs"
    private const val KEY_IS_FIRST_RUN = "is_first_run"
    private const val KEY_GENRES = "saved_genres"
    private const val KEY_LAST_REFRESHED = "last_genre_refreshed"
    private const val KEY_THEME_COLOR = "theme_color"

    // Ad System Keys
    private const val KEY_FIRST_OPEN_TIME = "first_open_time"
    private const val KEY_DAILY_DOWNLOADS = "daily_downloads_count"
    private const val KEY_LAST_DOWNLOAD_DATE = "last_download_date"
    private const val KEY_LAST_AD_SHOWN_DATE = "last_ad_shown_date"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun isFirstRun(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_FIRST_RUN, true)
    }

    fun setFirstRunCompleted(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_IS_FIRST_RUN, false)
        }
    }

    fun getGenres(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_GENRES, emptySet()) ?: emptySet()
    }

    fun saveGenres(context: Context, genres: Set<String>) {
        getPrefs(context).edit {
            putStringSet(KEY_GENRES, genres)
        }
    }

    fun addGenre(context: Context, genre: String) {
        val current = getGenres(context).toMutableSet()
        current.add(genre)
        saveGenres(context, current)
    }

    fun removeGenre(context: Context, genre: String) {
        val current = getGenres(context).toMutableSet()
        current.remove(genre)
        saveGenres(context, current)
    }

    fun getLastGenreRefreshTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_REFRESHED, 0L)
    }

    fun setLastGenreRefreshTime(context: Context, timestamp: Long) {
        getPrefs(context).edit {
            putLong(KEY_LAST_REFRESHED, timestamp)
        }
    }

    fun getThemeColor(context: Context): Long {
        // Default to Electric Purple (0xFF7D5FFF)
        return getPrefs(context).getLong(KEY_THEME_COLOR, 0xFF7D5FFF)
    }

    fun setThemeColor(context: Context, color: Long) {
        getPrefs(context).edit {
            putLong(KEY_THEME_COLOR, color)
        }
    }

    // --- Ad System Methods ---

    fun getFirstOpenTime(context: Context): Long {
        var time = getPrefs(context).getLong(KEY_FIRST_OPEN_TIME, 0L)
        if (time == 0L) {
            // If missing (existing user), initialize to now
            time = System.currentTimeMillis()
            getPrefs(context).edit { putLong(KEY_FIRST_OPEN_TIME, time) }
        }
        return time
    }

    fun getDailyDownloadCount(context: Context): Int {
        val prefs = getPrefs(context)
        val lastDate = prefs.getString(KEY_LAST_DOWNLOAD_DATE, "")
        val today = getTodayDate()

        return if (lastDate == today) {
            prefs.getInt(KEY_DAILY_DOWNLOADS, 0)
        } else {
            0
        }
    }

    fun incrementDailyDownloadCount(context: Context) {
        val prefs = getPrefs(context)
        val today = getTodayDate()
        val lastDate = prefs.getString(KEY_LAST_DOWNLOAD_DATE, "")

        var count = if (lastDate == today) {
            prefs.getInt(KEY_DAILY_DOWNLOADS, 0)
        } else {
            0
        }

        count++

        prefs.edit {
            putString(KEY_LAST_DOWNLOAD_DATE, today)
            putInt(KEY_DAILY_DOWNLOADS, count)
        }
    }

    fun isAdShownToday(context: Context): Boolean {
        val lastShown = getPrefs(context).getString(KEY_LAST_AD_SHOWN_DATE, "")
        return lastShown == getTodayDate()
    }

    fun setAdShownToday(context: Context) {
        getPrefs(context).edit {
            putString(KEY_LAST_AD_SHOWN_DATE, getTodayDate())
        }
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
