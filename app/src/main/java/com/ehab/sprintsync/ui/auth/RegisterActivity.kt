package com.ehab.sprintsync.ui.auth

import android.content.Intent
import android.os.Bundle
import com.ehab.sprintsync.R
import com.ehab.sprintsync.data.RepositoryProvider
import com.ehab.sprintsync.ui.common.InsetsAwareActivity
import com.ehab.sprintsync.ui.projects.ProjectsActivity
import com.ehab.sprintsync.util.AuthValidator
import com.ehab.sprintsync.util.BidiText
import com.ehab.sprintsync.util.SignalManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class RegisterActivity : InsetsAwareActivity() {
    private val repository by lazy { RepositoryProvider.repository }

    private lateinit var nameLayout: TextInputLayout
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var createButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        bindViews()

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<android.widget.TextView>(R.id.modeBadge).setText(
            if (repository.isDemoMode) R.string.demo_mode else R.string.firebase_mode
        )
        createButton.setOnClickListener { attemptRegistration() }
    }

    private fun bindViews() {
        nameLayout = findViewById(R.id.nameLayout)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout)
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        createButton = findViewById(R.id.createAccountButton)
    }

    private fun attemptRegistration() {
        listOf(nameLayout, emailLayout, passwordLayout, confirmPasswordLayout)
            .forEach { it.error = null }

        val name = nameInput.text?.toString().orEmpty().trim()
        val email = emailInput.text?.toString().orEmpty().trim()
        val password = passwordInput.text?.toString().orEmpty()
        val confirmation = confirmPasswordInput.text?.toString().orEmpty()
        var isValid = true

        if (name.isBlank()) {
            nameLayout.error = getString(R.string.required_field)
            isValid = false
        }
        if (!AuthValidator.isValidEmail(email)) {
            emailLayout.error = getString(R.string.invalid_email)
            isValid = false
        }
        if (!AuthValidator.isValidPassword(password)) {
            passwordLayout.error = getString(R.string.invalid_password)
            isValid = false
        }
        if (password != confirmation) {
            confirmPasswordLayout.error = getString(R.string.passwords_do_not_match)
            isValid = false
        }
        if (!isValid) return

        setLoading(true)
        repository.signUp(name, email, password) { result ->
            setLoading(false)
            result.onSuccess {
                SignalManager.success(this, getString(R.string.account_created))
                startActivity(
                    Intent(this, ProjectsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }.onFailure {
                SignalManager.error(
                    this,
                    getString(
                        R.string.something_went_wrong,
                        BidiText.isolate(it.localizedMessage.orEmpty())
                    )
                )
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        createButton.isEnabled = !loading
        createButton.setText(if (loading) R.string.loading else R.string.create_account)
    }
}
