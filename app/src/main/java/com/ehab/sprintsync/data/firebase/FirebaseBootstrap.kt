package com.ehab.sprintsync.data.firebase

import android.content.Context
import com.ehab.sprintsync.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase

object FirebaseBootstrap {
    fun initialize(context: Context): FirebaseApp? {
        val requiredValues = listOf(
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_DATABASE_URL,
            BuildConfig.FIREBASE_STORAGE_BUCKET
        )
        if (requiredValues.any(String::isBlank)) return null

        return runCatching {
            FirebaseApp.getApps(context).firstOrNull()
                ?: FirebaseApp.initializeApp(
                    context,
                    FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setDatabaseUrl(BuildConfig.FIREBASE_DATABASE_URL)
                        .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                        .build()
                )
        }.getOrNull()?.also(::enableOfflinePersistence)
    }

    /**
     * Caches board and task data on disk so the app stays usable on a flaky network.
     *
     * Firebase only accepts this before anything else touches the database, and throws on
     * a second call, so it is wrapped: a repeated [initialize] must not take the app down.
     */
    private fun enableOfflinePersistence(app: FirebaseApp) {
        runCatching { FirebaseDatabase.getInstance(app).setPersistenceEnabled(true) }
    }
}

