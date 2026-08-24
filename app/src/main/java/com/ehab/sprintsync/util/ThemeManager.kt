package com.ehab.sprintsync.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

object ThemeManager {
    private const val PREFERENCES_NAME = "sprint_sync_theme"
    private const val KEY_THEME_MODE = "theme_mode"

    /** Superseded by [KEY_THEME_MODE]; removed on first write so it cannot linger. */
    private const val KEY_LEGACY_DARK_MODE = "dark_mode"

    /**
     * The three states the toolbar action cycles through. [SYSTEM] is the default, so a
     * fresh install follows the device setting instead of forcing dark.
     */
    enum class ThemeMode(val storedValue: String, val nightMode: Int) {
        SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

        companion object {
            fun fromStored(value: String?): ThemeMode =
                entries.firstOrNull { it.storedValue == value } ?: SYSTEM
        }
    }

    fun applySavedTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(currentMode(context).nightMode)
    }

    /** Advances SYSTEM -> LIGHT -> DARK -> SYSTEM and applies the result. */
    fun toggle(context: Context) {
        val next = when (currentMode(context)) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        preferences(context).edit {
            putString(KEY_THEME_MODE, next.storedValue)
            remove(KEY_LEGACY_DARK_MODE)
        }
        applySavedTheme(context)
    }

    fun currentMode(context: Context): ThemeMode =
        ThemeMode.fromStored(preferences(context).getString(KEY_THEME_MODE, null))

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
