package com.ehab.sprintsync

import android.app.Application
import com.ehab.sprintsync.data.RepositoryProvider
import com.ehab.sprintsync.util.ThemeManager

class SprintSyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applySavedTheme(this)
        RepositoryProvider.initialize(this)
    }
}

