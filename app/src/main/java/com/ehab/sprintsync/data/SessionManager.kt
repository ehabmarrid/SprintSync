package com.ehab.sprintsync.data

import android.content.Context
import androidx.core.content.edit
import com.ehab.sprintsync.model.UserProfile
import com.google.gson.Gson

class SessionManager(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getUser(): UserProfile? =
        preferences.getString(KEY_USER, null)
            ?.let { runCatching { gson.fromJson(it, UserProfile::class.java) }.getOrNull() }

    fun saveUser(user: UserProfile) {
        preferences.edit { putString(KEY_USER, gson.toJson(user)) }
    }

    fun clear() {
        preferences.edit { remove(KEY_USER) }
    }

    companion object {
        private const val PREFERENCES_NAME = "sprint_sync_session"
        private const val KEY_USER = "current_user"
    }
}
