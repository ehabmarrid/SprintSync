package com.ehab.sprintsync.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.ehab.sprintsync.R
import com.ehab.sprintsync.data.RepositoryProvider
import com.ehab.sprintsync.model.UserProfile
import com.ehab.sprintsync.ui.common.InsetsAwareActivity
import com.ehab.sprintsync.ui.projects.ProjectsActivity
import com.ehab.sprintsync.util.AuthValidator
import com.ehab.sprintsync.util.BidiText
import com.ehab.sprintsync.util.SignalManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : InsetsAwareActivity() {
    private val repository by lazy { RepositoryProvider.repository }

    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var loginButton: MaterialButton
    private lateinit var demoButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        bindViews()
        findViewById<android.widget.TextView>(R.id.modeBadge).setText(
            if (repository.isDemoMode) R.string.demo_mode else R.string.firebase_mode
        )
        demoButton.visibility = if (repository.isDemoMode) View.VISIBLE else View.GONE

        loginButton.setOnClickListener { attemptLogin() }
        demoButton.setOnClickListener {
            emailInput.setText(DEMO_EMAIL)
            passwordInput.setText(DEMO_PASSWORD)
            attemptLogin()
        }
        findViewById<MaterialButton>(R.id.registerButton).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun bindViews() {
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginButton = findViewById(R.id.loginButton)
        demoButton = findViewById(R.id.demoButton)
    }

    private fun attemptLogin() {
        emailLayout.error = null
        passwordLayout.error = null
        val email = emailInput.text?.toString().orEmpty().trim()
        val password = passwordInput.text?.toString().orEmpty()

        var isValid = true
        if (!AuthValidator.isValidEmail(email)) {
            emailLayout.error = getString(R.string.invalid_email)
            isValid = false
        }
        if (!AuthValidator.isValidPassword(password)) {
            passwordLayout.error = getString(R.string.invalid_password)
            isValid = false
        }
        if (!isValid) return

        setLoading(true)
        repository.signIn(email, password) { result ->
            setLoading(false)
            result.onSuccess(::openProjects)
                .onFailure(::showError)
        }
    }

    private fun openProjects(user: UserProfile) {
        SignalManager.success(this, getString(R.string.signed_in_success, BidiText.isolate(user.name)))
        startActivity(
            Intent(this, ProjectsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }

    private fun showError(error: Throwable) {
        SignalManager.error(
            this,
            getString(
                R.string.something_went_wrong,
                BidiText.isolate(error.localizedMessage.orEmpty())
            )
        )
    }

    private fun setLoading(loading: Boolean) {
        loginButton.isEnabled = !loading
        demoButton.isEnabled = !loading
        loginButton.setText(if (loading) R.string.loading else R.string.login)
    }

    companion object {
        private const val DEMO_EMAIL = "ehab@sprintsync.dev"
        private const val DEMO_PASSWORD = "123456"
    }
}
