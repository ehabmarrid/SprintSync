package com.ehab.sprintsync.data

import android.content.Context
import com.ehab.sprintsync.data.firebase.FirebaseBootstrap
import com.ehab.sprintsync.data.firebase.FirebaseSprintRepository
import com.ehab.sprintsync.data.local.LocalSprintRepository

object RepositoryProvider {
    lateinit var repository: SprintRepository
        private set

    fun initialize(context: Context) {
        if (::repository.isInitialized) return

        val applicationContext = context.applicationContext
        val firebaseApp = FirebaseBootstrap.initialize(applicationContext)
        repository = if (firebaseApp != null) {
            FirebaseSprintRepository(applicationContext, firebaseApp)
        } else {
            LocalSprintRepository(applicationContext)
        }
    }
}

