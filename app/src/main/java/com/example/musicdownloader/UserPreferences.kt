package com.example.musicdownloader

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object UserPreferences {
    private const val PREF_NAME = "user_prefs"
    private const val KEY_IS_FIRST_RUN = "is_first_run"
    private const val KEY_GENRES = "saved_genres"

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
}
