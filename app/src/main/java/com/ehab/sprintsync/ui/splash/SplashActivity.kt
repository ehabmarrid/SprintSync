package com.ehab.sprintsync.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.ehab.sprintsync.R
import com.ehab.sprintsync.data.RepositoryProvider
import com.ehab.sprintsync.ui.auth.LoginActivity
import com.ehab.sprintsync.ui.common.InsetsAwareActivity
import com.ehab.sprintsync.ui.projects.ProjectsActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : InsetsAwareActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val navigateRunnable = Runnable {
        val destination = if (RepositoryProvider.repository.currentUser() == null) {
            LoginActivity::class.java
        } else {
            ProjectsActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        handler.postDelayed(navigateRunnable, SPLASH_DURATION_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigateRunnable)
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DURATION_MS = 850L
    }
}
