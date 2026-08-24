package com.ehab.sprintsync.util

object AuthValidator {
    private val emailPattern = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)

    fun isValidEmail(email: String): Boolean = emailPattern.matches(email.trim())
    fun isValidPassword(password: String): Boolean = password.length >= 6
}

